package com.autobook.cn.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room 数据库迁移测试。
 *
 * 用途说明：
 * - 模拟生产用户手机上的旧版数据库，验证升级到新版后不会清库。
 * - 锁定金额符号自愈与统计缓存重建逻辑，避免后续迁移误删或漏跑。
 */
@RunWith(AndroidJUnit4::class)
class AutobookMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AutobookDatabase::class.java,
    )

    @Test
    fun migration9To10RepairsNegativeAmountsAndRebuildsStats() {
        helper.createDatabase(TEST_DB, 9).use { connection ->
            createVersion9Schema(connection)
            seedVersion9DirtyData(connection)
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            10,
            true,
            AutobookMigrations.MIGRATION_9_10,
        )
        migrated.close()

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AutobookDatabase::class.java,
            TEST_DB,
        )
            .addMigrations(*AutobookMigrations.ALL)
            .build()
        try {
            val connection = database.openHelper.writableDatabase
            with(connection) {
                assertLedgerAmount(connection, id = 1L, expectedAmount = 125.5)
                assertLedgerAmount(connection, id = 2L, expectedAmount = 20.0)
                assertPendingAmount(connection, id = 1L, expectedAmount = 8.8)
                assertLedgerStats(
                    connection = connection,
                    expectedCount = 2,
                    expectedFirstTimestamp = 1000L,
                    expectedLastTimestamp = 3000L,
                )
                assertPendingStats(connection)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migration10To11MovesYear2025EntriesTo2026AndRebuildsStats() {
        helper.createDatabase(TEST_DB, 10).use { connection ->
            createVersion9Schema(connection)
            seedVersion10Year2025Data(connection)
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            11,
            true,
            AutobookMigrations.MIGRATION_10_11,
        )
        migrated.close()

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AutobookDatabase::class.java,
            TEST_DB,
        )
            .addMigrations(*AutobookMigrations.ALL)
            .build()
        try {
            val connection = database.openHelper.writableDatabase
            assertLedgerTimestamp(
                connection = connection,
                id = 10L,
                expectedTimestamp = epochMillisOf(2026, 5, 15, 11, 20),
            )
            assertLedgerTimestamp(
                connection = connection,
                id = 11L,
                expectedTimestamp = epochMillisOf(2026, 6, 1, 8, 30),
            )
            assertPendingTimestamp(
                connection = connection,
                id = 20L,
                expectedTimestamp = epochMillisOf(2026, 5, 16, 9, 5),
            )
            assertLedgerStats(
                connection = connection,
                expectedCount = 2,
                expectedFirstTimestamp = epochMillisOf(2026, 5, 15, 11, 20),
                expectedLastTimestamp = epochMillisOf(2026, 6, 1, 8, 30),
            )
            assertPendingStats(connection)
        } finally {
            database.close()
        }
    }

    /**
     * 创建当前生产 v9 数据库结构。
     *
     * 参数说明：
     * - connection：测试辅助工具创建出来的旧版 SQLite 连接。
     */
    private fun createVersion9Schema(connection: SupportSQLiteDatabase) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `notification_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packageName` TEXT NOT NULL, `title` TEXT, `text` TEXT, `postedAt` INTEGER NOT NULL, `rawSource` TEXT NOT NULL, `sourceApp` TEXT NOT NULL, `processStatus` TEXT NOT NULL, `lastError` TEXT, `retryCount` INTEGER NOT NULL, `screenshotRef` TEXT)",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_events_processStatus` ON `notification_events` (`processStatus`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_events_packageName_postedAt` ON `notification_events` (`packageName`, `postedAt`)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `ledger_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sortId` INTEGER NOT NULL, `amount` REAL NOT NULL, `direction` TEXT NOT NULL, `channel` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `category` TEXT NOT NULL, `note` TEXT, `traceSource` TEXT NOT NULL, `tracePath` TEXT NOT NULL, `traceModelVersion` TEXT NOT NULL, `traceNotificationId` INTEGER, `editedManually` INTEGER NOT NULL)",
        )
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ledger_entries_sortId` ON `ledger_entries` (`sortId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_timestamp` ON `ledger_entries` (`timestamp`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `ledger_stats` (`id` INTEGER NOT NULL, `entryCount` INTEGER NOT NULL, `firstTimestamp` INTEGER, `lastTimestamp` INTEGER, PRIMARY KEY(`id`))")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sortId` INTEGER NOT NULL, `amount` REAL NOT NULL, `direction` TEXT NOT NULL, `channel` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `confidenceScore` REAL NOT NULL, `categoryHint` TEXT NOT NULL, `note` TEXT, `rawSource` TEXT NOT NULL, `parsePath` TEXT NOT NULL, `modelVersion` TEXT NOT NULL, `reason` TEXT NOT NULL, `traceNotificationId` INTEGER, `isFalsePositive` INTEGER NOT NULL, `editedManually` INTEGER NOT NULL)",
        )
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_entries_sortId` ON `pending_entries` (`sortId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_entries_isFalsePositive` ON `pending_entries` (`isFalsePositive`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `pending_stats` (`id` INTEGER NOT NULL, `activeCount` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `model_training_samples` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `notificationId` INTEGER, `pendingEntryId` INTEGER, `ledgerEntryId` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `packageName` TEXT NOT NULL, `rawTitle` TEXT, `rawText` TEXT, `rawPostedAt` INTEGER NOT NULL, `sourceApp` TEXT NOT NULL, `modelVersion` TEXT NOT NULL, `parsePath` TEXT NOT NULL, `amount` REAL NOT NULL, `direction` TEXT NOT NULL, `channel` TEXT NOT NULL, `candidateTimestamp` INTEGER NOT NULL, `candidateCategory` TEXT NOT NULL, `candidateNote` TEXT, `confidenceScore` REAL NOT NULL, `confidenceLevel` TEXT NOT NULL, `confidenceReason` TEXT NOT NULL, `featureSnapshotJson` TEXT NOT NULL, `routeDestination` TEXT NOT NULL, `processStatus` TEXT NOT NULL, `feedbackLabel` TEXT NOT NULL, `feedbackSource` TEXT, `feedbackAt` INTEGER, `editedManually` INTEGER NOT NULL, `feedbackNote` TEXT, `correctedAmount` REAL, `correctedDirection` TEXT, `correctedChannel` TEXT, `correctedTimestamp` INTEGER, `correctedCategory` TEXT, `correctedNote` TEXT, `changedFieldsJson` TEXT, `uploadState` TEXT NOT NULL, `uploadedAt` INTEGER, `lastUploadAttemptAt` INTEGER)",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_model_training_samples_notificationId` ON `model_training_samples` (`notificationId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_model_training_samples_pendingEntryId` ON `model_training_samples` (`pendingEntryId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_model_training_samples_ledgerEntryId` ON `model_training_samples` (`ledgerEntryId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_model_training_samples_feedbackLabel` ON `model_training_samples` (`feedbackLabel`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_model_training_samples_uploadState` ON `model_training_samples` (`uploadState`)")
    }

    /**
     * 写入 v9 时代可能存在的脏数据。
     *
     * 参数说明：
     * - connection：测试辅助工具创建出来的旧版 SQLite 连接。
     */
    private fun seedVersion9DirtyData(connection: SupportSQLiteDatabase) {
        connection.execSQL(
            "INSERT INTO ledger_entries(id, sortId, amount, direction, channel, timestamp, category, note, traceSource, tracePath, traceModelVersion, traceNotificationId, editedManually) VALUES(1, 101, -125.5, 'EXPENSE', '截图', 1000, '餐饮', NULL, 'page-capture', 'page-capture:test', 'v1', NULL, 0)",
        )
        connection.execSQL(
            "INSERT INTO ledger_entries(id, sortId, amount, direction, channel, timestamp, category, note, traceSource, tracePath, traceModelVersion, traceNotificationId, editedManually) VALUES(2, 102, 20.0, 'INCOME', '截图', 3000, '退款', NULL, 'page-capture', 'page-capture:test', 'v1', NULL, 0)",
        )
        connection.execSQL("INSERT INTO ledger_stats(id, entryCount, firstTimestamp, lastTimestamp) VALUES(1, 99, 999999, 999999)")
        connection.execSQL(
            "INSERT INTO pending_entries(id, sortId, amount, direction, channel, timestamp, confidenceScore, categoryHint, note, rawSource, parsePath, modelVersion, reason, traceNotificationId, isFalsePositive, editedManually) VALUES(1, 201, -8.8, 'EXPENSE', '截图', 2000, 0.8, '待分类', NULL, 'page-capture', 'page-capture:test', 'v1', '待确认', NULL, 0, 0)",
        )
        connection.execSQL(
            "INSERT INTO pending_entries(id, sortId, amount, direction, channel, timestamp, confidenceScore, categoryHint, note, rawSource, parsePath, modelVersion, reason, traceNotificationId, isFalsePositive, editedManually) VALUES(2, 202, -9.9, 'EXPENSE', '截图', 2500, 0.8, '待分类', NULL, 'page-capture', 'page-capture:test', 'v1', '误识别', NULL, 1, 0)",
        )
        connection.execSQL("INSERT INTO pending_stats(id, activeCount) VALUES(1, 99)")
    }

    /**
     * 写入 v10 时代的 2025 年错位账单。
     *
     * 参数说明：
     * - connection：测试辅助工具创建出来的 v10 SQLite 连接。
     */
    private fun seedVersion10Year2025Data(connection: SupportSQLiteDatabase) {
        connection.execSQL(
            "INSERT INTO ledger_entries(id, sortId, amount, direction, channel, timestamp, category, note, traceSource, tracePath, traceModelVersion, traceNotificationId, editedManually) VALUES(10, 1001, 13.2, 'EXPENSE', '图片导入', ${epochMillisOf(2025, 5, 15, 11, 20)}, '餐饮', NULL, 'image-import', 'cloud', 'v1', NULL, 0)",
        )
        connection.execSQL(
            "INSERT INTO ledger_entries(id, sortId, amount, direction, channel, timestamp, category, note, traceSource, tracePath, traceModelVersion, traceNotificationId, editedManually) VALUES(11, 1002, 99.0, 'INCOME', '图片导入', ${epochMillisOf(2026, 6, 1, 8, 30)}, '工资', NULL, 'image-import', 'cloud', 'v1', NULL, 0)",
        )
        connection.execSQL("INSERT INTO ledger_stats(id, entryCount, firstTimestamp, lastTimestamp) VALUES(1, 99, 1, 2)")
        connection.execSQL(
            "INSERT INTO pending_entries(id, sortId, amount, direction, channel, timestamp, confidenceScore, categoryHint, note, rawSource, parsePath, modelVersion, reason, traceNotificationId, isFalsePositive, editedManually) VALUES(20, 2001, 8.8, 'EXPENSE', '图片导入', ${epochMillisOf(2025, 5, 16, 9, 5)}, 0.8, '餐饮', NULL, 'image-import', 'cloud', 'v1', '待确认', NULL, 0, 0)",
        )
        connection.execSQL(
            "INSERT INTO pending_entries(id, sortId, amount, direction, channel, timestamp, confidenceScore, categoryHint, note, rawSource, parsePath, modelVersion, reason, traceNotificationId, isFalsePositive, editedManually) VALUES(21, 2002, 9.9, 'EXPENSE', '图片导入', ${epochMillisOf(2025, 5, 16, 9, 10)}, 0.8, '餐饮', NULL, 'image-import', 'cloud', 'v1', '误识别', NULL, 1, 0)",
        )
        connection.execSQL("INSERT INTO pending_stats(id, activeCount) VALUES(1, 99)")
    }

    /**
     * 断言账本金额已经被迁移为正数。
     *
     * 参数说明：
     * - connection：迁移后的数据库连接。
     * - id：待检查账本记录主键。
     * - expectedAmount：期望金额。
     */
    private fun assertLedgerAmount(
        connection: SupportSQLiteDatabase,
        id: Long,
        expectedAmount: Double,
    ) {
        connection.query("SELECT amount FROM ledger_entries WHERE id = ?", arrayOf(id)).use { cursor ->
            cursor.moveToFirst()
            assertEquals(expectedAmount, cursor.getDouble(0), 0.0)
        }
    }

    /**
     * 断言待确认金额已经被迁移为正数。
     *
     * 参数说明：
     * - connection：迁移后的数据库连接。
     * - id：待检查待确认记录主键。
     * - expectedAmount：期望金额。
     */
    private fun assertPendingAmount(
        connection: SupportSQLiteDatabase,
        id: Long,
        expectedAmount: Double,
    ) {
        connection.query("SELECT amount FROM pending_entries WHERE id = ?", arrayOf(id)).use { cursor ->
            cursor.moveToFirst()
            assertEquals(expectedAmount, cursor.getDouble(0), 0.0)
        }
    }

    /**
     * 断言账本时间戳已经按迁移预期更新。
     *
     * 参数说明：
     * - connection：迁移后的数据库连接。
     * - id：待检查账本记录主键。
     * - expectedTimestamp：期望时间戳。
     */
    private fun assertLedgerTimestamp(
        connection: SupportSQLiteDatabase,
        id: Long,
        expectedTimestamp: Long,
    ) {
        connection.query("SELECT timestamp FROM ledger_entries WHERE id = ?", arrayOf(id)).use { cursor ->
            cursor.moveToFirst()
            assertEquals(expectedTimestamp, cursor.getLong(0))
        }
    }

    /**
     * 断言待确认时间戳已经按迁移预期更新。
     *
     * 参数说明：
     * - connection：迁移后的数据库连接。
     * - id：待检查待确认记录主键。
     * - expectedTimestamp：期望时间戳。
     */
    private fun assertPendingTimestamp(
        connection: SupportSQLiteDatabase,
        id: Long,
        expectedTimestamp: Long,
    ) {
        connection.query("SELECT timestamp FROM pending_entries WHERE id = ?", arrayOf(id)).use { cursor ->
            cursor.moveToFirst()
            assertEquals(expectedTimestamp, cursor.getLong(0))
        }
    }

    /**
     * 断言账本统计缓存已经按真实流水重建。
     *
     * 参数说明：
     * - connection：迁移后的数据库连接。
     */
    private fun assertLedgerStats(
        connection: SupportSQLiteDatabase,
        expectedCount: Int,
        expectedFirstTimestamp: Long,
        expectedLastTimestamp: Long,
    ) {
        connection.query("SELECT entryCount, firstTimestamp, lastTimestamp FROM ledger_stats WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(expectedCount, cursor.getLong(0).toInt())
            assertEquals(expectedFirstTimestamp, cursor.getLong(1))
            assertEquals(expectedLastTimestamp, cursor.getLong(2))
        }
    }

    /**
     * 断言待确认角标缓存已经按有效待确认重建。
     *
     * 参数说明：
     * - connection：迁移后的数据库连接。
     */
    private fun assertPendingStats(connection: SupportSQLiteDatabase) {
        connection.query("SELECT activeCount FROM pending_stats WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getLong(0).toInt())
        }
    }

    private companion object {
        private const val TEST_DB = "autobook-migration-test"
    }
}

/**
 * 构造本地时区毫秒时间戳。
 *
 * 参数说明：
 * - year：年份。
 * - month：月份。
 * - day：日期。
 * - hour：小时。
 * - minute：分钟。
 */
private fun epochMillisOf(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
): Long {
    return LocalDateTime.of(year, month, day, hour, minute)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
