package com.autobook.cn.core.data

import com.autobook.cn.core.data.impl.ImageImportOnDeviceLocalReasoner
import com.autobook.cn.core.llm.LocalTextGenerationRequest
import com.autobook.cn.core.llm.LocalTextGenerationResult
import com.autobook.cn.core.llm.LocalTextGenerationRuntime
import com.autobook.cn.core.model.DEFAULT_LOCAL_MODEL_ID
import com.autobook.cn.core.model.ImageImportAnalysisPath
import com.autobook.cn.core.model.LocalModelInstallStatus
import com.autobook.cn.core.model.LocalModelSnapshot
import com.autobook.cn.core.model.LocalModelSpec
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 验证图片导入本地 reasoner 能够消费端侧模型结构化输出，并在失败时回退到 OCR 草稿。 */
class ImageImportOnDeviceLocalReasonerTest {
    @Test
    fun `builds image import suggestion from on-device llm json`() = runTest {
        val reasoner = ImageImportOnDeviceLocalReasoner(
            runtime = FakeImageImportLocalTextGenerationRuntime(
                LocalTextGenerationResult(
                    text = """
                        {
                          "amount": 18.0,
                          "direction": "EXPENSE",
                          "merchant": "小象超市",
                          "category": "购物",
                          "note": "识别到支付成功、商户和金额",
                          "orderId": "IMG-REAL-001",
                          "confidenceScore": 0.91,
                          "requiresManualConfirmation": true
                        }
                    """.trimIndent(),
                    backendLabel = "llama.cpp",
                ),
            ),
            localModelRepository = FakeImageImportLocalModelRepository(LocalModelInstallStatus.READY),
        )

        val result = reasoner.analyze(
            imageRef = "content://demo/image-1",
            ocrText = "支付成功 商户 小象超市 金额 18.00 元 订单号 IMG-REAL-001",
        )

        assertTrue(result is OperationResult.Success)
        val suggestion = (result as OperationResult.Success).data
        assertEquals(ImageImportAnalysisPath.OCR_PLUS_LOCAL_LLM, suggestion.tracePath)
        assertEquals(18.0, suggestion.amount)
        assertEquals(MoneyDirection.EXPENSE, suggestion.direction)
        assertEquals("小象超市", suggestion.merchant)
        assertEquals("购物", suggestion.category)
        assertEquals("IMG-REAL-001", suggestion.orderId)
        assertTrue(suggestion.note.contains("端侧 Qwen"))
        assertTrue(suggestion.requiresManualConfirmation)
    }

    @Test
    fun `falls back to heuristic ocr draft when on-device output is invalid`() = runTest {
        val reasoner = ImageImportOnDeviceLocalReasoner(
            runtime = FakeImageImportLocalTextGenerationRuntime(
                LocalTextGenerationResult(
                    text = "这是一笔支付，但我没有按要求输出 JSON",
                    backendLabel = "llama.cpp",
                ),
            ),
            localModelRepository = FakeImageImportLocalModelRepository(LocalModelInstallStatus.READY),
        )

        val result = reasoner.analyze(
            imageRef = "content://demo/image-2",
            ocrText = "支付成功 商户 小象超市 金额 18.00 元",
        )

        assertTrue(result is OperationResult.Success)
        val suggestion = (result as OperationResult.Success).data
        assertEquals(ImageImportAnalysisPath.OCR_PLUS_LOCAL_LLM, suggestion.tracePath)
        assertEquals(18.0, suggestion.amount)
        assertEquals("小象超市", suggestion.merchant)
        assertTrue(suggestion.message.contains("OCR 草稿"))
        assertTrue(suggestion.requiresManualConfirmation)
    }
}

/** 图片导入本地文本生成运行时替身，只返回预设结构化文本。 */
private class FakeImageImportLocalTextGenerationRuntime(
    private val result: LocalTextGenerationResult,
) : LocalTextGenerationRuntime {
    var lastRequest: LocalTextGenerationRequest? = null

    override suspend fun generate(request: LocalTextGenerationRequest): LocalTextGenerationResult {
        lastRequest = request
        return result
    }
}

/** 图片导入本地模型仓储替身，用于控制模型是否处于就绪状态。 */
private class FakeImageImportLocalModelRepository(
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
