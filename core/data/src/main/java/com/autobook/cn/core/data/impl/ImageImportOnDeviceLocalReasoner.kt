package com.autobook.cn.core.data.impl

import android.util.Log
import com.autobook.cn.core.data.ImageImportLocalReasoner
import com.autobook.cn.core.data.LocalModelRepository
import com.autobook.cn.core.llm.LocalTextGenerationRequest
import com.autobook.cn.core.llm.LocalTextGenerationRuntime
import com.autobook.cn.core.model.ImageImportAnalysisPath
import com.autobook.cn.core.model.ImageImportCandidate
import com.autobook.cn.core.model.ImageImportSuggestion
import com.autobook.cn.core.model.ImageImportTimeParser
import com.autobook.cn.core.model.ImageImportTransactionState
import com.autobook.cn.core.model.LocalModelInstallStatus
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 图片导入记账场景下的真本地端侧 reasoner。
 *
 * 用途说明：
 * - 接收 OCR 提取出的文本，调用 `llama.cpp + Qwen` 在端侧生成结构化记账建议。
 * - 如果真本地模型不可用、输出不合法或字段严重缺失，则回退到轻量启发式草稿，避免图片导入链路完全不可用。
 *
 * 参数说明：
 * - runtime：底层真本地文本生成运行时，当前由 `llama.cpp` 驱动。
 * - localModelRepository：读取当前模型安装状态和落盘路径，确保图片链路与通知链路共用同一套本地模型选择。
 */
