package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.common.CurrencyFormatter
import com.autobook.cn.core.common.TimeFormatter
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.feature.ledger.model.LedgerCalendarDayUiModel
import com.autobook.cn.feature.ledger.model.LedgerItemUiModel
import com.autobook.cn.feature.ledger.model.LedgerRangeFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DecimalFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * 总览页 UI 状态。
 *
 * 用途说明：
 * - 只保留总览页真实展示的字段，避免复用月份详情页状态导致首页额外计算日历、趋势和分类数据。
 *
 * 参数说明：
 * - periodTitle：当前统计范围标题。
 * - incomeSummary：收入汇总文案。
 * - expenseSummary：支出汇总文案。
 * - netSummary：结余汇总文案。
 * - pendingCount：待确认数量。
 * - selectedRange：当前选中的统计范围。
 * - budgetAmountLabel：预算金额文案，为空表示未设置预算。
 * - budgetSpentLabel：预算已用文案。
 * - budgetRemainingLabel：预算剩余额度文案。
 * - budgetUsageRatio：预算使用比例。
 * - recentEntries：首页最近记录列表。
 */
data class OverviewUiState(
    val periodTitle: String = "本月账单",
    val incomeSummary: String = "¥0.00",
    val expenseSummary: String = "¥0.00",
    val netSummary: String = "¥0.00",
    val pendingCount: Int = 0,
    val selectedRange: LedgerRangeFilter = LedgerRangeFilter.MONTH,
    val budgetAmountLabel: String? = null,
    val budgetSpentLabel: String = "¥0.00",
    val budgetRemainingLabel: String = "去设置预算",
    val budgetUsageRatio: Float = 0f,
    val recentEntries: List<LedgerItemUiModel> = emptyList(),
)

/** 账本首页 ViewModel，负责总览页范围汇总、预算和最近记录映射。 */
@HiltViewModel
class LedgerViewModel @Inject constructor(
    ledgerRepository: LedgerRepository,
    pendingRepository: PendingRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val selectedRange = MutableStateFlow(LedgerRangeFilter.MONTH)
    private val sourceSnapshot = combine(
        ledgerRepository.observeEntries(),
        pendingRepository.observeActiveCount(),
        settingsRepository.settings,
        selectedRange,
    ) { entries, pendingCount, settings, range ->
        LedgerSourceSnapshot(
            entries = entries,
            pendingCount = pendingCount,
            monthlyBudgets = settings.monthlyBudgets,
            range = range,
        )
    }

    /** 把账本记录、待确认数量和筛选条件组合成首页状态。 */
    val uiState: StateFlow<OverviewUiState> = sourceSnapshot
        .mapLatest { snapshot ->
            // 首页会对全量账本做多轮筛选、排序、分组与统计，这里显式放到后台线程，避免首屏和切筛选时阻塞主线程。
            withContext(Dispatchers.Default) {
                buildOverviewUiState(snapshot = snapshot)
            }
        }
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OverviewUiState(),
    )

    fun setRange(range: LedgerRangeFilter) {
        selectedRange.value = range
    }
}

/**
 * 构建总览页最终 UI 状态。
 *
 * 用途说明：
 * - 把首页真实展示的范围汇总、预算和最近记录收口到一个纯计算函数中。
 *
 * 参数说明：
 * - snapshot：当前账本、待确认数量、预算和筛选条件快照。
 */
