package com.autobook.cn.core.data.impl

import com.autobook.cn.core.data.ImageImportLocalOcrEngine
import com.autobook.cn.core.data.ImageImportLocalReasoner
import com.autobook.cn.core.model.ImageImportAnalysisPath
import com.autobook.cn.core.model.ImageImportSuggestion
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.OperationResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 图片导入记账场景的本地 OCR fake 实现。
 *
 * 用途说明：
 * - 当前阶段不做真实 OCR，只返回固定文本，便于把本地图片链路中的“OCR 层”单独拆出来。
 * - 后续接入真实 OCR 时，只需要替换这个实现。
 */
@Singleton
class FakeImageImportLocalOcrEngine @Inject constructor() : ImageImportLocalOcrEngine {
    /**
     * 返回一段固定 OCR 文本，用于驱动后续本地轻量模型 mock。
     *
     * 参数说明：
     * - imageRef：图片引用；当前 fake 实现仅保留接口形状，不做实际读取。
     */
    override suspend fun extractText(imageRef: String): OperationResult<String> {
        return OperationResult.Success("支付成功 商户 小象超市 金额 18.00 元")
    }
}

/**
 * 图片导入记账场景的本地轻量模型 fake 实现。
 *
 * 用途说明：
 * - 接收 OCR 文本并输出结构化建议，模拟后续“本地 OCR + 轻量模型”路径。
 * - 上层分析器只负责编排，不再手写本地建议内容。
 */
@Singleton
class FakeImageImportLocalReasoner @Inject constructor() : ImageImportLocalReasoner {
    /**
     * 基于 OCR 文本返回固定结构化建议。
     *
     * 参数说明：
     * - imageRef：图片引用；当前阶段不参与真实推理。
     * - ocrText：OCR 层输出的文本；当前 fake 实现只用于保留接口形状。
     */
    override suspend fun analyze(
        imageRef: String,
        ocrText: String,
    ): OperationResult<ImageImportSuggestion> {
        return OperationResult.Success(
            ImageImportSuggestion(
                amount = 18.0,
                direction = MoneyDirection.EXPENSE,
                merchant = "本地图片识别示例商户",
                category = "购物",
                note = "来自 OCR + 本地轻量模型 mock，建议人工确认后保存",
                orderId = "IMG-LOCAL-MOCK-001",
                tracePath = ImageImportAnalysisPath.OCR_PLUS_LOCAL_LLM,
                message = "已按本地 OCR + 轻量模型 mock 生成建议，请核对后保存。",
                requiresManualConfirmation = true,
            ),
        )
    }
}
