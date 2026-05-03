package com.autobook.cn.feature.ledger.model

import com.autobook.cn.core.model.MoneyDirection
import java.time.LocalDate

/**
 * 账本前端共享模型定义。
 *
 * 用途说明：
 * - 把账本相关的筛选枚举、页面状态和展示模型集中收口到独立模型文件。
 * - 月份详情页和账本列表复用这里的范围、日历和流水展示模型。
 */

/**
 * 账本页面支持的时间范围筛选。
 *
 * 用途说明：
 * - 统一描述总览页、月份详情页等场景使用的时间范围。
 */
enum class LedgerRangeFilter {
    TODAY,
    WEEK,
    MONTH,
    YEAR,
    CALENDAR,
}

/**
 * 月历中单个日期格子的展示模型。
 *
 * 参数说明：
 * - date：当前格子对应的自然日。
 * - label：给 UI 展示的日期文案。
 * - isCurrentMonth：当前日期是否属于正在展示的月份。
 * - hasEntries：当前日期是否存在流水。
 * - isSelected：当前日期是否为用户选中的日期。
 * - isToday：当前日期是否为今天。
 * - incomeLabel：当前日期收入摘要，没有则为空。
 * - expenseLabel：当前日期支出摘要，没有则为空。
 */
data class LedgerCalendarDayUiModel(
    val date: LocalDate,
    val label: String,
    val isCurrentMonth: Boolean,
    val hasEntries: Boolean,
    val isSelected: Boolean,
    val isToday: Boolean,
    val incomeLabel: String? = null,
    val expenseLabel: String? = null,
)

/**
 * 账本列表中单条流水的展示模型。
 *
 * 参数说明：
 * - id：流水主键。
 * - title：列表主标题，优先展示备注首行，没有备注时展示分类。
 * - amount：已经格式化好的金额文案。
 * - subtitle：列表副标题，通常包含渠道和时间。
 * - category：分类名称。
 * - direction：收支方向，用于决定颜色和符号语义。
 */
data class LedgerItemUiModel(
    val id: Long,
    val title: String,
    val amount: String,
    val subtitle: String,
    val category: String,
    val direction: MoneyDirection = MoneyDirection.EXPENSE,
)

/**
 * 月份详情页 UI 状态。
 *
 * 参数说明：
 * - periodTitle：当前页面主标题。
 * - searchQuery：当前搜索词。
 * - selectedRange：当前选中的时间范围。
 * - rangeSummary：范围摘要说明。
 * - calendarMonthLabel：当前月历月份标题。
 * - calendarDays：月历格子数据。
 * - entriesSummary：流水列表摘要。
 * - entries：流水列表项。
 */
data class LedgerUiState(
    val periodTitle: String = "本月账单",
    val searchQuery: String = "",
    val selectedRange: LedgerRangeFilter = LedgerRangeFilter.MONTH,
    val rangeSummary: String = "本地账本全部记录",
    val calendarMonthLabel: String = "",
    val calendarDays: List<LedgerCalendarDayUiModel> = emptyList(),
    val entriesSummary: String = "当前筛选下暂无流水",
    val entries: List<LedgerItemUiModel> = emptyList(),
)
