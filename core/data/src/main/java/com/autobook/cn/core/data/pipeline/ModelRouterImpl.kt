package com.autobook.cn.core.data.pipeline

import com.autobook.cn.core.data.LocalLlmAnalyzer
import com.autobook.cn.core.data.ModelRouter
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.data.TransactionExtractor
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.HIGH_CONFIDENCE_THRESHOLD
import com.autobook.cn.core.model.MEDIUM_CONFIDENCE_THRESHOLD
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.PENDING_CONFIDENCE_MAX_SCORE
import com.autobook.cn.core.model.TransactionCandidate
import com.autobook.cn.core.network.CloudAnalysisApi
import com.autobook.cn.core.network.toCloudRequest
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.first

/**
 * 在本地规则、端侧 LLM 与云端分析之间做逐级兜底。
 *
 * 用途说明：
 * - 通知自动记账链路当前明确只依赖通知文本，不再把 OCR 当成默认兜底层。
 * - 优先尝试本地规则解析，保证主链路离线可用且时延最低。
 * - 当本地规则得到的是“中置信度银行转账候选”时，先进入本地 LLM 复核链路。
 * - 只有用户显式开启云端分析后，才继续升级到网络兜底。
 * - OCR 后续只作为“用户主动上传图片记账”能力的预留，不再参与通知自动记账路由。
 *
 * 参数说明：
 * - localExtractor：本地规则解析器。
 * - localLlmAnalyzer：本地 LLM 分析器，内部可先尝试 on-device 适配层，再按需回退到 baseline。
 * - cloudAnalysisApi：云端分析占位接口。
 * - settingsRepository：运行时开关与留存配置来源。
 */
