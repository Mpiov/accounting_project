package com.autobook.cn.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.autobook.cn.core.database.dao.LedgerEntryDao
import com.autobook.cn.core.database.dao.NotificationEventDao
import com.autobook.cn.core.database.dao.PendingEntryDao
import com.autobook.cn.core.database.entity.LedgerEntryEntity
import com.autobook.cn.core.database.entity.NotificationEventEntity
import com.autobook.cn.core.database.entity.PendingEntryEntity

/**
 * 保存通知原文、账本记录和待确认记录的 Room 数据库。
 *
 * 用途说明：
 * - 统一承载通知处理链涉及的三类核心数据表。
 * - 本次移除了通知级精确哈希字段，因此数据库版本同步提升。
 */
@Database(
    entities = [NotificationEventEntity::class, LedgerEntryEntity::class, PendingEntryEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AutobookDatabase : RoomDatabase() {
    /** 原始通知与处理状态表。 */
    abstract fun notificationEventDao(): NotificationEventDao

    /** 已确认账本记录表。 */
    abstract fun ledgerEntryDao(): LedgerEntryDao

    /** 待人工确认记录表。 */
    abstract fun pendingEntryDao(): PendingEntryDao
}
