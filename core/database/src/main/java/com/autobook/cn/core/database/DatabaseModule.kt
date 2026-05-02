package com.autobook.cn.core.database

import android.content.Context
import androidx.room.Room
import com.autobook.cn.core.database.dao.LedgerEntryDao
import com.autobook.cn.core.database.dao.ModelTrainingSampleDao
import com.autobook.cn.core.database.dao.NotificationEventDao
import com.autobook.cn.core.database.dao.PendingEntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 用途说明：
 * - 构建普通 Room 数据库并通过 Hilt 暴露各个 DAO。
 * - 当前项目仍处于前后端能力整合阶段，优先保持本地数据库实现简单、稳定、便于调试。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * 用途说明：
     * - 创建 Room 数据库实例，统一使用项目主数据库文件名。
     *
     * 参数说明：
     * - context：应用级 Context，用于定位数据库文件。
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AutobookDatabase {
        return Room.databaseBuilder(context, AutobookDatabase::class.java, DATABASE_NAME)
            // 当前项目尚未上线，数据库结构调整时优先保证开发迭代效率。
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * 用途说明：
     * - 暴露通知原文 DAO。
     *
     * 参数说明：
     * - database：已构建完成的数据库实例。
     */
    @Provides
    fun provideNotificationEventDao(database: AutobookDatabase): NotificationEventDao = database.notificationEventDao()

    /**
     * 用途说明：
     * - 暴露账本记录 DAO。
     *
     * 参数说明：
     * - database：已构建完成的数据库实例。
     */
    @Provides
    fun provideLedgerEntryDao(database: AutobookDatabase): LedgerEntryDao = database.ledgerEntryDao()

    /**
     * 用途说明：
     * - 暴露待确认记录 DAO。
     *
     * 参数说明：
     * - database：已构建完成的数据库实例。
     */
    @Provides
    fun providePendingEntryDao(database: AutobookDatabase): PendingEntryDao = database.pendingEntryDao()

    /**
     * 用途说明：
     * - 暴露模型训练样本 DAO。
     *
     * 参数说明：
     * - database：已构建完成的数据库实例。
     */
    @Provides
    fun provideModelTrainingSampleDao(database: AutobookDatabase): ModelTrainingSampleDao =
        database.modelTrainingSampleDao()
}

private const val DATABASE_NAME = "autobook.db"
