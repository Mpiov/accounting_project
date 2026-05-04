package com.autobook.cn.core.data.impl

import com.autobook.cn.core.data.ModelTrainingSampleRepository
import com.autobook.cn.core.data.ModelTrainingSampleUploadBatch
import com.autobook.cn.core.database.dao.ModelTrainingSampleDao
import com.autobook.cn.core.database.entity.ModelTrainingSampleEntity
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessStatus
import com.autobook.cn.core.model.ProcessDestination
import com.autobook.cn.core.model.TransactionCandidate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用途说明：
 * - 基于 Room 保存模型训练样本，并提供 JSONL 导出。
 *
 * 参数说明：
 * - modelTrainingSampleDao：模型训练样本表 DAO。
 */
@Singleton
class ModelTrainingSampleRepositoryImpl @Inject constructor(
    private val modelTrainingSampleDao: ModelTrainingSampleDao,
) : ModelTrainingSampleRepository {
    /**
     * 用途说明：
     * - 保存一条没有交易候选的原始通知样本。
     * - 这类样本没有稳定训练标签，只保留在本地，上传批次会过滤掉它。
     *
     * 参数说明：
     * - notificationId：通知事件本地主键。
     * - event：原始通知事件。
     * - status：通知最终处理状态。
     * - reason：未进入交易候选的原因。
     */
    override suspend fun recordRawNotification(
        notificationId: Long,
        event: NotificationEvent,
        status: NotificationProcessStatus,
        reason: String,
    ) {
        val now = System.currentTimeMillis()
        modelTrainingSampleDao.insert(
            ModelTrainingSampleEntity(
                notificationId = notificationId,
                pendingEntryId = null,
                ledgerEntryId = null,
                createdAt = now,
                updatedAt = now,
                packageName = event.packageName,
                rawTitle = event.title,
                rawText = event.text,
                rawPostedAt = event.postedAt,
                sourceApp = event.sourceApp.name,
                modelVersion = RAW_NOTIFICATION_MODEL_VERSION,
                parsePath = RAW_NOTIFICATION_PARSE_PATH,
                amount = 0.0,
                direction = RAW_NOTIFICATION_DIRECTION,
                channel = event.sourceApp.name,
                candidateTimestamp = event.postedAt,
                candidateCategory = "",
                candidateNote = null,
                confidenceScore = 0f,
                confidenceLevel = RAW_NOTIFICATION_CONFIDENCE_LEVEL,
                confidenceReason = reason,
                featureSnapshotJson = "{}",
                routeDestination = ProcessDestination.FILTERED.name,
                processStatus = status.name,
                feedbackLabel = FEEDBACK_RAW_NOTIFICATION_UNREVIEWED,
                feedbackSource = "raw_notification",
                feedbackAt = null,
                editedManually = false,
                feedbackNote = reason,
                correctedAmount = null,
                correctedDirection = null,
                correctedChannel = null,
                correctedTimestamp = null,
                correctedCategory = null,
                correctedNote = null,
                changedFieldsJson = null,
                uploadState = UPLOAD_STATE_PENDING,
                uploadedAt = null,
                lastUploadAttemptAt = null,
            ),
        )
    }

    override suspend fun recordPipelineDecision(
        notificationId: Long,
        event: NotificationEvent,
        candidate: TransactionCandidate,
        destination: ProcessDestination,
        status: NotificationProcessStatus,
        pendingEntryId: Long?,
        ledgerEntryId: Long?,
    ) {
        val now = System.currentTimeMillis()
        modelTrainingSampleDao.insert(
            ModelTrainingSampleEntity(
                notificationId = notificationId,
                pendingEntryId = pendingEntryId,
                ledgerEntryId = ledgerEntryId,
                createdAt = now,
                updatedAt = now,
                packageName = event.packageName,
                rawTitle = event.title,
                rawText = event.text,
                rawPostedAt = event.postedAt,
                sourceApp = candidate.sourceApp.name,
                modelVersion = candidate.modelVersion,
                parsePath = candidate.parsePath,
                amount = candidate.amount,
                direction = candidate.direction.name,
                channel = candidate.channel,
                candidateTimestamp = candidate.timestamp,
                candidateCategory = candidate.categoryHint,
                candidateNote = candidate.note,
                confidenceScore = candidate.confidence.score,
                confidenceLevel = candidate.confidence.level.name,
                confidenceReason = candidate.confidenceReason,
                featureSnapshotJson = candidate.modelFeatureSnapshotJson.ifBlank { "{}" },
                routeDestination = destination.name,
                processStatus = status.name,
                feedbackLabel = destination.toInitialFeedbackLabel(),
                feedbackSource = null,
                feedbackAt = null,
                editedManually = false,
                feedbackNote = null,
                correctedAmount = null,
                correctedDirection = null,
                correctedChannel = null,
                correctedTimestamp = null,
                correctedCategory = null,
                correctedNote = null,
                changedFieldsJson = null,
                uploadState = UPLOAD_STATE_PENDING,
                uploadedAt = null,
                lastUploadAttemptAt = null,
            ),
        )
    }

    override suspend fun linkLedgerEntry(
        notificationId: Long?,
        ledgerEntryId: Long,
    ) {
        if (notificationId == null) {
            return
        }
        modelTrainingSampleDao.linkLedgerEntryByNotificationId(
            notificationId = notificationId,
            ledgerEntryId = ledgerEntryId,
            updatedAt = System.currentTimeMillis(),
            uploadState = UPLOAD_STATE_PENDING,
        )
    }

    override suspend fun markPendingConfirmed(
        pendingEntryId: Long,
        editedManually: Boolean,
        correctedEntry: LedgerEntry?,
    ) {
        val sample = modelTrainingSampleDao.getByPendingEntryId(pendingEntryId)
        val correction = sample?.toCorrectionSnapshot(correctedEntry)
        modelTrainingSampleDao.updateFeedbackByPendingEntryId(
            pendingEntryId = pendingEntryId,
            feedbackLabel = if (editedManually) {
                FEEDBACK_USER_CONFIRMED_EDITED
            } else {
                FEEDBACK_USER_CONFIRMED_POSITIVE
            },
            feedbackSource = if (editedManually) "pending_edit_confirm" else "pending_confirm",
            feedbackAt = System.currentTimeMillis(),
            editedManually = editedManually,
            feedbackNote = if (editedManually) "用户编辑后确认入账" else "用户直接确认入账",
            correctedAmount = correction?.amount,
            correctedDirection = correction?.direction,
            correctedChannel = correction?.channel,
            correctedTimestamp = correction?.timestamp,
            correctedCategory = correction?.category,
            correctedNote = correction?.note,
            changedFieldsJson = correction?.changedFieldsJson,
            uploadState = UPLOAD_STATE_PENDING,
        )
    }

    override suspend fun markPendingIgnored(pendingEntryId: Long) {
        modelTrainingSampleDao.updateFeedbackByPendingEntryId(
            pendingEntryId = pendingEntryId,
            feedbackLabel = FEEDBACK_USER_DISMISSED_NEGATIVE,
            feedbackSource = "pending_ignore",
            feedbackAt = System.currentTimeMillis(),
            editedManually = false,
            feedbackNote = "用户忽略待确认候选",
            correctedAmount = null,
            correctedDirection = null,
            correctedChannel = null,
            correctedTimestamp = null,
            correctedCategory = null,
            correctedNote = null,
            changedFieldsJson = null,
            uploadState = UPLOAD_STATE_PENDING,
        )
    }

    override suspend fun markPendingFalsePositive(pendingEntryId: Long) {
        modelTrainingSampleDao.updateFeedbackByPendingEntryId(
            pendingEntryId = pendingEntryId,
            feedbackLabel = FEEDBACK_USER_MARKED_FALSE_POSITIVE,
            feedbackSource = "pending_false_positive",
            feedbackAt = System.currentTimeMillis(),
            editedManually = false,
            feedbackNote = "用户标记为误识别",
            correctedAmount = null,
            correctedDirection = null,
            correctedChannel = null,
            correctedTimestamp = null,
            correctedCategory = null,
            correctedNote = null,
            changedFieldsJson = null,
            uploadState = UPLOAD_STATE_PENDING,
        )
    }

    override suspend fun markLedgerEdited(entry: LedgerEntry) {
        val sample = modelTrainingSampleDao.getByLedgerTrace(
            ledgerEntryId = entry.id,
            notificationId = entry.traceNotificationId,
        )
        val correction = sample?.toCorrectionSnapshot(entry)
        modelTrainingSampleDao.updateFeedbackByLedgerTrace(
            ledgerEntryId = entry.id,
            notificationId = entry.traceNotificationId,
            feedbackLabel = FEEDBACK_USER_EDITED_LEDGER,
            feedbackSource = "ledger_edit",
            feedbackAt = System.currentTimeMillis(),
            editedManually = true,
            feedbackNote = "用户编辑已入账记录",
            correctedAmount = correction?.amount,
            correctedDirection = correction?.direction,
            correctedChannel = correction?.channel,
            correctedTimestamp = correction?.timestamp,
            correctedCategory = correction?.category,
            correctedNote = correction?.note,
            changedFieldsJson = correction?.changedFieldsJson,
            uploadState = UPLOAD_STATE_PENDING,
        )
    }

    override suspend fun markLedgerDeleted(entry: LedgerEntry) {
        modelTrainingSampleDao.updateFeedbackByLedgerTrace(
            ledgerEntryId = entry.id,
            notificationId = entry.traceNotificationId,
            feedbackLabel = FEEDBACK_USER_DELETED_LEDGER,
            feedbackSource = "ledger_delete",
            feedbackAt = System.currentTimeMillis(),
            editedManually = entry.editedManually,
            feedbackNote = "用户删除已入账记录",
            correctedAmount = null,
            correctedDirection = null,
            correctedChannel = null,
            correctedTimestamp = null,
            correctedCategory = null,
            correctedNote = null,
            changedFieldsJson = null,
            uploadState = UPLOAD_STATE_PENDING,
        )
    }

    /**
     * 用途说明：
     * - 读取一批仍需同步到服务器的样本，供云端学习增量上传使用。
     *
     * 参数说明：
     * - limit：单次上传最多读取的样本数量。
     */
    override suspend fun loadUploadBatch(limit: Int): ModelTrainingSampleUploadBatch {
        val samples = modelTrainingSampleDao.getPendingUploadBatch(
            uploadedState = UPLOAD_STATE_UPLOADED,
            uploadableLabels = UPLOADABLE_FEEDBACK_LABELS,
            limit = limit.coerceIn(1, MAX_UPLOAD_BATCH_SIZE),
        )
        return ModelTrainingSampleUploadBatch(
            sampleIds = samples.map { sample -> sample.id },
            jsonl = samples.joinToString(separator = "\n") { sample -> sample.toJsonLine() },
        )
    }

    /**
     * 用途说明：
     * - 记录一批样本已经尝试上传，失败时不改变待上传状态。
     *
     * 参数说明：
     * - sampleIds：本批样本的本地主键集合。
     * - attemptedAt：上传尝试时间戳。
     */
    override suspend fun markUploadBatchAttempted(
        sampleIds: List<Long>,
        attemptedAt: Long,
    ) {
        if (sampleIds.isEmpty()) {
            return
        }
        modelTrainingSampleDao.markUploadAttempted(
            ids = sampleIds,
            attemptedAt = attemptedAt,
        )
    }

    /**
     * 用途说明：
     * - 服务端确认接收后，把本批样本从待上传队列移出。
     *
     * 参数说明：
     * - sampleIds：本批样本的本地主键集合。
     * - uploadedAt：上传成功时间戳。
     */
    override suspend fun markUploadBatchUploaded(
        sampleIds: List<Long>,
        uploadedAt: Long,
    ) {
        if (sampleIds.isEmpty()) {
            return
        }
        modelTrainingSampleDao.markUploaded(
            ids = sampleIds,
            uploadedState = UPLOAD_STATE_UPLOADED,
            uploadedAt = uploadedAt,
        )
    }

    override suspend fun exportJsonl(): String {
        val samples = modelTrainingSampleDao.getAllForExport()
        return samples.joinToString(separator = "\n") { sample -> sample.toJsonLine() }
    }
}

