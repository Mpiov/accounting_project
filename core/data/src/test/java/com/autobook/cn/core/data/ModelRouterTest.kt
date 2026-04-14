package com.autobook.cn.core.data

import com.autobook.cn.core.data.pipeline.ModelRouterImpl
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.TransactionCandidate
import com.autobook.cn.core.network.CloudAnalysisApi
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 验证路由器会按“本地规则 -> 本地 LLM -> 云端”的顺序处理通知。 */
class ModelRouterTest {
    @Test
    fun `uses local result before cloud`() = runTest {
        val extractor = object : TransactionExtractor {
            /** 返回高置信度本地结果，验证此时不会再触发后续增强链路。 */
            override suspend fun extract(event: NotificationEvent): OperationResult<TransactionCandidate> =
                OperationResult.Success(
                    TransactionCandidate(
                        amount = 12.5,
                        direction = MoneyDirection.EXPENSE,
                        merchant = "微信支付",
                        channel = "微信支付",
                        timestamp = 1L,
                        confidence = ParseConfidence(0.9f),
                        confidenceReason = "Matched local rules",
                        rawSource = "notification",
                        parsePath = "local-parser",
                        sourceApp = NotificationSourceApp.WECHAT,
                    ),
                )
        }

        val result = ModelRouterImpl(
            localExtractor = extractor,
            localLlmAnalyzer = noopLocalLlmAnalyzer(),
            cloudAnalysisApi = mockk<CloudAnalysisApi>(relaxed = true),
            settingsRepository = buildSettingsRepository(
                AppSettings(
                    cloudAnalysisEnabled = true,
                    localLlmConfidenceBoostEnabled = true,
                ),
            ),
        ).route(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "支付成功 12.50 元",
                postedAt = 1L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals("local-parser", candidate.parsePath)
        assertEquals("local-mock-v1", candidate.modelVersion)
    }

    @Test
    fun `medium confidence triggers local llm enrichment`() = runTest {
        val mediumCandidate = TransactionCandidate(
            amount = 50.0,
            direction = MoneyDirection.EXPENSE,
            merchant = "银行服务",
            channel = "通知",
            timestamp = 1234L,
            confidence = ParseConfidence(0.65f),
            confidenceReason = "本地规则置信度不足",
            rawSource = "notification",
            parsePath = "local-parser",
            modelVersion = "local-mock-v1",
            sourceApp = NotificationSourceApp.BANK_SMS,
            categoryHint = "待分类",
        )
        val extractor = object : TransactionExtractor {
            /** 始终返回中置信度银行转账候选，触发本地 LLM 第二裁决。 */
            override suspend fun extract(event: NotificationEvent): OperationResult<TransactionCandidate> =
                OperationResult.Success(mediumCandidate.copy(timestamp = event.postedAt))
        }
        val llmAnalyzer = object : LocalLlmAnalyzer {
            /**
             * 模拟 on-device 结构化结果被路由器接纳。
             *
             * 参数说明：
             * - event：原始银行通知。
             * - seedCandidate：规则层给出的中置信度候选。
             */
            override suspend fun analyze(
                event: NotificationEvent,
                seedCandidate: TransactionCandidate,
            ): OperationResult<TransactionCandidate> {
                return OperationResult.Success(
                    seedCandidate.copy(
                        confidence = ParseConfidence(0.9f),
                        confidenceReason = "on-device structured output accepted",
                        parsePath = "${seedCandidate.parsePath}+local-llm",
                        modelVersion = "onnx-test-v1",
                        llmSuggestedAutoPost = true,
                        categoryHint = "转账",
                    ),
                )
            }
        }

        val result = ModelRouterImpl(
            localExtractor = extractor,
            localLlmAnalyzer = llmAnalyzer,
            cloudAnalysisApi = mockk(relaxed = true),
            settingsRepository = buildSettingsRepository(AppSettings(localLlmConfidenceBoostEnabled = true)),
        ).route(
            NotificationEvent(
                packageName = "com.bank.app",
                title = "银行转账提醒",
                text = "尾号1234的银行卡已转出50.00元",
                postedAt = 1234L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(0.9f, candidate.confidence.score)
        assertEquals("转账", candidate.categoryHint)
        assertEquals("local-parser+local-llm", candidate.parsePath)
        assertEquals("onnx-test-v1", candidate.modelVersion)
        assertEquals(true, candidate.llmSuggestedAutoPost)
        assertTrue(candidate.confidenceReason.contains("validated by local LLM guardrail"))
    }

    @Test
    fun `local llm can enrich but still keep candidate below auto post threshold`() = runTest {
        val extractor = object : TransactionExtractor {
            /** 返回一条中置信度银行转账候选，供本地 LLM 做第二裁决。 */
            override suspend fun extract(event: NotificationEvent): OperationResult<TransactionCandidate> {
                return OperationResult.Success(
                    TransactionCandidate(
                        amount = 88.0,
                        direction = MoneyDirection.EXPENSE,
                        merchant = "银行服务",
                        channel = "通知",
                        timestamp = event.postedAt,
                        confidence = ParseConfidence(0.67f),
                        confidenceReason = "规则层中置信度",
                        rawSource = "notification",
                        parsePath = "local-parser",
                        sourceApp = NotificationSourceApp.BANK_SMS,
                    ),
                )
            }
        }
        val llmAnalyzer = object : LocalLlmAnalyzer {
            /** 明确给出“不建议自动入账”的模型结果，验证 guardrail 会压低最终分数。 */
            override suspend fun analyze(
                event: NotificationEvent,
                seedCandidate: TransactionCandidate,
            ): OperationResult<TransactionCandidate> {
                return OperationResult.Success(
                    seedCandidate.copy(
                        confidence = ParseConfidence(0.9f),
                        confidenceReason = "Local LLM 建议保守处理",
                        parsePath = "${seedCandidate.parsePath}+local-llm",
                        modelVersion = "onnx-test-v1",
                        llmSuggestedAutoPost = false,
                        categoryHint = "转账",
                    ),
                )
            }
        }

        val result = ModelRouterImpl(
            localExtractor = extractor,
            localLlmAnalyzer = llmAnalyzer,
            cloudAnalysisApi = mockk(relaxed = true),
            settingsRepository = buildSettingsRepository(AppSettings(localLlmConfidenceBoostEnabled = true)),
        ).route(
            NotificationEvent(
                packageName = "com.bank.app",
                title = "银行转账提醒",
                text = "尾号1234的银行卡已转出88.00元",
                postedAt = 2000L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        // 说明：当前实现把本地 LLM 的保守上限与自动入账阈值对齐到 0.85，确保“中置信度增强”路径可达但不会越过自动入账门槛。
        assertEquals(0.85f, candidate.confidence.score)
        assertEquals(false, candidate.llmSuggestedAutoPost)
        assertTrue(candidate.confidenceReason.contains("autoPost=rejected"))
    }

    @Test
    fun `conflicting local llm direction keeps original local candidate`() = runTest {
        val localCandidate = TransactionCandidate(
            amount = 66.0,
            direction = MoneyDirection.EXPENSE,
            merchant = "银行服务",
            channel = "通知",
            timestamp = 3000L,
            confidence = ParseConfidence(0.66f),
            confidenceReason = "规则层中置信度",
            rawSource = "notification",
            parsePath = "local-parser",
            sourceApp = NotificationSourceApp.BANK_SMS,
        )
        val extractor = object : TransactionExtractor {
            /** 返回固定本地候选，便于验证冲突方向时会回落到原候选。 */
            override suspend fun extract(event: NotificationEvent): OperationResult<TransactionCandidate> {
                return OperationResult.Success(localCandidate.copy(timestamp = event.postedAt))
            }
        }
        val llmAnalyzer = object : LocalLlmAnalyzer {
            /** 故意输出与规则层冲突的方向，验证 guardrail 会拒绝这次提升。 */
            override suspend fun analyze(
                event: NotificationEvent,
                seedCandidate: TransactionCandidate,
            ): OperationResult<TransactionCandidate> {
                return OperationResult.Success(
                    seedCandidate.copy(
                        direction = MoneyDirection.INCOME,
                        confidence = ParseConfidence(0.92f),
                        confidenceReason = "Local LLM 与规则层冲突",
                        parsePath = "${seedCandidate.parsePath}+local-llm",
                        modelVersion = "onnx-conflict-v1",
                        llmSuggestedAutoPost = true,
                        categoryHint = "转账",
                    ),
                )
            }
        }

        val result = ModelRouterImpl(
            localExtractor = extractor,
            localLlmAnalyzer = llmAnalyzer,
            cloudAnalysisApi = mockk(relaxed = true),
            settingsRepository = buildSettingsRepository(AppSettings(localLlmConfidenceBoostEnabled = true)),
        ).route(
            NotificationEvent(
                packageName = "com.bank.app",
                title = "银行转账提醒",
                text = "尾号1234的银行卡已转出66.00元",
                postedAt = 3000L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(MoneyDirection.EXPENSE, candidate.direction)
        assertEquals("local-parser", candidate.parsePath)
        assertEquals("local-mock-v1", candidate.modelVersion)
    }
}

/**
 * 构造只读设置仓库替身。
 *
 * 参数说明：
 * - settings：本次测试期望暴露给路由器的设置快照。
 */
private fun buildSettingsRepository(settings: AppSettings): SettingsRepository {
    return object : SettingsRepository {
        override val settings: Flow<AppSettings> = flowOf(settings)
        override suspend fun setOnboardingCompleted(completed: Boolean) = Unit
        override suspend fun setAutoAccountingEnabled(enabled: Boolean) = Unit
        override suspend fun setCloudAnalysisEnabled(enabled: Boolean) = Unit
        override suspend fun setLocalLlmConfidenceBoostEnabled(enabled: Boolean) = Unit
        override suspend fun setSelectedLocalModelId(modelId: String) = Unit
        override suspend fun setCaptureEnabled(enabled: Boolean) = Unit
        override suspend fun setOcrEnabled(enabled: Boolean) = Unit
        override suspend fun setNotificationRetentionDays(days: Int) = Unit
        override suspend fun setScreenshotRetentionDays(days: Int) = Unit
        override suspend fun setSourceEnabled(source: NotificationSourceApp, enabled: Boolean) = Unit
    }
}

/** 构造一个始终拒绝执行本地 LLM 增强的替身，供不需要该链路的测试复用。 */
private fun noopLocalLlmAnalyzer(): LocalLlmAnalyzer {
    return object : LocalLlmAnalyzer {
        override suspend fun analyze(
            event: NotificationEvent,
            seedCandidate: TransactionCandidate,
        ): OperationResult<TransactionCandidate> = OperationResult.RetryableFailure("local llm disabled")
    }
}
