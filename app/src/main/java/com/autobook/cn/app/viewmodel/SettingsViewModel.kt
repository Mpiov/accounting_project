package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.common.CurrencyFormatter
import com.autobook.cn.core.common.TimeFormatter
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.LocalModelRepository
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.LocalModelInstallStatus
import com.autobook.cn.core.model.LocalModelSnapshot
import com.autobook.cn.core.model.LocalModelSpec
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 设置页 ViewModel，负责设置项读写和账本导出预览。 */
data class SettingsUiState(
    val autoAccountingEnabled: Boolean = true,
    val cloudAnalysisEnabled: Boolean = true,
    val captureEnabled: Boolean = false,
    val ocrEnabled: Boolean = false,
    val notificationRetentionDays: Int = 30,
    val screenshotRetentionDays: Int = 7,
    val enabledSources: Set<SettingsNotificationSource> = emptySet(),
    /** 是否启用本地语义增强通路，当前使用启发式分析作为占位，后续可直接切换为端侧本地 LLM。 */
    val localLlmConfidenceBoostEnabled: Boolean = false,
    /** 当前模型清单，供设置页展示推荐模型与轻量备选模型。 */
    val availableLocalModels: List<LocalModelSpec> = emptyList(),
    /** 当前选中的本地模型标识。 */
    val selectedLocalModelId: String = "",
    /** 当前模型安装状态。 */
    val localModelInstallStatus: LocalModelInstallStatus = LocalModelInstallStatus.NOT_DOWNLOADED,
    /** 当前模型文件路径。 */
    val selectedLocalModelPath: String? = null,
    /** 当前模型下载进度。 */
    val localModelDownloadProgressPercent: Int? = null,
    /** 当前模型状态说明。 */
    val localModelStatusMessage: String = "尚未下载本地模型",
    val totalEntries: Int = 0,
    val manualEntries: Int = 0,
    val autoEntries: Int = 0,
    val pendingEntries: Int = 0,
    /** 当前月份标签，供预算卡和概览卡统一展示。 */
    val currentMonthLabel: String = "本月",
    /** 当前月份预算输入框的默认值，不带货币符号，方便用户直接编辑。 */
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val localModelRepository: LocalModelRepository,
    private val ledgerRepository: LedgerRepository,
    private val pendingRepository: PendingRepository,
) : ViewModel() {
    private val exportPreview = MutableStateFlow("")
    private val monthlyBudgets: Flow<Map<String, Double>> = settingsRepository.settings
        .map { settings: AppSettings -> settings.monthlyBudgets }
        .distinctUntilChanged()

    /**
     * 设置页专用的账本衍生数据流。
     *
     * 用途说明：
     * - 把“全量账本统计 + 本月预算汇总 + 导出摘要”从最终 UI 组装流程里拆出来。
     * - 这样本地模型下载进度、普通设置切换等无关事件，就不会反复触发全量账本扫描。
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
        // 设置页概览会多次遍历全量账本，这里放到后台线程，避免打开页面或模型下载时卡住主线程。
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

    /** 把持久化设置、模型状态和轻量展示字段组合为设置页状态。 */
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        localModelRepository.snapshot,
        derivedData,
        exportPreview,
    ) { settings: AppSettings, localModelSnapshot: LocalModelSnapshot, derived: SettingsDerivedData, preview: String ->
        val overview = derived.overview
        SettingsUiState(
            autoAccountingEnabled = settings.autoAccountingEnabled,
            cloudAnalysisEnabled = settings.cloudAnalysisEnabled,
            captureEnabled = settings.captureEnabled,
            ocrEnabled = settings.ocrEnabled,
            notificationRetentionDays = settings.notificationRetentionDays,
            screenshotRetentionDays = settings.screenshotRetentionDays,
            enabledSources = settings.enabledSources.toSettingsNotificationSources(),
            localLlmConfidenceBoostEnabled = settings.localLlmConfidenceBoostEnabled,
            availableLocalModels = localModelSnapshot.availableModels,
            selectedLocalModelId = localModelSnapshot.selectedModelId,
            localModelInstallStatus = localModelSnapshot.installStatus,
            selectedLocalModelPath = localModelSnapshot.selectedModelPath,
            localModelDownloadProgressPercent = localModelSnapshot.downloadProgressPercent,
            localModelStatusMessage = localModelSnapshot.statusMessage,
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
    }
        .stateIn(
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

    fun setOcrEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setOcrEnabled(enabled) }
    }

    /** 控制本地语义增强通路的开关，enabled 为 true 时允许启用占位语义强化，并保持与未来本地 LLM 适配器一致的接口。 */
    fun setLocalLlmConfidenceBoostEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLocalLlmConfidenceBoostEnabled(enabled) }
    }

    /** 切换当前选中的本地模型。 */
    fun setSelectedLocalModelId(modelId: String) {
        viewModelScope.launch { localModelRepository.selectModel(modelId) }
    }

    /** 下载当前选中的本地模型。 */
    fun downloadSelectedLocalModel() {
        viewModelScope.launch { localModelRepository.downloadSelectedModel() }
    }

    /** 主动刷新本地模型状态，便于识别外部脚本或手动下载后的文件。 */
    fun refreshLocalModelState() {
        viewModelScope.launch { localModelRepository.refresh() }
    }

    fun setNotificationRetentionDays(days: Int) {
        viewModelScope.launch { settingsRepository.setNotificationRetentionDays(days) }
    }

    fun setScreenshotRetentionDays(days: Int) {
        viewModelScope.launch { settingsRepository.setScreenshotRetentionDays(days) }
    }

    /**
     * 保存当前月份预算。
     *
     * 参数说明：
     * - amountInput：来自设置页输入框的文本；留空代表清除本月预算。
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
     * 设置页来源桶开关写回。
     *
     * 用途说明：
     * - 把设置页的三类来源桶翻译成底层真实来源集合后，再统一写回持久层。
     * - 其中“其它来源”会同时联动 `BANK_SMS` 和 `UNKNOWN`，确保界面语义和底层过滤行为一致。
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

    fun loadExportPreview() {
        viewModelScope.launch {
            // 按需生成，避免每次进入设置页都做 CSV 拼装。
            exportPreview.value = ledgerRepository.exportCsv()
        }
    }
}

/**
 * 设置页重计算所需的输入快照。
 *
 * 用途说明：
 * - 把账本列表、待确认数量和预算映射成纯数据结构，便于在后台线程里集中做统计。
 */