/**
 * 用途说明：
 * - 为刚进入流水线分流的样本生成初始反馈标签。
 *
 * 参数说明：
 * - 接收者：流水线最终落点。
 */
private fun ProcessDestination.toInitialFeedbackLabel(): String = when (this) {
    ProcessDestination.LEDGER -> FEEDBACK_AUTO_LEDGER_UNREVIEWED
    ProcessDestination.PENDING -> FEEDBACK_PENDING_AWAITING_USER
    ProcessDestination.FILTERED -> FEEDBACK_FILTERED_UNREVIEWED
    ProcessDestination.DUPLICATE -> FEEDBACK_DUPLICATE_AWAITING_USER
}

/**
 * 用户纠错快照。
 *
 * 用途说明：
 * - 保存用户最终确认后的字段值，以及相对模型初始候选被改动的字段名。
 *
 * 参数说明：
 * - amount：用户最终确认的金额。
 * - direction：用户最终确认的收支方向。
 * - channel：用户最终确认的渠道。
 * - timestamp：用户最终确认的交易时间。
 * - category：用户最终确认的分类。
 * - note：用户最终确认的备注。
 * - changedFieldsJson：字段名数组 JSON。
 */
private data class CorrectionSnapshot(
    val amount: Double,
    val direction: String,
    val channel: String,
    val timestamp: Long,
    val category: String,
    val note: String?,
    val changedFieldsJson: String,
)

