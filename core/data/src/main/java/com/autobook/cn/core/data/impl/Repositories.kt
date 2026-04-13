package com.autobook.cn.core.data.impl

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.autobook.cn.core.data.CaptureCoordinator
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.NotificationIngestor
import com.autobook.cn.core.data.OcrEngine
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.data.mapper.toDomain
import com.autobook.cn.core.data.mapper.toEntity
import com.autobook.cn.core.data.parser.enrichForProcessing
import com.autobook.cn.core.data.pipeline.TransactionProcessingPipeline
import com.autobook.cn.core.database.dao.LedgerEntryDao
import com.autobook.cn.core.database.dao.NotificationEventDao
import com.autobook.cn.core.database.dao.PendingEntryDao
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.CaptureSession
import com.autobook.cn.core.model.CaptureSessionState
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessOutcome
import com.autobook.cn.core.model.NotificationProcessStatus
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.PendingEntry
import com.autobook.cn.core.model.ProcessDestination
import com.autobook.cn.core.model.TransactionCandidate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "autobook_settings")

/** 基于 Room 的账本仓储实现。 */
@Singleton
class LedgerRepositoryImpl @Inject constructor(
    private val ledgerEntryDao: LedgerEntryDao,
) : LedgerRepository {
    override fun observeEntries(): Flow<List<LedgerEntry>> = ledgerEntryDao.observeAll().map { entries ->
        entries.map { it.toDomain() }
    }

    override suspend fun add(entry: LedgerEntry): Long = ledgerEntryDao.insert(entry.toEntity())

    override suspend fun update(entry: LedgerEntry) {
        ledgerEntryDao.update(entry.toEntity())
    }

    override suspend fun delete(id: Long) {
        ledgerEntryDao.deleteById(id)
    }

    override suspend fun getById(id: Long): LedgerEntry? = ledgerEntryDao.getById(id)?.toDomain()

    override suspend fun findPotentialDuplicate(candidate: TransactionCandidate): LedgerEntry? {
        // 订单号优先精确匹配；缺少订单号时只在极短时间窗内做同方向近似查重，避免“刚收一笔又付一笔同额”被误杀。
        val duplicate = ledgerEntryDao.findPotentialDuplicate(
            orderId = candidate.orderId,
            amount = candidate.amount,
            direction = candidate.direction.name,
            merchant = candidate.merchant,
            windowStart = candidate.timestamp - DUPLICATE_WINDOW_MS,
            windowEnd = candidate.timestamp + DUPLICATE_WINDOW_MS,
        )
        return duplicate?.toDomain()
    }

    override suspend fun exportCsv(): String {
        val rows = observeEntries().first()
        return buildString {
            // 导出时保留 trace 字段，便于后续排查记录来源。
            appendLine("id,timestamp,direction,amount,merchant,channel,category,orderId,note,traceSource")
            rows.forEach { entry ->
                appendLine(
                    listOf(
                        entry.id,
                        entry.timestamp,
                        entry.direction.name,
                        entry.amount,
                        csvEscape(entry.merchant),
                        csvEscape(entry.channel),
                        csvEscape(entry.category),
                        csvEscape(entry.orderId),
                        csvEscape(entry.note),
                        csvEscape(entry.traceSource),
                    ).joinToString(","),
                )
            }
        }
    }
}

/** 基于 Room 的待确认仓储实现。 */
@Singleton
class PendingRepositoryImpl @Inject constructor(
    private val pendingEntryDao: PendingEntryDao,
) : PendingRepository {
    override fun observeEntries(): Flow<List<PendingEntry>> = pendingEntryDao.observeAll().map { entries ->
        entries.map { it.toDomain() }
    }

    override suspend fun add(entry: PendingEntry): Long = pendingEntryDao.insert(entry.toEntity())

    override suspend fun update(entry: PendingEntry) {
        pendingEntryDao.update(entry.toEntity())
    }

    override suspend fun getById(id: Long): PendingEntry? = pendingEntryDao.getById(id)?.toDomain()

    override suspend fun confirm(id: Long): PendingEntry? {
        // 确认时直接删除待确认项，并把最后状态交给上层转成账本记录。
        val item = getById(id) ?: return null
        pendingEntryDao.deleteById(id)
        return item
    }

    override suspend fun ignore(id: Long) {
        pendingEntryDao.deleteById(id)
    }

    override suspend fun markFalsePositive(id: Long) {
        val item = getById(id) ?: return
        pendingEntryDao.update(item.copy(isFalsePositive = true).toEntity())
    }
}

