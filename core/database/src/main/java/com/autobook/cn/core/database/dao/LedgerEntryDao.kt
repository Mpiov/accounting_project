package com.autobook.cn.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.autobook.cn.core.database.entity.LedgerEntryEntity
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

    @Query("SELECT * FROM ledger_entries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): LedgerEntryEntity?

    @Update
    suspend fun update(entry: LedgerEntryEntity)

    @Query("DELETE FROM ledger_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        SELECT * FROM ledger_entries
        -- 通知自动记账链路使用“同方向 + 同金额 + 极短时间窗”做第一层账本级判重；
        -- 不再要求商户完全一致，避免支付宝通知与银行卡动账提醒因为商户提取不同而漏判。
        -- orderId 如存在，仍然作为更强的辅助信号参与判定。
        WHERE (
                amount = :amount
                AND direction = :direction
                AND timestamp BETWEEN :windowStart AND :windowEnd
           )
           OR (
                :orderId IS NOT NULL
                AND orderId = :orderId
                AND amount = :amount
                AND direction = :direction
           )
        ORDER BY timestamp DESC
        LIMIT 1
        """,
    )
    suspend fun findPotentialDuplicate(
        orderId: String?,
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
        ORDER BY timestamp DESC
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
        ORDER BY timestamp DESC
        LIMIT 1
        """,
    )
    suspend fun findPotentialImageImportDuplicateWithoutTimestamp(
        amount: Double,
        direction: String,
    ): LedgerEntryEntity?
}
