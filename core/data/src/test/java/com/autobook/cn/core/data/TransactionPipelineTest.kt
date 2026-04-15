package com.autobook.cn.core.data

import com.autobook.cn.core.data.pipeline.TransactionProcessingPipeline
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.PendingEntry
import com.autobook.cn.core.model.ProcessDestination
import com.autobook.cn.core.model.TransactionCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** 验证处理流水线会根据最终置信度把候选分流到过滤、待确认或正式账本。 */
class TransactionPipelineTest {
    @Test
    fun `filters low confidence candidates instead of sending them to pending`() = runTest {
        val modelRouter = object : ModelRouter {
            /** 返回低置信度候选，验证其会被直接过滤，不再进入待确认队列。 */
            override suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate> =
                OperationResult.Success(
                    TransactionCandidate(
                        amount = 20.0,
                        direction = MoneyDirection.EXPENSE,
                        merchant = "Mock Merchant",
                        channel = "Notification",
                        timestamp = event.postedAt,
                        confidence = ParseConfidence(0.4f),
                        confidenceReason = "Low confidence candidate from local parser",
                        screenshotRef = "/tmp/pending.png",
                        rawSource = "notification",
                        parsePath = "local-parser",
                        sourceApp = NotificationSourceApp.ALIPAY,
                    ),
                )
        }
        var pendingCount = 0
        val ledgerRepository = object : LedgerRepository {
            override fun observeEntries(): Flow<List<LedgerEntry>> = emptyFlow()
            override suspend fun add(entry: LedgerEntry): Long = 1L
            override suspend fun update(entry: LedgerEntry) = Unit
            override suspend fun delete(id: Long) = Unit
            override suspend fun getById(id: Long): LedgerEntry? = null
            override suspend fun findPotentialDuplicate(candidate: TransactionCandidate): LedgerEntry? = null
            override suspend fun findPotentialImageImportDuplicate(
                orderId: String?,
                amount: Double,
                direction: MoneyDirection,
                merchant: String,
            ): LedgerEntry? = null
            override suspend fun exportCsv(): String = ""
        }
        val pendingRepository = object : PendingRepository {
            override fun observeEntries(): Flow<List<PendingEntry>> = emptyFlow()
            override suspend fun add(entry: PendingEntry): Long {
                pendingCount += 1
                return pendingCount.toLong()
            }

            override suspend fun update(entry: PendingEntry) = Unit
            override suspend fun getById(id: Long): PendingEntry? = null
            override suspend fun confirm(id: Long): PendingEntry? = null
            override suspend fun ignore(id: Long) = Unit
            override suspend fun markFalsePositive(id: Long) = Unit
        }
        val settingsRepository = buildSettingsRepository(AppSettings(autoAccountingEnabled = true))

        val outcome = TransactionProcessingPipeline(
            modelRouter = modelRouter,
            ledgerRepository = ledgerRepository,
            pendingRepository = pendingRepository,
            settingsRepository = settingsRepository,
        ).process(
            notificationId = 10L,
            event = NotificationEvent(
                packageName = "com.eg.android.AlipayGphone",
                title = "支付宝",
                text = "付款 20 元",
                postedAt = 1L,
            ),
        )

        val data = (outcome as OperationResult.Success).data
        assertEquals(ProcessDestination.FILTERED, data.destination)
        assertEquals("/tmp/pending.png", data.screenshotRef)
        assertEquals("低置信度候选已过滤：Low confidence candidate from local parser", data.message)
        assertEquals(0, pendingCount)
    }