/** 基于 DataStore 的设置仓储实现。 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    /** 所有设置键统一收口，避免散落字符串常量。 */
    private object Keys {
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val autoAccountingEnabled = booleanPreferencesKey("auto_accounting_enabled")
        val cloudAnalysisEnabled = booleanPreferencesKey("cloud_analysis_enabled")
        val captureEnabled = booleanPreferencesKey("capture_enabled")
        val ocrEnabled = booleanPreferencesKey("ocr_enabled")
        val notificationRetentionDays = intPreferencesKey("notification_retention_days")
        val screenshotRetentionDays = intPreferencesKey("screenshot_retention_days")
        val enabledSources = stringSetPreferencesKey("enabled_sources")
    }

    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        // 读取来源列表时过滤未知枚举值，避免旧数据导致崩溃。
        val sourceNames = preferences[Keys.enabledSources] ?: DEFAULT_SOURCES.mapTo(mutableSetOf()) { it.name }
        AppSettings(
            onboardingCompleted = preferences[Keys.onboardingCompleted] ?: false,
            autoAccountingEnabled = preferences[Keys.autoAccountingEnabled] ?: true,
            cloudAnalysisEnabled = preferences[Keys.cloudAnalysisEnabled] ?: false,
            captureEnabled = preferences[Keys.captureEnabled] ?: false,
            ocrEnabled = preferences[Keys.ocrEnabled] ?: false,
            notificationRetentionDays = preferences[Keys.notificationRetentionDays] ?: 30,
            screenshotRetentionDays = preferences[Keys.screenshotRetentionDays] ?: 7,
            enabledSources = sourceNames.mapNotNullTo(mutableSetOf()) { name ->
                NotificationSourceApp.entries.firstOrNull { it.name == name }
            }.ifEmpty { DEFAULT_SOURCES },
        )
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { it[Keys.onboardingCompleted] = completed }
    }

    override suspend fun setAutoAccountingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.autoAccountingEnabled] = enabled }
    }

    override suspend fun setCloudAnalysisEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.cloudAnalysisEnabled] = enabled }
    }

    override suspend fun setCaptureEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.captureEnabled] = enabled }
    }

    override suspend fun setOcrEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ocrEnabled] = enabled }
    }

    override suspend fun setNotificationRetentionDays(days: Int) {
        context.settingsDataStore.edit { it[Keys.notificationRetentionDays] = days.coerceIn(1, 90) }
    }

    override suspend fun setScreenshotRetentionDays(days: Int) {
        context.settingsDataStore.edit { it[Keys.screenshotRetentionDays] = days.coerceIn(1, 30) }
    }

    override suspend fun setSourceEnabled(source: NotificationSourceApp, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            val existing = preferences[Keys.enabledSources]?.toMutableSet()
                ?: DEFAULT_SOURCES.mapTo(mutableSetOf()) { it.name }
            if (enabled) {
                existing += source.name
            } else {
                existing -= source.name
            }
            preferences[Keys.enabledSources] = existing
        }
    }
}

