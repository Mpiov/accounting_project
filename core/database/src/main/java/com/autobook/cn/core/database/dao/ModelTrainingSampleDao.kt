package com.autobook.cn.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.autobook.cn.core.database.entity.ModelTrainingSampleEntity

/**
 * 置信度模型训练样本的 Room 访问接口。
 *
 * 用途说明：
 * - 记录解析阶段的模型输入输出。
 * - 在用户确认、编辑、删除或标记误报后回写反馈标签。
 */
@Dao
interface ModelTrainingSampleDao {
    /**
     * 用途说明：
     * - 插入一条新的模型训练样本。
     *
     * 参数说明：
     * - sample：待保存的训练样本实体。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: ModelTrainingSampleEntity): Long

    /**
     * 用途说明：
     * - 导出全部训练样本，离线训练时按创建顺序回放。
     */
    @Query("SELECT * FROM model_training_samples ORDER BY createdAt ASC, id ASC")
    suspend fun getAllForExport(): List<ModelTrainingSampleEntity>

    /**
     * 用途说明：
     * - 读取仍需上传到云端样本池的本地样本，避免每次同步都发送全量历史数据。
     *
     * 参数说明：
     * - uploadedState：已完成同步的状态值，本查询会排除该状态。
     * - uploadableLabels：允许上传的有效反馈标签，待确认、重复、原文、空标签和未知标签会被排除。
     * - limit：单次上传批次上限，和后端单批 200 条限制保持一致。
     */
    @Query(
        """
        SELECT * FROM model_training_samples
        WHERE uploadState <> :uploadedState
          AND feedbackLabel IN (:uploadableLabels)
        ORDER BY updatedAt ASC, id ASC
        LIMIT :limit
        """,
    )
    suspend fun getPendingUploadBatch(
        uploadedState: String,
        uploadableLabels: List<String>,
        limit: Int,
    ): List<ModelTrainingSampleEntity>

    /**
     * 用途说明：
     * - 读取待确认项对应的训练样本，用于计算用户编辑前后的字段差异。
     *
     * 参数说明：
     * - pendingEntryId：待确认记录主键。
     */
    @Query("SELECT * FROM model_training_samples WHERE pendingEntryId = :pendingEntryId LIMIT 1")
    suspend fun getByPendingEntryId(pendingEntryId: Long): ModelTrainingSampleEntity?

    /**
     * 用途说明：
     * - 读取账本记录对应的训练样本，用于在用户编辑已入账记录时补充纠错快照。
     *
     * 参数说明：
     * - ledgerEntryId：账本记录主键。
     * - notificationId：来源通知主键，账本主键未回填时作为兜底。
     */
    @Query(
        """
        SELECT * FROM model_training_samples
        WHERE ledgerEntryId = :ledgerEntryId
           OR (:notificationId IS NOT NULL AND notificationId = :notificationId)
        ORDER BY id DESC
        LIMIT 1
        """,
    )
    suspend fun getByLedgerTrace(
        ledgerEntryId: Long,
        notificationId: Long?,
    ): ModelTrainingSampleEntity?

    /**
     * 用途说明：
     * - 待确认项最终写入账本后，把账本主键补回样本。
     *
     * 参数说明：
     * - notificationId：来源通知主键。
     * - ledgerEntryId：最终账本记录主键。
     * - updatedAt：更新时间戳。
     */
    @Query(
        """
        UPDATE model_training_samples
        SET ledgerEntryId = :ledgerEntryId,
            updatedAt = :updatedAt,
            uploadState = :uploadState
        WHERE notificationId = :notificationId
        """,
    )
    suspend fun linkLedgerEntryByNotificationId(
        notificationId: Long,
        ledgerEntryId: Long,
        updatedAt: Long,
        uploadState: String,
    )

