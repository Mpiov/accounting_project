package com.autobook.cn.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.autobook.cn.core.database.entity.PendingEntryEntity
import com.autobook.cn.core.database.entity.PendingStatsEntity
import kotlinx.coroutines.flow.Flow

/** 待确认记录的 Room 访问接口。 */
@Dao
interface PendingEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PendingEntryEntity): Long

    /**
     * 用途说明：
     * - 观察待确认记录，并保持“最新进入待处理队列的排在前面”。
     * - 待确认最终不一定会入账，因此这里直接按待确认表自增 `id` 排序，避免交易时间影响处理顺序。
     */
    @Query("SELECT * FROM pending_entries WHERE isFalsePositive = 0 ORDER BY sortId DESC")
    fun observeAll(): Flow<List<PendingEntryEntity>>

    /**
     * 用途说明：
     * - 观察有效待确认数量缓存，服务于底部导航徽标等轻量场景。
     */
    @Query("SELECT * FROM pending_stats WHERE id = 1 LIMIT 1")
    fun observeStats(): Flow<PendingStatsEntity?>

    /**
     * 用途说明：
     * - 读取待确认统计缓存，仓储在新增、删除和误识别标记时维护该值。
     */
    @Query("SELECT * FROM pending_stats WHERE id = 1 LIMIT 1")
    suspend fun getStats(): PendingStatsEntity?

    /**
     * 用途说明：
     * - 覆盖写入待确认统计缓存；缓存只有固定主键 `1` 一行。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStats(stats: PendingStatsEntity)

    @Query("SELECT * FROM pending_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PendingEntryEntity?

    @Update
    suspend fun update(entry: PendingEntryEntity)

    @Query("DELETE FROM pending_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 用途说明：
     * - 云端已经确认接收误识别反馈后，清理本地隐藏的误识别待确认残留。
     *
     * 参数说明：
     * - uploadedState：训练样本已上传状态值，只有对应样本已同步才允许删除本地误识别行。
     */
    @Query(
        """
        DELETE FROM pending_entries
        WHERE isFalsePositive = 1
          AND id IN (
              SELECT pending_entries.id
              FROM pending_entries
              INNER JOIN model_training_samples
                      ON model_training_samples.pendingEntryId = pending_entries.id
              WHERE model_training_samples.uploadState = :uploadedState
          )
        """,
    )
    suspend fun purgeUploadedFalsePositives(uploadedState: String): Int
}
