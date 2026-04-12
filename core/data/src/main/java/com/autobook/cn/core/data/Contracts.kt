package com.autobook.cn.core.data

import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessOutcome
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.PendingEntry
import com.autobook.cn.core.model.TransactionCandidate
import kotlinx.coroutines.flow.Flow

interface NotificationIngestor {
    suspend fun ingest(event: NotificationEvent): OperationResult<NotificationProcessOutcome>
}

interface CaptureCoordinator {
    suspend fun requestCapture(reason: String): OperationResult<Unit>
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
}

interface PendingRepository {
    fun observeEntries(): Flow<List<PendingEntry>>

    suspend fun add(entry: PendingEntry): Long

    suspend fun confirm(id: Long): PendingEntry?

    suspend fun ignore(id: Long)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setAutoAccountingEnabled(enabled: Boolean)

    suspend fun setCloudAnalysisEnabled(enabled: Boolean)
}
