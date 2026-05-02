package com.autobook.cn.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.autobook.cn.core.database.dao.LedgerEntryDao
import com.autobook.cn.core.database.dao.ModelTrainingSampleDao
import com.autobook.cn.core.database.dao.NotificationEventDao
import com.autobook.cn.core.database.dao.PendingEntryDao
import com.autobook.cn.core.database.entity.LedgerEntryEntity
import com.autobook.cn.core.database.entity.ModelTrainingSampleEntity
import com.autobook.cn.core.database.entity.NotificationEventEntity
import com.autobook.cn.core.database.entity.PendingEntryEntity

/**
 * 保存通知原文、账本记录和待确认记录的 Room 数据库。
 *
 * 用途说明：
 * - 统一承载通知处理链涉及的三类核心数据表。
 * - 当前项目尚未上线，训练样本结构调整时直接提升版本并走破坏式迁移。
 */
@Database(
    entities = [
        NotificationEventEntity::class,
        LedgerEntryEntity::class,
        PendingEntryEntity::class,
        ModelTrainingSampleEntity::class,
    ],
    version = 7,
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
