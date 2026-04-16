package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.common.CurrencyFormatter
import com.autobook.cn.core.common.TimeFormatter
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.PendingEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 用途说明：
 * - 设置页聚合后的界面状态。
 */
data class SettingsUiState(
    val autoAccountingEnabled: Boolean = true,
    val cloudAnalysisEnabled: Boolean = true,
    val captureEnabled: Boolean = false,
    val notificationRetentionDays: Int = 30,
    val screenshotRetentionDays: Int = 7,
    val enabledSources: Set<SettingsNotificationSource> = emptySet(),
    val totalEntries: Int = 0,
    val manualEntries: Int = 0,
    val autoEntries: Int = 0,
    val pendingEntries: Int = 0,
    val currentMonthLabel: String = "本月",
    val currentMonthBudgetInput: String = "",
    val currentMonthBudgetLabel: String = "未设置",
    val currentMonthBudgetUsageSummary: String = "保存预算后，这里会自动展示本月剩余额度。",
    val currentMonthBudgetRemainingLabel: String = "去设置预算",
    val currentMonthBudgetUsageRatio: Float = 0f,
    val currentMonthBudgetExceeded: Boolean = false,
    val currentMonthIncome: String = "¥0.00",
    val currentMonthExpense: String = "¥0.00",
    val latestEntrySummary: String = "暂无记账记录",
    val exportPreview: String = "",
    val exportPreviewSummary: String = "",
)

/**
 * 用途说明：
 * - 设置页 ViewModel，负责设置读写与账本概览。
 *
 * 参数说明：
 * - settingsRepository：设置仓储。
 * - ledgerRepository：账本仓储。
 * - pendingRepository：待确认仓储。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ledgerRepository: LedgerRepository,
    private val pendingRepository: PendingRepository,
) : ViewModel() {
    private val exportPreview = MutableStateFlow("")
    private val monthlyBudgets: Flow<Map<String, Double>> = settingsRepository.settings
        .map { settings: AppSettings -> settings.monthlyBudgets }
        .distinctUntilChanged()

    /**
     * 用途说明：
     * - 集中计算设置页依赖的账本统计与预算概览。
     */
    private val derivedData: Flow<SettingsDerivedData> = combine(
        ledgerRepository.observeEntries(),
        pendingRepository.observeEntries(),
        monthlyBudgets,
    ) { entries: List<LedgerEntry>, pendingEntries: List<PendingEntry>, monthlyBudgetsValue: Map<String, Double> ->
        SettingsDerivedInput(
            entries = entries,
            pendingCount = pendingEntries.size,
            monthlyBudgets = monthlyBudgetsValue,
        )
    }.mapLatest { input: SettingsDerivedInput ->
        // 把全量统计放到后台线程，避免设置页被大账本数据拖慢。
        withContext(Dispatchers.Default) {
            SettingsDerivedData(
                overview = buildSettingsOverview(
                    entries = input.entries,
                    pendingCount = input.pendingCount,
                    monthlyBudgets = input.monthlyBudgets,
                ),
                exportPreviewSummary = buildExportPreviewSummary(input.entries),
            )
        }
    }

    /**
     * 用途说明：
     * - 聚合设置快照、账本概览和导出预览。
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        derivedData,
        exportPreview,
    ) { settings: AppSettings, derived: SettingsDerivedData, preview: String ->
        val overview = derived.overview
        SettingsUiState(
            autoAccountingEnabled = settings.autoAccountingEnabled,
            cloudAnalysisEnabled = settings.cloudAnalysisEnabled,
            captureEnabled = settings.captureEnabled,
            notificationRetentionDays = settings.notificationRetentionDays,
            screenshotRetentionDays = settings.screenshotRetentionDays,
            enabledSources = settings.enabledSources.toSettingsNotificationSources(),
            totalEntries = overview.totalEntries,
            manualEntries = overview.manualEntries,
            autoEntries = overview.autoEntries,
            pendingEntries = overview.pendingEntries,
            currentMonthLabel = overview.currentMonthLabel,
            currentMonthBudgetInput = overview.currentMonthBudgetInput,
            currentMonthBudgetLabel = overview.currentMonthBudgetLabel,
            currentMonthBudgetUsageSummary = overview.currentMonthBudgetUsageSummary,
            currentMonthBudgetRemainingLabel = overview.currentMonthBudgetRemainingLabel,
            currentMonthBudgetUsageRatio = overview.currentMonthBudgetUsageRatio,
            currentMonthBudgetExceeded = overview.currentMonthBudgetExceeded,
            currentMonthIncome = overview.currentMonthIncome,
            currentMonthExpense = overview.currentMonthExpense,
            latestEntrySummary = overview.latestEntrySummary,
            exportPreview = preview,
            exportPreviewSummary = if (preview.isBlank()) "" else derived.exportPreviewSummary,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setAutoAccountingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoAccountingEnabled(enabled) }
    }

    fun setCloudAnalysisEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCloudAnalysisEnabled(enabled) }
    }

    fun setCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCaptureEnabled(enabled) }
    }

    fun setNotificationRetentionDays(days: Int) {
        viewModelScope.launch { settingsRepository.setNotificationRetentionDays(days) }
    }

    fun setScreenshotRetentionDays(days: Int) {
        viewModelScope.launch { settingsRepository.setScreenshotRetentionDays(days) }
    }

    /**
     * 用途说明：
     * - 保存当前月份预算。
     *
     * 参数说明：
     * - amountInput：输入框中的预算文本；留空表示清除预算。
     */
    fun saveCurrentMonthBudget(amountInput: String) {
        viewModelScope.launch {
            val normalizedInput = amountInput.trim()
            val currentMonthKey = YearMonth.now().toString()
            if (normalizedInput.isBlank()) {
                settingsRepository.clearMonthlyBudget(currentMonthKey)
                return@launch
            }
            val amount = normalizedInput.toDoubleOrNull() ?: return@launch
            settingsRepository.setMonthlyBudget(currentMonthKey, amount)
        }
    }

    /**
     * 用途说明：
     * - 设置来源开关。
     *
     * 参数说明：
     * - source：设置页来源分组。
     * - enabled：目标状态。
     */
    fun setSourceEnabled(
        source: SettingsNotificationSource,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            settingsRepository.setSourcesEnabled(
                sources = source.toNotificationSourceApps(),
                enabled = enabled,
            )
        }
    }

    /**
     * 用途说明：
     * - 按需生成 CSV 预览，避免每次进入设置页都拼装导出文本。
     */
    fun loadExportPreview() {
        viewModelScope.launch {
            exportPreview.value = ledgerRepository.exportCsv()
        }
    }
}

