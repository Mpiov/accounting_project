package com.autobook.cn.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.common.CurrencyFormatter
import com.autobook.cn.core.common.TimeFormatter
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
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
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 用途说明：
 * - 月份详情页 ViewModel，负责承接“某个月”的日历、搜索与流水明细展示。
 */
@HiltViewModel
class LedgerMonthDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    ledgerRepository: LedgerRepository,
) : ViewModel() {
    private val initialMonth = savedStateHandle.get<String>("yearMonth")
        ?.runCatching { YearMonth.parse(this) }
        ?.getOrNull()
        ?: YearMonth.now()

    private val searchQuery = MutableStateFlow("")
    private val displayedMonth = MutableStateFlow(initialMonth)
    private val selectedDate = MutableStateFlow(buildInitialSelectedDate(initialMonth))
    private val selectedRange = MutableStateFlow(LedgerRangeFilter.MONTH)

    /**
     * 用途说明：
     * - 输出月份详情页状态，驱动顶部月份标题、日历和下方流水账列表。
     */
    val uiState: StateFlow<LedgerUiState> = combine(
        ledgerRepository.observeEntries(),
        searchQuery,
        displayedMonth,
        selectedDate,
        selectedRange,
    ) { entries, query, month, date, range ->
        val monthEntries = entries.filterWithinMonth(month)
        val sourceEntries = if (range == LedgerRangeFilter.CALENDAR) {
            monthEntries.filter { it.timestamp.toLedgerDetailLocalDate() == date }
        } else {
            monthEntries
        }
        val filteredEntries = sourceEntries
            .filterByQuery(query)
            .sortedByDescending { it.timestamp }

        LedgerUiState(
            periodTitle = if (range == LedgerRangeFilter.CALENDAR) {
                date.format(detailDayTitleFormatter)
            } else {
                month.format(detailMonthTitleFormatter)
            },
            periodSubtitle = buildDetailSubtitle(
                month = month,
                date = date,
                range = range,
                query = query,
                entryCount = filteredEntries.size,
            ),
            searchQuery = query,
            selectedRange = range,
            rangeSummary = if (range == LedgerRangeFilter.CALENDAR) {
                "再次点按已选日期，可返回整月流水"
            } else {
                "点击日历中的日期，可切到当天流水"
            },
            calendarMonthLabel = month.format(detailCalendarFormatter),
            selectedDateSummary = if (range == LedgerRangeFilter.CALENDAR) {
                "${date.format(detailDateFormatter)} · 当天 ${filteredEntries.size} 笔"
            } else {
                "${month.format(detailMonthSummaryFormatter)} · 共 ${filteredEntries.size} 笔"
            },
            calendarDays = buildLedgerCalendarDays(
                entries = monthEntries,
                displayedMonth = month,
                selectedDate = date,
                today = LocalDate.now(),
            ),
            entriesSummary = if (query.isBlank()) {
                if (range == LedgerRangeFilter.CALENDAR) {
                    "${date.format(detailDateFormatter)} · ${filteredEntries.size} 笔流水"
                } else {
                    "${month.format(detailMonthSummaryFormatter)} · ${filteredEntries.size} 笔流水"
                }
            } else {
                "搜索“$query” · 匹配 ${filteredEntries.size} 笔"
            },
            entries = filteredEntries.map { entry -> entry.toLedgerItemUiModel() },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState(calendarMonthLabel = initialMonth.format(detailCalendarFormatter)),
    )

    /**
     * 用途说明：
     * - 更新搜索关键字，按输入内容实时过滤当前月份或当前日期的流水账。
     *
     * 参数说明：
     * - query：用户输入的搜索关键词。
     */
    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    /**
     * 用途说明：
     * - 切换到上一个月份，并自动回到整月视图。
     */
    fun showPreviousMonth() {
        updateMonth(displayedMonth.value.minusMonths(1))
    }

    /**
     * 用途说明：
     * - 切换到下一个月份，并自动回到整月视图。
     */
    fun showNextMonth() {
        updateMonth(displayedMonth.value.plusMonths(1))
    }

    /**
     * 用途说明：
     * - 点击日历日期后切到当天流水；如果再次点击当前日期，则退回整月流水。
     *
     * 参数说明：
     * - date：用户点击的日期。
     */
    fun selectCalendarDate(date: LocalDate) {
        if (selectedRange.value == LedgerRangeFilter.CALENDAR && selectedDate.value == date) {
            selectedRange.value = LedgerRangeFilter.MONTH
        } else {
            selectedDate.value = date
            displayedMonth.value = YearMonth.from(date)
            selectedRange.value = LedgerRangeFilter.CALENDAR
        }
    }

    /**
     * 用途说明：
     * - 将当前界面切回整月范围，主要给月份翻页后复位使用。
     *
     * 参数说明：
     * - month：新的目标月份。
     */
    private fun updateMonth(month: YearMonth) {
        displayedMonth.value = month
        selectedDate.value = month.atDay(selectedDate.value.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
        selectedRange.value = LedgerRangeFilter.MONTH
    }
}

/**
 * 用途说明：
 * - 计算月份详情页初始选中日期；若是当前月份，则默认落在今天，否则落在该月 1 日。
 */
private fun buildInitialSelectedDate(
    month: YearMonth,
): LocalDate {
    val today = LocalDate.now()
    return if (month == YearMonth.from(today)) today else month.atDay(1)
}

/**
 * 用途说明：
 * - 构建月份详情页顶部副标题。
 *
 * 参数说明：
 * - month：当前展示月份。
 * - date：当前选中日期。
 * - range：当前范围模式。
 * - query：当前搜索词。
 * - entryCount：当前匹配记录数。
 */
private fun buildDetailSubtitle(
    month: YearMonth,
    date: LocalDate,
    range: LedgerRangeFilter,
    query: String,
    entryCount: Int,
): String {
    if (query.isNotBlank()) {
        return "在${month.format(detailMonthSummaryFormatter)}中搜索“$query”，共找到 $entryCount 笔"
    }
    return if (range == LedgerRangeFilter.CALENDAR) {
        "${date.format(detailDateFormatter)} 的流水明细，再次点按当天可返回整月"
    } else {
        "按日历回看 ${month.format(detailMonthSummaryFormatter)} 的整月流水"
    }
}

/**
 * 用途说明：
 * - 过滤指定月份账单。
 *
 * 参数说明：
 * - month：目标月份。
 */
private fun List<LedgerEntry>.filterWithinMonth(
    month: YearMonth,
): List<LedgerEntry> = filter { YearMonth.from(it.timestamp.toLedgerDetailLocalDate()) == month }

/**
 * 用途说明：
 * - 将领域账单模型映射成月份详情页的列表项。
 */
private fun LedgerEntry.toLedgerItemUiModel(): LedgerItemUiModel {
    return LedgerItemUiModel(
        id = id,
        merchant = merchant,
        amount = buildSignedAmountLabel(),
        subtitle = "${channel} · ${TimeFormatter.format(timestamp)}",
        category = category,
        note = note,
        isManual = editedManually || traceSource == "manual",
        direction = direction,
    )
}

/**
 * 用途说明：
 * - 为流水列表统一拼出带正负号的金额文本。
 */
private fun LedgerEntry.buildSignedAmountLabel(): String {
    val sign = if (direction == MoneyDirection.INCOME) "+" else "-"
    return "$sign${CurrencyFormatter.format(amount.absoluteValue)}"
}

/**
 * 用途说明：
 * - 将毫秒时间戳转换成本地日期，供月份详情页按月和按日过滤账单使用。
 */
private fun Long.toLedgerDetailLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

private val detailMonthTitleFormatter = DateTimeFormatter.ofPattern("yyyy年M月账单", Locale.CHINA)
private val detailCalendarFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
private val detailMonthSummaryFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
private val detailDayTitleFormatter = DateTimeFormatter.ofPattern("M月d日账单", Locale.CHINA)
private val detailDateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
