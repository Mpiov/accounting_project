package com.autobook.cn.core.data

import com.autobook.cn.core.data.impl.MockImageImportAnalyzer
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.ImageImportAnalysisPath
import com.autobook.cn.core.model.LocalModelInstallStatus
import com.autobook.cn.core.model.LocalModelSnapshot
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.network.GeminiImageImportApi
import com.autobook.cn.core.network.GeminiImageImportRequest
import com.autobook.cn.core.network.GeminiImageImportResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 验证图片导入 mock 分析器会按“云端优先 -> 本地模型 -> 手动确认草稿”的顺序返回建议。 */
class MockImageImportAnalyzerTest {
    @Test
    fun `uses gemini 3_1 pro preview mock when cloud analysis is enabled`() = runTest {
        val analyzer = MockImageImportAnalyzer(
            settingsRepository = fakeSettingsRepository(AppSettings(cloudAnalysisEnabled = true)),
            localModelRepository = fakeLocalModelRepository(LocalModelSnapshot(installStatus = LocalModelInstallStatus.NOT_DOWNLOADED)),
            geminiImageImportApi = fakeGeminiApi(),
            imageImportLocalOcrEngine = fakeLocalOcrEngine(),
            imageImportLocalReasoner = fakeLocalReasoner(),
        )

        val result = analyzer.analyze("mock://cloud")

        assertTrue(result is OperationResult.Success)
        val suggestion = (result as OperationResult.Success).data
        assertEquals(ImageImportAnalysisPath.GEMINI_3_1_PRO_PREVIEW_MOCK, suggestion.tracePath)
        assertEquals("Gemini 图片建议商户", suggestion.merchant)
        assertEquals(32.5, suggestion.amount)
        assertTrue(suggestion.requiresManualConfirmation)
    }

    @Test
    fun `uses local ocr plus llm mock when cloud is disabled and model is ready`() = runTest {
        val analyzer = MockImageImportAnalyzer(
            settingsRepository = fakeSettingsRepository(AppSettings(cloudAnalysisEnabled = false)),
            localModelRepository = fakeLocalModelRepository(LocalModelSnapshot(installStatus = LocalModelInstallStatus.READY)),
            geminiImageImportApi = fakeGeminiApi(),
            imageImportLocalOcrEngine = fakeLocalOcrEngine(),
            imageImportLocalReasoner = fakeLocalReasoner(),
        )

        val result = analyzer.analyze("mock://local")

        assertTrue(result is OperationResult.Success)
        val suggestion = (result as OperationResult.Success).data
        assertEquals(ImageImportAnalysisPath.OCR_PLUS_LOCAL_LLM_MOCK, suggestion.tracePath)
        assertEquals("本地图片识别示例商户", suggestion.merchant)
        assertEquals(18.0, suggestion.amount)
        assertTrue(suggestion.requiresManualConfirmation)
    }

    @Test
    fun `falls back to manual confirmation draft when local model is unavailable`() = runTest {
        val analyzer = MockImageImportAnalyzer(
            settingsRepository = fakeSettingsRepository(AppSettings(cloudAnalysisEnabled = false)),
            localModelRepository = fakeLocalModelRepository(LocalModelSnapshot(installStatus = LocalModelInstallStatus.NOT_DOWNLOADED)),
            geminiImageImportApi = fakeGeminiApi(),
            imageImportLocalOcrEngine = fakeLocalOcrEngine(),
            imageImportLocalReasoner = fakeLocalReasoner(),
        )

        val result = analyzer.analyze("mock://manual")

        assertTrue(result is OperationResult.Success)
        val suggestion = (result as OperationResult.Success).data
        assertEquals(ImageImportAnalysisPath.MANUAL_CONFIRMATION_REQUIRED, suggestion.tracePath)
        assertNull(suggestion.amount)
        assertTrue(suggestion.message.contains("本地模型未下载"))
        assertTrue(suggestion.requiresManualConfirmation)
    }
}

/** 构造固定设置快照的只读仓储，供图片导入 mock 测试复用。 */
private fun fakeSettingsRepository(settings: AppSettings): SettingsRepository {
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
        override suspend fun setSourceEnabled(source: com.autobook.cn.core.model.NotificationSourceApp, enabled: Boolean) = Unit
    }
}

/** 构造固定模型状态快照的只读仓储，供图片导入 mock 测试复用。 */
private fun fakeLocalModelRepository(snapshot: LocalModelSnapshot): LocalModelRepository {
    return object : LocalModelRepository {
        override val snapshot: Flow<LocalModelSnapshot> = flowOf(snapshot)
        override suspend fun refresh() = Unit
        override suspend fun selectModel(modelId: String) = Unit
        override suspend fun downloadSelectedModel() = Unit
    }
}

/** 构造固定 Gemini 图片分析响应的 fake 网络接口。 */
private fun fakeGeminiApi(): GeminiImageImportApi {
    return object : GeminiImageImportApi {
        override suspend fun analyze(request: GeminiImageImportRequest): GeminiImageImportResponse {
            return GeminiImageImportResponse(
                amount = 32.5,
                direction = "EXPENSE",
                merchant = "Gemini 图片建议商户",
                category = "餐饮",
                note = "来自 gemini-3.1-pro-preview mock，建议人工确认后保存",
                orderId = "IMG-GEMINI31PRO-MOCK-001",
                modelName = "gemini-3.1-pro-preview",
                message = "已按 gemini-3.1-pro-preview 生成 mock 建议，请核对金额、商户和备注后确认保存。",
                requiresManualConfirmation = true,
            )
        }
    }
}

/** 构造固定 OCR 文本输出的 fake 本地 OCR 引擎。 */
private fun fakeLocalOcrEngine(): ImageImportLocalOcrEngine {
    return object : ImageImportLocalOcrEngine {
        override suspend fun extractText(imageRef: String): OperationResult<String> {
            return OperationResult.Success("支付成功 商户 小象超市 金额 18.00 元")
        }
    }
}

/** 构造固定结构化建议输出的 fake 本地轻量模型。 */
private fun fakeLocalReasoner(): ImageImportLocalReasoner {
    return object : ImageImportLocalReasoner {
        override suspend fun analyze(
            imageRef: String,
            ocrText: String,
        ): OperationResult<com.autobook.cn.core.model.ImageImportSuggestion> {
            return OperationResult.Success(
                com.autobook.cn.core.model.ImageImportSuggestion(
                    amount = 18.0,
                    direction = com.autobook.cn.core.model.MoneyDirection.EXPENSE,
                    merchant = "本地图片识别示例商户",
                    category = "购物",
                    note = "来自 OCR + 本地轻量模型 mock，建议人工确认后保存",
                    orderId = "IMG-LOCAL-MOCK-001",
                    tracePath = ImageImportAnalysisPath.OCR_PLUS_LOCAL_LLM_MOCK,
                    message = "已按本地 OCR + 轻量模型 mock 生成建议，请核对后保存。",
                    requiresManualConfirmation = true,
                ),
            )
        }
    }
}
