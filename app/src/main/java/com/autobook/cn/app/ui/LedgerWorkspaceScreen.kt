@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.autobook.cn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.feature.ledger.LedgerCalendarDayUiModel
import com.autobook.cn.feature.ledger.LedgerCategoryBreakdownUiModel
import com.autobook.cn.feature.ledger.LedgerItemUiModel
import com.autobook.cn.feature.ledger.LedgerRangeFilter
import com.autobook.cn.feature.ledger.LedgerTrendPointUiModel
import com.autobook.cn.feature.ledger.LedgerUiState
import java.time.LocalDate

/**
 * 账本页承载搜索、筛选、分析和日历浏览。
 *
 * 参数说明：
 * - state：账本聚合后的界面状态。
 * - onSearchChange：搜索文本变更回调。
 * - onRangeChange：账期筛选变更回调。
 * - onPreviousMonthClick：日历向前翻月回调。
 * - onNextMonthClick：日历向后翻月回调。
 * - onCalendarDateClick：点击日期格子的回调。
 * - onAddClick：新建记账的回调。
 * - onEntryClick：打开账本详情的回调。
 * - modifier：外层修饰符。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LedgerWorkspaceScreen(
    state: LedgerUiState,
    onSearchChange: (String) -> Unit,
    onRangeChange: (LedgerRangeFilter) -> Unit,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    onCalendarDateClick: (LocalDate) -> Unit,
    onAddClick: () -> Unit,
    onEntryClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedSegment by rememberSaveable { mutableStateOf(LedgerSegment.Flow) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "ledger_header") {
            LedgerTopSummaryCard(
                state = state,
                modifier = Modifier.statusBarsPadding(),
            )
        }
        item(key = "ledger_filters") {
            LedgerFilterCard(
                state = state,
                selectedSegment = selectedSegment,
                onSearchChange = onSearchChange,
                onRangeChange = onRangeChange,
                onSegmentSelected = { segment ->
                    selectedSegment = segment
                    // 切到日历分段时同步切换账期，保证下方内容立刻进入按天视图。
                    if (segment == LedgerSegment.Calendar && state.selectedRange != LedgerRangeFilter.CALENDAR) {
                        onRangeChange(LedgerRangeFilter.CALENDAR)
                    }
                },
            )
        }
        when (selectedSegment) {
            LedgerSegment.Flow -> {
                item(key = "flow_header") {
                    LedgerSectionHeader(title = "流水", summary = state.entriesSummary)
                }
                if (state.entries.isEmpty()) {
                    item(key = "flow_empty") {
                        LedgerEmptyCard(
                            title = "当前账期没有流水",
                            summary = "你可以调整时间范围，或者先记一笔把账本补起来。",
                            actionLabel = "记一笔",
                            onAction = onAddClick,
                        )
                    }
                } else {
                    items(items = state.entries, key = { entry -> entry.id }) { entry ->
                        LedgerFlowCard(entry = entry, onClick = { onEntryClick(entry.id) })
                    }
                }
            }
            LedgerSegment.Trend -> {
                item(key = "trend_header") {
                    LedgerSectionHeader(title = state.trendTitle, summary = state.trendSubtitle)
                }
                item(key = "trend_chart") {
                    TrendCard(points = state.trendPoints)
                }
                item(key = "trend_insight") {
                    TrendInsightCard(points = state.trendPoints)
                }
            }
            LedgerSegment.Category -> {
                item(key = "category_header") {
                    LedgerSectionHeader(
                        title = "分类分布",
                        summary = "把主要支出分类放到一屏里，更方便快速定位花销重心。",
                    )
                }
                item(key = "category_breakdown") {
                    CategoryBreakdownCard(
                        categories = state.categoryBreakdown,
                        highlightMessage = state.highlightMessage,
                    )
                }
            }
            LedgerSegment.Calendar -> {
                item(key = "calendar_header") {
                    LedgerSectionHeader(
                        title = "日历回看",
                        summary = state.selectedDateSummary ?: "按天回看收支，适合复盘某一天发生了什么。",
                    )
                }
                item(key = "calendar_view") {
                    LedgerCalendarCard(
                        monthLabel = state.calendarMonthLabel,
                        days = state.calendarDays,
                        onPreviousMonthClick = onPreviousMonthClick,
                        onNextMonthClick = onNextMonthClick,
                        onCalendarDateClick = onCalendarDateClick,
                    )
                }
                if (state.entries.isEmpty()) {
                    item(key = "calendar_empty") {
                        LedgerEmptyCard(
                            title = "所选日期还没有记录",
                            summary = "切换到其他日期看看，或者先手动补记一笔。",
                            actionLabel = "记一笔",
                            onAction = onAddClick,
                        )
                    }
                } else {
                    items(items = state.entries, key = { entry -> entry.id }) { entry ->
                        LedgerFlowCard(entry = entry, onClick = { onEntryClick(entry.id) })
                    }
                }
            }
        }
    }
}

/** 顶部摘要负责承接账期、待确认入口和设置入口。 */
@Composable
private fun LedgerTopSummaryCard(
    state: LedgerUiState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "账本",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.periodTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = buildLedgerHeaderSummary(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LedgerMetricCard(
                    label = "收入",
                    value = state.incomeSummary,
                    valueColor = Color(0xFF0B6E4F),
                    modifier = Modifier.weight(1f),
                )
                LedgerMetricCard(
                    label = "支出",
                    value = state.expenseSummary,
                    valueColor = Color(0xFFC65D21),
                    modifier = Modifier.weight(1f),
                )
                LedgerMetricCard(
                    label = "结余",
                    value = state.netSummary,
                    valueColor = if (state.netPositive) Color(0xFF0B6E4F) else Color(0xFFC65D21),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 搜索和账期筛选统一收口到一个卡片，避免到处散落。 */
@Composable
private fun LedgerFilterCard(
    state: LedgerUiState,
    selectedSegment: LedgerSegment,
    onSearchChange: (String) -> Unit,
    onRangeChange: (LedgerRangeFilter) -> Unit,
    onSegmentSelected: (LedgerSegment) -> Unit,
) {
    val chipScrollState = rememberScrollState()

    Card(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜索商户、分类、备注") },
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(chipScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LedgerRangeFilter.entries.forEach { range ->
                    FilterChip(
                        selected = state.selectedRange == range,
                        onClick = { onRangeChange(range) },
                        label = { Text(text = range.toDisplayLabel()) },
                    )
                }
            }
            HorizontalDivider()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LedgerSegment.entries.forEach { segment ->
                    FilterChip(
                        selected = segment == selectedSegment,
                        onClick = { onSegmentSelected(segment) },
                        label = { Text(segment.label) },
                    )
                }
            }
        }
    }
}

/** 账本分段标题统一布局，减少页面节奏噪音。 */
@Composable
private fun LedgerSectionHeader(
    title: String,
    summary: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 趋势分段下补一张摘要卡，比“周/月/年账期卡组”更聚焦当前消费波动。 */
@Composable
private fun TrendInsightCard(
    points: List<LedgerTrendPointUiModel>,
) {
    val peakPoint = points.maxByOrNull { it.ratio }
    val currentPoint = points.lastOrNull()

    Card(shape = RoundedCornerShape(26.dp)) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InsightMetricCard(
                title = "峰值",
                value = peakPoint?.amountLabel ?: "0",
                supporting = peakPoint?.label ?: "暂无",
                modifier = Modifier.weight(1f),
            )
            InsightMetricCard(
                title = "最近",
                value = currentPoint?.amountLabel ?: "0",
                supporting = currentPoint?.label ?: "暂无",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 小型指标卡只承载一个数字和一个标签，减少冗余描述。 */
@Composable
private fun InsightMetricCard(
    title: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 单条流水卡片缩短信息路径，并对金额展示做单行限制。 */
@Composable
private fun LedgerFlowCard(
    entry: LedgerItemUiModel,
    onClick: () -> Unit,
) {
    val amountColor = if (entry.direction == MoneyDirection.INCOME) Color(0xFF0B6E4F) else Color(0xFFC65D21)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = entry.merchant,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = entry.amount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowTag(
                    text = entry.category,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                FlowTag(
                    text = if (entry.isManual) "手动补记" else "自动入账",
                    containerColor = if (entry.isManual) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (entry.isManual) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
            entry.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 趋势卡片用简化柱状图表达最近波动。 */
@Composable
private fun TrendCard(
    points: List<LedgerTrendPointUiModel>,
) {
    Card(shape = RoundedCornerShape(28.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            points.forEach { point ->
                val minRatio = if (point.amountLabel == "0") 0f else 0.18f
                val barHeight = 22.dp + 88.dp * point.ratio.coerceAtLeast(minRatio)
                val barColor = if (point.isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = point.amountLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                            .background(barColor),
                    )
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 周、月、年账单用精简版卡片保留对比视角。 */
@Composable
private fun BillCycleSummaryCard(
    state: LedgerUiState,
) {
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "账期摘要",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            state.billCycles.forEachIndexed { index, cycle ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = cycle.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = cycle.periodLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${cycle.entriesLabel} · 收入 ${cycle.incomeSummary} / 支出 ${cycle.expenseSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = cycle.netSummary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (cycle.netPositive) Color(0xFF0B6E4F) else Color(0xFFC65D21),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (index != state.billCycles.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

/** 分类卡突出主要分类和一句摘要，不再堆叠冗长列表。 */
@Composable
private fun CategoryBreakdownCard(
    categories: List<LedgerCategoryBreakdownUiModel>,
    highlightMessage: String,
) {
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = highlightMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (categories.isEmpty()) {
                Text(
                    text = "当前账期还没有可展示的分类分布，新增几笔支出后这里会自动补齐。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                categories.forEachIndexed { index, category ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = category.category,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${category.amount} · ${category.ratioLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(category.ratioValue.coerceIn(0.04f, 1f))
                                    .height(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                    if (index != categories.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** 日历卡片承接翻月与按天查看。 */
@Composable
private fun LedgerCalendarCard(
    monthLabel: String,
    days: List<LedgerCalendarDayUiModel>,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    onCalendarDateClick: (LocalDate) -> Unit,
) {
    Card(shape = RoundedCornerShape(28.dp)) {
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
                    text = monthLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonthSwitchButton(
                        icon = Icons.Outlined.ChevronLeft,
                        contentDescription = "上个月",
                        onClick = onPreviousMonthClick,
                    )
                    MonthSwitchButton(
                        icon = Icons.Outlined.ChevronRight,
                        contentDescription = "下个月",
                        onClick = onNextMonthClick,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 7,
            ) {
                days.forEach { day ->
                    CalendarDayCard(
                        day = day,
                        onClick = { onCalendarDateClick(day.date) },
                    )
                }
            }
        }
    }
}

/** 单个日期格子保留“有记录 / 今日 / 选中”三种重点状态。 */
@Composable
private fun CalendarDayCard(
    day: LedgerCalendarDayUiModel,
    onClick: () -> Unit,
) {
    val containerColor = when {
        day.isSelected -> MaterialTheme.colorScheme.primaryContainer
        day.isToday -> Color(0xFFFFF3D8)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Column(
        modifier = Modifier
            .width(40.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = day.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (day.isSelected || day.isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = if (day.isCurrentMonth) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            },
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    if (day.hasEntries) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                ),
        )
    }
}

/** 通用空状态卡，把“说明 + 行动建议”放在一起。 */
@Composable
private fun LedgerEmptyCard(
    title: String,
    summary: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAction),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 顶部指标卡对金额做单行限制，避免大金额换行挤压布局。 */
@Composable
private fun LedgerMetricCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 流水标签复用统一的轻量样式。 */
@Composable
private fun FlowTag(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

/** 日历翻月按钮用统一胶囊按钮承接。 */
@Composable
private fun MonthSwitchButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Box(
            modifier = Modifier.padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 账本二级分段定义。 */
private enum class LedgerSegment(
    val label: String,
) {
    Flow("流水"),
    Trend("趋势"),
    Category("分类"),
    Calendar("日历"),
}

/** 时间范围枚举映射成界面可读文案。 */
private fun LedgerRangeFilter.toDisplayLabel(): String = when (this) {
    LedgerRangeFilter.TODAY -> "今日"
    LedgerRangeFilter.WEEK -> "本周"
    LedgerRangeFilter.MONTH -> "本月"
    LedgerRangeFilter.YEAR -> "本年"
    LedgerRangeFilter.CALENDAR -> "日历"
    LedgerRangeFilter.ALL -> "全部"
}

/** 账本页顶部摘要只保留用户真正在意的信息。 */
private fun buildLedgerHeaderSummary(state: LedgerUiState): String {
    return when {
        state.searchQuery.isNotBlank() -> "搜索结果 ${state.entries.size} 笔"
        state.selectedRange == LedgerRangeFilter.CALENDAR -> "逐天回看收支"
        state.entries.isEmpty() -> "当前账期暂无记录"
        else -> "共 ${state.entries.size} 笔 · ${state.rangeSummary}"
    }
}