@Singleton
class ImageImportOnDeviceLocalReasoner @Inject constructor(
    private val runtime: LocalTextGenerationRuntime,
    private val localModelRepository: LocalModelRepository,
) : ImageImportLocalReasoner {
    /**
     * 基于 OCR 文本生成一份可编辑的图片记账建议。
     *
     * 参数说明：
     * - imageRef：图片引用；当前主要用于错误日志和后续回放追踪。
     * - ocrText：OCR 层已经整理好的纯文本。
     */
    override suspend fun analyze(
        imageRef: String,
        ocrText: String,
    ): OperationResult<ImageImportSuggestion> {
        val heuristicDraft = buildHeuristicDraft(ocrText)
        val snapshot = localModelRepository.snapshot.first()
        val modelPath = snapshot.selectedModelPath
        if (snapshot.installStatus != LocalModelInstallStatus.READY || modelPath.isNullOrBlank()) {
            return OperationResult.RetryableFailure("Selected local model is not installed yet")
        }

        return try {
            val completion = runtime.generate(
                LocalTextGenerationRequest(
                    modelPath = modelPath,
                    systemPrompt = IMAGE_IMPORT_LOCAL_REASONER_SYSTEM_PROMPT,
                    userPrompt = buildUserPrompt(
                        imageRef = imageRef,
                        ocrText = ocrText,
                        heuristicDraft = heuristicDraft,
                    ),
                    maxTokens = IMAGE_IMPORT_LOCAL_REASONER_MAX_TOKENS,
                ),
            )
            val parsedResponse = parseStructuredResponse(completion.text)
            if (parsedResponse == null || parsedResponse.candidates.isEmpty()) {
                logWarn(TAG, "图片导入本地 reasoner 输出无法解析为结构化 JSON，退回 OCR 草稿")
                return OperationResult.Success(
                    heuristicDraft.toSuggestion(
                        message = "本地模型输出无法解析，已退回 OCR 草稿，请手动核对后保存。",
                        notePrefix = "来自本地 OCR 草稿",
                    ),
                )
            }

            OperationResult.Success(
                buildSuggestionFromCandidates(
                    drafts = heuristicDraft.expandToDraftResults(parsedResponse.candidates),
                    message = parsedResponse.message.ifBlank { "已按本地 OCR + 端侧 Qwen 生成建议，请核对金额、商户和备注后保存。" },
                    notePrefix = "来自本地 OCR + 端侧 Qwen 建议",
                    requiresManualConfirmation = parsedResponse.requiresManualConfirmation,
                ),
            )
        } catch (error: UnsatisfiedLinkError) {
            logError(TAG, "图片导入本地 reasoner native 装载失败", error)
            OperationResult.Success(
                heuristicDraft.toSuggestion(
                    message = "本地模型运行时不可用，已退回 OCR 草稿，请手动核对后保存。",
                    notePrefix = "来自本地 OCR 草稿",
                ),
            )
        } catch (error: IllegalStateException) {
            logError(TAG, "图片导入本地 reasoner 状态异常", error)
            OperationResult.Success(
                heuristicDraft.toSuggestion(
                    message = "本地模型状态异常，已退回 OCR 草稿，请手动核对后保存。",
                    notePrefix = "来自本地 OCR 草稿",
                ),
            )
        } catch (error: IllegalArgumentException) {
            logError(TAG, "图片导入本地 reasoner prompt 参数非法", error)
            OperationResult.TerminalFailure("Image import local reasoner prompt input is invalid: ${error.message ?: error.javaClass.simpleName}")
        } catch (error: Exception) {
            logError(TAG, "图片导入本地 reasoner 执行失败", error)
            OperationResult.Success(
                heuristicDraft.toSuggestion(
                    message = "本地模型执行失败，已退回 OCR 草稿，请手动核对后保存。",
                    notePrefix = "来自本地 OCR 草稿",
                ),
            )
        }
    }

    /**
     * 构造图片导入场景的用户提示词。
     *
     * 参数说明：
     * - imageRef：当前图片引用。
     * - ocrText：OCR 提取出的文本。
     * - heuristicDraft：启发式草稿，用作端侧模型的稳定先验。
     */
    private fun buildUserPrompt(
        imageRef: String,
        ocrText: String,
        heuristicDraft: ParsedImageImportDraft,
    ): String {
        val payload = buildJsonObject {
            put("imageRef", imageRef)
            put("ocrText", ocrText)
            if (heuristicDraft.amount != null) {
                put("heuristicAmount", heuristicDraft.amount)
            } else {
                put("heuristicAmount", kotlinx.serialization.json.JsonNull)
            }
            if (heuristicDraft.timestamp != null) {
                put("heuristicTimestamp", heuristicDraft.timestamp)
            } else {
                put("heuristicTimestamp", kotlinx.serialization.json.JsonNull)
            }
            put("heuristicDirection", heuristicDraft.direction.name)
            put("heuristicMerchant", heuristicDraft.merchant)
            put("heuristicCategory", heuristicDraft.category)
            if (heuristicDraft.orderId != null) {
                put("heuristicOrderId", heuristicDraft.orderId)
            } else {
                put("heuristicOrderId", kotlinx.serialization.json.JsonNull)
            }
        }
        return buildString {
            appendLine("/no_think")
            appendLine("请根据下面的 OCR 文本，为一张支付或收款截图生成一份结构化记账建议。")
            appendLine("你只能输出一个 JSON 对象，不要输出 Markdown、代码块、思维链或额外说明。")
            appendLine(payload.toString())
        }
    }

    /**
     * 解析模型返回的结构化 JSON。
     *
     * 参数说明：
     * - rawText：模型原始输出文本。
     */
    private fun parseStructuredResponse(rawText: String): ParsedImageImportResponseEnvelope? {
        val jsonText = extractFirstJsonObject(rawText) ?: return null
        val rootObject = ImageImportStructuredJson.parseToJsonElement(jsonText).jsonObject
        val candidates = rootObject.arrayValue("candidates")
            ?.mapNotNull(::parseCandidate)
            .orEmpty()
        return ParsedImageImportResponseEnvelope(
            candidates = candidates,
            message = rootObject.stringValue("message").orEmpty(),
            requiresManualConfirmation = rootObject.booleanValue("requiresManualConfirmation") ?: true,
        )
    }

    /**
     * 从模型文本中抽取第一个完整 JSON 对象。
     *
     * 参数说明：
     * - rawText：模型原始输出，可能带有 code fence 或 `<think>` 块。
     */
    private fun extractFirstJsonObject(rawText: String): String? {
        val normalized = rawText
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

    /**
     * 基于 OCR 文本构造一份启发式草稿，供模型失败时回退使用。
     *
     * 参数说明：
     * - ocrText：OCR 提取出的文本。
     */
    private fun buildHeuristicDraft(ocrText: String): ParsedImageImportDraft {
        val amount = AMOUNT_REGEX.find(ocrText)
            ?.groupValues
            ?.drop(1)
            ?.firstOrNull { it.isNotBlank() }
            ?.toDoubleOrNull()
        val timestamp = ImageImportTimeParser.extractFromText(ocrText)
        val direction = when {
            INCOME_KEYWORDS.any(ocrText::contains) && !EXPENSE_KEYWORDS.any(ocrText::contains) -> MoneyDirection.INCOME
            EXPENSE_KEYWORDS.any(ocrText::contains) && !INCOME_KEYWORDS.any(ocrText::contains) -> MoneyDirection.EXPENSE
            else -> MoneyDirection.EXPENSE
        }
        val merchant = MERCHANT_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(ocrText)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        }.orEmpty()
        val category = when {
            listOf("超市", "便利店", "淘宝", "京东", "购物").any(ocrText::contains) -> "购物"
            listOf("餐饮", "奶茶", "咖啡", "外卖", "早餐", "午餐").any(ocrText::contains) -> "餐饮"
            listOf("打车", "出行", "滴滴", "地铁", "公交").any(ocrText::contains) -> "交通"
            listOf("工资", "薪资", "奖金").any(ocrText::contains) -> "工资"
            listOf("转账", "转入", "转出").any(ocrText::contains) -> "转账"
            else -> "待分类"
        }
        val orderId = ORDER_ID_REGEX.find(ocrText)?.groupValues?.getOrNull(1)
        val heuristicState = buildHeuristicTransactionState(ocrText)
        return ParsedImageImportDraft(
            amount = amount,
            timestamp = timestamp,
            direction = direction,
            merchant = merchant,
            category = category,
            orderId = orderId,
            transactionState = heuristicState.state,
            stateReason = heuristicState.reason,
            evidence = heuristicState.evidence.ifBlank { ocrText.take(80) },
        )
    }

    /**
     * 把启发式草稿和端侧模型候选逐条合并。
     *
     * 参数说明：
     * - candidates：端侧模型返回的候选数组。
     */
    private fun ParsedImageImportDraft.expandToDraftResults(
        candidates: List<ParsedImageImportResponse>,
    ): List<ParsedImageImportDraftResult> {
        return candidates.map { candidate -> mergeWith(candidate) }
    }

    /**
     * 将多条草稿结果压缩成当前对外图片建议。
     *
     * 参数说明：
     * - drafts：已合并的候选草稿。
     * - message：面向用户的总体说明。
     * - notePrefix：候选备注前缀。
     * - requiresManualConfirmation：是否要求人工确认。
     */
    private fun buildSuggestionFromCandidates(
        drafts: List<ParsedImageImportDraftResult>,
        message: String,
        notePrefix: String,
        requiresManualConfirmation: Boolean,
    ): ImageImportSuggestion {
        val primary = drafts.maxByOrNull { it.confidenceScore ?: 0f } ?: ParsedImageImportDraftResult(
            amount = null,
            timestamp = null,
            direction = MoneyDirection.UNKNOWN,
            merchant = "",
            category = "待分类",
            note = null,
            orderId = null,
            confidenceScore = 0f,
            transactionState = ImageImportTransactionState.UNKNOWN,
            stateReason = "",
            evidence = "",
            requiresManualConfirmation = true,
        )
        val candidates = drafts.map { draft ->
            draft.toCandidate(notePrefix = notePrefix)
        }
        return primary.toSuggestion(
            message = message,
            notePrefix = notePrefix,
            requiresManualConfirmation = requiresManualConfirmation,
            candidates = candidates,
        )
    }

    /**
     * 解析模型返回的单个候选对象。
     *
     * 参数说明：
     * - candidateObject：数组中的单个候选 JSON 对象。
     */
    private fun parseCandidate(candidateObject: kotlinx.serialization.json.JsonObject): ParsedImageImportResponse? {
        val direction = candidateObject.stringValue("direction")?.toMoneyDirection()
        val amount = candidateObject.doubleValue("amount")
        val merchant = candidateObject.stringValue("merchant")
        val category = candidateObject.stringValue("category")
        val note = candidateObject.stringValue("note")
        val orderId = candidateObject.stringValue("orderId")
        val timestamp = ImageImportTimeParser.parseToTimestamp(
            rawValue = candidateObject.stringValue("transactionTime") ?: candidateObject.stringValue("timestamp"),
        )
        val confidenceScore = candidateObject.doubleValue("confidenceScore")?.toFloat()
        if (amount != null && (!amount.isFinite() || amount <= 0.0)) {
            return null
        }
        if (confidenceScore != null && (!confidenceScore.isFinite() || confidenceScore !in 0f..1f)) {
            return null
        }
        return ParsedImageImportResponse(
            amount = amount,
            timestamp = timestamp,
            direction = direction,
            merchant = merchant,
            category = category,
            note = note,
            orderId = orderId,
            confidenceScore = confidenceScore,
            transactionState = candidateObject.stringValue("transactionState")?.toImageImportTransactionState()
                ?: ImageImportTransactionState.UNKNOWN,
            stateReason = candidateObject.stringValue("stateReason").orEmpty(),
            evidence = candidateObject.stringValue("evidence").orEmpty(),
        )
    }
}

