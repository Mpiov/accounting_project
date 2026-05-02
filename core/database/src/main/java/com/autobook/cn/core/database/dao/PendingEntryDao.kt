package com.autobook.cn.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.autobook.cn.core.database.entity.PendingEntryEntity
import kotlinx.coroutines.flow.Flow

/** 待确认记录的 Room 访问接口。 */
@Dao
interface PendingEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PendingEntryEntity): Long

    @Query("SELECT * FROM pending_entries WHERE isFalsePositive = 0 ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<PendingEntryEntity>>

    /**
     * 用途说明：
     * - 只观察有效待确认记录数量，服务于底部导航徽标等轻量场景。
     */
    @Query("SELECT COUNT(*) FROM pending_entries WHERE isFalsePositive = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM pending_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PendingEntryEntity?

    @Update
    suspend fun update(entry: PendingEntryEntity)

    @Query("DELETE FROM pending_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
