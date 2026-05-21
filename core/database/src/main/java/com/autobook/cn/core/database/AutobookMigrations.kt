package com.autobook.cn.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * 轻松记数据库迁移集合。
 *
 * 用途说明：
 * - 集中维护已经上线版本之间的 Room 数据迁移，保证用户升级 App 时本地账本不会被清空。
 * - 每个迁移都必须保持幂等思路：只修复确定错误的数据形态，不猜测用户真实语义。
 */
object AutobookMigrations {
    /**
     * 9 -> 10：修复截图导入历史金额符号，并重建轻量统计缓存。
     *
     * 用途说明：
     * - 历史截图导入可能把金额以负数写入数据库，而账本语义已经统一为“金额恒为正，收支由 direction 表达”。
     * - 迁移只把负数金额转成正数，并用真实明细重建统计缓存，不修改方向、分类、备注和时间。
     */
    val MIGRATION_9_10: Migration = object : Migration(9, 10) {
        /**
         * 执行 9 -> 10 的一次性数据自愈。
         *
         * 参数说明：
         * - connection：Room 提供的 SQLite 连接，迁移期间用于直接执行 SQL。
         */
        override fun migrate(connection: SQLiteConnection) {
            // 账本与待确认金额统一转正，避免历史负数在首页、图表或导出中再次抵消统计。
            connection.execSQL("UPDATE ledger_entries SET amount = ABS(amount) WHERE amount < 0")
            connection.execSQL("UPDATE pending_entries SET amount = ABS(amount) WHERE amount < 0")

            // 用真实账本明细重建设置页轻量统计缓存，修复旧版本缓存缺失或计数不准的问题。
            connection.execSQL(
                """
                INSERT OR REPLACE INTO ledger_stats(id, entryCount, firstTimestamp, lastTimestamp)
                SELECT 1, COUNT(*), MIN(timestamp), MAX(timestamp)
                FROM ledger_entries
                """.trimIndent(),
            )

            // 用当前有效待确认明细重建角标缓存，避免旧版本缓存和真实待确认列表不一致。
            connection.execSQL(
                """
                INSERT OR REPLACE INTO pending_stats(id, activeCount)
                SELECT 1, COUNT(*)
                FROM pending_entries
                WHERE isFalsePositive = 0
                """.trimIndent(),
            )
        }
    }

    /**
     * 10 -> 11：把 2025 年账单整体平移到 2026 年。
     *
     * 用途说明：
     * - 旧版截图识别在图片没有年份时，可能把本应属于 2026 年的账单落成 2025 年。
     * - 按产品决策，直接把正式账本和待确认表中本地年份为 2025 的交易时间加一年。
     * - 迁移只修改时间戳，不修改金额、方向、分类、备注和链路追踪字段。
     */
    val MIGRATION_10_11: Migration = object : Migration(10, 11) {
        /**
         * 执行 10 -> 11 的一次性年份自愈。
         *
         * 参数说明：
         * - connection：Room 提供的 SQLite 连接，迁移期间用于直接执行 SQL。
         */
        override fun migrate(connection: SQLiteConnection) {
            // SQLite 的 strftime 按本地时区判断年份，保证和 App 首页按系统本地时区统计的口径一致。
            connection.execSQL(
                """
                UPDATE ledger_entries
                SET timestamp = CAST(strftime('%s', timestamp / 1000, 'unixepoch', 'localtime', '+1 year', 'utc') AS INTEGER) * 1000
                WHERE strftime('%Y', timestamp / 1000, 'unixepoch', 'localtime') = '2025'
                """.trimIndent(),
            )
            connection.execSQL(
                """
                UPDATE pending_entries
                SET timestamp = CAST(strftime('%s', timestamp / 1000, 'unixepoch', 'localtime', '+1 year', 'utc') AS INTEGER) * 1000
                WHERE strftime('%Y', timestamp / 1000, 'unixepoch', 'localtime') = '2025'
                """.trimIndent(),
            )

            // 时间修正后重建账本统计缓存，避免设置页记账天数和首末账单时间继续使用旧年份。
            connection.execSQL(
                """
                INSERT OR REPLACE INTO ledger_stats(id, entryCount, firstTimestamp, lastTimestamp)
                SELECT 1, COUNT(*), MIN(timestamp), MAX(timestamp)
                FROM ledger_entries
                """.trimIndent(),
            )

            // 迁移期间顺手重建待确认角标缓存，保证升级后首页和底部徽标一致。
            connection.execSQL(
                """
                INSERT OR REPLACE INTO pending_stats(id, activeCount)
                SELECT 1, COUNT(*)
                FROM pending_entries
                WHERE isFalsePositive = 0
                """.trimIndent(),
            )
        }
    }

    /**
     * 暴露给 Room 构建器的完整迁移列表。
     *
     * 用途说明：
     * - 后续新增迁移时追加到这里，避免数据库模块遗漏某段升级路径。
     */
    val ALL: Array<Migration> = arrayOf(MIGRATION_9_10, MIGRATION_10_11)
}
