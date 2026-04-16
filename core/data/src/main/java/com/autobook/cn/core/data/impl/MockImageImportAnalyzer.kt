package com.autobook.cn.core.data.impl

import com.autobook.cn.core.data.ImageImportAnalyzer
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.model.ImageImportAnalysisPath
import com.autobook.cn.core.model.ImageImportCandidate
import com.autobook.cn.core.model.ImageImportSuggestion
import com.autobook.cn.core.model.ImageImportTransactionState
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.network.CloudImageImportApi
import com.autobook.cn.core.network.CloudImageImportRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 用途说明：
 * - 图片导入分析器。
 * - 当前仅保留“云端分析 + 本地规则后处理”主路径。
 * - 当用户关闭云端分析时，直接返回手动确认草稿，不再执行本地 OCR 或端侧模型推理。
 *
 * 参数说明：
 * - settingsRepository：读取云端分析开关。
 * - cloudImageImportApi：云端图片分析接口。
 * - candidatePostProcessor：对云端候选执行本地规则校验、去重和状态过滤。
 */
@Singleton
class MockImageImportAnalyzer @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cloudImageImportApi: CloudImageImportApi,
    private val candidatePostProcessor: ImageImportCandidatePostProcessor,
) : ImageImportAnalyzer {
    /**
     * 用途说明：
     * - 根据当前设置生成图片导入建议。
     *
     * 参数说明：
     * - imageRef：当前图片引用。
     */
    override suspend fun analyze(imageRef: String): OperationResult<ImageImportSuggestion> {
        val settings = settingsRepository.settings.first()
        if (!settings.cloudAnalysisEnabled) {
            return OperationResult.Success(
                ImageImportSuggestion(
                    amount = null,
                    direction = MoneyDirection.UNKNOWN,
                    merchant = "",
                    category = "待分类",
                    note = "已关闭云端分析，本地不再执行 OCR 或模型识别，请手动确认。",
                    orderId = null,
                    confidence = ParseConfidence(0f),
                    tracePath = ImageImportAnalysisPath.MANUAL_CONFIRMATION_REQUIRED,
                    message = "云端分析已关闭；当前本地仅保留规则校验，不再生成图片识别建议，请手动补全金额、商户和备注。",
                    requiresManualConfirmation = true,
                ),
            )
        }

        val response = cloudImageImportApi.analyze(
            CloudImageImportRequest(
                imageRef = imageRef,
                promptVersion = IMAGE_IMPORT_PROMPT_VERSION,
                requireManualConfirmation = true,
            ),
        ) ?: return OperationResult.RetryableFailure("Cloud image import service is unavailable")

        return OperationResult.Success(
            candidatePostProcessor.postProcess(
                ImageImportSuggestion(
                    amount = response.amount,
                    timestamp = response.timestamp,
                    direction = response.direction.toMoneyDirection(),
                    merchant = response.merchant,
                    category = response.category,
                    note = response.note,
                    orderId = response.orderId,
                    confidence = ParseConfidence(
                        response.candidates.maxOfOrNull { it.confidenceScore } ?: DEFAULT_IMAGE_IMPORT_CLOUD_CONFIDENCE,
                    ),
                    tracePath = ImageImportAnalysisPath.CLOUD_LLM_PROXY,
                    message = response.message,
                    requiresManualConfirmation = response.requiresManualConfirmation,
                    candidates = response.candidates.map { candidate ->
                        ImageImportCandidate(
                            amount = candidate.amount,
                            timestamp = candidate.timestamp,
                            direction = candidate.direction.toMoneyDirection(),
                            merchant = candidate.merchant,
                            category = candidate.category,
                            note = candidate.note,
                            orderId = candidate.orderId,
                            confidence = ParseConfidence(candidate.confidenceScore),
                            transactionState = candidate.transactionState.toImageImportTransactionState(),
                            stateReason = candidate.stateReason,
                            evidence = candidate.evidence,
                        )
                    },
                ),
            ),
        )
    }
}

/**
 * 用途说明：
 * - 图片导入云端提示词版本号。
 */
private const val IMAGE_IMPORT_PROMPT_VERSION = "image-import-v3-right-bubble-only"

/**
 * 用途说明：
 * - 云端路径缺少明确候选分数时使用的默认置信度。
 */
private const val DEFAULT_IMAGE_IMPORT_CLOUD_CONFIDENCE = 0.82f

/**
 * 用途说明：
 * - 把网络层方向字符串映射回领域枚举。
 *
 * 参数说明：
 * - 接收者：网络层返回的方向字符串。
 */
private fun String.toMoneyDirection(): MoneyDirection = when (uppercase()) {
    "INCOME" -> MoneyDirection.INCOME
    "EXPENSE" -> MoneyDirection.EXPENSE
    else -> MoneyDirection.EXPENSE
}

/**
 * 用途说明：
 * - 把网络层交易状态映射回图片候选状态。
 *
 * 参数说明：
 * - 接收者：网络层返回的状态字符串。
 */
private fun String.toImageImportTransactionState(): ImageImportTransactionState = when (uppercase()) {
    "ACTIVE" -> ImageImportTransactionState.ACTIVE
    "REFUNDED" -> ImageImportTransactionState.REFUNDED
    "RETURNED" -> ImageImportTransactionState.RETURNED
    "REVERSED" -> ImageImportTransactionState.REVERSED
    "CANCELLED" -> ImageImportTransactionState.CANCELLED
    else -> ImageImportTransactionState.UNKNOWN
}
