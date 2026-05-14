package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
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
 * 用途说明：
 * - 账本主页面的展示模式，支持“按月看”与“按年看”两种总览方式。
 */
enum class LedgerBoardMode {
    MONTH,
    YEAR,
}

/**
 * 用途说明：
 * - 月账单总览行模型，用于主账本页的月份列表展示。
 *
 * 参数说明：
 * - yearMonth：当前行对应的年月，点击后会带着它跳入月份详情页。
 * - monthLabel：月份文本。
 * - incomeLabel：月收入文本。
 * - expenseLabel：月支出文本。
 * - netLabel：月结余文本。
 * - entryCountLabel：本月记录数量文本。
 * - isCurrentMonth：是否是当前自然月，用于高亮当前月份。
 */
data class LedgerBoardMonthRowUiModel(
    val yearMonth: YearMonth,
    val monthLabel: String,
    val incomeLabel: String,
    val expenseLabel: String,
    val netLabel: String,
    val entryCountLabel: String,
    val isCurrentMonth: Boolean,
)

/**
 * 用途说明：
 * - 年账单总览行模型，用于切到“年账单”时展示各年的汇总。
 *
 * 参数说明：
 * - year：当前行对应年份。
 * - incomeLabel：年收入文本。
 * - expenseLabel：年支出文本。
 * - netLabel：年结余文本。
 * - entryCountLabel：当年记录数量文本。
 * - selected：是否为当前选中年份。
 */
data class LedgerBoardYearRowUiModel(
    val year: Int,
    val incomeLabel: String,
    val expenseLabel: String,
    val netLabel: String,
    val entryCountLabel: String,
    val selected: Boolean,
)

/**
 * 用途说明：
 * - 账本主页面聚合状态，负责驱动顶部年份切换、摘要卡和月份/年份列表。
 *
 * 参数说明：
 * - selectedYear：当前选中的年份。
 * - availableYears：可切换的年份集合。
 * - mode：当前总览模式。
 * - summaryTitle：顶部摘要卡标题。
 * - summaryNetLabel：顶部摘要卡的结余文本。
 * - summaryIncomeLabel：顶部摘要卡的收入文本。
 * - summaryExpenseLabel：顶部摘要卡的支出文本。
 * - summaryHint：顶部辅助说明。
 * - topRightLabel：右上角补充标签，用于增强页面信息密度。
 * - monthRows：月模式下的月份列表。
 * - yearRows：年模式下的年份列表。
 */
data class LedgerBoardUiState(
    val selectedYear: Int = LocalDate.now().year,
    val availableYears: List<Int> = listOf(LocalDate.now().year),
    val mode: LedgerBoardMode = LedgerBoardMode.MONTH,
    val summaryTitle: String = "总结余",
    val summaryNetLabel: String = "0.00",
    val summaryIncomeLabel: String = "0.00",
    val summaryExpenseLabel: String = "0.00",
    val summaryHint: String = "",
    val topRightLabel: String = "月账单",
    val monthRows: List<LedgerBoardMonthRowUiModel> = emptyList(),
    val yearRows: List<LedgerBoardYearRowUiModel> = emptyList(),
)

/**
 * 用途说明：
 * - 账本主页面 ViewModel，负责把原始账单映射成“按月总览”的结构。
 */
@HiltViewModel
class LedgerBoardViewModel @Inject constructor(
    ledgerRepository: LedgerRepository,
) : ViewModel() {
    private val selectedYear = MutableStateFlow(LocalDate.now().year)
    private val overviewMode = MutableStateFlow(LedgerBoardMode.MONTH)

    /**
     * 用途说明：
     * - 输出账本主页状态，随着年份、模式和账单源数据变化自动刷新。
     */
    val uiState: StateFlow<LedgerBoardUiState> = combine(
        ledgerRepository.observeEntries(),
        selectedYear,
        overviewMode,
    ) { entries, year, mode ->
        LedgerBoardSnapshot(
            entries = entries,
            selectedYear = year,
            mode = mode,
        )
    }
        .mapLatest { snapshot ->
            // 月/年账单页会反复做按月、按年分组统计，这里放到后台线程，避免切 Tab 时直接卡住 UI。
            withContext(Dispatchers.Default) {
                buildLedgerBoardUiState(snapshot)
            }
        }
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerBoardUiState(),
    )

    /**
     * 用途说明：
     * - 切换账本主页当前查看的年份。
     *
     * 参数说明：
     * - year：用户选择的新年份。
     */
    fun selectYear(year: Int) {
        selectedYear.value = year
    }

    /**
     * 用途说明：
     * - 切换账本主页当前展示模式。
     *
     * 参数说明：
     * - mode：新的展示模式。
     */
    fun selectMode(mode: LedgerBoardMode) {
        overviewMode.value = mode
    }
}

/**
 * 账本总览页的输入快照。
 *
 * 用途说明：
 * - 把账单源数据、年份选择和展示模式打包，方便交给后台纯计算函数处理。
 */
private data class LedgerBoardSnapshot(
    val entries: List<LedgerEntry>,
    val selectedYear: Int,
    val mode: LedgerBoardMode,
)

/**
 * 构建账本总览页最终状态。
 *
 * 参数说明：
 * - snapshot：账本总览页当前输入快照。
 */
