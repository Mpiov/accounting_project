package com.autobook.cn.core.data

import com.autobook.cn.core.data.pipeline.TransactionProcessingPipeline
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessStatus
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
                timestamp: Long?,
                amount: Double,
                direction: MoneyDirection,
            ): LedgerEntry? = null
            override suspend fun findPotentialImageImportDuplicateWithoutTimestamp(
                amount: Double,
                direction: MoneyDirection,
            ): LedgerEntry? = null
            override suspend fun exportCsv(): String = ""
        }
        val pendingRepository = object : PendingRepository {
            override fun observeEntries(): Flow<List<PendingEntry>> = emptyFlow()
            // 流水线测试只验证分流结果，数量徽标流保持空即可。
            override fun observeActiveCount(): Flow<Int> = emptyFlow()
            override suspend fun add(entry: PendingEntry): Long {
                pendingCount += 1
                return pendingCount.toLong()
            }

            override suspend fun update(entry: PendingEntry) = Unit
            override suspend fun getById(id: Long): PendingEntry? = null
            override suspend fun confirm(id: Long): PendingEntry? = null
            override suspend fun confirmEdited(
                id: Long,
                correctedEntry: LedgerEntry,
            ): PendingEntry? = null
            override suspend fun ignore(id: Long) = Unit
            override suspend fun markFalsePositive(id: Long) = Unit
            override suspend fun purgeUploadedFalsePositives(): Int = 0
        }
        val settingsRepository = buildSettingsRepository(AppSettings(autoAccountingEnabled = true))
        val trainingSampleRepository = RecordingModelTrainingSampleRepository()

        val outcome = TransactionProcessingPipeline(
            modelRouter = modelRouter,
            ledgerRepository = ledgerRepository,
            pendingRepository = pendingRepository,
            settingsRepository = settingsRepository,
            modelTrainingSampleRepository = trainingSampleRepository,
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
        assertEquals(listOf(ProcessDestination.FILTERED), trainingSampleRepository.destinations)
    }

    @Test
    fun `high confidence local candidate auto ledger`() = runTest {
        val modelRouter = object : ModelRouter {
            /**
             * 模拟一条高置信度本地规则候选，验证流水线会直接自动入账。
             *
             * 参数说明：
             * - event：当前待处理通知事件，仅用于携带时间戳。
             */
            override suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate> =
                OperationResult.Success(
                    TransactionCandidate(
                        amount = 100.0,
                        direction = MoneyDirection.EXPENSE,
                        merchant = "本地规则商户",
                        channel = "Bank App",
                        timestamp = event.postedAt,
                        confidence = ParseConfidence(0.9f),
                        confidenceReason = "Local parser validation passed",
                        rawSource = "notification",
                        parsePath = "local-parser",
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
                timestamp: Long?,
                amount: Double,
                direction: MoneyDirection,
            ): LedgerEntry? = null
            override suspend fun findPotentialImageImportDuplicateWithoutTimestamp(
                amount: Double,
                direction: MoneyDirection,
            ): LedgerEntry? = null
            override suspend fun exportCsv(): String = ""
        }
        val pendingRepository = object : PendingRepository {
            override fun observeEntries(): Flow<List<PendingEntry>> = emptyFlow()
            // 流水线测试只验证分流结果，数量徽标流保持空即可。
            override fun observeActiveCount(): Flow<Int> = emptyFlow()
            override suspend fun add(entry: PendingEntry): Long = 0L
            override suspend fun update(entry: PendingEntry) = Unit
            override suspend fun getById(id: Long): PendingEntry? = null
            override suspend fun confirm(id: Long): PendingEntry? = null
            override suspend fun confirmEdited(
                id: Long,
                correctedEntry: LedgerEntry,
            ): PendingEntry? = null
            override suspend fun ignore(id: Long) = Unit
            override suspend fun markFalsePositive(id: Long) = Unit
            override suspend fun purgeUploadedFalsePositives(): Int = 0
        }
        val settingsRepository = buildSettingsRepository(AppSettings(autoAccountingEnabled = true))
        val trainingSampleRepository = RecordingModelTrainingSampleRepository()

        val outcome = TransactionProcessingPipeline(
            modelRouter = modelRouter,
            ledgerRepository = ledgerRepository,
            pendingRepository = pendingRepository,
            settingsRepository = settingsRepository,
            modelTrainingSampleRepository = trainingSampleRepository,
        ).process(
            notificationId = 42L,
            event = NotificationEvent(
                packageName = "com.bank.app",
                title = "本地规则提醒",
                text = "已为您转出 100 元",
                postedAt = 2L,
            ),
        )

        val data = (outcome as OperationResult.Success).data
        assertEquals(ProcessDestination.LEDGER, data.destination)
        assertEquals(1, ledgerAddCount)
        assertEquals("Auto-posted with confidence 0.9", data.message)
        assertEquals(listOf(ProcessDestination.LEDGER), trainingSampleRepository.destinations)
    }

    /**
     * 验证本地规则候选只要置信度低于自动入账阈值，流水线就会把它路由到待确认并回填原因。
     */
    @Test
    fun `medium confidence candidate stays pending`() = runTest {
        val modelRouter = object : ModelRouter {
            override suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate> =
                OperationResult.Success(
                    TransactionCandidate(
                        amount = 55.0,
                        direction = MoneyDirection.EXPENSE,
                        merchant = "Local Pending Bank",
                        channel = "Bank App",
                        timestamp = event.postedAt,
                        confidence = ParseConfidence(0.84f),
                        confidenceReason = "Local parser medium confidence review",
                        rawSource = "notification",
                        parsePath = "local-parser",
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
                timestamp: Long?,
                amount: Double,
                direction: MoneyDirection,
            ): LedgerEntry? = null
            override suspend fun findPotentialImageImportDuplicateWithoutTimestamp(
                amount: Double,
                direction: MoneyDirection,
            ): LedgerEntry? = null
            override suspend fun exportCsv(): String = ""
        }
        val pendingRepository = object : PendingRepository {
            override fun observeEntries(): Flow<List<PendingEntry>> = emptyFlow()
            // 流水线测试只验证分流结果，数量徽标流保持空即可。
            override fun observeActiveCount(): Flow<Int> = emptyFlow()
            override suspend fun add(entry: PendingEntry): Long {
                pendingCount += 1
                return pendingCount.toLong()
            }

            override suspend fun update(entry: PendingEntry) = Unit
            override suspend fun getById(id: Long): PendingEntry? = null
            override suspend fun confirm(id: Long): PendingEntry? = null
            override suspend fun confirmEdited(
                id: Long,
                correctedEntry: LedgerEntry,
            ): PendingEntry? = null
            override suspend fun ignore(id: Long) = Unit
            override suspend fun markFalsePositive(id: Long) = Unit
            override suspend fun purgeUploadedFalsePositives(): Int = 0
        }
        val settingsRepository = buildSettingsRepository(AppSettings(autoAccountingEnabled = true))
        val trainingSampleRepository = RecordingModelTrainingSampleRepository()

        val outcome = TransactionProcessingPipeline(
            modelRouter = modelRouter,
            ledgerRepository = ledgerRepository,
            pendingRepository = pendingRepository,
            settingsRepository = settingsRepository,
            modelTrainingSampleRepository = trainingSampleRepository,
        ).process(
            notificationId = 100L,
            event = NotificationEvent(
                packageName = "com.bank.app",
                title = "Local Parser Notice",
                text = "Transfer 55.00",
                postedAt = 5L,
            ),
        )

        val data = (outcome as OperationResult.Success).data
        assertEquals(ProcessDestination.PENDING, data.destination)
        assertEquals("置信度不足：Local parser medium confidence review", data.message)
        assertEquals(1, pendingCount)
        assertEquals(listOf(ProcessDestination.PENDING), trainingSampleRepository.destinations)
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
        override suspend fun setCloudLearningEnabled(enabled: Boolean) = Unit
        override suspend fun setCaptureEnabled(enabled: Boolean) = Unit
        override suspend fun setNotificationRetentionDays(days: Int) = Unit
        override suspend fun setScreenshotRetentionDays(days: Int) = Unit
        override suspend fun setSourceEnabled(source: NotificationSourceApp, enabled: Boolean) = Unit
    }
}

/**
 * 记录流水线写入的训练样本落点。
 *
 * 用途说明：
 * - 让流水线单测不用依赖 Room，也能验证新增训练样本记录不会漏掉分流结果。
 */
private class RecordingModelTrainingSampleRepository : ModelTrainingSampleRepository {
    val destinations = mutableListOf<ProcessDestination>()

    override suspend fun recordPipelineDecision(
        notificationId: Long,
        event: NotificationEvent,
        candidate: TransactionCandidate,
        destination: ProcessDestination,
        status: NotificationProcessStatus,
        pendingEntryId: Long?,
        ledgerEntryId: Long?,
    ) {
        destinations += destination
    }

    override suspend fun linkLedgerEntry(
        notificationId: Long?,
        ledgerEntryId: Long,
    ) = Unit

    override suspend fun markPendingConfirmed(
        pendingEntryId: Long,
        editedManually: Boolean,
        correctedEntry: LedgerEntry?,
    ) = Unit

    override suspend fun markPendingIgnored(pendingEntryId: Long) = Unit

    override suspend fun markPendingFalsePositive(pendingEntryId: Long) = Unit

    override suspend fun markLedgerEdited(entry: LedgerEntry) = Unit

    override suspend fun markLedgerDeleted(entry: LedgerEntry) = Unit

    override suspend fun loadUploadBatch(limit: Int): ModelTrainingSampleUploadBatch =
        ModelTrainingSampleUploadBatch(sampleIds = emptyList(), jsonl = "")

    override suspend fun markUploadBatchAttempted(
        sampleIds: List<Long>,
        attemptedAt: Long,
    ) = Unit

    override suspend fun markUploadBatchUploaded(
        sampleIds: List<Long>,
        uploadedAt: Long,
    ) = Unit

    override suspend fun exportJsonl(): String = ""
}
