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
import java.io.File
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
        deleteLegacyPlaintextDatabaseIfNeeded(context)
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

/**
 * 兼容从明文 Room 数据库切换到 SQLCipher 的场景。
 * 如果检测到旧库仍然是标准 SQLite 头，就先删除，让加密库重建。
 */
private fun deleteLegacyPlaintextDatabaseIfNeeded(context: Context) {
    val databaseFile = context.getDatabasePath(DATABASE_NAME)
    if (!databaseFile.exists() || !databaseFile.isFile) return
    if (!databaseFile.isPlaintextSqliteFile()) return

    context.deleteDatabase(DATABASE_NAME)
    deleteIfExists(File(databaseFile.parentFile, "$DATABASE_NAME-wal"))
    deleteIfExists(File(databaseFile.parentFile, "$DATABASE_NAME-shm"))
    deleteIfExists(File(databaseFile.parentFile, "$DATABASE_NAME-journal"))
}

private fun File.isPlaintextSqliteFile(): Boolean {
    if (!exists() || length() < SQLITE_HEADER.size.toLong()) return false
    return inputStream().use { stream ->
        val header = ByteArray(SQLITE_HEADER.size)
        val bytesRead = stream.read(header)
        bytesRead == SQLITE_HEADER.size && header.contentEquals(SQLITE_HEADER)
    }
}

private fun deleteIfExists(file: File) {
    if (file.exists()) {
        file.delete()
    }
}

private const val DATABASE_NAME = "autobook.db"
private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
