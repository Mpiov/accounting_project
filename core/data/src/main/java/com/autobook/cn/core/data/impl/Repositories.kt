package com.autobook.cn.core.data.impl

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.NotificationIngestor
import com.autobook.cn.core.data.NotificationTraceRepository
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
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessOutcome
import com.autobook.cn.core.model.NotificationProcessStatus
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.PendingEntry
import com.autobook.cn.core.model.ProcessDestination
import com.autobook.cn.core.model.TransactionCandidate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.DecimalFormat
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "autobook_settings")

/**
 * 用途说明：
 * - 基于 Room 的账本仓储实现。
 *
 * 参数说明：
 * - ledgerEntryDao：账本表 DAO。
 */
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

    /**
     * 用途说明：
     * - 按通知主链路的判重规则查找疑似重复记录。
     *
     * 参数说明：
     * - candidate：当前待判重候选。
     */
    override suspend fun findPotentialDuplicate(candidate: TransactionCandidate): LedgerEntry? {
        val duplicate = ledgerEntryDao.findPotentialDuplicate(
            orderId = candidate.orderId,
            amount = candidate.amount,
            direction = candidate.direction.name,
            windowStart = candidate.timestamp - DUPLICATE_WINDOW_MS,
            windowEnd = candidate.timestamp + DUPLICATE_WINDOW_MS,
        )
        return duplicate?.toDomain()
    }

    /**
     * 用途说明：
     * - 对图片导入候选执行分钟级历史判重。
     *
     * 参数说明：
     * - timestamp：候选时间戳。
     * - amount：候选金额。
     * - direction：候选方向。
     */
    override suspend fun findPotentialImageImportDuplicate(
        timestamp: Long?,
        amount: Double,
        direction: MoneyDirection,
    ): LedgerEntry? {
        if (timestamp == null) {
            return null
        }
        val minuteWindow = timestamp.toMinuteWindow()
        val duplicate = ledgerEntryDao.findPotentialImageImportDuplicate(
            windowStart = minuteWindow.startInclusive,
            windowEnd = minuteWindow.endInclusive,
            amount = amount,
            direction = direction.name,
        )
        return duplicate?.toDomain()
    }

    /**
     * 用途说明：
     * - 在候选缺少时间时，用更宽松的规则探测历史重复风险。
     *
     * 参数说明：
     * - amount：候选金额。
     * - direction：候选方向。
     */
    override suspend fun findPotentialImageImportDuplicateWithoutTimestamp(
        amount: Double,
        direction: MoneyDirection,
    ): LedgerEntry? {
        val duplicate = ledgerEntryDao.findPotentialImageImportDuplicateWithoutTimestamp(
            amount = amount,
            direction = direction.name,
        )
        return duplicate?.toDomain()
    }

    override suspend fun exportCsv(): String {
        val rows = observeEntries().first()
        return buildString {
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

/**
 * 用途说明：
 * - 基于 Room 的待确认仓储实现。
 *
 * 参数说明：
 * - pendingEntryDao：待确认表 DAO。
 */
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

/**
 * 用途说明：
 * - 提供通知原文回看映射。
 *
 * 参数说明：
 * - notificationEventDao：通知事件表 DAO。
 */
@Singleton
class NotificationTraceRepositoryImpl @Inject constructor(
    private val notificationEventDao: NotificationEventDao,
) : NotificationTraceRepository {
    override fun observeRawTextById(): Flow<Map<Long, String>> = notificationEventDao.observeAll().map { events ->
        events.mapNotNull { event ->
            val rawText = listOfNotNull(
                event.title?.trim()?.takeIf { it.isNotEmpty() },
                event.text?.trim()?.takeIf { it.isNotEmpty() },
            ).distinct().takeIf { it.isNotEmpty() }?.joinToString(separator = "\n")
            rawText?.let { event.id to it }
        }.toMap()
    }
}

/**
 * 用途说明：
 * - 基于 DataStore 的设置仓储实现。
 *
 * 参数说明：
 * - context：应用级上下文。
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    /**
     * 用途说明：
     * - DataStore 键定义集中收口。
     */
    private object Keys {
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val autoAccountingEnabled = booleanPreferencesKey("auto_accounting_enabled")
        val cloudAnalysisEnabled = booleanPreferencesKey("cloud_analysis_enabled")
        val captureEnabled = booleanPreferencesKey("capture_enabled")
        val notificationRetentionDays = intPreferencesKey("notification_retention_days")
        val screenshotRetentionDays = intPreferencesKey("screenshot_retention_days")
        val monthlyBudgets = stringPreferencesKey("monthly_budgets")
        val enabledSources = stringSetPreferencesKey("enabled_sources")
    }

    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            onboardingCompleted = preferences[Keys.onboardingCompleted] ?: false,
            autoAccountingEnabled = preferences[Keys.autoAccountingEnabled] ?: true,
            cloudAnalysisEnabled = preferences[Keys.cloudAnalysisEnabled] ?: true,
            captureEnabled = preferences[Keys.captureEnabled] ?: false,
            notificationRetentionDays = preferences[Keys.notificationRetentionDays] ?: 30,
            screenshotRetentionDays = preferences[Keys.screenshotRetentionDays] ?: 7,
            monthlyBudgets = parseMonthlyBudgets(preferences[Keys.monthlyBudgets].orEmpty()),
            enabledSources = resolveEnabledSources(preferences[Keys.enabledSources]),
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

    override suspend fun setNotificationRetentionDays(days: Int) {
        context.settingsDataStore.edit { it[Keys.notificationRetentionDays] = days.coerceIn(1, 90) }
    }

    override suspend fun setScreenshotRetentionDays(days: Int) {
        context.settingsDataStore.edit { it[Keys.screenshotRetentionDays] = days.coerceIn(1, 30) }
    }

    override suspend fun setMonthlyBudget(monthKey: String, amount: Double) {
        context.settingsDataStore.edit { preferences ->
            val existingBudgets = parseMonthlyBudgets(preferences[Keys.monthlyBudgets].orEmpty()).toMutableMap()
            existingBudgets[monthKey] = amount.coerceAtLeast(0.0)
            preferences[Keys.monthlyBudgets] = serializeMonthlyBudgets(existingBudgets)
        }
    }

    override suspend fun clearMonthlyBudget(monthKey: String) {
        context.settingsDataStore.edit { preferences ->
            val existingBudgets = parseMonthlyBudgets(preferences[Keys.monthlyBudgets].orEmpty()).toMutableMap()
            existingBudgets.remove(monthKey)
            preferences[Keys.monthlyBudgets] = serializeMonthlyBudgets(existingBudgets)
        }
    }

    override suspend fun setSourceEnabled(source: NotificationSourceApp, enabled: Boolean) {
        setSourcesEnabled(setOf(source), enabled)
    }

    override suspend fun setSourcesEnabled(
        sources: Set<NotificationSourceApp>,
        enabled: Boolean,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.enabledSources] = applySourceEnabledChange(
                currentSourceNames = preferences[Keys.enabledSources],
                sources = sources,
                enabled = enabled,
            )
        }
    }
}