/**
 * 用途说明：
 * - 根据训练样本的初始候选值和用户最终账本值计算字段差异。
 *
 * 参数说明：
 * - correctedEntry：用户最终确认的账本记录；为空时表示直接确认原候选。
 */
private fun ModelTrainingSampleEntity.toCorrectionSnapshot(correctedEntry: LedgerEntry?): CorrectionSnapshot {
    val amountValue = correctedEntry?.amount ?: amount
    val directionValue = correctedEntry?.direction?.name ?: direction
    val channelValue = correctedEntry?.channel ?: channel
    val timestampValue = correctedEntry?.timestamp ?: candidateTimestamp
    val categoryValue = correctedEntry?.category ?: candidateCategory
    val noteValue = correctedEntry?.note ?: candidateNote
    val changedFields = buildList {
        if (!amount.closeTo(amountValue)) add("amount")
        if (direction != directionValue) add("direction")
        if (channel.normalizedText() != channelValue.normalizedText()) add("channel")
        if (candidateTimestamp != timestampValue) add("timestamp")
        if (candidateCategory.normalizedText() != categoryValue.normalizedText()) add("category")
        if (candidateNote.normalizedNullableText() != noteValue.normalizedNullableText()) add("note")
    }
    return CorrectionSnapshot(
        amount = amountValue,
        direction = directionValue,
        channel = channelValue,
        timestamp = timestampValue,
        category = categoryValue,
        note = noteValue,
        changedFieldsJson = changedFields.toJsonArray(),
    )
}