private fun buildLedgerBoardUiState(
    snapshot: LedgerBoardSnapshot,
): LedgerBoardUiState {
    val availableYears = buildAvailableYears(snapshot.entries)
    val effectiveYear = snapshot.selectedYear.takeIf { it in availableYears }
        ?: availableYears.firstOrNull()
        ?: LocalDate.now().year
    val summary = snapshot.entries.filterWithinYear(effectiveYear).summarize()
    val monthRows = buildMonthRows(snapshot.entries, effectiveYear)
    val yearRows = buildYearRows(snapshot.entries, availableYears, effectiveYear)

    return LedgerBoardUiState(
        selectedYear = effectiveYear,
        availableYears = availableYears,
        mode = snapshot.mode,
        summaryTitle = "总结余",
        summaryNetLabel = formatBoardMetric(summary.net, signed = true),
        summaryIncomeLabel = formatBoardMetric(summary.income),
        summaryExpenseLabel = formatBoardMetric(summary.expense),
        summaryHint = "",
        topRightLabel = if (snapshot.mode == LedgerBoardMode.MONTH) {
            "共${monthRows.size}个月"
        } else {
            "近${yearRows.size}年"
        },
        monthRows = monthRows,
        yearRows = yearRows,
    )
}

/**
 * 用途说明：
 * - 构建可切换年份列表；即使账本还没有数据，也保留当前年份。
 *
 * 参数说明：
 * - entries：原始账单列表。
 */
private fun buildAvailableYears(
    entries: List<LedgerEntry>,
): List<Int> {
    val currentYear = LocalDate.now().year
    return (entries.map { it.timestamp.toLedgerBoardLocalDate().year } + currentYear)
        .distinct()
        .sortedDescending()
}

/**
 * 用途说明：
 * - 构建按月汇总列表。
 *
 * 参数说明：
 * - entries：原始账单列表。
 * - year：目标年份。
 */
private fun buildMonthRows(
    entries: List<LedgerEntry>,
    year: Int,
): List<LedgerBoardMonthRowUiModel> {
    val currentMonth = YearMonth.now()
    val lastMonthValue = if (year == currentMonth.year) currentMonth.monthValue else 12

    return (lastMonthValue downTo 1).map { monthValue ->
        val yearMonth = YearMonth.of(year, monthValue)
        val summary = entries.filterWithinMonth(yearMonth).summarize()
        LedgerBoardMonthRowUiModel(
            yearMonth = yearMonth,
            monthLabel = "${monthValue}月",
            incomeLabel = formatBoardMetric(summary.income),
            expenseLabel = formatBoardMetric(summary.expense),
            netLabel = formatBoardMetric(summary.net, signed = true),
            entryCountLabel = "${summary.entryCount}笔",
            isCurrentMonth = yearMonth == currentMonth,
        )
    }
}

/**
 * 用途说明：
 * - 构建按年汇总列表。
 *
 * 参数说明：
 * - entries：原始账单列表。
 * - availableYears：可展示年份列表。
 * - selectedYear：当前选中年份，用于高亮对应行。
 */
private fun buildYearRows(
    entries: List<LedgerEntry>,
    availableYears: List<Int>,
    selectedYear: Int,
): List<LedgerBoardYearRowUiModel> {
    return availableYears.map { year ->
        val summary = entries.filterWithinYear(year).summarize()
        LedgerBoardYearRowUiModel(
            year = year,
            incomeLabel = formatBoardMetric(summary.income),
            expenseLabel = formatBoardMetric(summary.expense),
            netLabel = formatBoardMetric(summary.net, signed = true),
            entryCountLabel = "${summary.entryCount}笔",
            selected = year == selectedYear,
        )
    }
}

/**
 * 用途说明：
 * - 按月份过滤账单。
 *
 * 参数说明：
 * - month：目标年月。
 */
private fun List<LedgerEntry>.filterWithinMonth(
    month: YearMonth,
): List<LedgerEntry> = filter { YearMonth.from(it.timestamp.toLedgerBoardLocalDate()) == month }

/**
 * 用途说明：
 * - 按年份过滤账单。
 *
 * 参数说明：
 * - year：目标年份。
 */
private fun List<LedgerEntry>.filterWithinYear(
    year: Int,
): List<LedgerEntry> = filter { it.timestamp.toLedgerBoardLocalDate().year == year }

/**
 * 用途说明：
 * - 统计一组账单的收入、支出和净额。
 */
private fun List<LedgerEntry>.summarize(): LedgerBoardAmountSummary {
    val income = filter { it.direction == MoneyDirection.INCOME }.sumOf { it.amount.absoluteValue }
    val expense = filter { it.direction == MoneyDirection.EXPENSE }.sumOf { it.amount.absoluteValue }
    return LedgerBoardAmountSummary(
        income = income,
        expense = expense,
        entryCount = size,
    )
}

/**
 * 用途说明：
 * - 将账本总览金额格式化成完整数字文本，优先保证金额具体可读。
 *
 * 参数说明：
 * - amount：待格式化金额。
 * - signed：是否需要主动补充 `+` / `-` 号。
 */
private fun formatBoardMetric(
    amount: Double,
    signed: Boolean = false,
): String {
    val sign = when {
        signed && amount > 0 -> "+"
        amount < 0 -> "-"
        else -> ""
    }
    val absAmount = amount.toAbsoluteValue()
    return sign + boardDecimalFormatter.format(absAmount)
}

/**
 * 用途说明：
 * - 单独封装绝对值计算，避免在格式化函数中反复出现符号判断。
 */
private fun Double.toAbsoluteValue(): Double = kotlin.math.abs(this)

/**
 * 用途说明：
 * - 账本主页汇总结构。
 */
private data class LedgerBoardAmountSummary(
    val income: Double,
    val expense: Double,
    val entryCount: Int,
) {
    val net: Double = income - expense
}

/**
 * 用途说明：
 * - 将毫秒时间戳转成本地日期，供账本主页按年和按月分组使用。
 */
private fun Long.toLedgerBoardLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

private val boardDecimalFormatter = DecimalFormat("#,##0.00")