/**
 * 用途说明：
 * - 设置页统计所需输入快照。
 */
private data class SettingsDerivedInput(
    val entries: List<LedgerEntry>,
    val pendingCount: Int,
    val monthlyBudgets: Map<String, Double>,
)

/**
 * 用途说明：
 * - 设置页聚合统计结果。
 */
private data class SettingsDerivedData(
    val overview: SettingsLedgerOverview,
    val exportPreviewSummary: String,
)

internal data class SettingsLedgerOverview(
    val totalEntries: Int,
    val manualEntries: Int,
    val autoEntries: Int,
    val pendingEntries: Int,
    val currentMonthLabel: String,
    val currentMonthBudgetInput: String,
    val currentMonthBudgetLabel: String,
    val currentMonthBudgetUsageSummary: String,
    val currentMonthBudgetRemainingLabel: String,
    val currentMonthBudgetUsageRatio: Float,
    val currentMonthBudgetExceeded: Boolean,
    val currentMonthIncome: String,
    val currentMonthExpense: String,
    val latestEntrySummary: String,
)

/**
 * 用途说明：
 * - 构建设置页账本概览。
 *
 * 参数说明：
 * - entries：账本记录列表。
 * - pendingCount：待确认数量。
 * - monthlyBudgets：月预算映射。
 * - today：当前日期，测试中可注入。
 */
