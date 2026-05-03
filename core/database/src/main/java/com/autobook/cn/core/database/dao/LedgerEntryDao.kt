package com.autobook.cn.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.autobook.cn.core.database.entity.LedgerEntryEntity
import com.autobook.cn.core.database.entity.LedgerStatsEntity
import kotlinx.coroutines.flow.Flow

/**
 * 已入账记录的 Room 访问接口。
 *
 * 用途说明：
 * - 提供账本记录的增删改查。
 * - 也负责通知自动记账链路的账本级判重查询。
 */
@Dao
interface LedgerEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LedgerEntryEntity): Long

    /**
     * 用途说明：
     * - 观察全部已入账记录，并按最终入账顺序展示“最新在前”。
     * - `timestamp` 是交易发生时间，用户补记或修改时可能早于现有记录；最近记录更应该跟随自增 `id`。
     */
    @Query("SELECT * FROM ledger_entries ORDER BY sortId DESC")
    fun observeAll(): Flow<List<LedgerEntryEntity>>

    /**
     * 用途说明：
     * - 观察账本轻量统计缓存，给设置页等入口使用，避免订阅完整记录列表或执行高频聚合扫描。
     */
    @Query("SELECT * FROM ledger_stats WHERE id = 1 LIMIT 1")
    fun observeStats(): Flow<LedgerStatsEntity?>

    /**
     * 用途说明：
     * - 读取当前统计缓存，仓储在新增、删除和编辑边界时间时维护该值。
     */
    @Query("SELECT * FROM ledger_stats WHERE id = 1 LIMIT 1")
    suspend fun getStats(): LedgerStatsEntity?

    /**
     * 用途说明：
     * - 覆盖写入统计缓存；缓存只有固定主键 `1` 一行。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStats(stats: LedgerStatsEntity)

    /**
     * 用途说明：
     * - 在删除或修改了边界记录时，重新读取剩余账单的最早交易时间。
     */
    @Query("SELECT MIN(timestamp) FROM ledger_entries")
    suspend fun getFirstTimestamp(): Long?

    /**
     * 用途说明：
     * - 在删除或修改了边界记录时，重新读取剩余账单的最晚交易时间。
     */
    @Query("SELECT MAX(timestamp) FROM ledger_entries")
    suspend fun getLastTimestamp(): Long?

    @Query("SELECT * FROM ledger_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): LedgerEntryEntity?

    @Update
    suspend fun update(entry: LedgerEntryEntity)

    @Query("DELETE FROM ledger_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        SELECT * FROM ledger_entries
        -- 账本记录已经不再保存商户、对方或订单号，判重只依赖核心记账字段：
        -- 同方向、同金额、极短时间窗内的记录视为疑似重复，后续交给待确认页面处理。
        WHERE amount = :amount
          AND direction = :direction
          AND timestamp BETWEEN :windowStart AND :windowEnd
        ORDER BY timestamp DESC, sortId DESC
        LIMIT 1
        """,
    )
    suspend fun findPotentialDuplicate(
        amount: Double,
        direction: String,
        windowStart: Long,
        windowEnd: Long,
    ): LedgerEntryEntity?

    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE :windowStart IS NOT NULL
          AND timestamp BETWEEN :windowStart AND :windowEnd
          AND amount = :amount
          AND direction = :direction
        ORDER BY timestamp DESC, sortId DESC
        LIMIT 1
        """,
    )
    suspend fun findPotentialImageImportDuplicate(
        windowStart: Long?,
        windowEnd: Long?,
        amount: Double,
        direction: String,
    ): LedgerEntryEntity?

    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE amount = :amount
          AND direction = :direction
        ORDER BY timestamp DESC, sortId DESC
        LIMIT 1
        """,
    )
    suspend fun findPotentialImageImportDuplicateWithoutTimestamp(
        amount: Double,
        direction: String,
    ): LedgerEntryEntity?
}
