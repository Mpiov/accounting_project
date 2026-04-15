package com.autobook.cn.core.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gemini 图片导入分析请求。
 *
 * 用途说明：
 * - 为当前客户端走 Cloudflare 反向代理调用 `gemini-3-flash-preview` 保留最小请求协议。
 * - 当前阶段直接传入图片引用与提示词版本，后续继续调整代理或切后端服务时也可平滑复用这层调用参数。
 *
 * 参数说明：
 * - imageRef：图片引用，当前直接使用内容 Uri 字符串。
 * - promptVersion：提示词版本，便于后续灰度和回放。
 * - requireManualConfirmation：当前结果是否必须人工确认。
 */
data class GeminiImageImportRequest(
    val imageRef: String,
    val promptVersion: String,
    val requireManualConfirmation: Boolean = true,
)

/**
 * Gemini 图片导入分析响应。
 *
 * 用途说明：
 * - 描述图片分析返回的结构化建议字段。
 * - 当前无论返回什么，都只是建议，不会自动入账。
 *
 * 参数说明：
 * - amount：建议金额；为空表示模型没有稳定识别出金额。
 * - direction：建议方向。
 * - merchant：建议商户。
 * - category：建议分类。
 * - note：建议备注。
 * - orderId：建议订单号。
 * - modelName：命中的 Gemini 模型名。
 * - message：面向用户的提示信息。
 * - requiresManualConfirmation：是否仍需要人工确认。
 */
data class GeminiImageImportResponse(
    val amount: Double? = null,
    val direction: String = "EXPENSE",
    val merchant: String = "",
    val category: String = "待分类",
    val note: String = "",
    val orderId: String? = null,
    val modelName: String,
    val message: String,
    val requiresManualConfirmation: Boolean = true,
)

/**
 * Gemini 图片导入分析网络边界接口。
 *
 * 用途说明：
 * - 统一约束图片导入的云端分析入口。
 * - 当前可由真实直连实现或 fake 实现承载。
 */
interface GeminiImageImportApi {
    suspend fun analyze(request: GeminiImageImportRequest): GeminiImageImportResponse?
}

/**
 * 当前通过 Cloudflare 反向代理调用 Gemini 的真实实现。
 *
 * 用途说明：
 * - 从应用端把图片请求发送到 Cloudflare 反代域名，再由代理转发到 Gemini `generateContent` 接口。
 * - 当前不再从 `local.properties` 或 `BuildConfig` 读取 Gemini API key，避免把上游密钥下发到客户端。
 *
 * 参数说明：
 * - context：应用上下文，用于读取选图返回的内容 Uri。
 */
