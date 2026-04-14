package com.autobook.cn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.feature.ledger.LedgerCalendarDayUiModel
import com.autobook.cn.feature.ledger.LedgerItemUiModel
import com.autobook.cn.feature.ledger.LedgerRangeFilter
import com.autobook.cn.feature.ledger.LedgerUiState
import java.time.LocalDate

/**
 * 账本页。
 *
 * 用途说明：
 * - 聚焦“查看日历、搜索账单、浏览时间线”三类任务。
 * - 删掉与首页重复的总览卡和分析切换，避免同一信息在多个页面反复出现。
 *
 * 参数说明：
 * - state：账本页状态。
 * - onSearchChange：搜索文本变化回调。
 * - onSearchSubmit：确认搜索后的跳转回调。
 * - onRangeChange：切换时间范围回调。
 * - onPreviousMonthClick：查看上个月日历。
 * - onNextMonthClick：查看下个月日历。
 * - onCalendarDateClick：点击某一天后定位到按日账单。
 * - onEntryClick：打开单条账单。
 * - modifier：外层修饰符。
 */
@Composable
fun LedgerWorkspaceScreen(
    state: LedgerUiState,
    onSearchChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onRangeChange: (LedgerRangeFilter) -> Unit,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    onCalendarDateClick: (LocalDate) -> Unit,
    onEntryClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "calendar") {
            LedgerCalendarOverviewCard(
                state = state,
                onPreviousMonthClick = onPreviousMonthClick,
                onNextMonthClick = onNextMonthClick,
                onCalendarDateClick = onCalendarDateClick,
                modifier = Modifier.statusBarsPadding(),
            )
        }
        item(key = "search") {
            LedgerSearchCard(
                state = state,
                onSearchChange = onSearchChange,
                onSearchSubmit = onSearchSubmit,
                onRangeChange = onRangeChange,
            )
        }
        item(key = "timeline_header") {
            Text(
                text = if (state.searchQuery.isBlank()) "账单时间线" else "匹配结果预览",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        if (state.entries.isEmpty()) {
            item(key = "empty") {
                LedgerEmptyCard(range = state.selectedRange)
            }
        } else {
            items(items = state.entries, key = { entry -> entry.id }) { entry ->
                LedgerTimelineCard(
                    entry = entry,
                    onClick = { onEntryClick(entry.id) },
                )
            }
        }
    }
}

/** 月历总览框展示每天的收支和净额。 */
@Composable
private fun LedgerCalendarOverviewCard(
    state: LedgerUiState,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    onCalendarDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.calendarMonthLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalendarSwitchButton(
                        icon = Icons.Outlined.ChevronLeft,
                        contentDescription = "上个月",
                        onClick = onPreviousMonthClick,
                    )
                    CalendarSwitchButton(
                        icon = Icons.Outlined.ChevronRight,
                        contentDescription = "下个月",
                        onClick = onNextMonthClick,
                    )
                }
            }
            CalendarWeekHeader()
            state.calendarDays.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    week.forEach { day ->
                        LedgerCalendarDayCard(
                            day = day,
                            onClick = { onCalendarDateClick(day.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 搜索框改成更规整的笔记卡片风格，并用单一范围筛选替代一排重复按钮。 */
@Composable
private fun LedgerSearchCard(
    state: LedgerUiState,
    onSearchChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onRangeChange: (LedgerRangeFilter) -> Unit,
) {
    var rangeMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索商户、分类、备注") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                trailingIcon = {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "搜索",
                    )
                },
                shape = RoundedCornerShape(14.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.clickable { rangeMenuExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = state.selectedRange.ledgerRangeLabel(),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                DropdownMenu(
                    expanded = rangeMenuExpanded,
                    onDismissRequest = { rangeMenuExpanded = false },
                ) {
                    ledgerSearchRanges().forEach { range ->
                        DropdownMenuItem(
                            text = { Text(range.ledgerRangeLabel()) },
                            onClick = {
                                rangeMenuExpanded = false
                                onRangeChange(range)
                            },
                        )
                    }
                }
                Surface(
                    modifier = Modifier.clickable { onSearchSubmit(state.searchQuery) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "搜索",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/** 星期标题。 */
@Composable
private fun CalendarWeekHeader() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 单日卡展示收入、支出和净额。 */
@Composable
private fun LedgerCalendarDayCard(
    day: LedgerCalendarDayUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        day.isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        day.isToday -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = day.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (day.isToday || day.isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (day.isCurrentMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                day.incomeLabel?.let { income ->
                    Text(
                        text = income,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4A8C59),
                        maxLines = 1,
                    )
                }
                day.expenseLabel?.let { expense ->
                    Text(
                        text = expense,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB35B45),
                        maxLines = 1,
                    )
                }
                day.netLabel?.let { net ->
                    Text(
                        text = net,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 搜索结果列表与账本列表都复用时间线卡片。 */
@Composable
private fun LedgerTimelineCard(
    entry: LedgerItemUiModel,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = entry.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = entry.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = entry.amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (entry.direction == MoneyDirection.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** 空状态只提示下一步，不做冗长解释。 */
@Composable
private fun LedgerEmptyCard(
    range: LedgerRangeFilter,
) {
    val message = when (range) {
        LedgerRangeFilter.TODAY -> "今天还没有记录。"
        LedgerRangeFilter.WEEK -> "本周还没有记录。"
        LedgerRangeFilter.MONTH -> "本月还没有记录。"
        LedgerRangeFilter.YEAR -> "今年还没有记录。"
        LedgerRangeFilter.CALENDAR -> "这一天还没有记录。"
        LedgerRangeFilter.ALL -> "还没有记录。"
    }
    Card(shape = RoundedCornerShape(18.dp)) {
        Text(
            text = message,
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 月历翻页按钮。 */
@Composable
private fun CalendarSwitchButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            modifier = Modifier.padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = contentDescription,
            )
        }
    }
}

/** 账本页搜索支持的范围。 */
private fun ledgerSearchRanges(): List<LedgerRangeFilter> = listOf(
    LedgerRangeFilter.TODAY,
    LedgerRangeFilter.WEEK,
    LedgerRangeFilter.MONTH,
    LedgerRangeFilter.YEAR,
    LedgerRangeFilter.CALENDAR,
)

/** 账本页范围文案。 */
private fun LedgerRangeFilter.ledgerRangeLabel(): String = when (this) {
    LedgerRangeFilter.TODAY -> "今日"
    LedgerRangeFilter.WEEK -> "本周"
    LedgerRangeFilter.MONTH -> "本月"
    LedgerRangeFilter.YEAR -> "本年"
    LedgerRangeFilter.CALENDAR -> "按日"
    LedgerRangeFilter.ALL -> "全部"
}