/**
 * 用途说明：
 * - 通知入账主链路的仓储协调实现。
 *
 * 参数说明：
 * - notificationEventDao：通知事件表 DAO。
 * - pipeline：交易处理流水线。
 * - settingsRepository：运行时设置来源。
 */
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
        if (!settings.autoAccountingEnabled) {
            return completeSuccessfully(
                notificationId = id,
                destination = ProcessDestination.FILTERED,
                status = NotificationProcessStatus.FILTERED_OUT,
                message = "Auto accounting disabled by user settings",
                retryCount = persisted.retryCount,
                screenshotRef = persisted.screenshotRef,
            )
        }

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

    /**
     * 用途说明：
     * - 对过滤或短路结果统一落成功状态。
     *
     * 参数说明：
     * - notificationId：通知主键。
     * - destination：最终落点。
     * - status：处理状态。
     * - message：结果说明。
     * - retryCount：累计重试次数。
     * - screenshotRef：关联截图引用。
     */
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

/**
 * 用途说明：
 * - 默认启用的通知来源集合。
 */
private val DEFAULT_SOURCES = setOf(
    NotificationSourceApp.WECHAT,
    NotificationSourceApp.ALIPAY,
    NotificationSourceApp.BANK_SMS,
    NotificationSourceApp.UNKNOWN,
)

