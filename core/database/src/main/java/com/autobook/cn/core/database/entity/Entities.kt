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
@Entity(tableName = "ledger_entries")
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val amount: Double,
    val direction: String,
    val merchant: String,
    val channel: String,
    val timestamp: Long,
    val category: String,
    val orderId: String?,
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
@Entity(tableName = "pending_entries")
data class PendingEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val amount: Double,
    val direction: String,
    val merchant: String,
    val channel: String,
    val timestamp: Long,
    val confidenceScore: Float,
    val orderId: String?,
    val categoryHint: String,
    val rawSource: String,
    val parsePath: String,
    val modelVersion: String,
    val reason: String,
    val traceNotificationId: Long?,
    val isFalsePositive: Boolean,
    val editedManually: Boolean,
)
