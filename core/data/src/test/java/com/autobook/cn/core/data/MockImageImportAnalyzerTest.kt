package com.autobook.cn.core.data

import com.autobook.cn.core.data.impl.ImageImportCandidatePostProcessor
import com.autobook.cn.core.data.impl.MockImageImportAnalyzer
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.ImageImportAnalysisPath
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.network.CloudImageImportApi
import com.autobook.cn.core.network.CloudImageImportCandidateResponse
import com.autobook.cn.core.network.CloudImageImportRequest
import com.autobook.cn.core.network.CloudImageImportResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 用途说明：
 * - 验证图片导入分析器仅保留“云端分析 + 本地规则后处理”与“手动草稿”两条路径。
 */
class MockImageImportAnalyzerTest {
    @Test
    fun `uses cloud model proxy response when cloud analysis is enabled`() = runTest {
        val analyzer = MockImageImportAnalyzer(
            settingsRepository = fakeSettingsRepository(AppSettings(cloudAnalysisEnabled = true)),
            cloudImageImportApi = fakeCloudImageImportApi(),
            candidatePostProcessor = ImageImportCandidatePostProcessor(fakeLedgerRepository()),
        )

        val result = analyzer.analyze("mock://cloud")

        assertTrue(result is OperationResult.Success)
        val suggestion = (result as OperationResult.Success).data
        assertEquals(ImageImportAnalysisPath.CLOUD_LLM_PROXY, suggestion.tracePath)
        assertEquals("云端大模型图片建议商户", suggestion.merchant)
        assertEquals(32.5, suggestion.amount)
        assertEquals(1_744_598_800_000L, suggestion.timestamp)
        assertTrue(suggestion.requiresManualConfirmation)
        assertEquals(1, suggestion.candidates.size)
        assertEquals(1, suggestion.filteredOutCandidateCount)
    }

    @Test
    fun `falls back to manual confirmation draft when cloud analysis is disabled`() = runTest {
        val analyzer = MockImageImportAnalyzer(
            settingsRepository = fakeSettingsRepository(AppSettings(cloudAnalysisEnabled = false)),
            cloudImageImportApi = fakeCloudImageImportApi(),
            candidatePostProcessor = ImageImportCandidatePostProcessor(fakeLedgerRepository()),
        )

        val result = analyzer.analyze("mock://manual")

        assertTrue(result is OperationResult.Success)
        val suggestion = (result as OperationResult.Success).data
        assertEquals(ImageImportAnalysisPath.MANUAL_CONFIRMATION_REQUIRED, suggestion.tracePath)
        assertNull(suggestion.amount)
        assertTrue(suggestion.message.contains("云端分析已关闭"))
        assertTrue(suggestion.requiresManualConfirmation)
    }
}

/**
 * 用途说明：
 * - 构造固定设置快照的只读仓储。
 *
 * 参数说明：
 * - settings：测试期望暴露的设置快照。
 */
private fun fakeSettingsRepository(settings: AppSettings): SettingsRepository {
    return object : SettingsRepository {
        override val settings: Flow<AppSettings> = flowOf(settings)
        override suspend fun setOnboardingCompleted(completed: Boolean) = Unit
        override suspend fun setAutoAccountingEnabled(enabled: Boolean) = Unit
        override suspend fun setCloudAnalysisEnabled(enabled: Boolean) = Unit
        override suspend fun setLocalLlmConfidenceBoostEnabled(enabled: Boolean) = Unit
        override suspend fun setCaptureEnabled(enabled: Boolean) = Unit
        override suspend fun setNotificationRetentionDays(days: Int) = Unit
        override suspend fun setScreenshotRetentionDays(days: Int) = Unit
        override suspend fun setSourceEnabled(source: com.autobook.cn.core.model.NotificationSourceApp, enabled: Boolean) = Unit
    }
}

/**
 * 用途说明：
 * - 构造不会命中重复记录的账本仓储替身。
 */
private fun fakeLedgerRepository(): LedgerRepository {
    return object : LedgerRepository {
        override fun observeEntries(): Flow<List<com.autobook.cn.core.model.LedgerEntry>> = flowOf(emptyList())
        override suspend fun add(entry: com.autobook.cn.core.model.LedgerEntry): Long = 1L
        override suspend fun update(entry: com.autobook.cn.core.model.LedgerEntry) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun getById(id: Long): com.autobook.cn.core.model.LedgerEntry? = null
        override suspend fun findPotentialDuplicate(candidate: com.autobook.cn.core.model.TransactionCandidate): com.autobook.cn.core.model.LedgerEntry? = null
        override suspend fun findPotentialImageImportDuplicate(
            timestamp: Long?,
            amount: Double,
            direction: com.autobook.cn.core.model.MoneyDirection,
        ): com.autobook.cn.core.model.LedgerEntry? = null

        override suspend fun findPotentialImageImportDuplicateWithoutTimestamp(
            amount: Double,
            direction: com.autobook.cn.core.model.MoneyDirection,
        ): com.autobook.cn.core.model.LedgerEntry? = null

        override suspend fun exportCsv(): String = ""
    }
}

/**
 * 用途说明：
 * - 构造固定云端分析返回。
 */
private fun fakeCloudImageImportApi(): CloudImageImportApi {
    return object : CloudImageImportApi {
        override suspend fun analyze(request: CloudImageImportRequest): CloudImageImportResponse {
            return CloudImageImportResponse(
                amount = 32.5,
                timestamp = 1_744_598_800_000L,
                direction = "EXPENSE",
                merchant = "云端大模型图片建议商户",
                category = "餐饮",
                note = "来自云端大模型代理链路，建议人工确认后保存",
                orderId = "IMG-CLOUD-MODEL-PROXY-001",
                modelName = "gemini-3-flash-preview",
                message = "已按云端大模型代理链路生成建议，请核对金额、商户和备注后确认保存。",
                requiresManualConfirmation = true,
                candidates = listOf(
                    CloudImageImportCandidateResponse(
                        amount = 32.5,
                        timestamp = 1_744_598_800_000L,
                        direction = "EXPENSE",
                        merchant = "云端大模型图片建议商户",
                        category = "餐饮",
                        note = "来自云端大模型代理链路，建议人工确认后保存",
                        orderId = "IMG-CLOUD-MODEL-PROXY-001",
                        confidenceScore = 0.88f,
                        transactionState = "ACTIVE",
                        stateReason = "页面显示支付成功",
                        evidence = "支付成功 32.50 元",
                    ),
                    CloudImageImportCandidateResponse(
                        amount = 32.5,
                        timestamp = 1_744_503_800_000L,
                        direction = "EXPENSE",
                        merchant = "云端大模型旧交易记录",
                        category = "餐饮",
                        note = "已退款记录",
                        orderId = "IMG-CLOUD-MODEL-PROXY-REFUND-001",
                        confidenceScore = 0.64f,
                        transactionState = "REFUNDED",
                        stateReason = "页面展示退款成功",
                        evidence = "退款成功 32.50 元",
                    ),
                ),
            )
        }
    }
}