private data class SettingsDerivedInput(
    val entries: List<LedgerEntry>,
    val pendingCount: Int,
    val monthlyBudgets: Map<String, Double>,
)

/**
 * 设置页从账本数据派生出的聚合结果。
 *
 * 用途说明：
 * - 把页面中最重的“概览统计”和“导出摘要”收口到同一份派生数据，减少重复扫描账本列表。
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

internal fun buildExportPreviewSummary(entries: List<LedgerEntry>): String {
    if (entries.isEmpty()) {
        return "当前账本为空，导出的 CSV 将只包含表头。"
    }
    val earliest = entries.minByOrNull { it.timestamp } ?: return ""
    val latest = entries.maxByOrNull { it.timestamp } ?: return ""
    val manualEntries = entries.count { it.editedManually || it.traceSource == "manual" }
    return "共 ${entries.size} 条记录，覆盖 ${earliest.timestamp.toLocalDate().format(EXPORT_DATE_FORMATTER)} 至 " +
        "${latest.timestamp.toLocalDate().format(EXPORT_DATE_FORMATTER)}，其中手动补记 $manualEntries 条。"
}

private fun Long.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

/** 预算输入框不显示货币符号，便于用户直接修改数字。 */
private fun formatBudgetInput(amount: Double?): String {
    return when {
        amount == null -> ""
        amount % 1.0 == 0.0 -> amount.toLong().toString()
        else -> amount.toString()
    }
}

private val MONTH_LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINA)
private val EXPORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.CHINA)
