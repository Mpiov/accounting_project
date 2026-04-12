package com.autobook.cn.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.autobook.cn.core.database.entity.PendingEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PendingEntryEntity): Long

    @Query("SELECT * FROM pending_entries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<PendingEntryEntity>>

    @Query("SELECT * FROM pending_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PendingEntryEntity?

    @Query("DELETE FROM pending_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
