package com.autobook.cn.core.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
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
 * - 为当前客户端走 Cloudflare 反向代理调用 `gemini-3.1-pro-preview` 保留最小请求协议。
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
    val candidates: List<GeminiImageImportCandidateResponse> = emptyList(),
)

/**
 * Gemini 返回的单条图片候选。
 *
 * 用途说明：
 * - 支持一张截图返回多条候选交易，再由客户端本地过滤重复与已退还记录。
 *
 * 参数说明：
 * - amount：候选金额。
 * - direction：候选方向。
 * - merchant：候选商户。
 * - category：候选分类。
 * - note：候选备注。
 * - orderId：候选订单号。
 * - confidenceScore：候选置信度。
 * - transactionState：候选状态，如 ACTIVE / REFUNDED / RETURNED。
 * - stateReason：状态判断原因。
 * - evidence：证据片段。
 */
data class GeminiImageImportCandidateResponse(
    val amount: Double? = null,
    val direction: String = "EXPENSE",
    val merchant: String = "",
    val category: String = "待分类",
    val note: String = "",
    val orderId: String? = null,
    val confidenceScore: Float = 0f,
    val transactionState: String = "UNKNOWN",
    val stateReason: String = "",
    val evidence: String = "",
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
                        /**
                         * Gemini 3.1 Pro 预览模型在复杂截图上可能思考更久。
                         *
                         * 用途说明：
                         * - 连接建立通常不需要很久，因此 `connectTimeout` 保持中等值即可。
                         * - 真正容易超时的是模型生成与代理回传，所以 `readTimeout` 要明显放宽。
                         * - 再配合更长的协程总超时，避免在模型已经开始返回前就被客户端自己掐掉。
                         */
                        connectTimeout = NETWORK_CONNECT_TIMEOUT_MS
                        readTimeout = NETWORK_READ_TIMEOUT_MS
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
                            Log.w(TAG, "Gemini error body=$responseText")
                        }

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
            你是一个记账助手，请从图片中识别所有用户的交易候选记录，而不是只挑一条。
            当前提示词版本：$promptVersion。
            本次结果必须作为建议返回，requiresManualConfirmation 应保持为 $requireManualConfirmation。
            请严格返回一个 JSON 对象，不要使用 Markdown，不要输出额外解释。
            在微信、QQ这类气泡聊天场景下，只看右侧气泡，不要让左侧气泡影响上下文。
            举例：
            1.在微信聊天场景下，右侧气泡如果出现了"已被接受"，代表支出，如果出现了"已收款"，代表收入。
            2.在QQ聊天场景下，右侧气泡为红包，出现"已被领完"，此时金额未知，但代表支出。
            3.出现"您的3.0元补贴尚未使用"，表明这是噪音，抛弃。
            JSON 结构如下：
            {
              "candidates": [
                {
                  "amount": 可能为数字或 null,
                  "direction": "INCOME" | "EXPENSE" | "UNKNOWN",
                  "merchant": "字符串，无法确认时为空字符串",
                  "category": "字符串，无法确认时填待分类",
                  "note": "字符串，可为空",
                  "orderId": "字符串或 null",
                  "confidenceScore": 0.0,
                  "transactionState": "ACTIVE" | "REFUNDED" | "RETURNED" | "REVERSED" | "CANCELLED" | "UNKNOWN",
                  "stateReason": "字符串，可为空",
                  "evidence": "字符串，可为空"
                }
              ],
              "message": "给用户看的简短说明",
              "requiresManualConfirmation": true
            }
            如果图片看起来不像交易截图，candidates 返回空数组，并在 message 中说明需要用户手动确认。
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
        val parsedCandidates = payload.optJSONArray("candidates")
            ?.let(::parseImageCandidates)
            .orEmpty()
        val primaryCandidate = parsedCandidates.firstOrNull()
        return GeminiImageImportResponse(
            amount = primaryCandidate?.amount,
            direction = primaryCandidate?.direction ?: payload.optString("direction", "EXPENSE"),
            merchant = primaryCandidate?.merchant ?: payload.optString("merchant", ""),
            category = primaryCandidate?.category ?: payload.optString("category", "待分类"),
            note = primaryCandidate?.note ?: payload.optString("note", ""),
            orderId = primaryCandidate?.orderId ?: payload.optStringOrNull("orderId"),
            modelName = BuildConfig.GEMINI_IMAGE_MODEL,
            message = payload.optString("message", "图片分析已完成，请人工确认后保存。"),
            requiresManualConfirmation = payload.optBoolean("requiresManualConfirmation", true),
            candidates = parsedCandidates,
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
        /**
         * 截图链路传进来的并不总是 `content://`。
         *
         * 用途说明：
         * - 用户手动选图时通常是 `content://` Uri；
         * - 当前页面截图记账落盘后传进来的则是应用私有目录里的绝对路径。
         * - 如果这里只按 `contentResolver.openInputStream(uri)` 读取，截图链路即使开启云端分析，
         *   也会在“读图”这一步直接失败，看起来就像根本没走到 Gemini。
         */
        val rawBytes = when {
            uri.scheme.isNullOrBlank() -> readImageBytesFromFilePath(imageRef)
            uri.scheme == "file" -> readImageBytesFromFilePath(requireNotNull(uri.path))
            else -> resolver.openInputStream(uri)?.use { input -> input.readBytes() }
                ?: error("Unable to read image bytes from $imageRef")
        }

        // 说明：官方文档指出 inline image data 总请求体需控制在 20MB 内，且过大的图片应先缩放后再上传。
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: error("Unable to decode selected image")

        val normalizedBitmap = bitmap.normalizeForUpload()
        val compressedBytes = normalizedBitmap.compressToUploadJpeg()
        val finalByteSize = compressedBytes.size.toLong()
        if (finalByteSize > MAX_INLINE_IMAGE_BYTES) {
            error("Compressed image is still too large for inline upload: $finalByteSize bytes")
        }
        val encoded = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
        Log.d(
            TAG,
            "Prepared image for Gemini rawRef=$imageRef rawBytes=${rawBytes.size} normalized=${normalizedBitmap.width}x${normalizedBitmap.height} finalBytes=$finalByteSize",
        )
        return InlineImagePayload(
            // 说明：上传前统一重新压成 JPEG，因此这里直接声明最终上传的 MIME 类型即可。
            mimeType = DEFAULT_IMAGE_MIME_TYPE,
            base64Data = encoded,
        )
    }

    /**
     * 从应用私有目录绝对路径读取截图字节。
     *
     * 参数说明：
     * - path：截图文件绝对路径。
     */
    private fun readImageBytesFromFilePath(path: String): ByteArray {
        val file = File(path)
        check(file.exists()) { "Screenshot file does not exist: $path" }
        return file.inputStream().use { input -> input.readBytes() }
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
            note = "来自 gemini-3.1-pro-preview mock，建议人工确认后保存",
            orderId = "IMG-GEMINI3FLASH-MOCK-001",
            modelName = "gemini-3.1-pro-preview",
            message = "已按 gemini-3.1-pro-preview 生成 mock 建议，请核对金额、商户和备注后确认保存。",
            requiresManualConfirmation = true,
            candidates = listOf(
                GeminiImageImportCandidateResponse(
                    amount = 32.5,
                    direction = "EXPENSE",
                    merchant = "Gemini 图片建议商户",
                    category = "餐饮",
                    note = "来自 gemini-3.1-pro-preview mock，建议人工确认后保存",
                    orderId = "IMG-GEMINI3FLASH-MOCK-001",
                    confidenceScore = 0.88f,
                    transactionState = "ACTIVE",
                    stateReason = "页面呈现付款成功",
                    evidence = "支付成功 32.50 元",
                ),
            ),
        )
    }
}

