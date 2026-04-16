package com.autobook.cn.core.data.impl

import com.autobook.cn.core.data.ImageImportLocalOcrEngine
import com.autobook.cn.core.data.OcrEngine
import com.autobook.cn.core.model.OperationResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 图片导入记账场景下的真实本地 OCR 适配器。
 *
 * 用途说明：
 * - 把图片导入链路专用的 `ImageImportLocalOcrEngine` 抽象接到通用 `OcrEngine` 上，避免两条链路各自维护一套 OCR 实现。
 * - 保留“图片导入 OCR”和“通用 OCR”两个接口层，是为了继续隔离业务语义；但底层识别能力统一复用 ML Kit。
 *
 * 参数说明：
 * - ocrEngine：当前绑定到 ML Kit 的通用 OCR 引擎，负责真正执行端内文字识别。
 */
@Singleton
class ImageImportMlKitOcrEngine @Inject constructor(
    private val ocrEngine: OcrEngine,
) : ImageImportLocalOcrEngine {
    /**
     * 对图片导入传入的引用执行本地 OCR。
     *
     * 参数说明：
     * - imageRef：图片导入页面透传下来的图片引用；当前可能是 `content://` Uri 字符串，也可能是本地缓存路径。
     */
    override suspend fun extractText(imageRef: String): OperationResult<String> {
        return ocrEngine.extractText(imageRef)
    }
}
