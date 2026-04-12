package com.autobook.cn.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.autobook.cn.core.database.dao.LedgerEntryDao
import com.autobook.cn.core.database.dao.NotificationEventDao
import com.autobook.cn.core.database.dao.PendingEntryDao
import com.autobook.cn.core.database.entity.LedgerEntryEntity
import com.autobook.cn.core.database.entity.NotificationEventEntity
import com.autobook.cn.core.database.entity.PendingEntryEntity

@Database(
    entities = [NotificationEventEntity::class, LedgerEntryEntity::class, PendingEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AutobookDatabase : RoomDatabase() {
    abstract fun notificationEventDao(): NotificationEventDao

    abstract fun ledgerEntryDao(): LedgerEntryDao

    abstract fun pendingEntryDao(): PendingEntryDao
}
