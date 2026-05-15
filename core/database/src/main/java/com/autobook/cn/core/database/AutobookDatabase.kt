package com.autobook.cn.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.autobook.cn.core.database.dao.LedgerEntryDao
import com.autobook.cn.core.database.dao.ModelTrainingSampleDao
import com.autobook.cn.core.database.dao.NotificationEventDao
import com.autobook.cn.core.database.dao.PendingEntryDao
import com.autobook.cn.core.database.entity.LedgerEntryEntity
import com.autobook.cn.core.database.entity.LedgerStatsEntity
import com.autobook.cn.core.database.entity.ModelTrainingSampleEntity
import com.autobook.cn.core.database.entity.NotificationEventEntity
import com.autobook.cn.core.database.entity.PendingEntryEntity
import com.autobook.cn.core.database.entity.PendingStatsEntity

/**
 * 保存通知原文、账本记录和待确认记录的 Room 数据库。
 *
 * 用途说明：
 * - 统一承载通知处理链涉及的核心数据表。
 * - 项目已经上线，后续版本升级必须提供显式迁移，避免用户本地账本被清空。
 */
@Database(
    entities = [
        NotificationEventEntity::class,
        LedgerEntryEntity::class,
        LedgerStatsEntity::class,
        PendingEntryEntity::class,
        PendingStatsEntity::class,
        ModelTrainingSampleEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
abstract class AutobookDatabase : RoomDatabase() {
    /** 原始通知与处理状态表。 */
    abstract fun notificationEventDao(): NotificationEventDao

    /** 已确认账本记录表。 */
    abstract fun ledgerEntryDao(): LedgerEntryDao

    /** 待人工确认记录表。 */
    abstract fun pendingEntryDao(): PendingEntryDao

    /** 置信度模型训练样本表。 */
    abstract fun modelTrainingSampleDao(): ModelTrainingSampleDao
}
