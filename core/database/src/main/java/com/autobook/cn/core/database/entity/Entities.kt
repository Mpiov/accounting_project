package com.autobook.cn.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 原始通知与处理元数据的数据库实体。
 *
 * 用途说明：
 * - 只保存通知原文、来源、处理状态和错误信息，供后续排查与回放使用。
 * - 通知级精确哈希去重已移除，因此表结构里不再保留 `dedupeKey` 字段。
 */
@Entity(
    tableName = "notification_events",
    indices = [
        Index("processStatus"),
        Index(value = ["packageName", "postedAt"]),
    ],
)
data class NotificationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    val rawSource: String,
    val sourceApp: String,
    val processStatus: String,
    val lastError: String?,
    val retryCount: Int,
    val screenshotRef: String?,
)

/**
 * 已正式入账的账本实体。
 *
 * 用途说明：
 * - 保存最终确认后的账本记录。
 * - 保留链路追踪字段，方便从账本反查通知来源和解析路径。
 */
@Entity(
    tableName = "ledger_entries",
    indices = [
        Index("sortId", unique = true),
        Index("timestamp"),
    ],
)
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sortId: Long,
    val amount: Double,
    val direction: String,
    val channel: String,
    val timestamp: Long,
    val category: String,
    val note: String?,
    val traceSource: String,
    val tracePath: String,
    val traceModelVersion: String,
    val traceNotificationId: Long?,
    val editedManually: Boolean,
)

/**
 * 待人工确认或待排除的队列实体。
 *
 * 用途说明：
 * - 保存仍需人工确认的候选交易。
 * - 允许后续把误报标记为 false positive，持续优化规则效果。
 */
@Entity(
    tableName = "pending_entries",
    indices = [
        Index("sortId", unique = true),
        Index("isFalsePositive"),
    ],
)
data class PendingEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sortId: Long,
    val amount: Double,
    val direction: String,
    val channel: String,
    val timestamp: Long,
    val confidenceScore: Float,
    val categoryHint: String,
    val note: String?,
    val rawSource: String,
    val parsePath: String,
    val modelVersion: String,
    val reason: String,
    val traceNotificationId: Long?,
    val isFalsePositive: Boolean,
    val editedManually: Boolean,
)

/**
 * 待确认轻量统计缓存。
 *
 * 用途说明：
 * - 底部徽标和首页提醒只需要有效待确认数量，用缓存避免频繁执行 `COUNT(*)`。
 */
@Entity(tableName = "pending_stats")
data class PendingStatsEntity(
    @PrimaryKey val id: Int = 1,
    val activeCount: Int,
)

/**
 * 账本轻量统计缓存。
 *
 * 用途说明：
 * - 避免设置页频繁执行 `COUNT(*)` 或整表加载。
 * - 记录总笔数、第一笔交易时间和最后一笔交易时间，设置页可直接换算记账天数。
 */
@Entity(tableName = "ledger_stats")
data class LedgerStatsEntity(
    @PrimaryKey val id: Int = 1,
    val entryCount: Int,
    val firstTimestamp: Long?,
    val lastTimestamp: Long?,
)

/**
 * 置信度模型训练样本实体。
 *
 * 用途说明：
 * - 保存模型当时看到的结构化特征、分数、分流结果和用户后续反馈。
 * - 会员全局云端学习需要把通知标题和正文作为监督训练输入，因此这里保留原始通知文本快照。
 */
@Entity(
    tableName = "model_training_samples",
    indices = [
        Index("notificationId"),
        Index("pendingEntryId"),
        Index("ledgerEntryId"),
        Index("feedbackLabel"),
        Index("uploadState"),
    ],
)
data class ModelTrainingSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val notificationId: Long?,
    val pendingEntryId: Long?,
    val ledgerEntryId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val packageName: String,
    val rawTitle: String?,
    val rawText: String?,
    val rawPostedAt: Long,
    val sourceApp: String,
    val modelVersion: String,
    val parsePath: String,
    val amount: Double,
    val direction: String,
    val channel: String,
    val candidateTimestamp: Long,
    val candidateCategory: String,
    val candidateNote: String?,
    val confidenceScore: Float,
    val confidenceLevel: String,
    val confidenceReason: String,
    val featureSnapshotJson: String,
    val routeDestination: String,
    val processStatus: String,
    val feedbackLabel: String,
    val feedbackSource: String?,
    val feedbackAt: Long?,
    val editedManually: Boolean,
    val feedbackNote: String?,
    val correctedAmount: Double?,
    val correctedDirection: String?,
    val correctedChannel: String?,
    val correctedTimestamp: Long?,
    val correctedCategory: String?,
    val correctedNote: String?,
    val changedFieldsJson: String?,
    val uploadState: String,
    val uploadedAt: Long?,
    val lastUploadAttemptAt: Long?,
)
