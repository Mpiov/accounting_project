package com.autobook.cn.core.data.impl

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地截图存储器。
 *
 * 用途说明：
 * - 负责把自动化截图以 PNG 形式落盘到应用私有目录。
 * - 返回稳定的绝对路径给 OCR 引擎和后续留存策略使用。
 *
 * 参数说明：
 * - context：应用级上下文，用于定位私有文件目录。
 */
@Singleton
class ScreenshotStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * 保存一次 OCR 回退截图，并返回本地文件绝对路径。
     *
     * 参数说明：
     * - sessionId：本次截图会话 id，用于生成可追踪且不冲突的文件名。
     * - bitmap：待保存的截图位图。
     */
    fun save(sessionId: String, bitmap: Bitmap): String {
        val directory = File(context.filesDir, SCREENSHOT_DIRECTORY_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        val targetFile = File(directory, "ocr_capture_$sessionId.png")
        FileOutputStream(targetFile).use { outputStream ->
            // 使用 PNG 保留原始像素信息，避免再次压缩影响 OCR 结果。
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
        }
        return targetFile.absolutePath
    }

    companion object {
        private const val SCREENSHOT_DIRECTORY_NAME = "ocr_screenshots"
    }
}
