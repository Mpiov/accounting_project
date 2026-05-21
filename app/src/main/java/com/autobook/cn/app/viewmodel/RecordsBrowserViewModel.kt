package com.autobook.cn.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.common.CurrencyFormatter
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 详细记录页的浏览状态。
 *
 * 参数说明：
 * - title：页面顶部标题。
 * - summary：摘要卡片文案。
 * - items：时间线记录列表。
 */
data class RecordsBrowserUiState(
    val title: String = "详细记录",
    val summary: String = "当前还没有账单记录",
    val items: List<RecordTimelineItemUiModel> = emptyList(),
)

/**
 * 时间线记录展示模型。
 *
 * 参数说明：
 * - id：记录标识。
 * - title：主标题。
 * - category：分类。
 * - sourceLabel：来源说明。
 * - amountLabel：金额文本。
 * - dateLabel：日期标签。
 * - timeLabel：时间标签。
 * - isIncome：是否为收入。
 */
data class RecordTimelineItemUiModel(
    val id: Long,
    val title: String,
    val category: String,
    val sourceLabel: String,
    val amountLabel: String,
    val dateLabel: String,
    val timeLabel: String,
    val isIncome: Boolean,
)

/**
 * 记录浏览 ViewModel。
 *
 * 用途说明：
 * - 承接“详细记录页”的时间线数据源。
 * - 已删除无入口的搜索结果路由，因此这里只输出完整账单时间线。
 *
 * 参数说明：
 * - savedStateHandle：导航传入的筛选参数，分类明细页会携带方向、周期和分类。
 * - ledgerRepository：账本仓储，用于观察全部账单记录。
 */