/**
 * 用途说明：
 * - 把训练样本实体序列化为 JSONL 的单行 JSON。
 *
 * 参数说明：
 * - 接收者：待导出的训练样本实体。
 */
private fun ModelTrainingSampleEntity.toJsonLine(): String = buildString {
    append("{")
    appendJsonNumber("id", id)
    append(",")
    appendJsonNumber("sampleLocalId", id)
    append(",")
    appendJsonNullableNumber("notificationId", notificationId)
    append(",")
    appendJsonNullableNumber("pendingEntryId", pendingEntryId)
    append(",")
    appendJsonNullableNumber("ledgerEntryId", ledgerEntryId)
    append(",")
    appendJsonNumber("createdAt", createdAt)
    append(",")
    appendJsonNumber("updatedAt", updatedAt)
    append(",")
    appendJsonString("packageName", packageName)
    append(",")
    appendJsonNullableString("rawTitle", rawTitle)
    append(",")
    appendJsonNullableString("rawText", rawText)
    append(",")
    appendJsonNumber("rawPostedAt", rawPostedAt)
    append(",")
    appendJsonString("sourceApp", sourceApp)
    append(",")
    appendJsonString("modelVersion", modelVersion)
    append(",")
    appendJsonString("parsePath", parsePath)
    append(",")
    appendJsonDecimal("amount", amount)
    append(",")
    appendJsonString("direction", direction)
    append(",")
    appendJsonString("channel", channel)
    append(",")
    appendJsonNumber("candidateTimestamp", candidateTimestamp)
    append(",")
    appendJsonString("category", candidateCategory)
    append(",")
    appendJsonNullableString("note", candidateNote)
    append(",")
    appendJsonFloat("confidenceScore", confidenceScore)
    append(",")
    appendJsonString("confidenceLevel", confidenceLevel)
    append(",")
    appendJsonString("confidenceReason", confidenceReason)
    append(",\"features\":")
    append(featureSnapshotJson.ifBlank { "{}" })
    append(",")
    appendJsonString("routeDestination", routeDestination)
    append(",")
    appendJsonString("processStatus", processStatus)
    append(",")
    appendJsonString("feedbackLabel", feedbackLabel)
    append(",")
    appendJsonNullableString("feedbackSource", feedbackSource)
    append(",")
    appendJsonNullableNumber("feedbackAt", feedbackAt)
    append(",")
    appendJsonBoolean("editedManually", editedManually)
    append(",")
    appendJsonNullableString("feedbackNote", feedbackNote)
    append(",\"parsed\":")
    appendParsedSnapshotJson(this@toJsonLine)
    append(",\"corrected\":")
    appendCorrectedSnapshotJson(this@toJsonLine)
    append(",\"changedFields\":")
    append(changedFieldsJson ?: "[]")
    append("}")
}

