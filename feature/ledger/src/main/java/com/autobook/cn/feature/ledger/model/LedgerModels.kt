package com.autobook.cn.feature.ledger.model

import com.autobook.cn.core.model.MoneyDirection
import java.time.LocalDate

/**
 * 账本前端共享模型定义。
 *
 * 用途说明：
 * - 把账本相关的筛选枚举、页面状态和展示模型从旧版 `LedgerScreen` 中拆出来。
 * - 新版总览页、月份详情页和 ViewModel 统一依赖这里，避免继续和遗留页面文件耦合。
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
    ALL,
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
 * - merchant：商户或对方名称。
 * - amount：已经格式化好的金额文案。
 * - subtitle：列表副标题，通常包含渠道和时间。
 * - category：分类名称。
 * - note：备注，没有则为空。
 * - isManual：是否为手动补记。
 * - direction：收支方向，用于决定颜色和符号语义。
 */
data class LedgerItemUiModel(
    val id: Long,
    val merchant: String,
    val amount: String,
    val subtitle: String,
    val category: String,
    val note: String?,
    val isManual: Boolean,
    val direction: MoneyDirection = MoneyDirection.EXPENSE,
)

/**
 * 周期统计卡片的展示模型。
 *
 * 参数说明：
 * - title：卡片标题。
 * - periodLabel：周期文案。
 * - incomeSummary：收入汇总。
 * - expenseSummary：支出汇总。
 * - netSummary：结余汇总。
 * - entriesLabel：记录数量文案。
 * - netPositive：结余是否为正，用于控制视觉强调。
 */
data class LedgerBillCycleUiModel(
    val title: String,
    val periodLabel: String,
    val incomeSummary: String,
    val expenseSummary: String,
    val netSummary: String,
    val entriesLabel: String,
    val netPositive: Boolean,
)

/**
 * 首页洞察卡片的展示模型。
 *
 * 参数说明：
 * - title：洞察标题。
 * - value：洞察主值。
 * - supporting：辅助说明文案。
 */
data class LedgerInsightUiModel(
    val title: String,
    val value: String,
    val supporting: String,
)

/**
 * 趋势图中单个柱点的展示模型。
 *
 * 参数说明：
 * - label：横轴标签。
 * - amountLabel：金额文案。
 * - ratio：用于绘制柱高的比例值。
 * - isCurrent：是否为当前关注点。
 */
data class LedgerTrendPointUiModel(
    val label: String,
    val amountLabel: String,
    val ratio: Float,
    val isCurrent: Boolean,
)

/**
 * 分类分布项的展示模型。
 *
 * 参数说明：
 * - category：分类名称。
 * - amount：该分类金额文案。
 * - ratioLabel：占比文案。
 * - ratioValue：占比数值，供进度条或图形绘制使用。
 */
data class LedgerCategoryBreakdownUiModel(
    val category: String,
    val amount: String,
    val ratioLabel: String,
    val ratioValue: Float,
)

/**
 * 账本相关页面共用的完整 UI 状态。
 *
 * 参数说明：
 * - periodTitle：当前页面主标题。
 * - periodSubtitle：标题下辅助说明。
 * - incomeSummary：收入汇总文案。
 * - expenseSummary：支出汇总文案。
 * - netSummary：结余汇总文案。
 * - netPositive：结余是否为正。
 * - pendingCount：待确认数量。
 * - searchQuery：当前搜索词。
 * - selectedRange：当前选中的时间范围。
 * - rangeSummary：范围摘要说明。
 * - calendarMonthLabel：当前月历月份标题。
 * - selectedDateSummary：选中日期的补充说明。
 * - calendarDays：月历格子数据。
 * - billCycles：周期汇总卡片数据。
 * - insights：首页洞察数据。
 * - trendTitle：趋势区标题。
 * - trendSubtitle：趋势区副标题。
 * - trendPoints：趋势点数据。
 * - categoryBreakdown：分类分布数据。
 * - budgetAmountLabel：预算金额文案，为空表示未设置预算。
 * - budgetSpentLabel：预算已用文案。
 * - budgetRemainingLabel：预算剩余额度文案。
 * - budgetUsageSummary：预算区辅助说明。
 * - budgetUsageRatio：预算使用比例。
 * - budgetExceeded：是否已经超支。
 * - highlightMessage：页面高亮提示。
 * - entriesSummary：流水列表摘要。
 * - entries：流水列表项。
 */
data class LedgerUiState(
    val periodTitle: String = "本月账单",
    val periodSubtitle: String = "自动记账与手动补记会统一汇总在这里",
    val incomeSummary: String = "¥0.00",
    val expenseSummary: String = "¥0.00",
    val netSummary: String = "¥0.00",
    val netPositive: Boolean = true,
    val pendingCount: Int = 0,
    val searchQuery: String = "",
    val selectedRange: LedgerRangeFilter = LedgerRangeFilter.MONTH,
    val rangeSummary: String = "本地账本全部记录",
    val calendarMonthLabel: String = "",
    val selectedDateSummary: String? = null,
    val calendarDays: List<LedgerCalendarDayUiModel> = emptyList(),
    val billCycles: List<LedgerBillCycleUiModel> = emptyList(),
    val insights: List<LedgerInsightUiModel> = emptyList(),
    val trendTitle: String = "近 7 天支出趋势",
    val trendSubtitle: String = "把最近消费波动放到一个地方看清楚",
    val trendPoints: List<LedgerTrendPointUiModel> = emptyList(),
    val categoryBreakdown: List<LedgerCategoryBreakdownUiModel> = emptyList(),
    val budgetAmountLabel: String? = null,
    val budgetSpentLabel: String = "¥0.00",
    val budgetRemainingLabel: String = "去设置预算",
    val budgetUsageSummary: String = "设置本月预算后，这里会自动追踪剩余额度。",
    val budgetUsageRatio: Float = 0f,
    val budgetExceeded: Boolean = false,
    val highlightMessage: String = "待确认、趋势和分类分布都会在这里汇总。",
    val entriesSummary: String = "当前筛选下暂无流水",
    val entries: List<LedgerItemUiModel> = emptyList(),
)
