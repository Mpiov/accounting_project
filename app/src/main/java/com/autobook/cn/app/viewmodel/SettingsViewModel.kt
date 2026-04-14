package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.common.CurrencyFormatter
import com.autobook.cn.core.common.TimeFormatter
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.LocalModelRepository
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.LocalModelInstallStatus
import com.autobook.cn.core.model.LocalModelSnapshot
import com.autobook.cn.core.model.LocalModelSpec
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationSourceApp
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 设置页 ViewModel，负责设置项读写和账本导出预览。 */
data class SettingsUiState(
    val autoAccountingEnabled: Boolean = true,
    val cloudAnalysisEnabled: Boolean = false,
    val captureEnabled: Boolean = false,
    val ocrEnabled: Boolean = false,
    val notificationRetentionDays: Int = 30,
    val screenshotRetentionDays: Int = 7,
    val enabledSources: Set<NotificationSourceApp> = emptySet(),
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

    /** 把持久化设置与导出预览组合为设置页状态。 */
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        localModelRepository.snapshot,
        ledgerRepository.observeEntries(),
        pendingRepository.observeEntries(),
        exportPreview,
    ) { settings, localModelSnapshot, entries, pendingEntries, preview ->
        val overview = buildSettingsOverview(entries = entries, pendingCount = pendingEntries.size)
            SettingsUiState(
                autoAccountingEnabled = settings.autoAccountingEnabled,
                cloudAnalysisEnabled = settings.cloudAnalysisEnabled,
                captureEnabled = settings.captureEnabled,
                ocrEnabled = settings.ocrEnabled,
                notificationRetentionDays = settings.notificationRetentionDays,
                screenshotRetentionDays = settings.screenshotRetentionDays,
                enabledSources = settings.enabledSources,
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
                currentMonthIncome = overview.currentMonthIncome,
                currentMonthExpense = overview.currentMonthExpense,
                latestEntrySummary = overview.latestEntrySummary,
                exportPreview = preview,
                exportPreviewSummary = if (preview.isBlank()) "" else buildExportPreviewSummary(entries),
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

    fun setSourceEnabled(
        source: NotificationSourceApp,
        enabled: Boolean,
    ) {
        viewModelScope.launch { settingsRepository.setSourceEnabled(source, enabled) }
    }

    fun loadExportPreview() {
        viewModelScope.launch {
            // 按需生成，避免每次进入设置页都做 CSV 拼装。
            exportPreview.value = ledgerRepository.exportCsv()
        }
    }
}

internal data class SettingsLedgerOverview(
    val totalEntries: Int,
    val manualEntries: Int,
    val autoEntries: Int,
    val pendingEntries: Int,
    val currentMonthIncome: String,
    val currentMonthExpense: String,
    val latestEntrySummary: String,
)

internal fun buildSettingsOverview(
    entries: List<LedgerEntry>,
    pendingCount: Int,
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
    val latestEntry = entries.maxByOrNull { it.timestamp }
    return SettingsLedgerOverview(
        totalEntries = entries.size,
        manualEntries = manualEntries,
        autoEntries = entries.size - manualEntries,
        pendingEntries = pendingCount,
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

private val EXPORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.CHINA)
