package com.autobook.cn.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.autobook.cn.core.database.entity.LedgerEntryEntity
import kotlinx.coroutines.flow.Flow

/** 已入账记录的 Room 访问接口。 */
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
        -- 通知主链路仍以“同方向 + 同金额 + 同商户 + 极短时间窗”为主；
        -- orderId 如存在，只作为辅助信号，不再单独决定重复。
        WHERE (
                amount = :amount
                AND direction = :direction
                AND merchant = :merchant
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
        merchant: String,
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