@HiltViewModel
class RecordsBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    ledgerRepository: LedgerRepository,
) : ViewModel() {
    private val categoryFilter = savedStateHandle.toRecordsCategoryFilter()

    /** 输出可直接渲染的时间线状态。 */
    val uiState: StateFlow<RecordsBrowserUiState> = ledgerRepository.observeEntries()
        .map { entries ->
            buildRecordsBrowserUiState(
                entries = entries,
                categoryFilter = categoryFilter,
                today = LocalDate.now(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecordsBrowserUiState(),
        )
}

/**
 * 用途说明：
 * - 记录分类明细页支持的周期筛选。
 * - 名称直接对应路由中的 period 参数，方便从图表页透传 WEEK/MONTH/YEAR。
 */
internal enum class RecordsBrowserPeriod {
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ;

    companion object {
        /**
         * 用途说明：
         * - 将路由字符串安全解析为周期枚举，非法值交给调用方决定兜底策略。
         *
         * 参数说明：
         * - value：导航路由中的 period 参数。
         */
        fun fromRouteValue(value: String): RecordsBrowserPeriod? {
            return entries.firstOrNull { period -> period.name == value }
        }
    }
}

/**
 * 用途说明：
 * - 构建详细记录页状态的测试入口，避免单元测试依赖 Hilt 和导航栈。
 *
 * 参数说明：
 * - entries：待展示或筛选的账本记录。
 * - direction：分类明细目标方向；为空时展示全部记录。
 * - period：分类明细目标周期；为空时展示全部记录。
 * - category：分类明细目标分类；非空时会完整匹配账单分类。
 * - today：当前日期锚点，测试可固定它来验证本周、本月和今年语义。
 */
internal fun buildRecordsBrowserUiStateForTest(
    entries: List<LedgerEntry>,
    direction: MoneyDirection? = null,
    period: RecordsBrowserPeriod? = null,
    category: String = "",
    today: LocalDate = LocalDate.now(),
): RecordsBrowserUiState {
    val categoryFilter = if (direction == null || period == null) {
        null
    } else {
        RecordsCategoryFilter(
            direction = direction,
            period = period,
            category = category,
        )
    }
    return buildRecordsBrowserUiState(
        entries = entries,
        categoryFilter = categoryFilter,
        today = today,
    )
}

/**
 * 用途说明：
 * - 根据可选的分类筛选条件生成详细记录页最终 UI 状态。
 *
 * 参数说明：
 * - entries：仓储返回的全量账本记录。
 * - categoryFilter：分类明细筛选条件；为空表示展示全量记录。
 * - today：当前日期锚点，用于计算本周、本月和今年范围。
 */
private fun buildRecordsBrowserUiState(
    entries: List<LedgerEntry>,
    categoryFilter: RecordsCategoryFilter?,
    today: LocalDate,
): RecordsBrowserUiState {
    val filteredEntries = entries
        .filterByCategoryFilter(categoryFilter = categoryFilter, today = today)
        // 详细记录页继续按最终入账顺序倒排，保持点击编辑前后的列表口径稳定。
        .sortNewestForDisplay()
    return RecordsBrowserUiState(
        title = buildRecordsBrowserTitle(categoryFilter),
        summary = buildRecordsBrowserSummary(
            entries = filteredEntries,
            categoryFilter = categoryFilter,
        ),
        items = filteredEntries.map { entry -> entry.toTimelineItem() },
    )
}

/**
 * 用途说明：
 * - 按分类明细条件过滤账单记录。
 *
 * 参数说明：
 * - categoryFilter：目标方向、周期和分类。
 * - today：当前日期锚点。
 */
private fun List<LedgerEntry>.filterByCategoryFilter(
    categoryFilter: RecordsCategoryFilter?,
    today: LocalDate,
): List<LedgerEntry> {
    if (categoryFilter == null) return this
    return filter { entry ->
        val entryDate = entry.timestamp.toRecordLocalDate()
        entry.direction == categoryFilter.direction &&
            entryDate.isInRecordPeriod(period = categoryFilter.period, today = today) &&
            // 分类参数非空时必须完整匹配，避免“交通”误命中“公共交通”等相似分类。
            (categoryFilter.category.isEmpty() || entry.category == categoryFilter.category)
    }
}

/**
 * 用途说明：
 * - 生成详细记录页顶部标题。
 *
 * 参数说明：
 * - categoryFilter：分类明细筛选条件；为空时使用全量记录标题。
 */
private fun buildRecordsBrowserTitle(
    categoryFilter: RecordsCategoryFilter?,
): String {
    if (categoryFilter == null) return "详细记录"
    val titlePrefix = categoryFilter.category.ifEmpty { categoryFilter.direction.labelText() }
    return "${titlePrefix}明细"
}

/**
 * 用途说明：
 * - 生成详细记录页摘要卡片文案。
 *
 * 参数说明：
 * - entries：已经完成筛选和排序的账单记录。
 * - categoryFilter：分类明细筛选条件；为空时使用全量记录摘要。
 */
private fun buildRecordsBrowserSummary(
    entries: List<LedgerEntry>,
    categoryFilter: RecordsCategoryFilter?,
): String {
    if (categoryFilter == null) {
        return if (entries.isEmpty()) {
            "当前还没有账单记录"
        } else {
            "共 ${entries.size} 笔账单，按时间倒序展示"
        }
    }
    val totalAmount = entries.sumOf { entry -> entry.amount.absoluteValue }
    return "${categoryFilter.period.summaryLabel()}${categoryFilter.direction.labelText()} · " +
        "共 ${entries.size} 笔 · 合计 ${CurrencyFormatter.format(totalAmount)}"
}

/**
 * 用途说明：
 * - 将导航参数解析成分类明细筛选条件。
 *
 * 参数说明：
 * - 接收者：当前页面的 SavedStateHandle。
 */
private fun SavedStateHandle.toRecordsCategoryFilter(): RecordsCategoryFilter? {
    val direction = get<String>("direction")
        ?.takeIf { value -> value.isNotBlank() }
        ?.let { value -> runCatching { MoneyDirection.valueOf(value) }.getOrNull() }
        ?.takeIf { value -> value != MoneyDirection.UNKNOWN }
        ?: return null
    val period = get<String>("period")
        ?.let { value -> RecordsBrowserPeriod.fromRouteValue(value) }
        ?: RecordsBrowserPeriod.MONTH
    return RecordsCategoryFilter(
        direction = direction,
        period = period,
        category = get<String>("category").orEmpty(),
    )
}

/**
 * 用途说明：
 * - 将领域记录映射成时间线展示模型。
 */
private fun LedgerEntry.toTimelineItem(): RecordTimelineItemUiModel {
    val localDateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
    return RecordTimelineItemUiModel(
        id = id,
        title = buildLedgerTitle(),
        category = category,
        sourceLabel = channel,
        amountLabel = buildTimelineAmountLabel(),
        dateLabel = localDateTime.toLocalDate().format(RECORD_DATE_FORMATTER),
        timeLabel = localDateTime.format(RECORD_TIME_FORMATTER),
        isIncome = direction == MoneyDirection.INCOME,
    )
}

/**
 * 用途说明：
 * - 为时间线金额统一添加正负号。
 */
private fun LedgerEntry.buildTimelineAmountLabel(): String {
    val sign = if (direction == MoneyDirection.INCOME) "+" else "-"
    return "$sign${CurrencyFormatter.format(amount.absoluteValue)}"
}

/**
 * 用途说明：
 * - 判断某个账单日期是否落在当前周期。
 *
 * 参数说明：
 * - period：目标筛选周期。
 * - today：当前日期锚点。
 */
private fun LocalDate.isInRecordPeriod(
    period: RecordsBrowserPeriod,
    today: LocalDate,
): Boolean {
    return when (period) {
        RecordsBrowserPeriod.DAY -> this == today
        RecordsBrowserPeriod.WEEK -> {
            val weekStart = today.with(DayOfWeek.MONDAY)
            val weekEnd = weekStart.plusDays(6)
            !isBefore(weekStart) && !isAfter(weekEnd)
        }
        RecordsBrowserPeriod.MONTH -> YearMonth.from(this) == YearMonth.from(today)
        RecordsBrowserPeriod.YEAR -> year == today.year
    }
}

/**
 * 用途说明：
 * - 将毫秒时间戳转换成本地日期，供分类明细按周期过滤。
 */
private fun Long.toRecordLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

/**
 * 用途说明：
 * - 分类明细页摘要中的周期短标签。
 */
private fun RecordsBrowserPeriod.summaryLabel(): String {
    return when (this) {
        RecordsBrowserPeriod.DAY -> "今日"
        RecordsBrowserPeriod.WEEK -> "本周"
        RecordsBrowserPeriod.MONTH -> "本月"
        RecordsBrowserPeriod.YEAR -> "今年"
    }
}

/**
 * 用途说明：
 * - 将收支方向转换成中文展示文案。
 */
private fun MoneyDirection.labelText(): String {
    return when (this) {
        MoneyDirection.EXPENSE -> "支出"
        MoneyDirection.INCOME -> "收入"
        MoneyDirection.UNKNOWN -> "未知"
    }
}

/**
 * 分类明细筛选条件。
 *
 * 参数说明：
 * - direction：收入或支出方向。
 * - period：当前图表周期。
 * - category：目标分类；为空时表示该方向在当前周期的全部记录。
 */
private data class RecordsCategoryFilter(
    val direction: MoneyDirection,
    val period: RecordsBrowserPeriod,
    val category: String,
)

private val RECORD_DATE_FORMATTER = DateTimeFormatter.ofPattern("M 月 d 日", Locale.CHINA)
private val RECORD_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
