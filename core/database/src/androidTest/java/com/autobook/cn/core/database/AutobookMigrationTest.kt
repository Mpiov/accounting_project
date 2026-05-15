package com.autobook.cn.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
                assertLedgerStats(connection)
                assertPendingStats(connection)
            }
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
     * 断言账本统计缓存已经按真实流水重建。
     *
     * 参数说明：
     * - connection：迁移后的数据库连接。
     */
    private fun assertLedgerStats(connection: SupportSQLiteDatabase) {
        connection.query("SELECT entryCount, firstTimestamp, lastTimestamp FROM ledger_stats WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(2, cursor.getLong(0).toInt())
            assertEquals(1000L, cursor.getLong(1))
            assertEquals(3000L, cursor.getLong(2))
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