private fun buildOverviewUiState(
    snapshot: LedgerSourceSnapshot,
): OverviewUiState {
    val entries = snapshot.entries
    val pendingCount = snapshot.pendingCount
    val range = snapshot.range
    val today = LocalDate.now()
    val anchorDate = today
    val filtered = entries
        .filterByRange(range = range, selectedDate = today, today = today)
        .sortedByDescending { it.timestamp }
    val filteredSummary = filtered.summarize()
    val monthEntries = entries.filterWithinMonth(YearMonth.from(anchorDate))
    val budgetState = buildBudgetState(
        month = YearMonth.from(anchorDate),
        monthEntries = monthEntries,
        monthlyBudgets = snapshot.monthlyBudgets,
    )

    return OverviewUiState(
        periodTitle = buildOverviewPeriodTitle(range = range, today = today),
        incomeSummary = CurrencyFormatter.format(filteredSummary.income),
        expenseSummary = CurrencyFormatter.format(filteredSummary.expense),
        netSummary = formatSignedCurrency(filteredSummary.net),
        pendingCount = pendingCount,
        selectedRange = range,
        budgetAmountLabel = budgetState.amountLabel,
        budgetSpentLabel = budgetState.spentLabel,
        budgetRemainingLabel = budgetState.remainingLabel,
        budgetUsageRatio = budgetState.ratio,
        recentEntries = filtered.map { entry ->
            LedgerItemUiModel(
                id = entry.id,
                merchant = entry.merchant,
                amount = formatAmountByDirection(entry),
                subtitle = "${entry.channel} · ${TimeFormatter.format(entry.timestamp)}",
                category = entry.category,
                direction = entry.direction,
            )
        },
    )
}

/** 按关键词过滤商户、分类和备注。 */
internal fun List<LedgerEntry>.filterByQuery(query: String): List<LedgerEntry> {
    if (query.isBlank()) return this
    val normalized = query.trim()
    return filter { entry ->
        entry.merchant.contains(normalized, ignoreCase = true) ||
            entry.category.contains(normalized, ignoreCase = true) ||
            entry.note?.contains(normalized, ignoreCase = true) == true
    }
}

/** 按日期范围过滤账本记录。 */
internal fun List<LedgerEntry>.filterByRange(
    range: LedgerRangeFilter,
    selectedDate: LocalDate,
    today: LocalDate = LocalDate.now(),
): List<LedgerEntry> {
    return when (range) {
        LedgerRangeFilter.TODAY -> filter { it.timestamp.toLocalDate() == today }
        LedgerRangeFilter.WEEK -> filterWithinWeek(today)
        LedgerRangeFilter.MONTH -> filterWithinMonth(YearMonth.from(today))
        LedgerRangeFilter.YEAR -> filterWithinYear(today.year)
        LedgerRangeFilter.CALENDAR -> filter { it.timestamp.toLocalDate() == selectedDate }
    }
}

/** 为月历视图生成固定 6 周网格，并补充每日收入/支出摘要。 */
internal fun buildLedgerCalendarDays(
    entries: List<LedgerEntry>,
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate = LocalDate.now(),
): List<LedgerCalendarDayUiModel> {
    val entryDates = entries.mapTo(mutableSetOf()) { it.timestamp.toLocalDate() }
    val dailySummary = entries.groupBy { it.timestamp.toLocalDate() }
    val firstDayOfMonth = displayedMonth.atDay(1)
    val gridStart = firstDayOfMonth.minusDays((firstDayOfMonth.dayOfWeek.value - 1).toLong())
    return List(42) { index ->
        val date = gridStart.plusDays(index.toLong())
        val summary = dailySummary[date].orEmpty().summarize()
        LedgerCalendarDayUiModel(
            date = date,
            label = date.dayOfMonth.toString(),
            isCurrentMonth = YearMonth.from(date) == displayedMonth,
            hasEntries = date in entryDates,
            isSelected = date == selectedDate,
            isToday = date == today,
            incomeLabel = summary.income.takeIf { it > 0.0 }?.let { "+${formatCompactCurrency(it)}" },
            expenseLabel = summary.expense.takeIf { it > 0.0 }?.let { "-${formatCompactCurrency(it)}" },
            // 日历格子当前只展示收入和支出，不再生成结余文本，避免单格信息过载。
        )
    }
}

private fun List<LedgerEntry>.filterWithinWeek(anchorDate: LocalDate): List<LedgerEntry> {
    val weekStart = anchorDate.startOfWeek()
    val weekEnd = weekStart.plusDays(6)
    return filter { entry ->
        val date = entry.timestamp.toLocalDate()
        !date.isBefore(weekStart) && !date.isAfter(weekEnd)
    }
}

private fun List<LedgerEntry>.filterWithinMonth(month: YearMonth): List<LedgerEntry> {
    return filter { YearMonth.from(it.timestamp.toLocalDate()) == month }
}

