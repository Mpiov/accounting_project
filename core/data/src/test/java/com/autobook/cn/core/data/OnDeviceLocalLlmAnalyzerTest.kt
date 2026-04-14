package com.autobook.cn.core.data

import com.autobook.cn.core.data.impl.HeuristicLocalLlmAnalyzer
import com.autobook.cn.core.data.impl.OnDeviceLocalLlmAnalyzer
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.TransactionCandidate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 验证 on-device 本地 LLM 适配层的结构化输出、非法输出回退和引擎失败回退。 */
class OnDeviceLocalLlmAnalyzerTest {
    @Test
    fun `uses structured on-device response when valid`() = runTest {
        val analyzer = OnDeviceLocalLlmAnalyzer(
            onDeviceLlmEngine = object : OnDeviceLlmEngine {
                /** 返回一份合法的结构化模型输出，验证适配层会直接采纳。 */
                override suspend fun analyze(request: LocalLlmRequest): OperationResult<LocalLlmResponse> {
                    return OperationResult.Success(
                        LocalLlmResponse(
                            direction = MoneyDirection.EXPENSE,
                            categoryHint = "转账",
                            merchant = "张三",
                            confidenceScore = 0.91f,
                            explanation = "on-device structured output accepted",
                            shouldAutoPost = true,
                            modelVersion = "onnx-test-v1",
                        ),
                    )
                }
            },
            heuristicFallbackAnalyzer = HeuristicLocalLlmAnalyzer(),
        )

        val result = analyzer.analyze(
            event = bankTransferEvent(),
            seedCandidate = mediumBankSeed(),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals("张三", candidate.merchant)
        assertEquals("转账", candidate.categoryHint)
        assertEquals(0.91f, candidate.confidence.score)
        assertEquals("onnx-test-v1", candidate.modelVersion)
        assertEquals(true, candidate.llmSuggestedAutoPost)
        assertEquals("local-parser+local-llm", candidate.parsePath)
    }

    @Test
    fun `falls back to heuristic when on-device response is invalid`() = runTest {
        val analyzer = OnDeviceLocalLlmAnalyzer(
            onDeviceLlmEngine = object : OnDeviceLlmEngine {
                /** 返回非法结构化结果，验证适配层会切换到启发式 fallback。 */
                override suspend fun analyze(request: LocalLlmRequest): OperationResult<LocalLlmResponse> {
                    return OperationResult.Success(
                        LocalLlmResponse(
                            direction = MoneyDirection.UNKNOWN,
                            categoryHint = "转账",
                            merchant = "李四",
                            confidenceScore = 1.2f,
                            explanation = "",
                            shouldAutoPost = true,
                            modelVersion = "",
                        ),
                    )
                }
            },
            heuristicFallbackAnalyzer = HeuristicLocalLlmAnalyzer(),
        )

        val result = analyzer.analyze(
            event = bankTransferEvent(),
            seedCandidate = mediumBankSeed(),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals("heuristic-local-llm-fallback-v1", candidate.modelVersion)
        assertTrue(candidate.confidenceReason.contains("fallback-from-on-device"))
        assertEquals("local-parser+local-llm", candidate.parsePath)
    }

    @Test
    fun `falls back to heuristic when on-device engine fails`() = runTest {
        val analyzer = OnDeviceLocalLlmAnalyzer(
            onDeviceLlmEngine = object : OnDeviceLlmEngine {
                /** 模拟端侧模型暂时不可用，验证适配层会自动走 baseline。 */
                override suspend fun analyze(request: LocalLlmRequest): OperationResult<LocalLlmResponse> {
                    return OperationResult.RetryableFailure("engine timeout")
                }
            },
            heuristicFallbackAnalyzer = HeuristicLocalLlmAnalyzer(),
        )

        val result = analyzer.analyze(
            event = bankTransferEvent(),
            seedCandidate = mediumBankSeed(),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals("heuristic-local-llm-fallback-v1", candidate.modelVersion)
        assertTrue(candidate.confidenceReason.contains("engine timeout"))
        assertEquals(true, candidate.llmSuggestedAutoPost)
    }
}

/** 构造一条典型的银行转账通知事件，供适配层测试复用。 */
private fun bankTransferEvent(): NotificationEvent {
    return NotificationEvent(
        packageName = "com.bank.app",
        title = "银行转账提醒",
        text = "尾号1234的银行卡已转出100.00元，收款人张三",
        postedAt = 1234L,
        sourceApp = NotificationSourceApp.BANK_SMS,
    )
}

/** 构造一条中置信度银行转账种子候选，供适配层测试复用。 */
private fun mediumBankSeed(): TransactionCandidate {
    return TransactionCandidate(
        amount = 100.0,
        direction = MoneyDirection.EXPENSE,
        merchant = "银行通知",
        channel = "通知",
        timestamp = 1234L,
        confidence = ParseConfidence(0.68f),
        confidenceReason = "规则层中置信度候选",
        rawSource = "notification",
        parsePath = "local-parser",
        sourceApp = NotificationSourceApp.BANK_SMS,
    )
}
