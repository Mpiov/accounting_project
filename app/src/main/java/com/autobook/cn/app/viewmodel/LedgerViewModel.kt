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
import com.autobook.cn.feature.ledger.model.LedgerBillCycleUiModel
import com.autobook.cn.feature.ledger.model.LedgerCalendarDayUiModel
import com.autobook.cn.feature.ledger.model.LedgerCategoryBreakdownUiModel
import com.autobook.cn.feature.ledger.model.LedgerInsightUiModel
import com.autobook.cn.feature.ledger.model.LedgerItemUiModel
import com.autobook.cn.feature.ledger.model.LedgerRangeFilter
import com.autobook.cn.feature.ledger.model.LedgerTrendPointUiModel
import com.autobook.cn.feature.ledger.model.LedgerUiState
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
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** 账本首页 ViewModel，负责筛选、统计汇总和首页信息架构映射。 */
@HiltViewModel
class LedgerViewModel @Inject constructor(
    ledgerRepository: LedgerRepository,
    pendingRepository: PendingRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val selectedRange = MutableStateFlow(LedgerRangeFilter.MONTH)
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val displayedMonth = MutableStateFlow(YearMonth.now())
    private val calendarSelection = combine(selectedDate, displayedMonth) { date, month -> date to month }
    private val sourceSnapshot = combine(
        ledgerRepository.observeEntries(),
        pendingRepository.observeEntries(),
        settingsRepository.settings,
        searchQuery,
        selectedRange,
    ) { entries, pending, settings, query, range ->
        LedgerSourceSnapshot(
            entries = entries,
            pendingCount = pending.size,
            monthlyBudgets = settings.monthlyBudgets,
            query = query,
            range = range,
        )
    }

    /** 把账本记录、待确认数量和筛选条件组合成首页状态。 */
    val uiState: StateFlow<LedgerUiState> = combine(sourceSnapshot, calendarSelection) { snapshot, selection ->
        snapshot to selection
    }
        .mapLatest { (snapshot, selection) ->
            // 首页会对全量账本做多轮筛选、排序、分组与统计，这里显式放到后台线程，避免首屏和切筛选时阻塞主线程。
            withContext(Dispatchers.Default) {
                buildLedgerUiState(
                    snapshot = snapshot,
                    selectedDate = selection.first,
                    displayedMonth = selection.second,
                )
            }
        }
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState(),
    )

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setRange(range: LedgerRangeFilter) {
        if (range == LedgerRangeFilter.CALENDAR) {
            displayedMonth.value = YearMonth.from(selectedDate.value)
        }
        selectedRange.value = range
    }

    fun showPreviousMonth() {
        updateDisplayedMonth(displayedMonth.value.minusMonths(1))
    }

    fun showNextMonth() {
        updateDisplayedMonth(displayedMonth.value.plusMonths(1))
    }

    fun selectCalendarDate(date: LocalDate) {
        selectedDate.value = date
        displayedMonth.value = YearMonth.from(date)
        selectedRange.value = LedgerRangeFilter.CALENDAR
    }

    private fun updateDisplayedMonth(month: YearMonth) {
        displayedMonth.value = month
        selectedDate.value = month.atDay(selectedDate.value.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
        selectedRange.value = LedgerRangeFilter.CALENDAR
    }
}

/**
 * 构建总览页最终 UI 状态。
 *
 * 用途说明：
 * - 把首页所有筛选、分组、统计和 UI 映射收口到一个纯计算函数中，便于统一切到后台线程执行。
 *
 * 参数说明：
 * - snapshot：当前账本、待确认数量、预算和筛选条件快照。
 * - selectedDate：当前选中的日历日期。
 * - displayedMonth：当前正在展示的日历月份。
 */
