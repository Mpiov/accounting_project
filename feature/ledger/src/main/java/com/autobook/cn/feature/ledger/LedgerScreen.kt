package com.autobook.cn.feature.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** 账本首页支持的时间范围筛选。 */
enum class LedgerRangeFilter {
    TODAY,
    MONTH,
    CALENDAR,
    ALL,
}

/** 月历中的单个日期格子展示模型。 */
data class LedgerCalendarDayUiModel(
    val date: LocalDate,
    val label: String,
    val isCurrentMonth: Boolean,
    val hasEntries: Boolean,
    val isSelected: Boolean,
    val isToday: Boolean,
)

/** 账本列表单项的展示模型。 */
data class LedgerItemUiModel(
    val id: Long,
    val merchant: String,
    val amount: String,
    val subtitle: String,
    val category: String,
    val note: String?,
    val isManual: Boolean,
)

/** 账本首页的完整界面状态。 */
data class LedgerUiState(
    val incomeSummary: String = "¥0.00",
    val expenseSummary: String = "¥0.00",
    val pendingCount: Int = 0,
    val searchQuery: String = "",
    val selectedRange: LedgerRangeFilter = LedgerRangeFilter.TODAY,
    val calendarMonthLabel: String = "",
    val selectedDateSummary: String? = null,
    val calendarDays: List<LedgerCalendarDayUiModel> = emptyList(),
    val entries: List<LedgerItemUiModel> = emptyList(),
)

/** 账本首页，展示汇总、筛选条件和账本列表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    state: LedgerUiState,
    onSearchChange: (String) -> Unit,
    onRangeChange: (LedgerRangeFilter) -> Unit,
    onPendingClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit,
    onEntryClick: (Long) -> Unit,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    onCalendarDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("账本") },
                actions = {
                    IconButton(onClick = onAddClick) {
                        Icon(Icons.Outlined.Add, contentDescription = "新增")
                    }
                    IconButton(onClick = onPendingClick) {
                        Icon(Icons.Outlined.PendingActions, contentDescription = "待确认")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    title = "当前收入",
                    value = state.incomeSummary,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = "当前支出",
                    value = state.expenseSummary,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "待确认 ${state.pendingCount} 条",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜索商户、分类或备注") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LedgerRangeFilter.entries.forEach { range ->
                    FilterChip(
                        selected = state.selectedRange == range,
                        onClick = { onRangeChange(range) },
                        label = {
                            Text(
                                when (range) {
                                    LedgerRangeFilter.TODAY -> "今日"
                                    LedgerRangeFilter.MONTH -> "本月"
                                    LedgerRangeFilter.CALENDAR -> "日历"
                                    LedgerRangeFilter.ALL -> "全部"
                                },
                            )
                        },
                    )
                }
            }
            if (state.selectedRange == LedgerRangeFilter.CALENDAR) {
                CalendarOverviewCard(
                    monthLabel = state.calendarMonthLabel,
                    days = state.calendarDays,
                    onPreviousMonthClick = onPreviousMonthClick,
                    onNextMonthClick = onNextMonthClick,
                    onDateClick = onCalendarDateClick,
                )
            }
            state.selectedDateSummary?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.entries.isEmpty()) {
                    item {
                        Card {
                            Text(
                                text = when (state.selectedRange) {
                                    LedgerRangeFilter.CALENDAR ->
                                        "所选日期还没有记账记录。你可以切换其它日期，或手动补记一笔。"
                                    else -> "还没有符合筛选条件的记账记录。你可以手动新增，或者等待通知自动入账。"
                                },
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    items(state.entries, key = { it.id }) { entry ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEntryClick(entry.id) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(entry.merchant, style = MaterialTheme.typography.titleMedium)
                                    Text(entry.subtitle, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = buildString {
                                            append(entry.category)
                                            if (entry.isManual) append(" · 手动")
                                            if (!entry.note.isNullOrBlank()) append(" · ${entry.note}")
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                Text(entry.amount, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    /** 收入/支出汇总卡片的复用组件。 */
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun CalendarOverviewCard(
    monthLabel: String,
    days: List<LedgerCalendarDayUiModel>,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    onDateClick: (LocalDate) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row {
                    IconButton(onClick = onPreviousMonthClick) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "上个月")
                    }
                    IconButton(onClick = onNextMonthClick) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "下个月")
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { weekday ->
                    Text(
                        text = weekday,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            days.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    week.forEach { day ->
                        CalendarDayCell(
                            day = day,
                            onClick = { onDateClick(day.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: LedgerCalendarDayUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        day.isSelected -> MaterialTheme.colorScheme.primaryContainer
        day.isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val contentColor = when {
        day.isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        day.isToday -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = day.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (day.isCurrentMonth) contentColor else contentColor.copy(alpha = 0.55f),
                fontWeight = if (day.isSelected || day.isToday) FontWeight.SemiBold else FontWeight.Normal,
            )
            Box(
                modifier = Modifier.height(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (day.hasEntries) {
                    Text(
                        text = "●",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day.isCurrentMonth) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}
