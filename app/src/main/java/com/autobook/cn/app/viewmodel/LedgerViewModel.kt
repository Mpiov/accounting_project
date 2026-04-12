package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.common.CurrencyFormatter
import com.autobook.cn.core.common.TimeFormatter
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.feature.ledger.LedgerCalendarDayUiModel
import com.autobook.cn.feature.ledger.LedgerItemUiModel
import com.autobook.cn.feature.ledger.LedgerRangeFilter
import com.autobook.cn.feature.ledger.LedgerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 账本首页 ViewModel，负责筛选、汇总和映射展示模型。 */
@HiltViewModel
class LedgerViewModel @Inject constructor(
    ledgerRepository: LedgerRepository,
    pendingRepository: PendingRepository,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val selectedRange = MutableStateFlow(LedgerRangeFilter.TODAY)
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val displayedMonth = MutableStateFlow(YearMonth.now())
    private val calendarSelection = combine(selectedDate, displayedMonth) { date, month -> date to month }

    /** 把账本记录、待确认数量和筛选条件组合成首页状态。 */
    val uiState: StateFlow<LedgerUiState> = combine(
        ledgerRepository.observeEntries(),
        pendingRepository.observeEntries(),
        searchQuery,
        selectedRange,
        calendarSelection,
    ) { entries, pending, query, range, selection ->
        val (date, month) = selection
        val filtered = entries
            .filterByRange(range = range, selectedDate = date)
            .filterByQuery(query)
        val income = filtered.filter { it.direction == MoneyDirection.INCOME }.sumOf { it.amount }
        val expense = filtered.filter { it.direction == MoneyDirection.EXPENSE }.sumOf { it.amount }
        LedgerUiState(
            incomeSummary = CurrencyFormatter.format(income),
            expenseSummary = CurrencyFormatter.format(expense),
            pendingCount = pending.size,
            searchQuery = query,
            selectedRange = range,
            calendarMonthLabel = month.format(MONTH_LABEL_FORMATTER),
            selectedDateSummary = if (range == LedgerRangeFilter.CALENDAR) {
                formatSelectedDateSummary(date = date, entryCount = filtered.size)
            } else {
                null
            },
            calendarDays = buildLedgerCalendarDays(
                entries = entries,
                displayedMonth = month,
                selectedDate = date,
            ),
            entries = filtered.map { entry ->
                LedgerItemUiModel(
                    id = entry.id,
                    merchant = entry.merchant,
                    amount = CurrencyFormatter.format(entry.amount),
                    subtitle = "${entry.channel} · ${TimeFormatter.format(entry.timestamp)}",
                    category = entry.category,
                    note = entry.note,
                    isManual = entry.editedManually || entry.traceSource == "manual",
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState(),
    )

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setRange(range: LedgerRangeFilter) {
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
        LedgerRangeFilter.MONTH -> filter { YearMonth.from(it.timestamp.toLocalDate()) == YearMonth.from(today) }
        LedgerRangeFilter.CALENDAR -> filter { it.timestamp.toLocalDate() == selectedDate }
        LedgerRangeFilter.ALL -> this
    }
}

/** 为月历视图生成固定 6 周网格，并标记有流水的日期。 */
internal fun buildLedgerCalendarDays(
    entries: List<LedgerEntry>,
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate = LocalDate.now(),
): List<LedgerCalendarDayUiModel> {
    val entryDates = entries.mapTo(mutableSetOf()) { it.timestamp.toLocalDate() }
    val firstDayOfMonth = displayedMonth.atDay(1)
    val gridStart = firstDayOfMonth.minusDays((firstDayOfMonth.dayOfWeek.value - 1).toLong())
    return List(42) { index ->
        val date = gridStart.plusDays(index.toLong())
        LedgerCalendarDayUiModel(
            date = date,
            label = date.dayOfMonth.toString(),
            isCurrentMonth = YearMonth.from(date) == displayedMonth,
            hasEntries = date in entryDates,
            isSelected = date == selectedDate,
            isToday = date == today,
        )
    }
}

private fun Long.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

private fun formatSelectedDateSummary(
    date: LocalDate,
    entryCount: Int,
): String {
    val weekday = date.format(WEEKDAY_LABEL_FORMATTER)
    return "${date.format(DATE_LABEL_FORMATTER)} $weekday · $entryCount 条记录"
}

private val MONTH_LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
private val DATE_LABEL_FORMATTER = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val WEEKDAY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)