/** inline data 所需的最小图片载荷。 */
private data class InlineImagePayload(
    val mimeType: String,
    val base64Data: String,
)

/**
 * 解析图片候选数组。
 *
 * 参数说明：
 * - array：模型返回的候选数组。
 */
private fun parseImageCandidates(array: JSONArray): List<GeminiImageImportCandidateResponse> {
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                GeminiImageImportCandidateResponse(
                    amount = item.optDoubleOrNull("amount"),
                    direction = item.optString("direction", "EXPENSE"),
                    merchant = item.optString("merchant", ""),
                    category = item.optString("category", "待分类"),
                    note = item.optString("note", ""),
                    orderId = item.optStringOrNull("orderId"),
                    confidenceScore = item.optDouble("confidenceScore", 0.0).toFloat(),
                    transactionState = item.optString("transactionState", "UNKNOWN"),
                    stateReason = item.optString("stateReason", ""),
                    evidence = item.optString("evidence", ""),
                ),
            )
        }
    }
}

private fun JSONObject.optDoubleOrNull(key: String): Double? {
    return if (isNull(key)) null else optDouble(key)
}

private fun JSONObject.optStringOrNull(key: String): String? {
    return if (isNull(key)) null else optString(key)
}

/**
 * 将原始位图归一化为更适合 Gemini inline upload 的尺寸。
 *
 * 用途说明：
 * - 避免超长截图或超高分辨率原图直接上传时触发 Gemini 的图片处理失败。
 * - 在保持纵横比的前提下，把最长边压到安全阈值以内。
 */