/**
 * 用途说明：
 * - 通知主链路的短时判重窗口。
 */
private const val DUPLICATE_WINDOW_MS = 30 * 1000L

/**
 * 用途说明：
 * - 分钟窗口结构。
 *
 * 参数说明：
 * - startInclusive：窗口起点。
 * - endInclusive：窗口终点。
 */
private data class MinuteWindow(
    val startInclusive: Long,
    val endInclusive: Long,
)

/**
 * 用途说明：
 * - 把毫秒时间戳归一化成分钟窗口。
 *
 * 参数说明：
 * - 接收者：原始毫秒时间戳。
 */
private fun Long.toMinuteWindow(): MinuteWindow {
    val startInclusive = this - (this % ONE_MINUTE_MILLIS)
    val endInclusive = startInclusive + ONE_MINUTE_MILLIS - 1
    return MinuteWindow(
        startInclusive = startInclusive,
        endInclusive = endInclusive,
    )
}

/**
 * 用途说明：
 * - 解析启用来源集合。
 *
 * 参数说明：
 * - sourceNames：DataStore 中保存的来源名称集合。
 */
internal fun resolveEnabledSources(sourceNames: Set<String>?): Set<NotificationSourceApp> {
    if (sourceNames == null) {
        return DEFAULT_SOURCES.toSet()
    }
    return sourceNames.mapNotNullTo(mutableSetOf()) { sourceName ->
        NotificationSourceApp.entries.firstOrNull { source -> source.name == sourceName }
    }
}

/**
 * 用途说明：
 * - 应用来源开关变更。
 *
 * 参数说明：
 * - currentSourceNames：当前已启用来源名称集合。
 * - sources：本次要切换的来源集合。
 * - enabled：目标状态。
 */
internal fun applySourceEnabledChange(
    currentSourceNames: Set<String>?,
    sources: Set<NotificationSourceApp>,
    enabled: Boolean,
): Set<String> {
    val updatedSourceNames = currentSourceNames?.toMutableSet()
        ?: DEFAULT_SOURCES.mapTo(mutableSetOf()) { source -> source.name }
    val targetSourceNames = sources.mapTo(mutableSetOf()) { source -> source.name }
    if (enabled) {
        updatedSourceNames += targetSourceNames
    } else {
        updatedSourceNames -= targetSourceNames
    }
    return updatedSourceNames
}

private const val ONE_MINUTE_MILLIS = 60_000L

/**
 * 用途说明：
 * - 生成 CSV 安全文本。
 *
 * 参数说明：
 * - value：待转义的原始值。
 */
private fun csvEscape(value: Any?): String {
    val text = value?.toString().orEmpty()
    return "\"${text.replace("\"", "\"\"")}\""
}

/**
 * 用途说明：
 * - 对错误消息做最小脱敏。
 *
 * 参数说明：
 * - message：原始错误信息。
 */
private fun sanitize(message: String): String {
    return message.replace(Regex("""\d{6,}"""), "******")
}

/**
 * 用途说明：
 * - 解析月预算映射。
 *
 * 参数说明：
 * - rawValue：DataStore 中保存的原始字符串。
 */
private fun parseMonthlyBudgets(rawValue: String): Map<String, Double> {
    if (rawValue.isBlank()) return emptyMap()
    return rawValue.split(";")
        .mapNotNull { entry ->
            val separatorIndex = entry.indexOf('=')
            if (separatorIndex <= 0 || separatorIndex == entry.lastIndex) {
                return@mapNotNull null
            }
            val monthKey = entry.substring(0, separatorIndex)
            val amount = entry.substring(separatorIndex + 1).toDoubleOrNull() ?: return@mapNotNull null
            monthKey to amount
        }
        .toMap()
}

/**
 * 用途说明：
 * - 序列化月预算映射。
 *
 * 参数说明：
 * - budgets：待序列化预算映射。
 */
private fun serializeMonthlyBudgets(budgets: Map<String, Double>): String {
    if (budgets.isEmpty()) return ""
    return budgets.toSortedMap().entries.joinToString(separator = ";") { (monthKey, amount) ->
        "$monthKey=${BUDGET_AMOUNT_FORMAT.format(amount)}"
    }
}

private val BUDGET_AMOUNT_FORMAT = DecimalFormat("0.##")