/**
 * 图片导入建议的内部草稿态。
 *
 * 用途说明：
 * - 统一承载启发式草稿和端侧模型结果的合并逻辑，避免在主流程里散落字段兜底判断。
 */
private data class ParsedImageImportDraft(
    val amount: Double?,
    val timestamp: Long?,
    val direction: MoneyDirection,
    val merchant: String,
    val category: String,
    val orderId: String?,
    val transactionState: ImageImportTransactionState,
    val stateReason: String,
    val evidence: String,
) {
    /**
     * 把端侧模型结果合并进启发式草稿。
     *
     * 参数说明：
     * - response：端侧模型解析出的结构化结果。
     */
    fun mergeWith(response: ParsedImageImportResponse): ParsedImageImportDraftResult {
        return ParsedImageImportDraftResult(
            amount = response.amount ?: amount,
            timestamp = response.timestamp ?: timestamp,
            direction = response.direction ?: direction,
            merchant = response.merchant?.takeIf(String::isNotBlank) ?: merchant,
            category = response.category?.takeIf(String::isNotBlank) ?: category,
            note = response.note?.takeIf(String::isNotBlank),
            orderId = response.orderId?.takeIf(String::isNotBlank) ?: orderId,
            confidenceScore = response.confidenceScore,
            transactionState = if (response.transactionState != ImageImportTransactionState.UNKNOWN) {
                response.transactionState
            } else {
                transactionState
            },
            stateReason = response.stateReason.ifBlank { stateReason },
            evidence = response.evidence.ifBlank { evidence },
            requiresManualConfirmation = true,
        )
    }

    /**
     * 把纯启发式草稿直接转成图片建议。
     *
     * 参数说明：
     * - message：面向用户的状态说明。
     * - notePrefix：写入备注前缀，提示当前建议来自哪条链路。
     */
    fun toSuggestion(
        message: String,
        notePrefix: String,
    ): ImageImportSuggestion {
        return ParsedImageImportDraftResult(
            amount = amount,
            timestamp = timestamp,
            direction = direction,
            merchant = merchant,
            category = category,
            note = null,
            orderId = orderId,
            confidenceScore = null,
            transactionState = transactionState,
            stateReason = stateReason,
            evidence = evidence,
            requiresManualConfirmation = true,
        ).toSuggestion(
            message = message,
            notePrefix = notePrefix,
            requiresManualConfirmation = true,
            candidates = listOf(
                ImageImportCandidate(
                    amount = amount,
                    timestamp = timestamp,
                    direction = direction,
                    merchant = merchant,
                    category = category,
                    note = notePrefix,
                    orderId = orderId,
                    confidence = ParseConfidence(0f),
                    transactionState = transactionState,
                    stateReason = stateReason,
                    evidence = evidence.ifBlank { message },
                ),
            ),
        )
    }
}

