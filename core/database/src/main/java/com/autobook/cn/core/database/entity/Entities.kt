package com.autobook.cn.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_events")
data class NotificationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    val rawSource: String,
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
    val note: String?,
    val traceSource: String,
    val tracePath: String,
    val traceModelVersion: String,
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
    val rawSource: String,
    val parsePath: String,
    val modelVersion: String,
    val reason: String,
)
