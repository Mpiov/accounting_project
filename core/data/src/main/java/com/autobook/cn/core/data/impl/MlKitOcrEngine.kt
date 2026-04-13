package com.autobook.cn.core.data.impl

import android.graphics.BitmapFactory
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
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 基于端内 ML Kit 的 OCR 引擎。
 *
 * 用途说明：
 * - 读取本地截图文件并执行中文文本识别。
 * - 把识别结果整理成适合继续走本地交易解析器的纯文本。
 *
 * 参数说明：
 * - 当前实现无构造参数；识别器内部按懒加载方式初始化，避免应用冷启动阶段额外做重活。
 */
@Singleton
class MlKitOcrEngine @Inject constructor() : OcrEngine {
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
     * - imageRef：由截图协调器返回的本地文件绝对路径。
     */
    override suspend fun extractText(imageRef: String): OperationResult<String> {
        val imageFile = File(imageRef)
        if (!imageFile.exists()) {
            return OperationResult.TerminalFailure("OCR image does not exist: $imageRef")
        }

        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            ?: return OperationResult.TerminalFailure("Unable to decode OCR image: $imageRef")
        val image = InputImage.fromBitmap(bitmap, 0)

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
