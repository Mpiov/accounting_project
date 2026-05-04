package com.autobook.cn.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    /**
     * 用途说明：
     * - 事务化增减待确认数量缓存，避免短时间连续写入时出现“两个协程都读到旧值再覆盖写回”的丢失更新。
     * - 该方法不扫描待确认表，只维护 `pending_stats.activeCount` 这一行缓存。
     *
     * 参数说明：
     * - delta：有效待确认数量变化量，新增为 `+1`，确认/忽略/误识别为 `-1`。
     */
    @Transaction
    suspend fun applyActiveCountDelta(delta: Int) {
        ensureStatsRow()
        updateActiveCountByDelta(delta = delta)
    }

    /**
     * 用途说明：
     * - 确保待确认统计缓存存在固定主键 `1` 的行。
     * - 只在更新缓存前补空行，不读取真实待确认表。
     */
    @Query("INSERT OR IGNORE INTO pending_stats(id, activeCount) VALUES(1, 0)")
    suspend fun ensureStatsRow()

    /**
     * 用途说明：
     * - 在 SQLite 单条 `UPDATE` 中完成数量增减，数据库会串行化写入，避免并发丢失更新。
     * - 使用 `CASE` 把结果钳制到 0 以上，防止重复点击或历史异常把徽标数量减成负数。
     *
     * 参数说明：
     * - delta：有效待确认数量变化量。
     */
    @Query(
        """
        UPDATE pending_stats
        SET activeCount = CASE
            WHEN activeCount + :delta < 0 THEN 0
            ELSE activeCount + :delta
        END
        WHERE id = 1
        """,
    )
    suspend fun updateActiveCountByDelta(delta: Int)

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
