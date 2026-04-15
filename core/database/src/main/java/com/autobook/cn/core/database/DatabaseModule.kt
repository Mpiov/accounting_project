package com.autobook.cn.core.database

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.room.Room
import com.autobook.cn.core.database.dao.LedgerEntryDao
import com.autobook.cn.core.database.dao.NotificationEventDao
import com.autobook.cn.core.database.dao.PendingEntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * 用途说明：
 * - 构建加密数据库并通过 Hilt 暴露各个 DAO。
 * - 本模块已经切换到新版 `sqlcipher-android`，用于兼容 Android 15+ 的 16 KB 页大小设备。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * 用途说明：
     * - SQLCipher 新版库要求在首次使用前显式加载原生 `sqlcipher` 动态库。
     * - 这里通过惰性单例保证整个进程只加载一次，避免重复加载导致的风险。
     */
    private object SqlcipherNativeLoader {
        private var loaded = false

        fun ensureLoaded() {
            if (loaded) return
            synchronized(this) {
                if (loaded) return
                System.loadLibrary("sqlcipher")
                loaded = true
            }
        }
    }

    /**
     * 用途说明：
     * - 创建 Room 数据库实例，并接入 SQLCipher 的加密 open helper。
     *
     * 参数说明：
     * - context：应用级 Context，用于定位数据库文件和读取受保护口令。
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AutobookDatabase {
        return runCatching {
            // 新版 sqlcipher-android 需要在构建 Room 工厂之前先加载 native 库，否则可能在启动阶段直接崩溃。
            SqlcipherNativeLoader.ensureLoaded()

            // SQLCipher 需要二进制口令，这里统一从受保护的本地存储中读取或生成。
            val passphrase = DatabasePassphraseProvider(context).getOrCreate()
            deleteLegacyPlaintextDatabaseIfNeeded(context)
            val factory = SupportOpenHelperFactory(passphrase)

            Room.databaseBuilder(context, AutobookDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }.getOrElse { throwable ->
            // 某些 x86_64 / 模拟器环境即便 so 已经 16 KB 对齐，仍可能在 native 初始化阶段直接失败。
            // 为了保证 Android Studio 调试可继续进行，这里仅在模拟器环境下回退到明文 Room 数据库。
            if (!isLikelyEmulator()) {
                throw throwable
            }

            Log.e(
                DATABASE_TAG,
                "SQLCipher init failed on emulator, fallback to plaintext Room for local testing.",
                throwable,
            )
            Room.databaseBuilder(context, AutobookDatabase::class.java, EMULATOR_DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .build()
        }
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
}

/**
 * 用途说明：
 * - 兼容从明文 Room 数据库切换到 SQLCipher 的场景。
 * - 如果检测到旧库仍然是标准 SQLite 文件头，就先删除，让加密库重新创建。
 *
 * 参数说明：
 * - context：应用级 Context，用于定位数据库文件。
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

/**
 * 用途说明：
 * - 判断目标数据库文件是否仍然是明文 SQLite 文件头。
 *
 * 参数说明：
 * - 当前 File：待检查数据库文件。
 */
private fun File.isPlaintextSqliteFile(): Boolean {
    if (!exists() || length() < SQLITE_HEADER.size.toLong()) return false
    return inputStream().use { stream ->
        val header = ByteArray(SQLITE_HEADER.size)
        val bytesRead = stream.read(header)
        bytesRead == SQLITE_HEADER.size && header.contentEquals(SQLITE_HEADER)
    }
}

/**
 * 用途说明：
 * - 如果文件存在则删除，主要用于清理旧数据库的 wal / shm / journal 附属文件。
 *
 * 参数说明：
 * - file：待删除文件。
 */
private fun deleteIfExists(file: File) {
    if (file.exists()) {
        file.delete()
    }
}

/**
 * 用途说明：
 * - 判断当前运行环境是否高度疑似模拟器。
 * - 仅用于数据库 native 初始化失败后的兼容兜底，不参与正式业务逻辑判断。
 */
private fun isLikelyEmulator(): Boolean {
    return Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
        Build.MODEL.contains("Emulator", ignoreCase = true) ||
        Build.MODEL.contains("sdk_gphone", ignoreCase = true) ||
        Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
        Build.PRODUCT.contains("sdk", ignoreCase = true) ||
        Build.BRAND.startsWith("generic", ignoreCase = true) ||
        Build.DEVICE.startsWith("generic", ignoreCase = true)
}

private const val DATABASE_NAME = "autobook.db"
private const val EMULATOR_DATABASE_NAME = "autobook-emulator.db"
private const val DATABASE_TAG = "AutobookDatabase"
private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