/** 通知入库与处理流水线的总协调器。 */
@Singleton
class NotificationIngestorImpl @Inject constructor(
    private val notificationEventDao: NotificationEventDao,
    private val pipeline: TransactionProcessingPipeline,
    private val settingsRepository: SettingsRepository,
) : NotificationIngestor {
    override suspend fun ingest(event: NotificationEvent): OperationResult<NotificationProcessOutcome> {
        val enriched = event.enrichForProcessing()
        val id = notificationEventDao.insert(enriched.toEntity())
        val persisted = enriched.copy(id = id)
        val settings = settingsRepository.settings.first()

        // 用户已经关闭的来源在这里直接短路，不再继续进入后续流水线。
        if (persisted.sourceApp !in settings.enabledSources) {
            return completeSuccessfully(
                notificationId = id,
                destination = ProcessDestination.FILTERED,
                status = NotificationProcessStatus.FILTERED_OUT,
                message = "Source disabled by user settings",
                retryCount = persisted.retryCount,
                screenshotRef = persisted.screenshotRef,
            )
        }

        val duplicate = notificationEventDao.findLatestDuplicate(
            dedupeKey = persisted.dedupeKey,
            excludeId = id,
        )
        // 去重命中后直接写入重复状态，避免重复入账或重复进入待确认。
        if (duplicate != null) {
            return completeSuccessfully(
                notificationId = id,
                destination = ProcessDestination.DUPLICATE,
                status = NotificationProcessStatus.DUPLICATE_SKIPPED,
                message = "Duplicate notification skipped",
                retryCount = persisted.retryCount,
                screenshotRef = persisted.screenshotRef,
            )
        }

        notificationEventDao.updateProcessingState(
            id = id,
            processStatus = NotificationProcessStatus.PROCESSING.name,
            lastError = null,
            retryCount = persisted.retryCount,
            screenshotRef = persisted.screenshotRef,
        )

        return when (val result = pipeline.process(notificationId = id, event = persisted)) {
            is OperationResult.Success -> {
                notificationEventDao.updateProcessingState(
                    id = id,
                    processStatus = result.data.status.name,
                    lastError = result.data.message,
                    retryCount = persisted.retryCount,
                    screenshotRef = result.data.screenshotRef ?: persisted.screenshotRef,
                )
                result
            }

            is OperationResult.RetryableFailure -> {
                notificationEventDao.updateProcessingState(
                    id = id,
                    processStatus = NotificationProcessStatus.FAILED_RETRYABLE.name,
                    lastError = sanitize(result.message),
                    retryCount = persisted.retryCount + 1,
                    screenshotRef = persisted.screenshotRef,
                )
                result
            }

            is OperationResult.TerminalFailure -> {
                notificationEventDao.updateProcessingState(
                    id = id,
                    processStatus = NotificationProcessStatus.FAILED_TERMINAL.name,
                    lastError = sanitize(result.message),
                    retryCount = persisted.retryCount,
                    screenshotRef = persisted.screenshotRef,
                )
                result
            }
        }
    }

    /** 过滤、去重等成功分支共用的状态回写逻辑。 */
    private suspend fun completeSuccessfully(
        notificationId: Long,
        destination: ProcessDestination,
        status: NotificationProcessStatus,
        message: String,
        retryCount: Int,
        screenshotRef: String?,
    ): OperationResult.Success<NotificationProcessOutcome> {
        notificationEventDao.updateProcessingState(
            id = notificationId,
            processStatus = status.name,
            lastError = message,
            retryCount = retryCount,
            screenshotRef = screenshotRef,
        )
        return OperationResult.Success(
            NotificationProcessOutcome(
                notificationId = notificationId,
                destination = destination,
                status = status,
                screenshotRef = screenshotRef,
                message = message,
            ),
        )
    }
}

/** MediaProjection 尚未接入前的截图协调器占位实现。 */
@Singleton
class PlaceholderCaptureCoordinator @Inject constructor() : CaptureCoordinator {
    override suspend fun requestAuthorization(reason: String): OperationResult<CaptureSession> {
        return OperationResult.Success(
            CaptureSession(
                sessionId = UUID.randomUUID().toString(),
                state = CaptureSessionState.REQUESTED,
                reason = reason,
            ),
        )
    }

    override suspend fun capture(sessionId: String): OperationResult<String> {
        return OperationResult.RetryableFailure("Capture session $sessionId is not connected to MediaProjection yet")
    }

    override suspend fun cancel(sessionId: String) = Unit

    override suspend fun reportFailure(sessionId: String, message: String) = Unit
}

/** OCR 尚未接入前的占位实现。 */
@Singleton
class FakeOcrEngine @Inject constructor() : OcrEngine {
    override suspend fun extractText(imageRef: String): OperationResult<String> {
        return OperationResult.RetryableFailure("OCR engine is not enabled for $imageRef")
    }
}

/** 首次安装时默认开启的通知来源。 */
private val DEFAULT_SOURCES = setOf(
    NotificationSourceApp.WECHAT,
    NotificationSourceApp.ALIPAY,
    NotificationSourceApp.BANK_SMS,
)

/** 缺少订单号时用于模糊去重的时间窗口，仅保留 5 秒避免误判连续同额交易。 */
private const val DUPLICATE_WINDOW_MS = 5 * 1000L

/** 为 CSV 导出做最小转义，避免逗号和引号破坏结构。 */
private fun csvEscape(value: Any?): String {
    val text = value?.toString().orEmpty()
    return "\"${text.replace("\"", "\"\"")}\""
}

/** 写入失败信息前做一次脱敏，避免长数字直接持久化。 */
private fun sanitize(message: String): String {
    return message.replace(Regex("""\d{6,}"""), "******")
}
