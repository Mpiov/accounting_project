package com.autobook.cn.core.data.impl

import com.autobook.cn.core.database.dao.ModelTrainingSampleDao
import com.autobook.cn.core.database.entity.ModelTrainingSampleEntity
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessStatus
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.ProcessDestination
import com.autobook.cn.core.model.TransactionCandidate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 用途说明：
 * - 验证模型训练样本仓储的 JSONL 导出内容。
 */
class ModelTrainingSampleRepositoryImplTest {
    /**
     * 用途说明：
     * - 确认导出的 JSONL 包含通知原始标题、正文和通知时间。
     */
    @Test
    fun `export jsonl keeps raw notification text for cloud training`() = runTest {
        val dao = RecordingModelTrainingSampleDao()
        val repository = ModelTrainingSampleRepositoryImpl(dao)

        repository.recordPipelineDecision(
            notificationId = 42L,
            event = NotificationEvent(
                id = 42L,
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "向 便利店 支付 18.80 元\n订单号 \"A001\"",
                postedAt = 1_746_117_600_000L,
                sourceApp = NotificationSourceApp.WECHAT,
            ),
            candidate = TransactionCandidate(
                amount = 18.8,
                direction = MoneyDirection.EXPENSE,
                merchant = "便利店",
                channel = "微信",
                timestamp = 1_746_117_600_000L,
                confidence = ParseConfidence(0.91f),
                confidenceReason = "本地模型高置信度",
                rawSource = "notification",
                parsePath = "local-parser",
                modelVersion = "local-linear-confidence-v1",
                modelFeatureSnapshotJson = """{"hasAmount":true}""",
                sourceApp = NotificationSourceApp.WECHAT,
            ),
            destination = ProcessDestination.LEDGER,
            status = NotificationProcessStatus.LEDGER_POSTED,
            pendingEntryId = null,
            ledgerEntryId = 7L,
        )

        val jsonl = repository.exportJsonl()

        assertTrue(jsonl.contains(""""rawTitle":"微信支付""""))
        assertTrue(jsonl.contains(""""rawText":"向 便利店 支付 18.80 元\n订单号 \"A001\"""""))
        assertTrue(jsonl.contains(""""rawPostedAt":1746117600000"""))
        assertTrue(jsonl.contains(""""sampleLocalId":1"""))
    }

    /**
     * 用途说明：
     * - 确认用户编辑待确认项后，导出的样本会包含纠错后的字段和 changedFields。
     */
    @Test
    fun `export jsonl includes corrected fields after pending edit confirm`() = runTest {
        val dao = RecordingModelTrainingSampleDao()
        val repository = ModelTrainingSampleRepositoryImpl(dao)

        repository.recordPipelineDecision(
            notificationId = 77L,
            event = NotificationEvent(
                id = 77L,
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "余额 88 元，向便利店支付 18.80 元",
                postedAt = 1_746_117_600_000L,
                sourceApp = NotificationSourceApp.WECHAT,
            ),
            candidate = TransactionCandidate(
                amount = 88.0,
                direction = MoneyDirection.INCOME,
                merchant = "微信支付",
                channel = "微信",
                timestamp = 1_746_117_600_000L,
                confidence = ParseConfidence(0.7f),
                confidenceReason = "误把余额当金额",
                rawSource = "notification",
                parsePath = "local-parser",
                modelVersion = "local-linear-confidence-v1",
                categoryHint = "待分类",
                sourceApp = NotificationSourceApp.WECHAT,
            ),
            destination = ProcessDestination.PENDING,
            status = NotificationProcessStatus.PENDING_REVIEW,
            pendingEntryId = 9L,
            ledgerEntryId = null,
        )

        repository.markPendingConfirmed(
            pendingEntryId = 9L,
            editedManually = true,
            correctedEntry = LedgerEntry(
                id = 3L,
                amount = 18.8,
                direction = MoneyDirection.EXPENSE,
                merchant = "便利店",
                channel = "微信",
                timestamp = 1_746_117_900_000L,
                category = "餐饮",
                orderId = "A001",
                note = "用户修正",
                traceSource = "notification",
                tracePath = "local-parser",
                traceModelVersion = "local-linear-confidence-v1",
                traceNotificationId = 77L,
                editedManually = true,
            ),
        )

        val jsonl = repository.exportJsonl()

        assertTrue(jsonl.contains(""""corrected":{"amount":18.80,"direction":"EXPENSE","merchant":"便利店""""))
        assertTrue(jsonl.contains(""""changedFields":["amount","direction","merchant","timestamp","category","orderId","note"]"""))
        assertTrue(jsonl.contains(""""parsed":{"amount":88.00,"direction":"INCOME","merchant":"微信支付""""))
    }

    /**
     * 用途说明：
     * - 确认新样本默认进入待上传批次，服务端成功后本地会标记为已同步。
     */
    @Test
    fun `upload batch only returns dirty samples until marked uploaded`() = runTest {
        val dao = RecordingModelTrainingSampleDao()
        val repository = ModelTrainingSampleRepositoryImpl(dao)

        repository.recordPipelineDecision(
            notificationId = 88L,
            event = NotificationEvent(
                id = 88L,
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "向便利店支付 18.80 元",
                postedAt = 1_746_117_600_000L,
                sourceApp = NotificationSourceApp.WECHAT,
            ),
            candidate = TransactionCandidate(
                amount = 18.8,
                direction = MoneyDirection.EXPENSE,
                merchant = "便利店",
                channel = "微信",
                timestamp = 1_746_117_600_000L,
                confidence = ParseConfidence(0.91f),
                confidenceReason = "本地模型高置信度",
                rawSource = "notification",
                parsePath = "local-parser",
                modelVersion = "local-linear-confidence-v1",
                sourceApp = NotificationSourceApp.WECHAT,
            ),
            destination = ProcessDestination.LEDGER,
            status = NotificationProcessStatus.LEDGER_POSTED,
            pendingEntryId = null,
            ledgerEntryId = 7L,
        )

        val firstBatch = repository.loadUploadBatch(limit = 200)
        assertEquals(listOf(1L), firstBatch.sampleIds)
        assertTrue(firstBatch.jsonl.contains(""""sampleLocalId":1"""))

        repository.markUploadBatchAttempted(
            sampleIds = firstBatch.sampleIds,
            attemptedAt = 1_746_117_700_000L,
        )
        repository.markUploadBatchUploaded(
            sampleIds = firstBatch.sampleIds,
            uploadedAt = 1_746_117_800_000L,
        )

        val secondBatch = repository.loadUploadBatch(limit = 200)
        assertEquals(emptyList<Long>(), secondBatch.sampleIds)
        assertEquals("", secondBatch.jsonl)
    }
}

/**
 * 用途说明：
 * - 以内存列表模拟 Room DAO，避免单测依赖真实数据库。
 */
private class RecordingModelTrainingSampleDao : ModelTrainingSampleDao {
    private val samples = mutableListOf<ModelTrainingSampleEntity>()

    /**
     * 用途说明：
     * - 保存样本并补一个自增主键，模拟 Room 插入行为。
     *
     * 参数说明：
     * - sample：待插入的训练样本。
     */
    override suspend fun insert(sample: ModelTrainingSampleEntity): Long {
        val nextId = (samples.size + 1).toLong()
        // Room 会在自增主键为 0 时生成新 id，这里用 copy 模拟该行为。
        samples += sample.copy(id = nextId)
        return nextId
    }

    /**
     * 用途说明：
     * - 按插入顺序返回全部样本，供 JSONL 导出。
     */
    override suspend fun getAllForExport(): List<ModelTrainingSampleEntity> = samples.toList()

    /**
     * 用途说明：
     * - 返回尚未标记为已同步的样本，模拟客户端增量上传选择逻辑。
     *
     * 参数说明：
     * - uploadedState：已同步状态值。
     * - limit：本次最多读取的样本数量。
     */
    override suspend fun getPendingUploadBatch(
        uploadedState: String,
        limit: Int,
    ): List<ModelTrainingSampleEntity> {
        return samples
            .filter { sample -> sample.uploadState != uploadedState }
            .sortedWith(compareBy<ModelTrainingSampleEntity> { sample -> sample.updatedAt }.thenBy { sample -> sample.id })
            .take(limit)
    }

    /**
     * 用途说明：
     * - 根据待确认主键返回样本，供仓储计算纠错字段。
     */
    override suspend fun getByPendingEntryId(pendingEntryId: Long): ModelTrainingSampleEntity? {
        return samples.firstOrNull { sample -> sample.pendingEntryId == pendingEntryId }
    }

    /**
     * 用途说明：
     * - 根据账本主键或通知主键返回样本，供仓储计算账本编辑字段。
     */
    override suspend fun getByLedgerTrace(
        ledgerEntryId: Long,
        notificationId: Long?,
    ): ModelTrainingSampleEntity? {
        return samples.firstOrNull { sample ->
            sample.ledgerEntryId == ledgerEntryId ||
                (notificationId != null && sample.notificationId == notificationId)
        }
    }

    /**
     * 用途说明：
     * - 本测试不验证账本回链，因此保持空实现。
     *
     * 参数说明：
     * - notificationId：来源通知主键。
     * - ledgerEntryId：账本记录主键。
     * - updatedAt：更新时间戳。
     */
    override suspend fun linkLedgerEntryByNotificationId(
        notificationId: Long,
        ledgerEntryId: Long,
        updatedAt: Long,
        uploadState: String,
    ) = Unit

    /**
     * 用途说明：
     * - 本测试不验证待确认反馈回写，因此保持空实现。
     *
     * 参数说明：
     * - pendingEntryId：待确认记录主键。
     * - feedbackLabel：反馈标签。
     * - feedbackSource：反馈来源。
     * - feedbackAt：反馈时间。
     * - editedManually：是否人工编辑。
     * - feedbackNote：反馈备注。
     */
    override suspend fun updateFeedbackByPendingEntryId(
        pendingEntryId: Long,
        feedbackLabel: String,
        feedbackSource: String,
        feedbackAt: Long,
        editedManually: Boolean,
        feedbackNote: String?,
        correctedAmount: Double?,
        correctedDirection: String?,
        correctedMerchant: String?,
        correctedChannel: String?,
        correctedTimestamp: Long?,
        correctedCategory: String?,
        correctedOrderId: String?,
        correctedNote: String?,
        changedFieldsJson: String?,
        uploadState: String,
    ) {
        samples.replaceAll { sample ->
            if (sample.pendingEntryId != pendingEntryId) {
                sample
            } else {
                sample.copy(
                    feedbackLabel = feedbackLabel,
                    feedbackSource = feedbackSource,
                    feedbackAt = feedbackAt,
                    updatedAt = feedbackAt,
                    editedManually = editedManually,
                    feedbackNote = feedbackNote,
                    correctedAmount = correctedAmount,
                    correctedDirection = correctedDirection,
                    correctedMerchant = correctedMerchant,
                    correctedChannel = correctedChannel,
                    correctedTimestamp = correctedTimestamp,
                    correctedCategory = correctedCategory,
                    correctedOrderId = correctedOrderId,
                    correctedNote = correctedNote,
                    changedFieldsJson = changedFieldsJson,
                    uploadState = uploadState,
                )
            }
        }
    }

    /**
     * 用途说明：
     * - 本测试不验证账本侧反馈回写，因此保持空实现。
     *
     * 参数说明：
     * - ledgerEntryId：账本记录主键。
     * - notificationId：来源通知主键。
     * - feedbackLabel：反馈标签。
     * - feedbackSource：反馈来源。
     * - feedbackAt：反馈时间。
     * - editedManually：是否人工编辑。
     * - feedbackNote：反馈备注。
     */
    override suspend fun updateFeedbackByLedgerTrace(
        ledgerEntryId: Long,
        notificationId: Long?,
        feedbackLabel: String,
        feedbackSource: String,
        feedbackAt: Long,
        editedManually: Boolean,
        feedbackNote: String?,
        correctedAmount: Double?,
        correctedDirection: String?,
        correctedMerchant: String?,
        correctedChannel: String?,
        correctedTimestamp: Long?,
        correctedCategory: String?,
        correctedOrderId: String?,
        correctedNote: String?,
        changedFieldsJson: String?,
        uploadState: String,
    ) = Unit

    /**
     * 用途说明：
     * - 在内存样本上写入最近上传尝试时间。
     *
     * 参数说明：
     * - ids：本地样本主键集合。
     * - attemptedAt：上传尝试时间。
     */
    override suspend fun markUploadAttempted(
        ids: List<Long>,
        attemptedAt: Long,
    ) {
        samples.replaceAll { sample ->
            if (sample.id in ids) {
                sample.copy(lastUploadAttemptAt = attemptedAt)
            } else {
                sample
            }
        }
    }

    /**
     * 用途说明：
     * - 在内存样本上写入已同步状态，模拟服务端确认后的 Room 回写。
     *
     * 参数说明：
     * - ids：本地样本主键集合。
     * - uploadedState：已同步状态值。
     * - uploadedAt：上传成功时间。
     */
    override suspend fun markUploaded(
        ids: List<Long>,
        uploadedState: String,
        uploadedAt: Long,
    ) {
        samples.replaceAll { sample ->
            if (sample.id in ids) {
                sample.copy(
                    uploadState = uploadedState,
                    uploadedAt = uploadedAt,
                    lastUploadAttemptAt = uploadedAt,
                )
            } else {
                sample
            }
        }
    }
}
