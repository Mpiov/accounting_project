package com.autobook.cn.core.data.impl

import android.content.Context
import android.net.Uri
import com.autobook.cn.core.data.OcrEngine
import com.autobook.cn.core.model.OperationResult
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * 基于端内 ML Kit 的 OCR 引擎。
 *
 * 用途说明：
 * - 读取本地截图文件并执行中文文本识别。
 * - 把识别结果整理成适合继续走本地交易解析器的纯文本。
 *
 * 参数说明：
 * - context：应用上下文，用于解析 `content://` Uri、`file://` Uri 和本地绝对路径对应的图片资源。
 */
@Singleton
class MlKitOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : OcrEngine {
    /**
     * 端内中文 OCR 识别器。
     *
     * 说明：
     * - 选择中文模型是因为当前产品范围聚焦中国区通知与支付截图，同时中文模型也能覆盖常见数字和英文订单号。
     */
    private val recognizer by lazy {
        TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build(),
        )
    }

    /**
     * 读取截图路径并返回 OCR 文本。
     *
     * 参数说明：
     * - imageRef：图片引用；当前既支持截图协调器返回的本地文件绝对路径，也支持图片导入时传入的 `content://` Uri 字符串。
     */
    override suspend fun extractText(imageRef: String): OperationResult<String> {
        val image = buildInputImage(imageRef)
            ?: return OperationResult.TerminalFailure("Unable to resolve OCR image: $imageRef")

        return try {
            val result = recognizer.process(image).awaitResult()
            val normalizedText = result.text.normalizeForParser()
            if (normalizedText.isBlank()) {
                OperationResult.RetryableFailure("OCR completed but no readable text was found")
            } else {
                OperationResult.Success(normalizedText)
            }
        } catch (error: Throwable) {
            OperationResult.RetryableFailure(
                message = "OCR processing failed for $imageRef",
                cause = error,
            )
        }
    }

    /**
     * 把统一的图片引用解析成 ML Kit 可消费的 `InputImage`。
     *
     * 参数说明：
     * - imageRef：上层传入的图片引用；可以是绝对路径、`file://` Uri 或 `content://` Uri。
     */
    private suspend fun buildInputImage(imageRef: String): InputImage? = withContext(Dispatchers.IO) {
        val trimmedRef = imageRef.trim()
        if (trimmedRef.isBlank()) {
            return@withContext null
        }

        val imageUri = when {
            trimmedRef.startsWith("content://") || trimmedRef.startsWith("file://") -> Uri.parse(trimmedRef)
            else -> {
                val imageFile = File(trimmedRef)
                if (!imageFile.exists()) {
                    return@withContext null
                }
                Uri.fromFile(imageFile)
            }
        }

        runCatching {
            InputImage.fromFilePath(context, imageUri)
        }.getOrNull()
    }
}

/**
 * 把 Google Task 包装成挂起函数，方便在协程链路里顺序编排 OCR。
 *
 * 参数说明：
 * - T：Task 成功时返回的数据类型。
 */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value ->
        if (continuation.isActive) {
            continuation.resume(value)
        }
    }
    addOnFailureListener { error ->
        if (continuation.isActive) {
            continuation.resumeWith(Result.failure(error))
        }
    }
    addOnCanceledListener {
        if (continuation.isActive) {
            continuation.cancel()
        }
    }
}

/**
 * 对 OCR 原始文本做最小清洗，减少换行、重复空白对规则解析器的干扰。
 */
private fun String.normalizeForParser(): String {
    return replace("\u3000", " ")
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString(separator = " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}
