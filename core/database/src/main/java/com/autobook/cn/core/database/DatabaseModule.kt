package com.autobook.cn.core.database

import android.content.Context
import androidx.room.Room
import com.autobook.cn.core.database.dao.LedgerEntryDao
import com.autobook.cn.core.database.dao.NotificationEventDao
import com.autobook.cn.core.database.dao.PendingEntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.sqlcipher.database.SupportFactory

/** 构建加密数据库并通过 Hilt 暴露各个 DAO。 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AutobookDatabase {
        // SQLCipher 需要二进制口令，这里统一从受保护的本地存储中取出。
        val passphrase = DatabasePassphraseProvider(context).getOrCreate()
        val factory = SupportFactory(passphrase)
        return Room.databaseBuilder(context, AutobookDatabase::class.java, "autobook.db")
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideNotificationEventDao(database: AutobookDatabase): NotificationEventDao =
        database.notificationEventDao()

    @Provides
    fun provideLedgerEntryDao(database: AutobookDatabase): LedgerEntryDao = database.ledgerEntryDao()

    @Provides
    fun providePendingEntryDao(database: AutobookDatabase): PendingEntryDao = database.pendingEntryDao()
}