/**
 * 用途说明：
 * - 追加模型最初解析出的候选字段快照。
 *
 * 参数说明：
 * - 接收者：待导出的训练样本实体。
 */
private fun StringBuilder.appendParsedSnapshotJson(sample: ModelTrainingSampleEntity) {
    append("{")
    appendJsonDecimal("amount", sample.amount)
    append(",")
    appendJsonString("direction", sample.direction)
    append(",")
    appendJsonString("channel", sample.channel)
    append(",")
    appendJsonNumber("timestamp", sample.candidateTimestamp)
    append(",")
    appendJsonString("category", sample.candidateCategory)
    append(",")
    appendJsonNullableString("note", sample.candidateNote)
    append("}")
}

/**
 * 用途说明：
 * - 追加用户最终确认的字段快照；没有编辑反馈时输出 null。
 *
 * 参数说明：
 * - 接收者：待导出的训练样本实体。
 */
private fun StringBuilder.appendCorrectedSnapshotJson(sample: ModelTrainingSampleEntity) {
    if (sample.correctedAmount == null &&
        sample.correctedDirection == null &&
        sample.correctedTimestamp == null &&
        sample.correctedNote == null
    ) {
        append("null")
        return
    }
    append("{")
    appendJsonNullableDecimal("amount", sample.correctedAmount)
    append(",")
    appendJsonNullableString("direction", sample.correctedDirection)
    append(",")
    appendJsonNullableString("channel", sample.correctedChannel)
    append(",")
    appendJsonNullableNumber("timestamp", sample.correctedTimestamp)
    append(",")
    appendJsonNullableString("category", sample.correctedCategory)
    append(",")
    appendJsonNullableString("note", sample.correctedNote)
    append("}")
}