class ModelRouterImpl @Inject constructor(
    private val localExtractor: TransactionExtractor,
    private val localLlmAnalyzer: LocalLlmAnalyzer,
    private val cloudAnalysisApi: CloudAnalysisApi,
    private val settingsRepository: SettingsRepository,
) : ModelRouter {
    /**
     * 按既定优先级路由当前通知。
     *
     * 参数说明：
     * - event：当前待路由的原始通知事件。
     */
    override suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate> {
        val settings = settingsRepository.settings.first()
        val local = localExtractor.extract(event)
        if (local is OperationResult.Success) {
            val promotedByLocalLlm = tryPromoteMediumConfidenceCandidate(
                event = event,
                seedCandidate = local.data,
                settings = settings,
            )
            return promotedByLocalLlm ?: local
        }

        if (!settings.cloudAnalysisEnabled) {
            return local
        }

        val cloudCandidate = cloudAnalysisApi.analyze(event.toCloudRequest())
            ?: return OperationResult.RetryableFailure("Cloud analysis unavailable")
        return OperationResult.Success(cloudCandidate.copy(parsePath = "cloud-placeholder"))
    }

    /**
     * 在规则层得到“中置信度银行转账候选”时，尝试进入本地 LLM 复核链路。
     *
     * 参数说明：
     * - event：原始通知事件，用于补充银行与转账语义。
     * - seedCandidate：规则层已经提取出的基础候选。
     * - settings：当前运行时设置，用于判断是否允许启用本地 LLM 增强。
     */
    private suspend fun tryPromoteMediumConfidenceCandidate(
        event: NotificationEvent,
        seedCandidate: TransactionCandidate,
        settings: AppSettings,
    ): OperationResult.Success<TransactionCandidate>? {
        if (!settings.localLlmConfidenceBoostEnabled) {
            return null
        }
        if (!isEligibleForLocalLlmPromotion(event = event, seedCandidate = seedCandidate)) {
            return null
        }

        val llmResult = localLlmAnalyzer.analyze(
            event = event,
            seedCandidate = seedCandidate,
        )
        if (llmResult !is OperationResult.Success) {
            return null
        }

        val validatedCandidate = validateLocalLlmCandidate(
            event = event,
            seedCandidate = seedCandidate,
            promotedCandidate = llmResult.data,
        ) ?: return null
        return OperationResult.Success(validatedCandidate)
    }

    /**
     * 判断当前候选是否值得进入本地 LLM 复核。
     *
     * 参数说明：
     * - event：原始通知事件。
     * - seedCandidate：规则层已经给出的基础候选。
     */
    private fun isEligibleForLocalLlmPromotion(
        event: NotificationEvent,
        seedCandidate: TransactionCandidate,
    ): Boolean {
        val score = seedCandidate.confidence.score
        if (score !in LOCAL_LLM_MIN_CONFIDENCE..LOCAL_LLM_MAX_CONFIDENCE) {
            return false
        }

        val mergedText = listOfNotNull(event.title, event.text)
            .joinToString(separator = " ")
            .lowercase()
        val looksLikeBank = seedCandidate.sourceApp == NotificationSourceApp.BANK_SMS ||
            BANK_LIKE_KEYWORDS.any(mergedText::contains)
        val looksLikeTransfer = TRANSFER_LIKE_KEYWORDS.any(mergedText::contains) ||
            seedCandidate.categoryHint == "转账"
        return looksLikeBank && looksLikeTransfer
    }

    /**
     * 对本地 LLM 输出做最终硬校验，只有满足金额、方向和语义一致性时才接受提升。
     *
     * 参数说明：
     * - event：原始通知事件，用于再次检查是否仍然存在银行转账语义。
     * - seedCandidate：规则层的基础候选。
     * - promotedCandidate：本地 LLM 输出的增强候选。
     */
    private fun validateLocalLlmCandidate(
        event: NotificationEvent,
        seedCandidate: TransactionCandidate,
        promotedCandidate: TransactionCandidate,
    ): TransactionCandidate? {
        if (abs(promotedCandidate.amount - seedCandidate.amount) > MAX_AMOUNT_DELTA) {
            return null
        }
        if (promotedCandidate.direction == MoneyDirection.UNKNOWN) {
            return null
        }
        if (
            seedCandidate.direction != MoneyDirection.UNKNOWN &&
            promotedCandidate.direction != seedCandidate.direction
        ) {
            return null
        }

        val mergedText = listOfNotNull(event.title, event.text)
            .joinToString(separator = " ")
            .lowercase()
        if (!TRANSFER_LIKE_KEYWORDS.any(mergedText::contains)) {
            return null
        }

        val promotedScore = buildValidatedPromotedScore(promotedCandidate)
        return promotedCandidate.copy(
            confidence = ParseConfidence(promotedScore),
            confidenceReason = buildValidatedConfidenceReason(promotedCandidate),
        )
    }

    /**
     * 根据本地 LLM 是否建议自动入账来裁剪最终分数。
     *
     * 参数说明：
     * - promotedCandidate：已经过本地 LLM 语义增强、待进入最终 guardrail 的候选。
     */
    private fun buildValidatedPromotedScore(promotedCandidate: TransactionCandidate): Float {
        val boundedScore = promotedCandidate.confidence.score.coerceAtMost(MAX_PROMOTED_CONFIDENCE)
        return when (promotedCandidate.llmSuggestedAutoPost) {
            false -> boundedScore.coerceAtMost(LOCAL_LLM_MAX_CONFIDENCE)
            true, null -> boundedScore.coerceAtLeast(LOCAL_LLM_PROMOTED_CONFIDENCE)
        }
    }

    /**
     * 生成校验后的置信度说明，明确记录端侧模型建议是否允许自动入账。
     *
     * 参数说明：
     * - promotedCandidate：已经过本地 LLM 语义增强的候选。
     */
    private fun buildValidatedConfidenceReason(promotedCandidate: TransactionCandidate): String {
        val autoPostLabel = when (promotedCandidate.llmSuggestedAutoPost) {
            true -> "suggested"
            false -> "rejected"
            null -> "unknown"
        }
        return "${promotedCandidate.confidenceReason}; validated by local heuristic guardrail; autoPost=$autoPostLabel"
    }
}

/** 只有中置信度区间的候选才值得进入本地 LLM 第二裁决，避免高低两端都浪费成本。 */
private const val LOCAL_LLM_MIN_CONFIDENCE = MEDIUM_CONFIDENCE_THRESHOLD

/** 允许把贴近自动入账阈值的边界候选也交给本地 LLM 复核，避免 0.85 附近的浮点边界值直接卡在 pending。 */
private const val LOCAL_LLM_MAX_CONFIDENCE = PENDING_CONFIDENCE_MAX_SCORE

/** 本地 LLM 通过校验后，至少要把置信度提升到这个分数才允许进入自动记账判断。 */
private const val LOCAL_LLM_PROMOTED_CONFIDENCE = HIGH_CONFIDENCE_THRESHOLD

/** 即使本地 LLM 提示非常强，也限制最大提升分值，避免把链路变成黑箱“满分”。 */
private const val MAX_PROMOTED_CONFIDENCE = 0.93f

/** 金额校验容忍 1 分以内的浮动，避免格式化差异导致本地 LLM 合法结果被拒绝。 */
private const val MAX_AMOUNT_DELTA = 0.01

/** 说明银行来源的语义关键词。 */
private val BANK_LIKE_KEYWORDS = listOf("银行", "卡号", "尾号", "账户", "借记卡", "信用卡")

/** 说明转账语义的关键词。 */
private val TRANSFER_LIKE_KEYWORDS = listOf("转账", "转出", "转入", "汇款", "汇出", "汇入")
