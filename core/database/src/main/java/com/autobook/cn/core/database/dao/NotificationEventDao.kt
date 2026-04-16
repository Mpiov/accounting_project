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
}