/**
 * 用途说明：
 * - 追加 JSON 字符串字段。
 *
 * 参数说明：
 * - name：字段名。
 * - value：字段值。
 */
private fun StringBuilder.appendJsonString(
    name: String,
    value: String,
) {
    appendJsonValue(name)
    append(":")
    appendJsonValue(value)
}

/**
 * 用途说明：
 * - 追加可为空的 JSON 字符串字段。
 *
 * 参数说明：
 * - name：字段名。
 * - value：字段值，可为空。
 */
private fun StringBuilder.appendJsonNullableString(
    name: String,
    value: String?,
) {
    appendJsonValue(name)
    append(":")
    if (value == null) {
        append("null")
    } else {
        appendJsonValue(value)
    }
}

/**
 * 用途说明：
 * - 追加 JSON 长整数字段。
 *
 * 参数说明：
 * - name：字段名。
 * - value：字段值。
 */
private fun StringBuilder.appendJsonNumber(
    name: String,
    value: Long,
) {
    appendJsonValue(name)
    append(":")
    append(value)
}

/**
 * 用途说明：
 * - 追加可为空的 JSON 长整数字段。
 *
 * 参数说明：
 * - name：字段名。
 * - value：字段值，可为空。
 */
private fun StringBuilder.appendJsonNullableNumber(
    name: String,
    value: Long?,
) {
    appendJsonValue(name)
    append(":")
    append(value?.toString() ?: "null")
}

/**
 * 用途说明：
 * - 追加 JSON 浮点字段。
 *
 * 参数说明：
 * - name：字段名。
 * - value：字段值。
 */
private fun StringBuilder.appendJsonFloat(
    name: String,
    value: Float,
) {
    appendJsonValue(name)
    append(":")
    append(String.format(Locale.ROOT, "%.6f", value))
}

/**
 * 用途说明：
 * - 追加 JSON 金额字段。
 *
 * 参数说明：
 * - name：字段名。
 * - value：字段值。
 */
private fun StringBuilder.appendJsonDecimal(
    name: String,
    value: Double,
) {
    appendJsonValue(name)
    append(":")
    append(String.format(Locale.ROOT, "%.2f", value))
}

/**
 * 用途说明：
 * - 追加可为空的 JSON 金额字段。
 *
 * 参数说明：
 * - name：字段名。
 * - value：字段值，可为空。
 */
private fun StringBuilder.appendJsonNullableDecimal(
    name: String,
    value: Double?,
) {
    appendJsonValue(name)
    append(":")
    if (value == null) {
        append("null")
    } else {
        append(String.format(Locale.ROOT, "%.2f", value))
    }
}

/**
 * 用途说明：
 * - 追加 JSON 布尔字段。
 *
 * 参数说明：
 * - name：字段名。
 * - value：字段值。
 */
private fun StringBuilder.appendJsonBoolean(
    name: String,
    value: Boolean,
) {
    appendJsonValue(name)
    append(":")
    append(value)
}

/**
 * 用途说明：
 * - 追加带引号和转义的 JSON 字符串值。
 *
 * 参数说明：
 * - value：待写入的原始字符串。
 */
private fun StringBuilder.appendJsonValue(value: String) {
    append('"')
    append(value.jsonEscaped())
    append('"')
}

/**
 * 用途说明：
 * - 判断两个金额是否可以视为一致。
 *
 * 参数说明：
 * - other：另一个金额。
 */
