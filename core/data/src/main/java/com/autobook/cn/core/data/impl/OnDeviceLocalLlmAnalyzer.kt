package com.autobook.cn.core.data.impl

import android.util.Log
import com.autobook.cn.core.data.LocalLlmAnalyzer
import com.autobook.cn.core.data.LocalLlmRequest
import com.autobook.cn.core.data.LocalLlmResponse
import com.autobook.cn.core.data.OnDeviceLlmEngine
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.TransactionCandidate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 真端侧 LLM 的适配分析器。
 *
 * 用途说明：
 * - 对上继续暴露稳定的 `LocalLlmAnalyzer` 抽象，不改现有路由器调用方式。
 * - 对内先走标准化的端侧模型协议；如果模型不可用、超时或输出非法，再回退到启发式 baseline。
 *
 * 参数说明：
 * - onDeviceLlmEngine：端侧模型引擎抽象，未来可替换为 ONNX、GGUF 等真实推理实现。
 * - heuristicFallbackAnalyzer：当真模型不可用或输出不可信时使用的兜底语义分析器。
 */
@Singleton
class OnDeviceLocalLlmAnalyzer @Inject constructor(
    private val onDeviceLlmEngine: OnDeviceLlmEngine,
    private val heuristicFallbackAnalyzer: HeuristicLocalLlmAnalyzer,
) : LocalLlmAnalyzer {
    /**
     * 先尝试端侧模型输出结构化结果，再在必要时回退到启发式 baseline。
     *
     * 参数说明：
     * - event：原始通知事件。
     * - seedCandidate：规则层给出的基础候选。
     */
    override suspend fun analyze(
        event: NotificationEvent,
        seedCandidate: TransactionCandidate,
    ): OperationResult<TransactionCandidate> {
        val request = LocalLlmRequest(
            title = event.title,
            body = event.text,
            sourceApp = seedCandidate.sourceApp,
            seedAmount = seedCandidate.amount,
            seedDirection = seedCandidate.direction,
            seedMerchant = seedCandidate.merchant,
            seedCategoryHint = seedCandidate.categoryHint,
            seedConfidenceScore = seedCandidate.confidence.score,
            seedConfidenceReason = seedCandidate.confidenceReason,
            seedParsePath = seedCandidate.parsePath,
        )

        return when (val engineResult = onDeviceLlmEngine.analyze(request)) {
            is OperationResult.Success -> {
                val validatedResponse = validateEngineResponse(engineResult.data)
                if (validatedResponse == null) {
                    logWarn(TAG, "真本地 LLM 返回结构化结果未通过校验，准备回退到启发式实现")
                    fallbackToHeuristic(
                        event = event,
                        seedCandidate = seedCandidate,
                        reason = "on-device engine returned invalid structured output",
                    )
                } else {
                    OperationResult.Success(
                        buildPromotedCandidate(
                            seedCandidate = seedCandidate,
                            response = validatedResponse,
                        ),
                    )
                }
            }

            is OperationResult.RetryableFailure -> fallbackToHeuristic(
                event = event,
                seedCandidate = seedCandidate,
                reason = engineResult.message,
            )

            is OperationResult.TerminalFailure -> fallbackToHeuristic(
                event = event,
                seedCandidate = seedCandidate,
                reason = engineResult.message,
            )
        }
    }

    /**
     * 校验端侧模型结构化输出是否满足最小可信要求。
     *
     * 参数说明：
     * - response：端侧模型返回的结构化结果。
     */
    private fun validateEngineResponse(response: LocalLlmResponse): LocalLlmResponse? {
        if (response.direction == MoneyDirection.UNKNOWN) {
            logWarn(TAG, "丢弃真本地 LLM 结果：direction=UNKNOWN")
            return null
        }
        if (!response.confidenceScore.isFinite() || response.confidenceScore !in 0f..1f) {
            logWarn(TAG, "丢弃真本地 LLM 结果：confidenceScore=${response.confidenceScore}")
            return null
        }
        if (response.explanation.isBlank()) {
            logWarn(TAG, "丢弃真本地 LLM 结果：explanation 为空")
            return null
        }
        if (response.modelVersion.isBlank()) {
            logWarn(TAG, "丢弃真本地 LLM 结果：modelVersion 为空")
            return null
        }
        return response
    }

    /**
     * 把端侧模型结构化输出映射回统一的交易候选。
     *
     * 参数说明：
     * - seedCandidate：规则层候选。
     * - response：端侧模型输出。
     */
    private fun buildPromotedCandidate(
        seedCandidate: TransactionCandidate,
        response: LocalLlmResponse,
    ): TransactionCandidate {
        return seedCandidate.copy(
            direction = response.direction,
            merchant = response.merchant?.trim().takeUnless { it.isNullOrBlank() } ?: seedCandidate.merchant,
            categoryHint = response.categoryHint?.trim().takeUnless { it.isNullOrBlank() } ?: seedCandidate.categoryHint,
            confidence = ParseConfidence(response.confidenceScore),
            confidenceReason = response.explanation,
            parsePath = "${seedCandidate.parsePath}+local-llm",
            modelVersion = response.modelVersion,
            llmSuggestedAutoPost = response.shouldAutoPost,
        )
    }

    /**
     * 端侧模型不可用时回退到启发式 baseline，并把回退原因补入审计信息。
     *
     * 参数说明：
     * - event：原始通知事件。
     * - seedCandidate：规则层候选。
     * - reason：触发回退的模型失败原因。
     */
    private suspend fun fallbackToHeuristic(
        event: NotificationEvent,
        seedCandidate: TransactionCandidate,
        reason: String,
    ): OperationResult<TransactionCandidate> {
        logWarn(TAG, "真本地 LLM 回退到启发式实现：$reason")
        return when (val fallback = heuristicFallbackAnalyzer.analyze(event, seedCandidate)) {
            is OperationResult.Success -> {
                OperationResult.Success(
                    fallback.data.copy(
                        confidenceReason = "${fallback.data.confidenceReason}; fallback-from-on-device=$reason",
                    ),
                )
            }

            is OperationResult.RetryableFailure -> {
                OperationResult.RetryableFailure(
                    "On-device local LLM failed and heuristic fallback also failed: $reason / ${fallback.message}",
                )
            }

            is OperationResult.TerminalFailure -> {
                OperationResult.TerminalFailure(
                    "On-device local LLM failed and heuristic fallback also failed: $reason / ${fallback.message}",
                )
            }
        }
    }
}

/** 说明：统一 on-device 分析器调试日志标签。 */
private const val TAG = "AutobookLocalLlm"

/**
 * 安全输出警告日志，避免 JVM 单测环境中 `android.util.Log` 未 mock 时抛异常。
 *
 * 参数说明：
 * - tag：日志标签。
 * - message：日志正文。
 */
private fun logWarn(
    tag: String,
    message: String,
) {
    runCatching { Log.w(tag, message) }
}
