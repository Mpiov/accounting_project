package com.autobook.cn.core.data.impl

import com.autobook.cn.core.data.ImageImportAnalyzer
import com.autobook.cn.core.data.ImageImportLocalOcrEngine
import com.autobook.cn.core.data.ImageImportLocalReasoner
import com.autobook.cn.core.data.LocalModelRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.model.ImageImportAnalysisPath
import com.autobook.cn.core.model.ImageImportCandidate
import com.autobook.cn.core.model.ImageImportSuggestion
import com.autobook.cn.core.model.ImageImportTransactionState
import com.autobook.cn.core.model.LocalModelInstallStatus
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.network.CloudImageImportApi
import com.autobook.cn.core.network.CloudImageImportRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 图片导入记账分析器的 mock 实现。
 *
 * 用途说明：
 * - 在真实图片选择、真实 OCR、真实云端多模态尚未接入前，先把产品路由规则固化下来。
 * - 当前云端路径已收口成“云端大模型代理链路”，且仍通过独立网络边界接口取结果，便于后续继续替换真实后端时保持接口语义一致。
 * - 根据当前设置和本地模型状态，返回“云端大模型代理链路”“本地 OCR + 端侧 Qwen”或“手动确认草稿”三种结果。
 *
 * 参数说明：
 * - settingsRepository：读取云端分析开关，决定是否优先走云端大模型代理链路。
 * - localModelRepository：读取当前本地模型安装状态，决定是否允许走本地 OCR + 端侧 Qwen 路径。
 * - cloudImageImportApi：云端大模型图片分析接口，当前由网络层负责把图片请求转发到代理。
 * - imageImportLocalOcrEngine：本地图片 OCR 层，当前由 ML Kit 识别图片文字。
 * - imageImportLocalReasoner：本地端侧推理层，当前由 `llama.cpp + Qwen` 生成结构化记账建议。
 */
@Singleton
class MockImageImportAnalyzer @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val localModelRepository: LocalModelRepository,
    private val cloudImageImportApi: CloudImageImportApi,
    private val imageImportLocalOcrEngine: ImageImportLocalOcrEngine,
    private val imageImportLocalReasoner: ImageImportLocalReasoner,
    private val candidatePostProcessor: ImageImportCandidatePostProcessor,
) : ImageImportAnalyzer {
    /**
     * 基于当前运行时配置生成一条 mock 图片导入建议。
     *
     * 参数说明：
     * - imageRef：图片引用；当前阶段仅用于保留接口形状，不参与真实解析。
     */
    override suspend fun analyze(imageRef: String): OperationResult<ImageImportSuggestion> {
        val settings = settingsRepository.settings.first()
        if (settings.cloudAnalysisEnabled) {
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
                    direction = response.direction.toMoneyDirection(),
                    merchant = response.merchant,
                    category = response.category,
                    note = response.note,
                    orderId = response.orderId,
                    confidence = ParseConfidence(response.candidates.maxOfOrNull { it.confidenceScore } ?: DEFAULT_IMAGE_IMPORT_CLOUD_CONFIDENCE),
                    tracePath = ImageImportAnalysisPath.CLOUD_LLM_PROXY,
                    message = response.message,
                    requiresManualConfirmation = response.requiresManualConfirmation,
                    candidates = response.candidates.map { candidate ->
                        ImageImportCandidate(
                            amount = candidate.amount,
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

        // 只有在用户明确开启 OCR 预留开关后，才允许进入本地图片文字识别路径。
        if (!settings.ocrEnabled) {
            return OperationResult.Success(
                ImageImportSuggestion(
                    amount = null,
                    direction = MoneyDirection.EXPENSE,
                    merchant = "",
                    category = "待分类",
                    note = "本地 OCR 未开启，当前进入手动确认草稿。",
                    orderId = null,
                    tracePath = ImageImportAnalysisPath.MANUAL_CONFIRMATION_REQUIRED,
                    message = "云端分析未开启，且本地 OCR 识别开关处于关闭状态，请在设置中开启后重试或手动补全。",
                    requiresManualConfirmation = true,
                ),
            )
        }

        val localModelSnapshot = localModelRepository.snapshot.first()
        return if (localModelSnapshot.installStatus == LocalModelInstallStatus.READY) {
            when (val ocrResult = imageImportLocalOcrEngine.extractText(imageRef)) {
                is OperationResult.Success -> {
                    when (val reasonerResult = imageImportLocalReasoner.analyze(
                        imageRef = imageRef,
                        ocrText = ocrResult.data,
                    )) {
                        is OperationResult.Success -> OperationResult.Success(candidatePostProcessor.postProcess(reasonerResult.data))
                        is OperationResult.RetryableFailure -> reasonerResult
                        is OperationResult.TerminalFailure -> reasonerResult
                    }
                }
                is OperationResult.RetryableFailure -> ocrResult
                is OperationResult.TerminalFailure -> ocrResult
            }
        } else {
            OperationResult.Success(
                ImageImportSuggestion(
                    amount = null,
                    direction = MoneyDirection.UNKNOWN,
                    merchant = "",
                    category = "待分类",
                    note = "本地模型未下载，当前进入手动确认草稿。",
                    orderId = null,
                    confidence = ParseConfidence(0f),
                    tracePath = ImageImportAnalysisPath.MANUAL_CONFIRMATION_REQUIRED,
                    message = "云端分析未开启且本地模型未下载，请手动补全金额、商户和备注。",
                    requiresManualConfirmation = true,
                ),
            )
        }
    }
}

/** 当前图片导入提示词版本号，供后续接真实后端时做灰度与回放追踪。 */
private const val IMAGE_IMPORT_PROMPT_VERSION = "image-import-v3-right-bubble-only"

/** 云端路径当前没有严格的结构化候选分数时，给一档开发阶段默认置信度。 */
private const val DEFAULT_IMAGE_IMPORT_CLOUD_CONFIDENCE = 0.82f

/** 把网络层方向字符串映射回领域枚举，未知值统一回退为支出建议并交给用户确认。 */
private fun String.toMoneyDirection(): MoneyDirection = when (uppercase()) {
    "INCOME" -> MoneyDirection.INCOME
    "EXPENSE" -> MoneyDirection.EXPENSE
    else -> MoneyDirection.EXPENSE
}

/** 把网络层交易状态映射成图片导入候选状态。 */
private fun String.toImageImportTransactionState(): ImageImportTransactionState = when (uppercase()) {
    "ACTIVE" -> ImageImportTransactionState.ACTIVE
    "REFUNDED" -> ImageImportTransactionState.REFUNDED
    "RETURNED" -> ImageImportTransactionState.RETURNED
    "REVERSED" -> ImageImportTransactionState.REVERSED
    "CANCELLED" -> ImageImportTransactionState.CANCELLED
    else -> ImageImportTransactionState.UNKNOWN
}