private fun Bitmap.normalizeForUpload(): Bitmap {
    val maxDimension = maxOf(width, height)
    if (maxDimension <= MAX_IMAGE_DIMENSION) {
        return this
    }

    val scale = MAX_IMAGE_DIMENSION.toFloat() / maxDimension.toFloat()
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    val matrix = Matrix().apply {
        postScale(scale, scale)
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/**
 * 把位图压成适合 inline upload 的 JPEG 字节数组。
 *
 * 用途说明：
 * - 逐步降低质量，尽量把最终字节数控制到 Gemini inline data 的安全区间。
 */
private fun Bitmap.compressToUploadJpeg(): ByteArray {
    var quality = INITIAL_JPEG_QUALITY
    var compressed = ByteArray(0)
    while (quality >= MIN_JPEG_QUALITY) {
        val output = ByteArrayOutputStream()
        check(compress(Bitmap.CompressFormat.JPEG, quality, output)) { "Unable to compress bitmap to JPEG" }
        compressed = output.toByteArray()
        if (compressed.size <= TARGET_INLINE_IMAGE_BYTES) {
            return compressed
        }
        quality -= JPEG_QUALITY_STEP
    }
    return compressed
}

private const val GEMINI_PROXY_BASE_URL = "https://api.wenapi.shop/v1beta"
/**
 * 连接超时。
 *
 * 用途说明：
 * - 只控制“能不能连上代理”这一步。
 * - 即使模型本身较慢，也不应拖慢 TCP/HTTP 建连阶段，因此保持 20 秒即可。
 */
private const val NETWORK_CONNECT_TIMEOUT_MS = 20_000

/**
 * 读取超时。
 *
 * 用途说明：
 * - `gemini-3.1-pro-preview` 在复杂长图、多候选提取场景下可能生成较久。
 * - 这里放宽到 120 秒，避免模型还在返回时就被客户端过早中断。
 */
private const val NETWORK_READ_TIMEOUT_MS = 120_000

/**
 * 整体协程超时。
 *
 * 用途说明：
 * - 覆盖图片读取、压缩、网络请求、模型生成和结果解析整条链路。
 * - 需要比 `readTimeout` 略长，给图片预处理和响应解析留出缓冲空间。
 */
private const val GEMINI_TOTAL_TIMEOUT_MS = 150_000L
private const val DEFAULT_IMAGE_MIME_TYPE = "image/jpeg"
private const val GEMINI_PROXY_USER_AGENT = "Autobook-Android-GeminiProxy/1.0"
private const val MAX_IMAGE_DIMENSION = 2048
private const val TARGET_INLINE_IMAGE_BYTES = 4L * 1024L * 1024L
private const val MAX_INLINE_IMAGE_BYTES = 18L * 1024L * 1024L
private const val INITIAL_JPEG_QUALITY = 85
private const val MIN_JPEG_QUALITY = 45
private const val JPEG_QUALITY_STEP = 10
private const val TAG = "GeminiImageImportApi"
