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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * 用途说明：
 * - 月份详情页承接“某个月”的日历、搜索与流水账浏览。
 *
 * 参数说明：
 * - state：月份详情页状态。
 * - onBack：返回上一级页面的回调。
 * - onSearchChange：搜索关键字变化回调。
 * - onPreviousMonthClick：切换到上一个月的回调。
 * - onNextMonthClick：切换到下一个月的回调。
 * - onCalendarDateClick：点击某个日期的回调。
 * - onEntryClick：点击某条流水账后的回调。
 * - modifier：外层修饰符，便于导航层注入布局约束。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerWorkspaceScreen(
    state: LedgerUiState,
    onBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    onCalendarDateClick: (LocalDate) -> Unit,
    onEntryClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = state.periodTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = state.periodSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "calendar") {
                LedgerCalendarOverviewCard(
                    state = state,
                    onPreviousMonthClick = onPreviousMonthClick,
                    onNextMonthClick = onNextMonthClick,
                    onCalendarDateClick = onCalendarDateClick,
                )
            }
            item(key = "search") {
                LedgerSearchCard(
                    state = state,
                    onSearchChange = onSearchChange,
                )
            }
            state.selectedDateSummary?.let { summary ->
                item(key = "summary_hint") {
                    Surface(
                        shape = AutobookUiTokens.chipShape,
                        border = autobookCardBorder(),
                        color = autobookSoftFillColor(),
                    ) {
                        Text(
                            text = summary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "timeline_header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (state.selectedRange == LedgerRangeFilter.CALENDAR) "当天流水账" else "整月流水账",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.entriesSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
}

/**
 * 用途说明：
 * - 月历总览卡用于展示当前月份的每日收支概览，并支持直接切到某一天。
 *
 * 参数说明：
 * - state：月份详情页状态。
 * - onPreviousMonthClick：上个月翻页回调。
 * - onNextMonthClick：下个月翻页回调。
 * - onCalendarDateClick：点击日历日期回调。
 */
@Composable
private fun LedgerCalendarOverviewCard(
    state: LedgerUiState,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    onCalendarDateClick: (LocalDate) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
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
            Text(
                text = state.rangeSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

/**
 * 用途说明：
 * - 搜索卡只保留单个搜索框，按输入内容实时过滤当前月份或当天流水。
 *
 * 参数说明：
 * - state：月份详情页状态。
 * - onSearchChange：搜索关键字变化回调。
 */
@Composable
private fun LedgerSearchCard(
    state: LedgerUiState,
    onSearchChange: (String) -> Unit,
) {
    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "搜索",
                    )
                },
                shape = AutobookUiTokens.chipShape,
            )
            Text(
                text = if (state.selectedRange == LedgerRangeFilter.CALENDAR) {
                    "当前正在查看单日流水，再次点按已选日期可切回整月。"
                } else {
                    "当前正在查看整月流水，点日历日期可快速切到当天。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 用途说明：
 * - 星期表头负责对齐下方日历网格。
 */
@Composable
private fun CalendarWeekHeader() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 用途说明：
 * - 单日卡片展示日期和当日收支摘要。
 *
 * 参数说明：
 * - day：当前日期卡片模型。
 * - onClick：点击日期后的回调。
 * - modifier：外层修饰符，便于父布局分配宽度。
 */
@Composable
private fun LedgerCalendarDayCard(
    day: LedgerCalendarDayUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        day.isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
        day.isToday -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
        day.hasEntries -> autobookSoftFillColor()
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = AutobookUiTokens.innerPanelShape,
        border = autobookCardBorder(emphasized = day.isSelected || day.isToday),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = day.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (day.isToday || day.isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (day.isCurrentMonth) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                },
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

/**
 * 用途说明：
 * - 月份详情页流水卡片负责展示商户、分类、时间和金额。
 *
 * 参数说明：
 * - entry：当前账单条目。
 * - onClick：点击条目后的回调。
 */
@Composable
private fun LedgerTimelineCard(
    entry: LedgerItemUiModel,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = AutobookUiTokens.compactPanelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
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
                color = if (entry.direction == MoneyDirection.INCOME) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            )
        }
    }
}

/**
 * 用途说明：
 * - 空状态负责提示当前月份或当前日期暂时没有流水。
 *
 * 参数说明：
 * - range：当前详情页范围。
 */
@Composable
private fun LedgerEmptyCard(
    range: LedgerRangeFilter,
) {
    val message = when (range) {
        LedgerRangeFilter.CALENDAR -> "所选日期还没有记录，再次点按当天可回到整月。"
        LedgerRangeFilter.MONTH -> "这个月还没有流水，翻页看看其他月份，或先手动补记一笔。"
        else -> "当前范围还没有流水。"
    }

    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 用途说明：
 * - 月份翻页按钮。
 *
 * 参数说明：
 * - icon：按钮图标。
 * - contentDescription：无障碍描述。
 * - onClick：点击按钮回调。
 */
@Composable
private fun CalendarSwitchButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        border = autobookCardBorder(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier.padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
            )
        }
    }
}
