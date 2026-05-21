package com.autobook.cn.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.autobook.cn.core.database.entity.NotificationEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * 原始通知及其处理状态的 Room 访问接口。
 *
 * 用途说明：
 * - 负责插入通知原文，并更新处理状态、错误信息与截图引用。
 * - 通知级精确哈希去重已移除，因此这里不再提供按 `dedupeKey` 查询重复通知的接口。
 */
@Dao
interface NotificationEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: NotificationEventEntity): Long

    @Query(
        """
        UPDATE notification_events
        SET processStatus = :processStatus,
            lastError = :lastError,
            retryCount = :retryCount,
            screenshotRef = :screenshotRef
        WHERE id = :id
        """,
    )
    suspend fun updateProcessingState(
        id: Long,
        processStatus: String,
        lastError: String?,
        retryCount: Int,
        screenshotRef: String?,
    )

    @Query("SELECT * FROM notification_events ORDER BY postedAt DESC")
    fun observeAll(): Flow<List<NotificationEventEntity>>

    /**
     * 用途说明：
     * - 观察指定通知 ID 集合的原始通知记录。
     * - Room 会按主键查询，待确认页只传当前列表关联的少量 ID，避免全量通知历史映射拖慢首屏。
     *
     * 参数说明：
     * - ids：通知事件主键集合。
     */
    @Query("SELECT * FROM notification_events WHERE id IN (:ids)")
    fun observeByIds(ids: Set<Long>): Flow<List<NotificationEventEntity>>
}
