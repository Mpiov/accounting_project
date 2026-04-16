package com.autobook.cn.core.data.impl

import android.util.Log
import com.autobook.cn.core.data.LocalLlmRequest
import com.autobook.cn.core.data.LocalLlmResponse
import com.autobook.cn.core.data.LocalModelRepository
import com.autobook.cn.core.data.OnDeviceLlmEngine
import com.autobook.cn.core.llm.LocalTextGenerationRequest
import com.autobook.cn.core.llm.LocalTextGenerationRuntime
import com.autobook.cn.core.model.LocalModelInstallStatus
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.OperationResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * 基于 llama.cpp 真本地运行时的 on-device LLM 引擎实现。
 *
 * 用途说明：
 * - 负责把上层标准化 `LocalLlmRequest` 转成严格结构化 prompt，
 *   再把真实模型返回的文本解析回 `LocalLlmResponse`。
 * - 业务层只信任结构化字段；如果运行时失败、模型未安装或输出不合法，会交给上游分析器继续走回退链路。
 *
 * 参数说明：
 * - runtime：底层本地文本生成运行时，当前由 `llama.cpp + GGUF` 实现。
 * - localModelRepository：模型选择与安装状态来源，用于确认当前模型文件是否可用。
 */
@Singleton
class LlamaCppOnDeviceLlmEngine @Inject constructor(
    private val runtime: LocalTextGenerationRuntime,
    private val localModelRepository: LocalModelRepository,
) : OnDeviceLlmEngine {
    /**
     * 调用真本地运行时完成一次结构化抽取。
     *
     * 参数说明：
     * - request：已经标准化的本地 LLM 请求，包含通知正文与规则层候选。
     */
    override suspend fun analyze(request: LocalLlmRequest): OperationResult<LocalLlmResponse> {
        val snapshot = localModelRepository.snapshot.first()
        val modelPath = snapshot.selectedModelPath
        if (snapshot.installStatus != LocalModelInstallStatus.READY || modelPath.isNullOrBlank()) {
            logWarn(
                TAG,
                "跳过真本地 LLM：installStatus=${snapshot.installStatus} selectedModelId=${snapshot.selectedModelId} modelPath=${modelPath.orEmpty()}",
            )
            return OperationResult.RetryableFailure("Selected local model is not installed yet")
        }

        return try {
            logDebug(
                TAG,
                "开始调用真本地 LLM：selectedModelId=${snapshot.selectedModelId} modelPath=$modelPath",
            )
            val completion = runtime.generate(
                LocalTextGenerationRequest(
                    modelPath = modelPath,
                    systemPrompt = LOCAL_LLM_SYSTEM_PROMPT,
                    userPrompt = buildUserPrompt(request),
                    maxTokens = LOCAL_LLM_MAX_TOKENS,
                ),
            )
            logDebug(
                TAG,
                "真本地 LLM 原始输出预览=${completion.text.toLogPreview()}",
            )
            val structuredResponse = parseStructuredResponse(completion.text)
                ?: run {
                    logWarn(TAG, "真本地 LLM 输出无法解析为结构化 JSON")
                    return OperationResult.RetryableFailure("llama.cpp returned non-parseable structured output")
                }
            OperationResult.Success(
                LocalLlmResponse(
                    direction = structuredResponse.direction,
                    categoryHint = structuredResponse.categoryHint,
                    merchant = structuredResponse.merchant,
                    confidenceScore = structuredResponse.confidenceScore,
                    explanation = structuredResponse.explanation,
                    shouldAutoPost = structuredResponse.shouldAutoPost,
                    modelVersion = "${completion.backendLabel}:${snapshot.selectedModelId}",
                ),
            )
        } catch (error: UnsatisfiedLinkError) {
            logError(TAG, "真本地 LLM native 装载失败", error)
            OperationResult.RetryableFailure("llama.cpp native library is unavailable: ${error.message ?: error.javaClass.simpleName}")
        } catch (error: IllegalStateException) {
            logError(TAG, "真本地 LLM 运行时状态异常", error)
            OperationResult.RetryableFailure("llama.cpp runtime state error: ${error.message ?: error.javaClass.simpleName}")
        } catch (error: IllegalArgumentException) {
            logError(TAG, "真本地 LLM prompt 参数非法", error)
            OperationResult.RetryableFailure("llama.cpp prompt input is invalid: ${error.message ?: error.javaClass.simpleName}")
        } catch (error: Exception) {
            logError(TAG, "真本地 LLM 执行失败", error)
            OperationResult.RetryableFailure("llama.cpp runtime failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    /**
     * 构造 user prompt，把规则层候选完整传给端侧模型。
     *
     * 参数说明：
     * - request：当前通知与规则候选的统一输入。
     */
    private fun buildUserPrompt(request: LocalLlmRequest): String {
        val payload = buildJsonObject {
            if (request.title != null) {
                put("title", request.title)
            } else {
                put("title", JsonNull)
            }
            if (request.body != null) {
                put("body", request.body)
            } else {
                put("body", JsonNull)
            }
            put("sourceApp", request.sourceApp.name)
            putJsonObject("ruleCandidate") {
                put("amount", request.seedAmount)
                put("direction", request.seedDirection.name)
                put("merchant", request.seedMerchant)
                put("categoryHint", request.seedCategoryHint)
                put("confidenceScore", request.seedConfidenceScore)
                put("confidenceReason", request.seedConfidenceReason)
                put("parsePath", request.seedParsePath)
            }
        }
        return buildString {
            // 说明：Qwen3 默认可能先输出 `<think>` 思维链；这里显式附加 `/no_think`，优先要求直接返回结构化 JSON。
            appendLine("/no_think")
            appendLine("请根据以下通知与规则候选，判断它是否仍然符合“银行转账通知”的语义。")
            appendLine("你只能输出一个 JSON 对象，不要输出 Markdown、代码块标题、解释性前缀或额外文本。")
            appendLine(payload.toString())
        }
    }

    /**
     * 解析端侧模型返回的结构化 JSON。
     *
     * 参数说明：
     * - rawText：模型原始输出文本，可能夹带 Markdown code fence 或额外空白。
     */
    private fun parseStructuredResponse(rawText: String): ParsedStructuredResponse? {
        val jsonText = extractFirstJsonObject(rawText) ?: return null
        val rootObject = StructuredJson.parseToJsonElement(jsonText).jsonObject

        val direction = rootObject.stringValue("direction")
            ?.toMoneyDirection()
            ?: return null
        if (direction == MoneyDirection.UNKNOWN) {
            return null
        }

        val explanation = rootObject.stringValue("explanation")
            ?.trim()
            .orEmpty()
        if (explanation.isBlank()) {
            return null
        }

        val confidenceScore = rootObject.floatValue("confidenceScore")
            ?: rootObject.floatValue("confidence")
            ?: return null
        if (!confidenceScore.isFinite() || confidenceScore !in 0f..1f) {
            return null
        }

        return ParsedStructuredResponse(
            direction = direction,
            categoryHint = rootObject.stringValue("categoryHint")
                ?: rootObject.stringValue("category"),
            merchant = rootObject.stringValue("merchant")
                ?: rootObject.stringValue("counterparty"),
            confidenceScore = confidenceScore,
            explanation = explanation,
            shouldAutoPost = rootObject.booleanValue("shouldAutoPost")
                ?: rootObject.booleanValue("autoPost")
                ?: false,
        )
    }

    /**
     * 从模型文本里抽出第一个完整 JSON 对象。
     *
     * 参数说明：
     * - rawText：模型原始输出，可能包含 code fence 或额外自然语言。
     */
    private fun extractFirstJsonObject(rawText: String): String? {
        val normalized = rawText
            // 说明：部分模型即使收到了 `/no_think`，仍可能返回 `<think>...</think>` 包裹，这里先整体剥离再找 JSON。
            .replace(Regex("""<think>.*?</think>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace("```json", "")
            .replace("```JSON", "")
            .replace("```", "")
            .trim()
        val startIndex = normalized.indexOf('{')
        if (startIndex < 0) {
            return null
        }

        var depth = 0
        var inString = false
        var isEscaped = false
        for (index in startIndex until normalized.length) {
            val char = normalized[index]
            if (inString) {
                if (isEscaped) {
                    isEscaped = false
                } else if (char == '\\') {
                    isEscaped = true
                } else if (char == '"') {
                    inString = false
                }
                continue
            }

            when (char) {
                '"' -> inString = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return normalized.substring(startIndex, index + 1)
                    }
                }
            }
        }
        return null
    }
}

/**
 * 端侧模型结构化结果的内部中间态。
 *
 * 用途说明：
 * - 先把模型输出解析成内部可校验对象，再映射回对外稳定的 `LocalLlmResponse`。
 *
 * 参数说明：
 * - direction：模型判断后的交易方向。
 * - categoryHint：模型建议的分类提示。
 * - merchant：模型建议的对手方或商户。
 * - confidenceScore：模型建议的置信度。
 * - explanation：模型给出的简短解释文本。
 * - shouldAutoPost：模型是否建议自动入账。
 */
private data class ParsedStructuredResponse(
    val direction: MoneyDirection,
    val categoryHint: String?,
    val merchant: String?,
    val confidenceScore: Float,
    val explanation: String,
    val shouldAutoPost: Boolean,
)

/**
 * 统一本地 JSON 解析配置。
 *
 * 用途说明：
 * - 容忍真实模型输出中多余字段，同时避免严格序列化导致整个解析直接失败。
 */
private val StructuredJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * 获取 JSON 对象中的字符串字段。
 *
 * 参数说明：
 * - key：待读取字段名。
 */
private fun kotlinx.serialization.json.JsonObject.stringValue(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
}

/**
 * 获取 JSON 对象中的浮点数字段。
 *
 * 参数说明：
 * - key：待读取字段名。
 */
private fun kotlinx.serialization.json.JsonObject.floatValue(key: String): Float? {
    return this[key]?.jsonPrimitive?.floatOrNull
}

/**
 * 获取 JSON 对象中的布尔字段。
 *
 * 参数说明：
 * - key：待读取字段名。
 */
private fun kotlinx.serialization.json.JsonObject.booleanValue(key: String): Boolean? {
    val content = this[key]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: return null
    return when (content) {
        "true", "1", "yes", "y", "是", "建议自动入账" -> true
        "false", "0", "no", "n", "否", "不要自动入账" -> false
        else -> null
    }
}

/**
 * 把模型输出的方向字段映射成内部枚举。
 *
 * 参数说明：
 * - rawDirection：模型原始方向文本，允许中英文同义词。
 */
private fun String.toMoneyDirection(): MoneyDirection {
    return when (trim().uppercase()) {
        "INCOME", "TRANSFER_IN", "CREDIT", "转入", "收入", "收款", "入账" -> MoneyDirection.INCOME
        "EXPENSE", "TRANSFER_OUT", "DEBIT", "转出", "支出", "付款", "出账" -> MoneyDirection.EXPENSE
        else -> MoneyDirection.UNKNOWN
    }
}

/**
 * 真本地结构化抽取的 system prompt。
 *
 * 用途说明：
 * - 强制模型只做银行转账语义判断与字段补全，不允许篡改金额，也不允许输出自由散文。
 */
private val LOCAL_LLM_SYSTEM_PROMPT = """
你是一个运行在本地设备上的记账辅助模型，只负责分析银行转账通知。
你的任务是根据通知文本和规则层候选，输出一个 JSON 对象。
必须满足以下要求：
1. 只输出一个 JSON 对象，不要输出 Markdown、注释或额外解释。
1.1 禁止输出 `<think>`、思维链、推理过程或任何前置分析文本。
2. 你不能修改金额；金额由规则层决定，你只做方向、类别、对手方、置信度和自动入账建议。
3. 如果你认为它不像银行转账，仍然输出 JSON，但要降低 confidenceScore，并把 shouldAutoPost 设为 false。
4. direction 只能是 INCOME 或 EXPENSE。
5. categoryHint 推荐使用“转账”。
6. explanation 用简短中文说明判断依据。
返回 JSON 字段固定为：
{
  "direction": "INCOME|EXPENSE",
  "categoryHint": "转账",
  "merchant": "对手方名称",
  "confidenceScore": 0.0,
  "explanation": "简短中文原因",
  "shouldAutoPost": false
}
""".trimIndent()

/** 说明：结构化抽取不需要长回答，把生成上限收紧到 192 个 token 即可。 */
private const val LOCAL_LLM_MAX_TOKENS = 192

/** 说明：统一 data 层本地 LLM 调试日志标签。 */
private const val TAG = "AutobookLocalLlm"

/**
 * 把模型原始输出压缩成适合 logcat 的短预览。
 *
 * 参数说明：
 * - 无显式额外参数；接收者就是模型返回的原始文本。
 */
private fun String.toLogPreview(): String {
    return replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
        .take(240)
}

/**
 * 安全输出调试日志，避免 JVM 单测环境里 `android.util.Log` 未 mock 时直接抛异常。
 *
 * 参数说明：
 * - tag：日志标签。
 * - message：日志正文。
 */
private fun logDebug(
    tag: String,
    message: String,
) {
    runCatching { Log.d(tag, message) }
}

/**
 * 安全输出警告日志。
 *
 * 参数说明：
 * - tag：日志标签。
 * - message：日志正文。
 */
private fun logWarn(
    tag: String,
    message: String,
) {
    runCatching { Log.w(tag, message) }
}

/**
 * 安全输出错误日志。
 *
 * 参数说明：
 * - tag：日志标签。
 * - message：日志正文。
 * - error：异常对象。
 */
private fun logError(
    tag: String,
    message: String,
    error: Throwable,
) {
    runCatching { Log.e(tag, message, error) }
}