    @Test
    fun `llm promoted medium candidate auto ledger`() = runTest {
        val modelRouter = object : ModelRouter {
            /**
             * 模拟一条已经被本地 LLM 提升过的银行转账候选，验证流水线会直接自动入账。
             *
             * 参数说明：
             * - event：当前待处理通知事件，仅用于携带时间戳。
             */
            override suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate> =
                OperationResult.Success(
                    TransactionCandidate(
                        amount = 100.0,
                        direction = MoneyDirection.EXPENSE,
                        merchant = "LLM 升级商户",
                        channel = "Bank App",
                        timestamp = event.postedAt,
                        confidence = ParseConfidence(0.9f),
                        confidenceReason = "Local LLM 语义复核通过",
                        rawSource = "notification",
                        parsePath = "local-parser+local-llm",
                        modelVersion = "onnx-stub-bank-v1",
                        llmSuggestedAutoPost = true,
                        sourceApp = NotificationSourceApp.BANK_SMS,
                    ),
                )
        }
        var ledgerAddCount = 0
        val ledgerRepository = object : LedgerRepository {
            override fun observeEntries(): Flow<List<LedgerEntry>> = emptyFlow()

            override suspend fun add(entry: LedgerEntry): Long {
                ledgerAddCount += 1
                return 1L
            }

            override suspend fun update(entry: LedgerEntry) = Unit
            override suspend fun delete(id: Long) = Unit
            override suspend fun getById(id: Long): LedgerEntry? = null
            override suspend fun findPotentialDuplicate(candidate: TransactionCandidate): LedgerEntry? = null
            override suspend fun findPotentialImageImportDuplicate(
                orderId: String?,
                amount: Double,
                direction: MoneyDirection,
                merchant: String,
            ): LedgerEntry? = null
            override suspend fun exportCsv(): String = ""
        }
        val pendingRepository = object : PendingRepository {
            override fun observeEntries(): Flow<List<PendingEntry>> = emptyFlow()
            override suspend fun add(entry: PendingEntry): Long = 0L
            override suspend fun update(entry: PendingEntry) = Unit
            override suspend fun getById(id: Long): PendingEntry? = null
            override suspend fun confirm(id: Long): PendingEntry? = null
            override suspend fun ignore(id: Long) = Unit
            override suspend fun markFalsePositive(id: Long) = Unit
        }
        val settingsRepository = buildSettingsRepository(AppSettings(autoAccountingEnabled = true))

        val outcome = TransactionProcessingPipeline(
            modelRouter = modelRouter,
            ledgerRepository = ledgerRepository,
            pendingRepository = pendingRepository,
            settingsRepository = settingsRepository,
        ).process(
            notificationId = 42L,
            event = NotificationEvent(
                packageName = "com.bank.app",
                title = "LLM 复核提醒",
                text = "已为您转出 100 元",
                postedAt = 2L,
            ),
        )

        val data = (outcome as OperationResult.Success).data
        assertEquals(ProcessDestination.LEDGER, data.destination)
        assertEquals(1, ledgerAddCount)
        assertEquals("Auto-posted with confidence 0.9", data.message)
    }

    /**
     * 验证即使本地 LLM 已经补全候选，只要置信度仍低于自动入账阈值，流水线就会把它路由到待确认并回填原因。
     */
    @Test
    fun `medium confidence candidate stays pending`() = runTest {
        val modelRouter = object : ModelRouter {
            override suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate> =
                OperationResult.Success(
                    TransactionCandidate(
                        amount = 55.0,
                        direction = MoneyDirection.EXPENSE,
                        merchant = "LLM Pending Bank",
                        channel = "Bank App",
                        timestamp = event.postedAt,
                        confidence = ParseConfidence(0.84f),
                        confidenceReason = "Local LLM guardrail review",
                        rawSource = "notification",
                        parsePath = "local-parser+local-llm",
                        modelVersion = "onnx-stub-bank-v1",
                        llmSuggestedAutoPost = false,
                        sourceApp = NotificationSourceApp.BANK_SMS,
                    ),
                )
        }
        var pendingCount = 0
        val ledgerRepository = object : LedgerRepository {
            override fun observeEntries(): Flow<List<LedgerEntry>> = emptyFlow()
            override suspend fun add(entry: LedgerEntry): Long = 0L
            override suspend fun update(entry: LedgerEntry) = Unit
            override suspend fun delete(id: Long) = Unit
            override suspend fun getById(id: Long): LedgerEntry? = null
            override suspend fun findPotentialDuplicate(candidate: TransactionCandidate): LedgerEntry? = null
            override suspend fun findPotentialImageImportDuplicate(
                orderId: String?,
                amount: Double,
                direction: MoneyDirection,
                merchant: String,
            ): LedgerEntry? = null
            override suspend fun exportCsv(): String = ""
        }
        val pendingRepository = object : PendingRepository {
            override fun observeEntries(): Flow<List<PendingEntry>> = emptyFlow()
            override suspend fun add(entry: PendingEntry): Long {
                pendingCount += 1
                return pendingCount.toLong()
            }

            override suspend fun update(entry: PendingEntry) = Unit
            override suspend fun getById(id: Long): PendingEntry? = null
            override suspend fun confirm(id: Long): PendingEntry? = null
            override suspend fun ignore(id: Long) = Unit
            override suspend fun markFalsePositive(id: Long) = Unit
        }
        val settingsRepository = buildSettingsRepository(AppSettings(autoAccountingEnabled = true))

        val outcome = TransactionProcessingPipeline(
            modelRouter = modelRouter,
            ledgerRepository = ledgerRepository,
            pendingRepository = pendingRepository,
            settingsRepository = settingsRepository,
        ).process(
            notificationId = 100L,
            event = NotificationEvent(
                packageName = "com.bank.app",
                title = "LLM Guardrail Notice",
                text = "Transfer 55.00",
                postedAt = 5L,
            ),
        )

        val data = (outcome as OperationResult.Success).data
        assertEquals(ProcessDestination.PENDING, data.destination)
        assertEquals("置信度不足：Local LLM guardrail review", data.message)
        assertEquals(1, pendingCount)
    }
}

/**
 * 构造只读设置仓库替身。
 *
 * 参数说明：
 * - settings：本次测试期望暴露给处理流水线的设置快照。
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