/**
 * 图片导入端侧模型的内部结果。
 *
 * 参数说明：
 * - amount：模型建议金额；为空时沿用启发式草稿。
 * - direction：模型建议方向；为空时沿用启发式草稿。
 * - merchant：模型建议商户。
 * - category：模型建议分类。
 * - note：模型建议备注。
 * - orderId：模型建议订单号。
 * - confidenceScore：模型建议置信度，仅用于构造说明文案。
 * - requiresManualConfirmation：是否要求手动确认；当前产品语义下最终仍强制为 true。
 */
private data class ParsedImageImportResponse(
    val amount: Double?,
    val timestamp: Long?,
    val direction: MoneyDirection?,
    val merchant: String?,
    val category: String?,
    val note: String?,
    val orderId: String?,
    val confidenceScore: Float?,
    val transactionState: ImageImportTransactionState,
    val stateReason: String,
    val evidence: String,
)

/**
 * 图片导入端侧模型的整体响应。
 *
 * 参数说明：
 * - candidates：候选数组。
 * - message：总体说明。
 * - requiresManualConfirmation：是否要求人工确认。
 */
private data class ParsedImageImportResponseEnvelope(
    val candidates: List<ParsedImageImportResponse>,
    val message: String,
    val requiresManualConfirmation: Boolean,
)