class DirectGeminiImageImportApi @Inject constructor(
    @ApplicationContext private val context: Context,
) : GeminiImageImportApi {
    /**
     * 把图片转成 Gemini 可接收的 inline data，并请求模型返回 JSON 建议。
     *
     * 参数说明：
     * - request：当前图片导入分析请求。
     */
    override suspend fun analyze(request: GeminiImageImportRequest): GeminiImageImportResponse? {
        return try {
            withTimeout(GEMINI_TOTAL_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "Start reading image for Gemini request: ${request.imageRef}")
                    val imagePayload = runCatching { buildInlineImagePayload(request.imageRef) }.getOrNull()
                        ?: return@withContext GeminiImageImportResponse(
                            amount = null,
                            direction = "EXPENSE",
                            merchant = "",
                            category = "待分类",
                            note = "",
                            orderId = null,
                            modelName = BuildConfig.GEMINI_IMAGE_MODEL,
                            message = "无法读取所选图片，请重新选择图片后再试。",
                            requiresManualConfirmation = true,
                        )
                    Log.d(TAG, "Image prepared for Gemini request, mime=${imagePayload.mimeType}, bytes(base64)=${imagePayload.base64Data.length}")

                    val body = buildRequestBody(
                        promptVersion = request.promptVersion,
                        requireManualConfirmation = request.requireManualConfirmation,
                        mimeType = imagePayload.mimeType,
                        base64Data = imagePayload.base64Data,
                    )

                    val connection = (URL(buildEndpointUrl()).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = NETWORK_TIMEOUT_MS
                        readTimeout = NETWORK_TIMEOUT_MS
                        doInput = true
                        doOutput = true
                        applyProxyHeaders(request)
                    }

                    try {
                        Log.d(TAG, "Sending request to Gemini model=${BuildConfig.GEMINI_IMAGE_MODEL}")
                        connection.outputStream.use { stream ->
                            stream.write(body.toByteArray(Charsets.UTF_8))
                        }
                        val responseText = (if (connection.responseCode in 200..299) {
                            connection.inputStream
                        } else {
                            connection.errorStream
                        })?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                        Log.d(TAG, "Gemini HTTP status=${connection.responseCode}")

                        if (connection.responseCode !in 200..299) {
                            GeminiImageImportResponse(
                                amount = null,
                                direction = "EXPENSE",
                                merchant = "",
                                category = "待分类",
                                note = "",
                                orderId = null,
                                modelName = BuildConfig.GEMINI_IMAGE_MODEL,
                                message = "Gemini 代理请求失败：HTTP ${connection.responseCode}，请检查代理或网络后重试。",
                                requiresManualConfirmation = true,
                            )
                        } else {
                            parseResponse(responseText) ?: GeminiImageImportResponse(
                                amount = null,
                                direction = "EXPENSE",
                                merchant = "",
                                category = "待分类",
                                note = "",
                                orderId = null,
                                modelName = BuildConfig.GEMINI_IMAGE_MODEL,
                                message = "Gemini 已返回结果，但当前无法稳定解析，请手动确认。",
                                requiresManualConfirmation = true,
                            )
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            Log.e(TAG, "Gemini request timed out", timeout)
            GeminiImageImportResponse(
                amount = null,
                direction = "EXPENSE",
                merchant = "",
                category = "待分类",
                note = "",
                orderId = null,
                modelName = BuildConfig.GEMINI_IMAGE_MODEL,
                message = "Gemini 请求超时，请检查网络或稍后重试。",
                requiresManualConfirmation = true,
            )
        } catch (timeout: SocketTimeoutException) {
            Log.e(TAG, "Gemini socket timeout", timeout)
            GeminiImageImportResponse(
                amount = null,
                direction = "EXPENSE",
                merchant = "",
                category = "待分类",
                note = "",
                orderId = null,
                modelName = BuildConfig.GEMINI_IMAGE_MODEL,
                message = "Gemini 连接超时，请检查网络或稍后重试。",
                requiresManualConfirmation = true,
            )
        } catch (throwable: Throwable) {
            Log.e(TAG, "Gemini request failed", throwable)
            GeminiImageImportResponse(
                amount = null,
                direction = "EXPENSE",
                merchant = "",
                category = "待分类",
                note = "",
                orderId = null,
                modelName = BuildConfig.GEMINI_IMAGE_MODEL,
                message = "Gemini 调用失败：${throwable.message ?: throwable.javaClass.simpleName}",
                requiresManualConfirmation = true,
            )
        }
    }

    /** 组装经过 Cloudflare 反代的 Gemini REST endpoint。 */
    private fun buildEndpointUrl(): String {
        return "$GEMINI_PROXY_BASE_URL/models/${BuildConfig.GEMINI_IMAGE_MODEL}:generateContent"
    }

    /**
     * 统一设置反向代理请求头。
     *
     * 用途说明：
     * - 把模型名、提示词版本和人工确认语义显式透传给 Cloudflare 代理，方便代理层做路由、审计和灰度。
     * - 这里不再下发上游 API key，只保留与业务语义相关的头。
     *
     * 参数说明：
     * - request：当前图片导入分析请求，用于透传提示词版本和人工确认标记。
     */
    private fun HttpURLConnection.applyProxyHeaders(request: GeminiImageImportRequest) {
        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", GEMINI_PROXY_USER_AGENT)
        setRequestProperty("X-Proxy-Provider", "gemini")
        setRequestProperty("X-Proxy-Model", BuildConfig.GEMINI_IMAGE_MODEL)
        setRequestProperty("X-Prompt-Version", request.promptVersion)
        setRequestProperty("X-Require-Manual-Confirmation", request.requireManualConfirmation.toString())
    }

    /**
     * 构造 generateContent 请求体。
     *
     * 参数说明：
     * - promptVersion：提示词版本号。
     * - requireManualConfirmation：当前是否强制人工确认。
     * - mimeType：图片 MIME 类型。
     * - base64Data：图片的 Base64 内容。
     */
    private fun buildRequestBody(
        promptVersion: String,
        requireManualConfirmation: Boolean,
        mimeType: String,
        base64Data: String,
    ): String {
        val textPrompt = """
            你是一个记账助手，请从图片中识别一条最可能的交易记录。
            当前提示词版本：$promptVersion。
            本次结果必须作为建议返回，requiresManualConfirmation 应保持为 $requireManualConfirmation。
            请严格返回一个 JSON 对象，不要使用 Markdown，不要输出额外解释。
            方向判断规则必须优先满足以下约束：
            1. 如果图片表达的是“收款成功、到账、入账、转入、收入、退款到账、收款码收款”等语义，direction 必须返回 INCOME。
            2. 如果图片表达的是“支付成功、付款成功、支出、转出、消费、扣款”等语义，direction 返回 EXPENSE。
            3. 无法判断时才返回 UNKNOWN，绝对不要在看到明显收款/到账语义时返回 EXPENSE。
            4. 如果金额、方向、商户不确定，宁可保守留空或返回 UNKNOWN，也不要编造。
            JSON 结构如下：
            {
              "amount": 可能为数字或 null,
              "direction": "INCOME" | "EXPENSE" | "UNKNOWN",
              "merchant": "字符串，无法确认时为空字符串",
              "category": "字符串，无法确认时填待分类",
              "note": "字符串，可为空",
              "orderId": "字符串或 null",
              "message": "给用户看的简短说明",
              "requiresManualConfirmation": true
            }
            如果图片看起来不像交易截图，amount 返回 null，并在 message 中说明需要用户手动确认。
        """.trimIndent()

        val requestJson = JSONObject()
        val contents = JSONArray()
        val userContent = JSONObject()
        val parts = JSONArray()
        parts.put(JSONObject().put("text", textPrompt))
        parts.put(
            JSONObject().put(
                "inlineData",
                JSONObject()
                    .put("mimeType", mimeType)
                    .put("data", base64Data),
            ),
        )
        userContent.put("parts", parts)
        contents.put(userContent)
        requestJson.put("contents", contents)
        requestJson.put(
            "generationConfig",
            JSONObject()
                .put("temperature", 0.2)
                .put("responseMimeType", "application/json"),
        )
        return requestJson.toString()
    }

    /**
     * 从 Gemini 返回里提取第一段文本，并解析成结构化建议。
     *
     * 参数说明：
     * - responseText：Gemini 返回的原始 JSON 文本。
     */
    private fun parseResponse(responseText: String): GeminiImageImportResponse? {
        val root = JSONObject(responseText)
        val candidates = root.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null

        val firstCandidate = candidates.optJSONObject(0) ?: return null
        val parts = firstCandidate.optJSONObject("content")?.optJSONArray("parts") ?: return null
        if (parts.length() == 0) return null

        val rawText = parts.optJSONObject(0)?.optString("text").orEmpty()
        if (rawText.isBlank()) return null

        val normalizedJson = rawText
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val payload = JSONObject(normalizedJson)
        return GeminiImageImportResponse(
            amount = payload.optDoubleOrNull("amount"),
            direction = payload.optString("direction", "EXPENSE"),
            merchant = payload.optString("merchant", ""),
            category = payload.optString("category", "待分类"),
            note = payload.optString("note", ""),
            orderId = payload.optStringOrNull("orderId"),
            modelName = BuildConfig.GEMINI_IMAGE_MODEL,
            message = payload.optString("message", "图片分析已完成，请人工确认后保存。"),
            requiresManualConfirmation = payload.optBoolean("requiresManualConfirmation", true),
        )
    }

    /**
     * 读取用户选择的图片并转成 Gemini inline data。
     *
     * 参数说明：
     * - imageRef：当前图片内容 Uri 字符串。
     */
    private fun buildInlineImagePayload(imageRef: String): InlineImagePayload {
        val uri = Uri.parse(imageRef)
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: DEFAULT_IMAGE_MIME_TYPE
        val rawBytes = resolver.openInputStream(uri)?.use { input -> input.readBytes() }
            ?: error("Unable to read image bytes from $imageRef")

        // 说明：当前开发阶段优先保证调用成功率，先把图片统一压到较小 JPEG，控制请求体积。
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: error("Unable to decode selected image")
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        val compressedBytes = output.toByteArray()
        val encoded = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
        return InlineImagePayload(
            mimeType = if (mimeType.startsWith("image/")) DEFAULT_IMAGE_MIME_TYPE else mimeType,
            base64Data = encoded,
        )
    }
}

/**
 * Gemini 图片导入分析的 fake 实现。
 *
 * 用途说明：
 * - 在不希望发真实网络请求时，仍能返回固定结果联调图片导入流程。
 */
class FakeGeminiImageImportApi : GeminiImageImportApi {
    override suspend fun analyze(request: GeminiImageImportRequest): GeminiImageImportResponse {
        return GeminiImageImportResponse(
            amount = 32.5,
            direction = "EXPENSE",
            merchant = "Gemini 图片建议商户",
            category = "餐饮",
            note = "来自 gemini-3-flash-preview mock，建议人工确认后保存",
            orderId = "IMG-GEMINI3FLASH-MOCK-001",
            modelName = "gemini-3-flash-preview",
            message = "已按 gemini-3-flash-preview 生成 mock 建议，请核对金额、商户和备注后确认保存。",
            requiresManualConfirmation = true,
        )
    }
}

/** inline data 所需的最小图片载荷。 */
private data class InlineImagePayload(
    val mimeType: String,
    val base64Data: String,
)

private fun JSONObject.optDoubleOrNull(key: String): Double? {
    return if (isNull(key)) null else optDouble(key)
}

private fun JSONObject.optStringOrNull(key: String): String? {
    return if (isNull(key)) null else optString(key)
}

private const val GEMINI_PROXY_BASE_URL = "https://api.wenapi.shop/v1beta"
private const val NETWORK_TIMEOUT_MS = 30_000
private const val GEMINI_TOTAL_TIMEOUT_MS = 35_000L
private const val JPEG_QUALITY = 85
private const val DEFAULT_IMAGE_MIME_TYPE = "image/jpeg"
private const val GEMINI_PROXY_USER_AGENT = "Autobook-Android-GeminiProxy/1.0"
private const val TAG = "GeminiImageImportApi"
