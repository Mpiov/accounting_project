package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.LedgerEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
 * 预算设置页状态。
 *
 * 用途说明：
 * - 预算设置页只需要展示“当前月份、当前预算、当前支出和默认输入值”四类信息。
 * - 单独定义轻量状态，避免为了一个预算编辑页去订阅完整设置页的大状态树。
 *
 * 参数说明：
 * - currentMonthLabel：当前展示月份文案。
 * - currentMonthBudgetInput：预算输入框默认值，不带货币符号。
 * - currentMonthBudgetLabel：当前预算展示文案。
 * - currentMonthExpense：当前月份累计支出展示文案。
 */
data class BudgetSettingsUiState(
    val currentMonthLabel: String = "本月",
    val currentMonthBudgetInput: String = "",
    val currentMonthBudgetLabel: String = "未设置",
    val currentMonthExpense: String = "¥0.00",
)

/**
 * 预算设置页专用 ViewModel。
 *
 * 用途说明：
 * - 只负责预算编辑页真正需要的那一小撮数据，不再复用重量级 `SettingsViewModel`。
 * - 这样从总览页点击“去设置”时，目标页首帧不必等待来源开关、导出摘要等无关计算。
 *
 * 参数说明：
 * - ledgerRepository：读取账本记录，用于计算本月支出。
 * - settingsRepository：读取与写入月度预算。
 */
@HiltViewModel
class BudgetSettingsViewModel @Inject constructor(
    ledgerRepository: LedgerRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    /**
     * 预算设置页只关心每月预算映射，不需要订阅完整设置对象。
     *
     * 用途说明：
     * - 把预算字段单独摘出来，避免其它设置项变化时预算页也跟着整页重算。
     */
    private val monthlyBudgets: Flow<Map<String, Double>> = settingsRepository.settings
        .map { settings: AppSettings -> settings.monthlyBudgets }
        .distinctUntilChanged()

    /**
     * 输入快照。
     *
     * 用途说明：
     * - 把账本记录和预算映射收口成一份轻量输入，便于统一切到后台线程计算。
     *
     * 参数说明：
     * - entries：账本记录列表。
     * - monthlyBudgets：月度预算映射。
     */
    private data class BudgetSettingsInput(
        val entries: List<LedgerEntry>,
        val monthlyBudgets: Map<String, Double>,
    )

    /**
     * 预算页状态流。
     *
     * 用途说明：
     * - 复用现有 `buildSettingsOverview`，但只提取预算页需要的字段。
     * - 重计算显式放到后台线程，尽量缩短预算页切入时的主线程停顿。
     */
    val uiState: StateFlow<BudgetSettingsUiState> = combine(
        ledgerRepository.observeEntries(),
        monthlyBudgets,
    ) { entries: List<LedgerEntry>, monthlyBudgetsValue: Map<String, Double> ->
        BudgetSettingsInput(
            entries = entries,
            monthlyBudgets = monthlyBudgetsValue,
        )
    }.mapLatest { input: BudgetSettingsInput ->
        withContext(Dispatchers.Default) {
            val overview = buildSettingsOverview(
                entries = input.entries,
                pendingCount = 0,
                monthlyBudgets = input.monthlyBudgets,
            )
            BudgetSettingsUiState(
                currentMonthLabel = overview.currentMonthLabel,
                currentMonthBudgetInput = overview.currentMonthBudgetInput,
                currentMonthBudgetLabel = overview.currentMonthBudgetLabel,
                currentMonthExpense = overview.currentMonthExpense,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetSettingsUiState(),
    )

    /**
     * 保存当前月份预算。
     *
     * 参数说明：
     * - amountInput：预算输入框文本；为空时表示清除本月预算。
     */
    fun saveBudget(amountInput: String) {
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
}