/**
 * 合并完成后的图片导入草稿。
 *
 * 参数说明：
 * - amount：最终金额建议。
 * - direction：最终方向建议。
 * - merchant：最终商户建议。
 * - category：最终分类建议。
 * - note：最终备注建议。
 * - orderId：最终订单号建议。
 * - confidenceScore：模型置信度。
 * - requiresManualConfirmation：是否要求手动确认。
 */
private data class ParsedImageImportDraftResult(
    val amount: Double?,
    val timestamp: Long?,
    val direction: MoneyDirection,
    val merchant: String,
    val category: String,
    val note: String?,
    val orderId: String?,
    val confidenceScore: Float?,
    val transactionState: ImageImportTransactionState,
    val stateReason: String,
    val evidence: String,
    val requiresManualConfirmation: Boolean,
) {
    /**
     * 转成最终对外的图片导入建议。
     *
     * 参数说明：
     * - message：当前建议的状态说明。
     * - notePrefix：当前建议链路说明。
     */
    fun toSuggestion(
        message: String,
        notePrefix: String,
        requiresManualConfirmation: Boolean,
        candidates: List<ImageImportCandidate>,
    ): ImageImportSuggestion {
        val confidenceLabel = confidenceScore
            ?.let { score -> "（本地模型置信度 ${"%.2f".format(score)}）" }
            .orEmpty()
        val mergedNote = buildString {
            append(notePrefix)
            append(confidenceLabel)
            append("，建议人工确认后保存")
            if (!note.isNullOrBlank()) {
                append("。")
                append(note)
            }
        }
        return ImageImportSuggestion(
            amount = amount,
            timestamp = timestamp,
            direction = direction,
            merchant = merchant,
            category = category,
            note = mergedNote,
            orderId = orderId,
            confidence = ParseConfidence(confidenceScore ?: 0f),
            tracePath = ImageImportAnalysisPath.OCR_PLUS_LOCAL_LLM,
            message = message,
            // 图片导入当前始终保持“建议态”，即使模型建议通过，也不直接自动入账。
            requiresManualConfirmation = true,
            candidates = candidates,
        )
    }

    /**
     * 转成单条图片候选。
     *
     * 参数说明：
     * - notePrefix：链路说明前缀。
     */
    fun toCandidate(notePrefix: String): ImageImportCandidate {
        val mergedNote = buildString {
            append(notePrefix)
            if (!note.isNullOrBlank()) {
                append("。")
                append(note)
            }
        }
        return ImageImportCandidate(
            amount = amount,
            timestamp = timestamp,
            direction = direction,
            merchant = merchant,
            category = category,
            note = mergedNote,
            orderId = orderId,
            confidence = ParseConfidence(confidenceScore ?: 0f),
            transactionState = transactionState,
            stateReason = stateReason,
            evidence = evidence,
        )
    }
}

/**
 * OCR 启发式交易状态结果。
 *
 * 参数说明：
 * - state：交易状态。
 * - reason：状态判断原因。
 * - evidence：命中的证据片段。
 */
private data class HeuristicTransactionState(
    val state: ImageImportTransactionState,
    val reason: String,
    val evidence: String,
)

/**
 * 图片导入结构化输出解析配置。
 */
private val ImageImportStructuredJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * 获取 JSON 对象中的字符串字段。
 *
 * 参数说明：
 * - key：字段名。
 */
