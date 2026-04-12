package com.autobook.cn.core.data

import com.autobook.cn.core.model.CaptureSession
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessOutcome
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.PendingEntry
import com.autobook.cn.core.model.TransactionCandidate
import kotlinx.coroutines.flow.Flow

/** 通知监听服务写入并处理通知的统一入口。 */
interface NotificationIngestor {
    suspend fun ingest(event: NotificationEvent): OperationResult<NotificationProcessOutcome>
}

/** 协调截图授权与截图执行，供 OCR 回退链路使用。 */
interface CaptureCoordinator {
    suspend fun requestAuthorization(reason: String): OperationResult<CaptureSession>
    suspend fun capture(sessionId: String): OperationResult<String>
    suspend fun cancel(sessionId: String)
    suspend fun reportFailure(sessionId: String, message: String)
}

/** OCR 能力抽象，便于后续替换真实实现。 */
interface OcrEngine {
    suspend fun extractText(imageRef: String): OperationResult<String>
}

/** 从原始通知中提取标准化交易候选。 */
interface TransactionExtractor {
    suspend fun extract(event: NotificationEvent): OperationResult<TransactionCandidate>
}

/** 在本地解析、OCR 回退与云端分析之间做路由决策。 */
interface ModelRouter {
    suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate>
}

/** 账本数据的持久化与导出接口。 */
interface LedgerRepository {
    fun observeEntries(): Flow<List<LedgerEntry>>
    suspend fun add(entry: LedgerEntry): Long
    suspend fun update(entry: LedgerEntry)
    suspend fun delete(id: Long)
    suspend fun getById(id: Long): LedgerEntry?
    suspend fun findPotentialDuplicate(candidate: TransactionCandidate): LedgerEntry?
    suspend fun exportCsv(): String
}

/** 待确认队列的持久化接口。 */
interface PendingRepository {
    fun observeEntries(): Flow<List<PendingEntry>>
    suspend fun add(entry: PendingEntry): Long
    suspend fun update(entry: PendingEntry)
    suspend fun getById(id: Long): PendingEntry?
    suspend fun confirm(id: Long): PendingEntry?
    suspend fun ignore(id: Long)
    suspend fun markFalsePositive(id: Long)
}

/** 驱动权限、能力开关与留存策略的设置存储接口。 */
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setAutoAccountingEnabled(enabled: Boolean)
    suspend fun setCloudAnalysisEnabled(enabled: Boolean)
    suspend fun setCaptureEnabled(enabled: Boolean)
    suspend fun setOcrEnabled(enabled: Boolean)
    suspend fun setNotificationRetentionDays(days: Int)
    suspend fun setScreenshotRetentionDays(days: Int)
    suspend fun setSourceEnabled(source: NotificationSourceApp, enabled: Boolean)
}