private fun List<LedgerEntry>.filterWithinYear(year: Int): List<LedgerEntry> {
    return filter { it.timestamp.toLocalDate().year == year }
}

private fun List<LedgerEntry>.summarize(): LedgerAmountSummary {
    val income = filter { it.direction == MoneyDirection.INCOME }.sumOf { it.amount }
    val expense = filter { it.direction == MoneyDirection.EXPENSE }.sumOf { it.amount }
    return LedgerAmountSummary(
        income = income,
        expense = expense,
    )
}

private fun buildOverviewPeriodTitle(
    range: LedgerRangeFilter,
    today: LocalDate,
): String {
    return when (range) {
        LedgerRangeFilter.TODAY -> "今日账单"
        LedgerRangeFilter.WEEK -> "本周账单"
        LedgerRangeFilter.MONTH -> "${YearMonth.from(today).format(MONTH_LABEL_FORMATTER)}账单"
        LedgerRangeFilter.YEAR -> "${today.year} 年账单"
        LedgerRangeFilter.CALENDAR -> "今日账单"
    }
}

/** 把预算金额与本月支出映射成总览和账本页可直接消费的展示状态。 */
private fun buildBudgetState(
    month: YearMonth,
    monthEntries: List<LedgerEntry>,
    monthlyBudgets: Map<String, Double>,
): LedgerBudgetState {
    val budgetAmount = monthlyBudgets[month.toString()]
    val spentAmount = monthEntries
        .filter { it.direction == MoneyDirection.EXPENSE }
        .sumOf { it.amount }
    if (budgetAmount == null || budgetAmount <= 0.0) {
        return LedgerBudgetState(
            amountLabel = null,
            spentLabel = CurrencyFormatter.format(spentAmount),
            remainingLabel = "未设置",
            ratio = 0f,
        )
    }

    val remainingAmount = budgetAmount - spentAmount
    val rawRatio = if (budgetAmount == 0.0) 0.0 else spentAmount / budgetAmount
    return LedgerBudgetState(
        amountLabel = CurrencyFormatter.format(budgetAmount),
        spentLabel = CurrencyFormatter.format(spentAmount),
        remainingLabel = if (remainingAmount >= 0.0) {
            "剩余 ${CurrencyFormatter.format(remainingAmount)}"
        } else {
            "超出 ${CurrencyFormatter.format(remainingAmount.absoluteValue)}"
        },
        ratio = rawRatio.coerceIn(0.0, 1.0).toFloat(),
    )
}

private fun formatAmountByDirection(entry: LedgerEntry): String {
    val sign = if (entry.direction == MoneyDirection.INCOME) "+" else "-"
    return "$sign${CurrencyFormatter.format(entry.amount)}"
}

private fun formatSignedCurrency(amount: Double): String {
    val sign = when {
        amount > 0.0 -> "+"
        amount < 0.0 -> "-"
        else -> ""
    }
    return sign + CurrencyFormatter.format(amount.absoluteValue)
}

private fun formatCompactCurrency(amount: Double): String {
    return when {
        amount >= 10_000 -> "${ONE_DECIMAL_FORMAT.format(amount / 10_000)}w"
        amount >= 1_000 -> "${ONE_DECIMAL_FORMAT.format(amount / 1_000)}k"
        else -> INTEGER_FORMAT.format(amount)
    }
}

private fun LocalDate.startOfWeek(): LocalDate = with(DayOfWeek.MONDAY)

private fun Long.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

private data class LedgerAmountSummary(
    val income: Double,
    val expense: Double,
) {
    val net: Double = income - expense
}

private data class LedgerBudgetState(
    val amountLabel: String?,
    val spentLabel: String,
    val remainingLabel: String,
    val ratio: Float,
)

private data class LedgerSourceSnapshot(
    val entries: List<LedgerEntry>,
    val pendingCount: Int,
    val monthlyBudgets: Map<String, Double>,
    val range: LedgerRangeFilter,
)

private val ONE_DECIMAL_FORMAT = DecimalFormat("0.#")
private val INTEGER_FORMAT = DecimalFormat("0")
private val MONTH_LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINA)