private fun kotlinx.serialization.json.JsonObject.stringValue(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
}

/**
 * 获取 JSON 对象中的数值字段。
 *
 * 参数说明：
 * - key：字段名。
 */
private fun kotlinx.serialization.json.JsonObject.doubleValue(key: String): Double? {
    return this[key]?.jsonPrimitive?.doubleOrNull
}

/**
 * 获取 JSON 对象中的布尔字段。
 *
 * 参数说明：
 * - key：字段名。
 */
private fun kotlinx.serialization.json.JsonObject.booleanValue(key: String): Boolean? {
    val content = this[key]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: return null
    return when (content) {
        "true", "1", "yes", "y", "是" -> true
        "false", "0", "no", "n", "否" -> false
        else -> null
    }
}

/**
 * 读取 JSON 对象中的数组字段。
 *
 * 参数说明：
 * - key：字段名。
 */
private fun kotlinx.serialization.json.JsonObject.arrayValue(key: String): List<kotlinx.serialization.json.JsonObject>? {
    val array = this[key] as? JsonArray ?: return null
    return array.mapNotNull { element -> element as? kotlinx.serialization.json.JsonObject }
}

/**
 * 把模型输出方向映射成内部枚举。
 *
 * 参数说明：
 * - 无显式额外参数；接收者就是模型原始方向文本。
 */
private fun String.toMoneyDirection(): MoneyDirection {
    return when (trim().uppercase()) {
        "INCOME", "转入", "收入", "收款", "到账", "入账" -> MoneyDirection.INCOME
        "EXPENSE", "转出", "支出", "付款", "支付", "扣款", "消费" -> MoneyDirection.EXPENSE
        else -> MoneyDirection.EXPENSE
    }
}

/**
 * 把模型输出交易状态映射成内部枚举。
 *
 * 参数说明：
 * - 无显式额外参数；接收者就是模型原始状态文本。
 */
private fun String.toImageImportTransactionState(): ImageImportTransactionState {
    return when (trim().uppercase()) {
        "ACTIVE" -> ImageImportTransactionState.ACTIVE
        "REFUNDED" -> ImageImportTransactionState.REFUNDED
        "RETURNED" -> ImageImportTransactionState.RETURNED
        "REVERSED" -> ImageImportTransactionState.REVERSED
        "CANCELLED" -> ImageImportTransactionState.CANCELLED
        else -> ImageImportTransactionState.UNKNOWN
    }
}

/**
 * 图片导入本地 reasoner 的系统提示词。
 *
 * 用途说明：
 * - 把端侧模型限制在“根据 OCR 文本生成记账建议”的窄任务上，减少自由发挥。
 */
private val IMAGE_IMPORT_LOCAL_REASONER_SYSTEM_PROMPT = """
你是一个运行在本地设备上的图片记账辅助模型。
你的输入是一段 OCR 提取出的支付截图文本，以及一份启发式草稿。
你的任务是输出一份结构化记账建议候选数组。
必须满足以下要求：
1. 只输出一个 JSON 对象，不要输出 Markdown、思维链、解释前缀或额外文本。
2. 一张截图里可能包含多条交易，请尽量把可见候选都放进 candidates 数组。
2.1. 如果 OCR 文本明显来自微信聊天气泡界面，只看右侧气泡，对应“我发出的消息/我的转账记录”；左侧气泡一律不作为独立记账候选。
2.2. 微信聊天界面中，右侧“转账给你”“转账”“已被接收”“已收款”等文案，仍然只算右侧这 1 笔转账本身，不要再把左侧收款提示重复记一笔。
2.3. 只有当同一张图里存在两条或以上彼此独立的右侧气泡交易时，才输出多条 candidates。
3. amount 可以为数字，也可以为 null；如果 OCR 文本里没有可靠金额，就返回 null。
4. direction 只能是 INCOME 或 EXPENSE。
5. category 使用简短中文分类，例如“餐饮”“购物”“交通”“工资”“转账”“待分类”。
6. merchant 尽量提取商户或收款对象；不确定时可为空字符串。
7. 如果 OCR 文本里能明确看到支付时间、到账时间、交易时间或账单时间，请尽量识别并放到 transactionTime，格式固定为 yyyy-MM-dd HH:mm:ss；无法确认时返回 null。
8. transactionState 只能是 ACTIVE / REFUNDED / RETURNED / REVERSED / CANCELLED / UNKNOWN。
9. note 用简短中文描述判断依据。
10. requiresManualConfirmation 始终输出 true。
返回 JSON 字段固定为：
{
  "candidates": [
    {
      "amount": 0.0,
      "transactionTime": "yyyy-MM-dd HH:mm:ss 或 null",
      "direction": "EXPENSE",
      "merchant": "商户名称",
      "category": "待分类",
      "note": "简短中文原因",
      "orderId": "订单号或 null",
      "confidenceScore": 0.0,
      "transactionState": "ACTIVE",
      "stateReason": "状态判断原因",
      "evidence": "证据片段"
    }
  ],
  "message": "给用户看的总体说明",
  "requiresManualConfirmation": true
}
""".trimIndent()

