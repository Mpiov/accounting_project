package com.autobook.cn.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.common.CurrencyFormatter
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 详细记录页与搜索结果页共用的浏览状态。
 *
 * 参数说明：
 * - title：页面标题。
 * - summary：顶部摘要。
 * - query：搜索关键词；为空代表展示全部记录。
 * - items：时间线记录列表。
 */
data class RecordsBrowserUiState(
    val title: String = "详细记录",
    val summary: String = "按时间顺序查看全部账单",
    val query: String = "",
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
 * - 承接“详细记录页”和“搜索结果页”的统一数据源。
 * - 搜索结果页通过路由参数传入 query，其余逻辑与完整时间线保持一致。
 */
@HiltViewModel
class RecordsBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    ledgerRepository: LedgerRepository,
) : ViewModel() {
    private val query: String = savedStateHandle.get<String>("query").orEmpty().trim()

    /** 输出可直接渲染的时间线状态。 */
    val uiState: StateFlow<RecordsBrowserUiState> = ledgerRepository.observeEntries()
        .map { entries ->
            val filteredEntries = entries
                .filterByTimelineQuery(query)
                .sortedByDescending { it.timestamp }
            RecordsBrowserUiState(
                title = if (query.isBlank()) "详细记录" else "搜索结果",
                summary = if (query.isBlank()) {
                    "按时间顺序查看全部账单"
                } else {
                    "“$query” 共匹配 ${filteredEntries.size} 条记录"
                },
                query = query,
                items = filteredEntries.map { entry -> entry.toTimelineItem() },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecordsBrowserUiState(query = query),
        )
}

/** 搜索结果页使用独立扩展名，避免与账本页的筛选扩展函数重名。 */
private fun List<LedgerEntry>.filterByTimelineQuery(query: String): List<LedgerEntry> {
    if (query.isBlank()) return this
    return filter { entry ->
        entry.merchant.contains(query, ignoreCase = true) ||
            entry.category.contains(query, ignoreCase = true) ||
            entry.note?.contains(query, ignoreCase = true) == true
    }
}

/** 将领域记录映射成时间线展示模型。 */
private fun LedgerEntry.toTimelineItem(): RecordTimelineItemUiModel {
    val localDateTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
    return RecordTimelineItemUiModel(
        id = id,
        title = merchant,
        category = category,
        sourceLabel = channel,
        amountLabel = buildTimelineAmountLabel(),
        dateLabel = localDateTime.toLocalDate().format(RECORD_DATE_FORMATTER),
        timeLabel = localDateTime.format(RECORD_TIME_FORMATTER),
        isIncome = direction == MoneyDirection.INCOME,
    )
}

/** 为时间线金额统一添加正负号。 */
private fun LedgerEntry.buildTimelineAmountLabel(): String {
    val sign = if (direction == MoneyDirection.INCOME) "+" else "-"
    return "$sign${CurrencyFormatter.format(amount)}"
}

private val RECORD_DATE_FORMATTER = DateTimeFormatter.ofPattern("M 月 d 日", Locale.CHINA)
private val RECORD_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
