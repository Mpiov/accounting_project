package com.autobook.cn.app.viewmodel

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
 * 详细记录页的浏览状态。
 *
 * 参数说明：
 * - items：时间线记录列表。
 */
data class RecordsBrowserUiState(
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
 * - ledgerRepository：账本仓储，用于观察全部账单记录。
 */
@HiltViewModel
class RecordsBrowserViewModel @Inject constructor(
    ledgerRepository: LedgerRepository,
) : ViewModel() {
    /** 输出可直接渲染的时间线状态。 */
    val uiState: StateFlow<RecordsBrowserUiState> = ledgerRepository.observeEntries()
        .map { entries ->
            // 详细记录页没有二次筛选，直接按最终入账顺序倒排，和首页最近记录保持一致。
            val timelineEntries = entries.sortNewestForDisplay()
            RecordsBrowserUiState(
                items = timelineEntries.map { entry -> entry.toTimelineItem() },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecordsBrowserUiState(),
        )
}

/** 将领域记录映射成时间线展示模型。 */
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

/** 为时间线金额统一添加正负号。 */
private fun LedgerEntry.buildTimelineAmountLabel(): String {
    val sign = if (direction == MoneyDirection.INCOME) "+" else "-"
    return "$sign${CurrencyFormatter.format(amount)}"
}

private val RECORD_DATE_FORMATTER = DateTimeFormatter.ofPattern("M 月 d 日", Locale.CHINA)
private val RECORD_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