internal fun buildSettingsOverview(
    entries: List<LedgerEntry>,
    pendingCount: Int,
    monthlyBudgets: Map<String, Double> = emptyMap(),
    today: LocalDate = LocalDate.now(),
): SettingsLedgerOverview {
    val currentMonth = YearMonth.from(today)
    val currentMonthEntries = entries.filter { YearMonth.from(it.timestamp.toLocalDate()) == currentMonth }
    val manualEntries = entries.count { it.editedManually || it.traceSource == "manual" }
    val currentMonthIncome = currentMonthEntries
        .filter { it.direction == MoneyDirection.INCOME }
        .sumOf { it.amount }
    val currentMonthExpense = currentMonthEntries
        .filter { it.direction == MoneyDirection.EXPENSE }
        .sumOf { it.amount }
    val currentMonthBudget = monthlyBudgets[currentMonth.toString()]?.takeIf { it > 0.0 }
    val budgetRemaining = currentMonthBudget?.minus(currentMonthExpense)
    val budgetRatio = if (currentMonthBudget == null || currentMonthBudget <= 0.0) {
        0f
    } else {
        (currentMonthExpense / currentMonthBudget).coerceIn(0.0, 1.0).toFloat()
    }
    val latestEntry = entries.maxByOrNull { it.timestamp }
    return SettingsLedgerOverview(
        totalEntries = entries.size,
        manualEntries = manualEntries,
        autoEntries = entries.size - manualEntries,
        pendingEntries = pendingCount,
        currentMonthLabel = currentMonth.format(MONTH_LABEL_FORMATTER),
        currentMonthBudgetInput = formatBudgetInput(currentMonthBudget),
        currentMonthBudgetLabel = currentMonthBudget?.let(CurrencyFormatter::format) ?: "未设置",
        currentMonthBudgetUsageSummary = if (currentMonthBudget == null) {
            "清空输入后保存可移除预算，设置后总览页会自动展示剩余额度。"
        } else {
            "已用 ${((currentMonthExpense / currentMonthBudget) * 100).roundToInt()}% · 支出 ${CurrencyFormatter.format(currentMonthExpense)} / 预算 ${CurrencyFormatter.format(currentMonthBudget)}"
        },
        currentMonthBudgetRemainingLabel = if (budgetRemaining == null) {
            "去设置预算"
        } else if (budgetRemaining >= 0.0) {
            "剩余 ${CurrencyFormatter.format(budgetRemaining)}"
        } else {
            "超出 ${CurrencyFormatter.format(kotlin.math.abs(budgetRemaining))}"
        },
        currentMonthBudgetUsageRatio = budgetRatio,
        currentMonthBudgetExceeded = budgetRemaining != null && budgetRemaining < 0.0,
        currentMonthIncome = CurrencyFormatter.format(currentMonthIncome),
        currentMonthExpense = CurrencyFormatter.format(currentMonthExpense),
        latestEntrySummary = latestEntry?.let { entry ->
            "${entry.merchant} · ${CurrencyFormatter.format(entry.amount)} · ${TimeFormatter.format(entry.timestamp)}"
        } ?: "暂无记账记录",
    )
}

/**
 * 用途说明：
 * - 构建导出预览摘要。
 *
 * 参数说明：
 * - entries：账本记录列表。
 */
internal fun buildExportPreviewSummary(entries: List<LedgerEntry>): String {
    if (entries.isEmpty()) {
        return "当前账本为空，导出的 CSV 将只包含表头。"
    }
    val earliest = entries.minByOrNull { it.timestamp } ?: return ""
    val latest = entries.maxByOrNull { it.timestamp } ?: return ""
    val manualEntries = entries.count { it.editedManually || it.traceSource == "manual" }
    return "共 ${entries.size} 条记录，覆盖 ${earliest.timestamp.toLocalDate().format(EXPORT_DATE_FORMATTER)} 至 ${latest.timestamp.toLocalDate().format(EXPORT_DATE_FORMATTER)}，其中手动补记 $manualEntries 条。"
}

/**
 * 用途说明：
 * - 把时间戳转换为本地日期。
 *
 * 参数说明：
 * - 接收者：毫秒时间戳。
 */
private fun Long.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

/**
 * 用途说明：
 * - 预算输入框不显示货币符号，方便直接编辑数字。
 *
 * 参数说明：
 * - amount：预算金额。
 */
private fun formatBudgetInput(amount: Double?): String {
    return when {
        amount == null -> ""
        amount % 1.0 == 0.0 -> amount.toLong().toString()
        else -> amount.toString()
    }
}

private val MONTH_LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINA)
private val EXPORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.CHINA)