    /**
     * 用途说明：
     * - 根据待确认主键回写用户反馈标签。
     *
     * 参数说明：
     * - pendingEntryId：待确认记录主键。
     * - feedbackLabel：反馈标签。
     * - feedbackSource：反馈来源。
     * - feedbackAt：反馈时间戳。
     * - editedManually：用户是否编辑过候选内容。
     * - feedbackNote：反馈补充说明。
     * - correctedAmount：用户最终确认的金额。
     * - correctedDirection：用户最终确认的收支方向。
     * - correctedChannel：用户最终确认的渠道。
     * - correctedTimestamp：用户最终确认的交易时间。
     * - correctedCategory：用户最终确认的分类。
     * - correctedNote：用户最终确认的备注。
     * - changedFieldsJson：被用户改动的字段名 JSON 数组。
     */
    @Query(
        """
        UPDATE model_training_samples
        SET feedbackLabel = :feedbackLabel,
            feedbackSource = :feedbackSource,
            feedbackAt = :feedbackAt,
            updatedAt = :feedbackAt,
            editedManually = :editedManually,
            feedbackNote = :feedbackNote,
            correctedAmount = :correctedAmount,
            correctedDirection = :correctedDirection,
            correctedChannel = :correctedChannel,
            correctedTimestamp = :correctedTimestamp,
            correctedCategory = :correctedCategory,
            correctedNote = :correctedNote,
            changedFieldsJson = :changedFieldsJson,
            uploadState = :uploadState
        WHERE pendingEntryId = :pendingEntryId
        """,
    )
    suspend fun updateFeedbackByPendingEntryId(
        pendingEntryId: Long,
        feedbackLabel: String,
        feedbackSource: String,
        feedbackAt: Long,
        editedManually: Boolean,
        feedbackNote: String?,
        correctedAmount: Double?,
        correctedDirection: String?,
        correctedChannel: String?,
        correctedTimestamp: Long?,
        correctedCategory: String?,
        correctedNote: String?,
        changedFieldsJson: String?,
        uploadState: String,
    )

    /**
     * 用途说明：
     * - 根据账本主键或通知主键回写账本侧反馈。
     *
     * 参数说明：
     * - ledgerEntryId：账本记录主键。
     * - notificationId：来源通知主键，可能为空。
     * - feedbackLabel：反馈标签。
     * - feedbackSource：反馈来源。
     * - feedbackAt：反馈时间戳。
     * - editedManually：用户是否编辑过账本内容。
     * - feedbackNote：反馈补充说明。
     * - correctedAmount：用户最终确认的金额。
     * - correctedDirection：用户最终确认的收支方向。
     * - correctedChannel：用户最终确认的渠道。
     * - correctedTimestamp：用户最终确认的交易时间。
     * - correctedCategory：用户最终确认的分类。
     * - correctedNote：用户最终确认的备注。
     * - changedFieldsJson：被用户改动的字段名 JSON 数组。
     */
    @Query(
        """
        UPDATE model_training_samples
        SET feedbackLabel = :feedbackLabel,
            feedbackSource = :feedbackSource,
            feedbackAt = :feedbackAt,
            updatedAt = :feedbackAt,
            editedManually = :editedManually,
            feedbackNote = :feedbackNote,
            correctedAmount = :correctedAmount,
            correctedDirection = :correctedDirection,
            correctedChannel = :correctedChannel,
            correctedTimestamp = :correctedTimestamp,
            correctedCategory = :correctedCategory,
            correctedNote = :correctedNote,
            changedFieldsJson = :changedFieldsJson,
            uploadState = :uploadState
        WHERE ledgerEntryId = :ledgerEntryId
           OR (:notificationId IS NOT NULL AND notificationId = :notificationId)
        """,
    )
    suspend fun updateFeedbackByLedgerTrace(
        ledgerEntryId: Long,
        notificationId: Long?,
        feedbackLabel: String,
        feedbackSource: String,
        feedbackAt: Long,
        editedManually: Boolean,
        feedbackNote: String?,
        correctedAmount: Double?,
        correctedDirection: String?,
        correctedChannel: String?,
        correctedTimestamp: Long?,
        correctedCategory: String?,
        correctedNote: String?,
        changedFieldsJson: String?,
        uploadState: String,
    )

    /**
     * 用途说明：
     * - 记录本批样本已经发起过一次上传，便于后续排查同步失败和退避策略。
     *
     * 参数说明：
     * - ids：本地训练样本主键集合。
     * - attemptedAt：本次上传尝试发生的时间戳。
     */
    @Query(
        """
        UPDATE model_training_samples
        SET lastUploadAttemptAt = :attemptedAt
        WHERE id IN (:ids)
        """,
    )
    suspend fun markUploadAttempted(
        ids: List<Long>,
        attemptedAt: Long,
    )

    /**
     * 用途说明：
     * - 后端成功接收或更新样本后，把本地样本标记为已同步。
     *
     * 参数说明：
     * - ids：本地训练样本主键集合。
     * - uploadedState：已同步状态值。
     * - uploadedAt：服务端成功响应对应的本地时间戳。
     */
    @Query(
        """
        UPDATE model_training_samples
        SET uploadState = :uploadedState,
            uploadedAt = :uploadedAt,
            lastUploadAttemptAt = :uploadedAt
        WHERE id IN (:ids)
        """,
    )
    suspend fun markUploaded(
        ids: List<Long>,
        uploadedState: String,
        uploadedAt: Long,
    )
}
