package com.autobook.cn.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_events",
    indices = [
        Index("dedupeKey"),
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
    val dedupeKey: String,
    val sourceApp: String,
    val processStatus: String,
    val lastError: String?,
    val retryCount: Int,
    val screenshotRef: String?,
)

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