/** 图片导入结构化建议不需要长回答，控制生成上限避免冗余输出。 */
private const val IMAGE_IMPORT_LOCAL_REASONER_MAX_TOKENS = 256

/** 图片导入本地 reasoner 的日志标签。 */
private const val TAG = "AutobookImageLlm"

/** 常见图片金额提取规则。 */
private val AMOUNT_REGEX = Regex("""(?:人民币|RMB|CNY|￥|¥)\s*([0-9]+(?:\.[0-9]{1,2})?)|([0-9]+(?:\.[0-9]{1,2})?)\s*(?:元|块)""")

/** 常见图片订单号提取规则。 */
private val ORDER_ID_REGEX = Regex("""(?:订单号|交易单号|单号|订单)\s*[:：]?\s*([A-Za-z0-9-]{6,})""")

/** 常见商户提取模式。 */
private val MERCHANT_PATTERNS = listOf(
    Regex("""(?:商户|商家|收款方|收款人)\s*[:：]?\s*(.+?)(?=\s*(?:金额|订单|备注|￥|¥|[0-9]+(?:\.[0-9]{1,2})?\s*(?:元|块)|$))"""),
    Regex("""向(.+?)(?=\s*(?:付款|支付|转账|收款|金额|￥|¥|$))"""),
)

/** 收入语义关键词。 */
private val INCOME_KEYWORDS = listOf("收款", "到账", "收入", "入账", "转入", "退款")

/** 支出语义关键词。 */
private val EXPENSE_KEYWORDS = listOf("支付", "付款", "扣款", "消费", "转出", "转账")

/** 退款/退还语义关键词。 */
private val REFUND_KEYWORDS = listOf("退款", "已退款", "退款成功", "退回", "退还", "已退还")

/** 撤销/取消语义关键词。 */
private val REVERSED_KEYWORDS = listOf("撤销", "已撤销", "撤回", "已取消", "取消成功", "已关闭")

/**
 * 基于 OCR 文本做一次启发式交易状态判断。
 *
 * 参数说明：
 * - ocrText：OCR 文本。
 */
private fun buildHeuristicTransactionState(ocrText: String): HeuristicTransactionState {
    val refundKeyword = REFUND_KEYWORDS.firstOrNull(ocrText::contains)
    if (refundKeyword != null) {
        return HeuristicTransactionState(
            state = if (refundKeyword.contains("退还")) ImageImportTransactionState.RETURNED else ImageImportTransactionState.REFUNDED,
            reason = "OCR 命中退款/退还语义",
            evidence = refundKeyword,
        )
    }

    val reversedKeyword = REVERSED_KEYWORDS.firstOrNull(ocrText::contains)
    if (reversedKeyword != null) {
        return HeuristicTransactionState(
            state = if (reversedKeyword.contains("取消") || reversedKeyword.contains("关闭")) {
                ImageImportTransactionState.CANCELLED
            } else {
                ImageImportTransactionState.REVERSED
            },
            reason = "OCR 命中撤销/取消语义",
            evidence = reversedKeyword,
        )
    }

    return HeuristicTransactionState(
        state = ImageImportTransactionState.ACTIVE,
        reason = "OCR 未命中退款或撤销语义",
        evidence = "",
    )
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
