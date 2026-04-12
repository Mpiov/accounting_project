package com.autobook.cn.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.autobook.cn.core.database.entity.NotificationEventEntity
import kotlinx.coroutines.flow.Flow

/** 原始通知及其处理状态的 Room 访问接口。 */
@Dao
interface NotificationEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: NotificationEventEntity): Long

    @Query(
        """
        SELECT * FROM notification_events
        WHERE dedupeKey = :dedupeKey AND id != :excludeId
        ORDER BY postedAt DESC
        LIMIT 1
        """,
    )
    suspend fun findLatestDuplicate(
        dedupeKey: String,
        excludeId: Long,
    ): NotificationEventEntity?

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