private fun buildLedgerUiState(
    snapshot: LedgerSourceSnapshot,
    selectedDate: LocalDate,
    displayedMonth: YearMonth,
): LedgerUiState {
    val entries = snapshot.entries
    val pendingCount = snapshot.pendingCount
    val query = snapshot.query
    val range = snapshot.range
    val today = LocalDate.now()
    val anchorDate = if (range == LedgerRangeFilter.CALENDAR) selectedDate else today
    val filtered = entries
        .filterByRange(range = range, selectedDate = selectedDate, today = today)
        .filterByQuery(query)
        .sortedByDescending { it.timestamp }
    val filteredSummary = filtered.summarize()
    val weekEntries = entries.filterWithinWeek(anchorDate)
    val monthEntries = entries.filterWithinMonth(YearMonth.from(anchorDate))
    val yearEntries = entries.filterWithinYear(anchorDate.year)
    val trend = buildTrend(entries = entries, range = range, anchorDate = anchorDate)
    val categorySource = filtered.ifEmpty { monthEntries }
    val categoryBreakdown = buildCategoryBreakdown(categorySource)
    val topCategory = categoryBreakdown.firstOrNull()
    val autoCount = filtered.count { !it.isManualEntry() }
    val manualCount = filtered.count { it.isManualEntry() }
    val budgetState = buildBudgetState(
        month = YearMonth.from(anchorDate),
        monthEntries = monthEntries,
        monthlyBudgets = snapshot.monthlyBudgets,
    )

    return LedgerUiState(
        periodTitle = buildPeriodTitle(range = range, selectedDate = selectedDate, today = today),
        periodSubtitle = buildPeriodSubtitle(
            range = range,
            query = query,
            filteredCount = filtered.size,
            autoCount = autoCount,
            manualCount = manualCount,
        ),
        incomeSummary = CurrencyFormatter.format(filteredSummary.income),
        expenseSummary = CurrencyFormatter.format(filteredSummary.expense),
        netSummary = formatSignedCurrency(filteredSummary.net),
        netPositive = filteredSummary.net >= 0.0,
        pendingCount = pendingCount,
        searchQuery = query,
        selectedRange = range,
        rangeSummary = buildRangeSummary(
            range = range,
            selectedDate = selectedDate,
            calendarMonth = displayedMonth,
            today = today,
        ),
        calendarMonthLabel = displayedMonth.format(MONTH_LABEL_FORMATTER),
        selectedDateSummary = if (range == LedgerRangeFilter.CALENDAR) {
            buildSelectedDateSummary(date = selectedDate, entries = filtered)
        } else {
            null
        },
        calendarDays = buildLedgerCalendarDays(
            entries = entries,
            displayedMonth = displayedMonth,
            selectedDate = selectedDate,
            today = today,
        ),
        billCycles = buildBillCycles(
            anchorDate = anchorDate,
            weekEntries = weekEntries,
            monthEntries = monthEntries,
            yearEntries = yearEntries,
        ),
        insights = buildInsights(
            allEntries = entries,
            pendingCount = pendingCount,
            weekEntries = weekEntries,
            monthEntries = monthEntries,
        ),
        trendTitle = trend.title,
        trendSubtitle = trend.subtitle,
        trendPoints = trend.points,
        categoryBreakdown = categoryBreakdown,
        budgetAmountLabel = budgetState.amountLabel,
        budgetSpentLabel = budgetState.spentLabel,
        budgetRemainingLabel = budgetState.remainingLabel,
        budgetUsageSummary = budgetState.summary,
        budgetUsageRatio = budgetState.ratio,
        budgetExceeded = budgetState.exceeded,
        highlightMessage = buildHighlightMessage(
            pendingCount = pendingCount,
            filteredSummary = filteredSummary,
            topCategory = topCategory,
        ),
        entriesSummary = buildEntriesSummary(
            filteredCount = filtered.size,
            autoCount = autoCount,
            manualCount = manualCount,
        ),
        entries = filtered.map { entry ->
            LedgerItemUiModel(
                id = entry.id,
                merchant = entry.merchant,
                amount = formatAmountByDirection(entry),
                subtitle = "${entry.channel} · ${TimeFormatter.format(entry.timestamp)}",
                category = entry.category,
                note = entry.note,
                isManual = entry.isManualEntry(),
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
        LedgerRangeFilter.ALL -> this
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

internal fun buildCategoryBreakdown(entries: List<LedgerEntry>): List<LedgerCategoryBreakdownUiModel> {
    val expenseEntries = entries.filter { it.direction == MoneyDirection.EXPENSE }
    val totalExpense = expenseEntries.sumOf { it.amount }
    if (totalExpense <= 0.0) return emptyList()

    return expenseEntries
        .groupBy { it.category.ifBlank { "未分类" } }
        .map { (category, groupedEntries) ->
            val amount = groupedEntries.sumOf { it.amount }
            LedgerCategoryBreakdownUiModel(
                category = category,
                amount = CurrencyFormatter.format(amount),
                ratioLabel = "${((amount / totalExpense) * 100).roundToInt()}%",
                ratioValue = (amount / totalExpense).toFloat(),
            )
        }
        .sortedByDescending { it.ratioValue }
        .take(4)
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
        entryCount = size,
    )
}

private fun buildPeriodTitle(
    range: LedgerRangeFilter,
    selectedDate: LocalDate,
    today: LocalDate,
): String {
    return when (range) {
        LedgerRangeFilter.TODAY -> "今日账单"
        LedgerRangeFilter.WEEK -> "本周账单"
        LedgerRangeFilter.MONTH -> "${YearMonth.from(today).format(MONTH_LABEL_FORMATTER)}账单"
        LedgerRangeFilter.YEAR -> "${today.year} 年账单"
        LedgerRangeFilter.CALENDAR -> selectedDate.format(CALENDAR_TITLE_FORMATTER)
        LedgerRangeFilter.ALL -> "全部流水"
    }
}

private fun buildPeriodSubtitle(
    range: LedgerRangeFilter,
    query: String,
    filteredCount: Int,
    autoCount: Int,
    manualCount: Int,
): String {
    val base = if (query.isNotBlank()) {
        "搜索 “$query” 命中 $filteredCount 笔"
    } else {
        when (range) {
            LedgerRangeFilter.TODAY -> "聚焦今天的入账和支出变化"
            LedgerRangeFilter.WEEK -> "按周回看最近 7 天现金流"
            LedgerRangeFilter.MONTH -> "月度账本适合看预算和分类分布"
            LedgerRangeFilter.YEAR -> "年度账本更适合看长期趋势"
            LedgerRangeFilter.CALENDAR -> "切到具体日期，逐天复盘收支"
            LedgerRangeFilter.ALL -> "完整流水会保留自动记账与手动补记"
        }
    }
    return "$base · 自动 $autoCount / 手动 $manualCount"
}

private fun buildRangeSummary(
    range: LedgerRangeFilter,
    selectedDate: LocalDate,
    calendarMonth: YearMonth,
    today: LocalDate,
): String {
    return when (range) {
        LedgerRangeFilter.TODAY -> today.format(DATE_LABEL_FORMATTER)
        LedgerRangeFilter.WEEK -> {
            val start = today.startOfWeek()
            "${start.format(SHORT_DATE_FORMATTER)} - ${start.plusDays(6).format(SHORT_DATE_FORMATTER)}"
        }
        LedgerRangeFilter.MONTH -> YearMonth.from(today).format(MONTH_LABEL_FORMATTER)
        LedgerRangeFilter.YEAR -> "${today.year} 年"
        LedgerRangeFilter.CALENDAR -> "${calendarMonth.format(MONTH_LABEL_FORMATTER)} · ${selectedDate.format(DATE_LABEL_FORMATTER)}"
        LedgerRangeFilter.ALL -> "本地账本全部记录"
    }
}

private fun buildSelectedDateSummary(
    date: LocalDate,
    entries: List<LedgerEntry>,
): String {
    val summary = entries.summarize()
    val weekday = date.format(WEEKDAY_LABEL_FORMATTER)
    return buildString {
        append(date.format(DATE_LABEL_FORMATTER))
        append(" ")
        append(weekday)
        append(" · 收入 ")
        append(CurrencyFormatter.format(summary.income))
        append(" · 支出 ")
        append(CurrencyFormatter.format(summary.expense))
        append(" · ")
        append(summary.entryCount)
        append(" 笔")
    }
}

private fun buildBillCycles(
    anchorDate: LocalDate,
    weekEntries: List<LedgerEntry>,
    monthEntries: List<LedgerEntry>,
    yearEntries: List<LedgerEntry>,
): List<LedgerBillCycleUiModel> {
    val weekStart = anchorDate.startOfWeek()
    return listOf(
        buildBillCycleCard(
            title = "周账单",
            periodLabel = "${weekStart.format(SHORT_DATE_FORMATTER)} - ${weekStart.plusDays(6).format(SHORT_DATE_FORMATTER)}",
            entries = weekEntries,
        ),
        buildBillCycleCard(
            title = "月账单",
            periodLabel = YearMonth.from(anchorDate).format(MONTH_LABEL_FORMATTER),
            entries = monthEntries,
        ),
        buildBillCycleCard(
            title = "年账单",
            periodLabel = "${anchorDate.year} 年",
            entries = yearEntries,
        ),
    )
}

private fun buildBillCycleCard(
    title: String,
    periodLabel: String,
    entries: List<LedgerEntry>,
): LedgerBillCycleUiModel {
    val summary = entries.summarize()
    return LedgerBillCycleUiModel(
        title = title,
        periodLabel = periodLabel,
        incomeSummary = CurrencyFormatter.format(summary.income),
        expenseSummary = CurrencyFormatter.format(summary.expense),
        netSummary = formatSignedCurrency(summary.net),
        entriesLabel = "${summary.entryCount} 笔记录",
        netPositive = summary.net >= 0.0,
    )
}

private fun buildInsights(
    allEntries: List<LedgerEntry>,
    pendingCount: Int,
    weekEntries: List<LedgerEntry>,
    monthEntries: List<LedgerEntry>,
): List<LedgerInsightUiModel> {
    val weekSummary = weekEntries.summarize()
    val monthSummary = monthEntries.summarize()
    val monthDays = YearMonth.now().lengthOfMonth().coerceAtLeast(1)
    val averageExpense = if (monthSummary.expense == 0.0) 0.0 else monthSummary.expense / monthDays
    val autoCount = allEntries.count { !it.isManualEntry() }
    val autoRate = if (allEntries.isEmpty()) 0 else ((autoCount * 100.0) / allEntries.size).roundToInt()
    val activeDays = monthEntries.map { it.timestamp.toLocalDate() }.distinct().size

    return listOf(
        LedgerInsightUiModel(
            title = "本周结余",
            value = formatSignedCurrency(weekSummary.net),
            supporting = "${weekSummary.entryCount} 笔 · 周内现金流",
        ),
        LedgerInsightUiModel(
            title = "本月日均支出",
            value = CurrencyFormatter.format(averageExpense),
            supporting = "按 $monthDays 天摊算 · 活跃 $activeDays 天",
        ),
        LedgerInsightUiModel(
            title = "自动入账率",
            value = "$autoRate%",
            supporting = "自动 $autoCount / 全部 ${allEntries.size}",
        ),
        LedgerInsightUiModel(
            title = "待确认",
            value = "$pendingCount 笔",
            supporting = if (pendingCount == 0) "当前无需复核" else "建议优先处理避免统计偏差",
        ),
    )
}

private fun buildTrend(
    entries: List<LedgerEntry>,
    range: LedgerRangeFilter,
    anchorDate: LocalDate,
): LedgerTrend {
    return if (range == LedgerRangeFilter.YEAR || range == LedgerRangeFilter.ALL) {
        buildMonthlyTrend(entries = entries, anchorDate = anchorDate)
    } else {
        buildDailyTrend(entries = entries, anchorDate = anchorDate)
    }
}

private fun buildDailyTrend(
    entries: List<LedgerEntry>,
    anchorDate: LocalDate,
): LedgerTrend {
    val days = (6L downTo 0L).map { anchorDate.minusDays(it) }
    val expensesByDay = entries
        .filter { it.direction == MoneyDirection.EXPENSE }
        .groupBy { it.timestamp.toLocalDate() }
        .mapValues { (_, groupedEntries) -> groupedEntries.sumOf { it.amount } }
    val maxExpense = days.maxOfOrNull { expensesByDay[it] ?: 0.0 } ?: 0.0
    val points = days.map { date ->
        val amount = expensesByDay[date] ?: 0.0
        LedgerTrendPointUiModel(
            label = date.format(TREND_DAY_LABEL_FORMATTER),
            amountLabel = if (amount == 0.0) "0" else formatCompactCurrency(amount),
            ratio = if (maxExpense == 0.0) 0f else (amount / maxExpense).toFloat(),
            isCurrent = date == anchorDate,
        )
    }
    return LedgerTrend(
        title = "近 7 天支出走势",
        subtitle = "快速看消费波动，判断最近是否有异常峰值",
        points = points,
    )
}

private fun buildMonthlyTrend(
    entries: List<LedgerEntry>,
    anchorDate: LocalDate,
): LedgerTrend {
    val months = (5L downTo 0L).map { YearMonth.from(anchorDate).minusMonths(it) }
    val expensesByMonth = entries
        .filter { it.direction == MoneyDirection.EXPENSE }
        .groupBy { YearMonth.from(it.timestamp.toLocalDate()) }
        .mapValues { (_, groupedEntries) -> groupedEntries.sumOf { it.amount } }
    val maxExpense = months.maxOfOrNull { expensesByMonth[it] ?: 0.0 } ?: 0.0
    val currentMonth = YearMonth.from(anchorDate)
    val points = months.map { month ->
        val amount = expensesByMonth[month] ?: 0.0
        LedgerTrendPointUiModel(
            label = month.format(TREND_MONTH_LABEL_FORMATTER),
            amountLabel = if (amount == 0.0) "0" else formatCompactCurrency(amount),
            ratio = if (maxExpense == 0.0) 0f else (amount / maxExpense).toFloat(),
            isCurrent = month == currentMonth,
        )
    }
    return LedgerTrend(
        title = "近 6 个月支出走势",
        subtitle = "年度账单下更适合观察月度消费节奏",
        points = points,
    )
}

private fun buildHighlightMessage(
    pendingCount: Int,
    filteredSummary: LedgerAmountSummary,
    topCategory: LedgerCategoryBreakdownUiModel?,
): String {
    return when {
        pendingCount > 0 -> "还有 $pendingCount 笔待确认记录，处理后统计会更准确"
        filteredSummary.entryCount == 0 -> "当前账期还没有记录，手动补记一笔也能快速填满首页"
        topCategory != null -> "当前支出主要集中在 ${topCategory.category}，占比 ${topCategory.ratioLabel}"
        filteredSummary.net >= 0.0 -> "当前账期保持结余，整体现金流比较稳"
        else -> "支出高于收入，建议结合分类分布回看重点消费"
    }
}

private fun buildEntriesSummary(
    filteredCount: Int,
    autoCount: Int,
    manualCount: Int,
): String {
    return if (filteredCount == 0) {
        "当前筛选下暂无流水"
    } else {
        "共 $filteredCount 笔 · 自动 $autoCount · 手动 $manualCount"
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
            summary = "设置预算后会自动更新本月进度。",
            ratio = 0f,
            exceeded = false,
        )
    }

    val remainingAmount = budgetAmount - spentAmount
    val rawRatio = if (budgetAmount == 0.0) 0.0 else spentAmount / budgetAmount
    val usagePercent = (rawRatio * 100).roundToInt()
    return LedgerBudgetState(
        amountLabel = CurrencyFormatter.format(budgetAmount),
        spentLabel = CurrencyFormatter.format(spentAmount),
        remainingLabel = if (remainingAmount >= 0.0) {
            "剩余 ${CurrencyFormatter.format(remainingAmount)}"
        } else {
            "超出 ${CurrencyFormatter.format(remainingAmount.absoluteValue)}"
        },
        summary = if (remainingAmount >= 0.0) {
            "本月预算已使用 $usagePercent%，继续保持这个节奏比较稳。"
        } else {
            "本月预算已超支，建议优先回看分类分布和高峰消费。"
        },
        ratio = rawRatio.coerceIn(0.0, 1.0).toFloat(),
        exceeded = remainingAmount < 0.0,
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

private fun LedgerEntry.isManualEntry(): Boolean = editedManually || traceSource == "manual"

private fun LocalDate.startOfWeek(): LocalDate = with(DayOfWeek.MONDAY)

private fun Long.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

private data class LedgerAmountSummary(
    val income: Double,
    val expense: Double,
    val entryCount: Int,
) {
    val net: Double = income - expense
}

private data class LedgerTrend(
    val title: String,
    val subtitle: String,
    val points: List<LedgerTrendPointUiModel>,
)

private data class LedgerBudgetState(
    val amountLabel: String?,
    val spentLabel: String,
    val remainingLabel: String,
    val summary: String,
    val ratio: Float,
    val exceeded: Boolean,
)

private data class LedgerSourceSnapshot(
    val entries: List<LedgerEntry>,
    val pendingCount: Int,
    val monthlyBudgets: Map<String, Double>,
    val query: String,
    val range: LedgerRangeFilter,
)

private val ONE_DECIMAL_FORMAT = DecimalFormat("0.#")
private val INTEGER_FORMAT = DecimalFormat("0")
private val MONTH_LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINA)
private val DATE_LABEL_FORMATTER = DateTimeFormatter.ofPattern("M 月 d 日", Locale.CHINA)
private val SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("M.d", Locale.CHINA)
private val WEEKDAY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)
private val CALENDAR_TITLE_FORMATTER = DateTimeFormatter.ofPattern("M 月 d 日账单", Locale.CHINA)
private val TREND_DAY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)
private val TREND_MONTH_LABEL_FORMATTER = DateTimeFormatter.ofPattern("M 月", Locale.CHINA)