private fun Double.closeTo(other: Double): Boolean {
    return kotlin.math.abs(this - other) < AMOUNT_DIFF_EPSILON
}

/**
 * 用途说明：
 * - 归一化文本字段，避免首尾空格造成虚假的字段纠错。
 */
private fun String.normalizedText(): String = trim()

/**
 * 用途说明：
 * - 归一化可空文本字段，空字符串和 null 视作同一类缺失值。
 */
private fun String?.normalizedNullableText(): String? = this?.trim()?.takeIf(String::isNotEmpty)

/**
 * 用途说明：
 * - 把字段名列表序列化为 JSON 数组。
 */
private fun List<String>.toJsonArray(): String = buildString {
    append("[")
    this@toJsonArray.forEachIndexed { index, value ->
        if (index > 0) {
            append(",")
        }
        appendJsonValue(value)
    }
    append("]")
}

/**
 * 用途说明：
 * - 对 JSON 字符串做最小安全转义。
 *
 * 参数说明：
 * - 接收者：待转义字符串。
 */
private fun String.jsonEscaped(): String = buildString {
    this@jsonEscaped.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}

private const val FEEDBACK_AUTO_LEDGER_UNREVIEWED = "AUTO_LEDGER_UNREVIEWED"
private const val FEEDBACK_PENDING_AWAITING_USER = "PENDING_AWAITING_USER"
private const val FEEDBACK_FILTERED_UNREVIEWED = "FILTERED_UNREVIEWED"
private const val FEEDBACK_DUPLICATE_AWAITING_USER = "DUPLICATE_AWAITING_USER"
private const val FEEDBACK_RAW_NOTIFICATION_UNREVIEWED = "RAW_NOTIFICATION_UNREVIEWED"
private const val FEEDBACK_USER_CONFIRMED_POSITIVE = "USER_CONFIRMED_POSITIVE"
private const val FEEDBACK_USER_CONFIRMED_EDITED = "USER_CONFIRMED_EDITED"
private const val FEEDBACK_USER_DISMISSED_NEGATIVE = "USER_DISMISSED_NEGATIVE"
private const val FEEDBACK_USER_MARKED_FALSE_POSITIVE = "USER_MARKED_FALSE_POSITIVE"
private const val FEEDBACK_USER_EDITED_LEDGER = "USER_EDITED_LEDGER"
private const val FEEDBACK_USER_DELETED_LEDGER = "USER_DELETED_LEDGER"

/**
 * 用途说明：
 * - 限定客户端允许上传到云端样本池的反馈标签。
 * - 待用户确认、重复待确认、原文样本、空标签和未知标签都没有稳定训练含义，因此先留在本地等待后续用户反馈或直接不参与同步。
 */
private val UPLOADABLE_FEEDBACK_LABELS = listOf(
    FEEDBACK_AUTO_LEDGER_UNREVIEWED,
    FEEDBACK_FILTERED_UNREVIEWED,
    FEEDBACK_USER_CONFIRMED_POSITIVE,
    FEEDBACK_USER_CONFIRMED_EDITED,
    FEEDBACK_USER_DISMISSED_NEGATIVE,
    FEEDBACK_USER_MARKED_FALSE_POSITIVE,
    FEEDBACK_USER_EDITED_LEDGER,
    FEEDBACK_USER_DELETED_LEDGER,
)
private const val RAW_NOTIFICATION_MODEL_VERSION = "raw-notification-v1"
private const val RAW_NOTIFICATION_PARSE_PATH = "raw-notification"
private const val RAW_NOTIFICATION_DIRECTION = "UNKNOWN"
private const val RAW_NOTIFICATION_CONFIDENCE_LEVEL = "LOW"
private const val UPLOAD_STATE_PENDING = "PENDING"
private const val UPLOAD_STATE_UPLOADED = "UPLOADED"
private const val MAX_UPLOAD_BATCH_SIZE = 200
private const val AMOUNT_DIFF_EPSILON = 0.005
