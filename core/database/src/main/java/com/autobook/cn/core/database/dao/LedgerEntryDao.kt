package com.autobook.cn.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.autobook.cn.core.database.entity.LedgerEntryEntity
import kotlinx.coroutines.flow.Flow

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
        WHERE (:orderId IS NOT NULL AND orderId = :orderId)
           OR (amount = :amount AND merchant = :merchant AND timestamp BETWEEN :windowStart AND :windowEnd)
        ORDER BY timestamp DESC
        LIMIT 1
        """,
    )
    suspend fun findPotentialDuplicate(
        orderId: String?,
        amount: Double,
        merchant: String,
        windowStart: Long,
        windowEnd: Long,
    ): LedgerEntryEntity?
}
