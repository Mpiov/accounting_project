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

interface NotificationIngestor {
    suspend fun ingest(event: NotificationEvent): OperationResult<NotificationProcessOutcome>
}

interface CaptureCoordinator {
    suspend fun requestAuthorization(reason: String): OperationResult<CaptureSession>
    suspend fun capture(sessionId: String): OperationResult<String>
    suspend fun cancel(sessionId: String)
    suspend fun reportFailure(sessionId: String, message: String)
}

interface OcrEngine {
    suspend fun extractText(imageRef: String): OperationResult<String>
}

interface TransactionExtractor {
    suspend fun extract(event: NotificationEvent): OperationResult<TransactionCandidate>
}

interface ModelRouter {
    suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate>
}

interface LedgerRepository {
    fun observeEntries(): Flow<List<LedgerEntry>>
    suspend fun add(entry: LedgerEntry): Long
    suspend fun update(entry: LedgerEntry)
    suspend fun delete(id: Long)
    suspend fun getById(id: Long): LedgerEntry?
    suspend fun findPotentialDuplicate(candidate: TransactionCandidate): LedgerEntry?
    suspend fun exportCsv(): String
}

interface PendingRepository {
    fun observeEntries(): Flow<List<PendingEntry>>
    suspend fun add(entry: PendingEntry): Long
    suspend fun update(entry: PendingEntry)
    suspend fun getById(id: Long): PendingEntry?
    suspend fun confirm(id: Long): PendingEntry?
    suspend fun ignore(id: Long)
    suspend fun markFalsePositive(id: Long)
}

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
