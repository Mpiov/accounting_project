package com.autobook.cn.core.data

import com.autobook.cn.core.data.impl.LlamaCppOnDeviceLlmEngine
import com.autobook.cn.core.llm.LocalTextGenerationRequest
import com.autobook.cn.core.llm.LocalTextGenerationResult
import com.autobook.cn.core.llm.LocalTextGenerationRuntime
import com.autobook.cn.core.model.DEFAULT_LOCAL_MODEL_ID
import com.autobook.cn.core.model.LocalModelInstallStatus
import com.autobook.cn.core.model.LocalModelSnapshot
import com.autobook.cn.core.model.LocalModelSpec
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 验证基于 llama.cpp 的 on-device LLM 引擎能够正确处理结构化输出、错误输出与模型缺失场景。 */
class LlamaCppOnDeviceLlmEngineTest {
    @Test
    fun `parses structured json response from local runtime`() = runTest {
        val engine = LlamaCppOnDeviceLlmEngine(
            runtime = FakeLocalTextGenerationRuntime(
                LocalTextGenerationResult(
                    text = """
                        {
                          "direction": "EXPENSE",
                          "categoryHint": "转账",
                          "merchant": "张三",
                          "confidenceScore": 0.89,
                          "explanation": "识别到银行转出和收款人",
                          "shouldAutoPost": true
                        }
                    """.trimIndent(),
                    backendLabel = "llama.cpp",
                ),
            ),
            localModelRepository = FakeLocalModelRepository(installStatus = LocalModelInstallStatus.READY),
        )

        val result = engine.analyze(sampleRequest())

        assertTrue(result is OperationResult.Success)
        val response = (result as OperationResult.Success).data
        assertEquals(MoneyDirection.EXPENSE, response.direction)
        assertEquals("转账", response.categoryHint)
        assertEquals("张三", response.merchant)
        assertEquals(0.89f, response.confidenceScore)
        assertEquals(true, response.shouldAutoPost)
        assertEquals("llama.cpp:$DEFAULT_LOCAL_MODEL_ID", response.modelVersion)
    }

    @Test
    fun `accepts fenced json and chinese direction synonyms`() = runTest {
        val engine = LlamaCppOnDeviceLlmEngine(
            runtime = FakeLocalTextGenerationRuntime(
                LocalTextGenerationResult(
                    text = """
                        ```json
                        {
                          "direction": "转入",
                          "category": "转账",
                          "counterparty": "李四",
                          "confidence": 0.78,
                          "explanation": "检测到到账与对手方",
                          "autoPost": false
                        }
                        ```
                    """.trimIndent(),
                    backendLabel = "llama.cpp",
                ),
            ),
            localModelRepository = FakeLocalModelRepository(installStatus = LocalModelInstallStatus.READY),
        )

        val result = engine.analyze(sampleRequest())

        assertTrue(result is OperationResult.Success)
        val response = (result as OperationResult.Success).data
        assertEquals(MoneyDirection.INCOME, response.direction)
        assertEquals("转账", response.categoryHint)
        assertEquals("李四", response.merchant)
        assertEquals(0.78f, response.confidenceScore)
        assertEquals(false, response.shouldAutoPost)
    }

    @Test
    fun `returns retryable failure when model output is not valid json`() = runTest {
        val engine = LlamaCppOnDeviceLlmEngine(
            runtime = FakeLocalTextGenerationRuntime(
                LocalTextGenerationResult(
                    text = "这看起来像一条转账通知，但我没有按要求输出 JSON",
                    backendLabel = "llama.cpp",
                ),
            ),
            localModelRepository = FakeLocalModelRepository(installStatus = LocalModelInstallStatus.READY),
        )

        val result = engine.analyze(sampleRequest())

        assertTrue(result is OperationResult.RetryableFailure)
        assertTrue((result as OperationResult.RetryableFailure).message.contains("non-parseable"))
    }

    @Test
    fun `returns retryable failure when selected model is not installed`() = runTest {
        val engine = LlamaCppOnDeviceLlmEngine(
            runtime = FakeLocalTextGenerationRuntime(
                LocalTextGenerationResult(
                    text = "{}",
                    backendLabel = "llama.cpp",
                ),
            ),
            localModelRepository = FakeLocalModelRepository(installStatus = LocalModelInstallStatus.NOT_DOWNLOADED),
        )

        val result = engine.analyze(sampleRequest())

        assertTrue(result is OperationResult.RetryableFailure)
        assertTrue((result as OperationResult.RetryableFailure).message.contains("not installed"))
    }
}

/** 构造一份典型的银行转账语义增强请求，供真运行时引擎单测复用。 */
private fun sampleRequest(): LocalLlmRequest {
    return LocalLlmRequest(
        title = "银行转账提醒",
        body = "尾号1234的银行卡转出100元，收款人张三",
        sourceApp = NotificationSourceApp.BANK_SMS,
        seedAmount = 100.0,
        seedDirection = MoneyDirection.EXPENSE,
        seedMerchant = "银行通知",
        seedCategoryHint = "转账",
        seedConfidenceScore = 0.67f,
        seedConfidenceReason = "规则层识别到银行卡与转出语义",
        seedParsePath = "local-parser",
    )
}

/**
 * 假本地文本生成运行时。
 *
 * 用途说明：
 * - 让单测只关注 prompt 协议和结构化结果映射，不依赖真实 JNI 与 GGUF 文件。
 *
 * 参数说明：
 * - result：预设的生成结果。
 */
private class FakeLocalTextGenerationRuntime(
    private val result: LocalTextGenerationResult,
) : LocalTextGenerationRuntime {
    /** 记录最近一次收到的请求，便于后续扩展断言 prompt 内容。 */
    var lastRequest: LocalTextGenerationRequest? = null

    override suspend fun generate(request: LocalTextGenerationRequest): LocalTextGenerationResult {
        lastRequest = request
        return result
    }
}

/**
 * 假本地模型仓储。
 *
 * 用途说明：
 * - 提供最小可用的模型状态流，验证运行时是否会在模型不可用时正确拒绝执行。
 *
 * 参数说明：
 * - installStatus：当前测试场景下的模型安装状态。
 */
private class FakeLocalModelRepository(
    installStatus: LocalModelInstallStatus,
) : LocalModelRepository {
    override val snapshot: Flow<LocalModelSnapshot> = MutableStateFlow(
        LocalModelSnapshot(
            availableModels = listOf(
                LocalModelSpec(
                    id = DEFAULT_LOCAL_MODEL_ID,
                    displayName = "Qwen3 1.7B Q8_0",
                    family = "Qwen3",
                    quantization = "Q8_0",
                    runtimeHint = "llama.cpp",
                    estimatedSizeBytes = 1_710_000_000L,
                    downloadUrl = "https://example.invalid/model.gguf",
                    isRecommended = true,
                ),
            ),
            selectedModelId = DEFAULT_LOCAL_MODEL_ID,
            selectedModelPath = if (installStatus == LocalModelInstallStatus.READY) "/tmp/qwen.gguf" else null,
            installStatus = installStatus,
            statusMessage = "test",
        ),
    )

    override suspend fun refresh() = Unit

    override suspend fun selectModel(modelId: String) = Unit

    override suspend fun downloadSelectedModel() = Unit
}
