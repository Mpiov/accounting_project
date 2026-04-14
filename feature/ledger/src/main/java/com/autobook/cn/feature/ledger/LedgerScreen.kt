package com.autobook.cn.feature.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autobook.cn.core.model.MoneyDirection
import java.time.LocalDate
import kotlinx.coroutines.launch

/** 账本首页支持的时间范围筛选。 */
enum class LedgerRangeFilter {
    TODAY,
    WEEK,
    MONTH,
    YEAR,
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
    val incomeLabel: String? = null,
    val expenseLabel: String? = null,
    /** 当天净额，正数代表结余，负数代表支出高于收入。 */
    val netLabel: String? = null,
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
    val direction: MoneyDirection = MoneyDirection.EXPENSE,
)

/** 周/月/年账单卡片展示模型。 */
data class LedgerBillCycleUiModel(
    val title: String,
    val periodLabel: String,
    val incomeSummary: String,
    val expenseSummary: String,
    val netSummary: String,
    val entriesLabel: String,
    val netPositive: Boolean,
)

/** 首页小洞察卡片展示模型。 */
data class LedgerInsightUiModel(
    val title: String,
    val value: String,
    val supporting: String,
)

/** 趋势图中的柱状点展示模型。 */
data class LedgerTrendPointUiModel(
    val label: String,
    val amountLabel: String,
    val ratio: Float,
    val isCurrent: Boolean,
)

/** 分类分布展示模型。 */
data class LedgerCategoryBreakdownUiModel(
    val category: String,
    val amount: String,
    val ratioLabel: String,
    val ratioValue: Float,
)

/** 账本首页的完整界面状态。 */
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
    val trendTitle: String = "近 7 天支出走势",
    val trendSubtitle: String = "把最近消费波动放到一个地方看清楚",
    val trendPoints: List<LedgerTrendPointUiModel> = emptyList(),
    val categoryBreakdown: List<LedgerCategoryBreakdownUiModel> = emptyList(),
    /** 预算金额为空时代表当前月份还未设置预算。 */
    val budgetAmountLabel: String? = null,
    val budgetSpentLabel: String = "¥0.00",
    val budgetRemainingLabel: String = "去设置预算",
    val budgetUsageSummary: String = "设置本月预算后，这里会自动追踪剩余额度。",
    val budgetUsageRatio: Float = 0f,
    val budgetExceeded: Boolean = false,
    val highlightMessage: String = "待确认、趋势和分类分布都会在这里汇总",
    val entriesSummary: String = "当前筛选下暂无流水",
    val entries: List<LedgerItemUiModel> = emptyList(),
)

/** 账本首页，展示总览、时间筛选、工作台、趋势和账单流水。 */
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val billSectionIndex = if (state.selectedRange == LedgerRangeFilter.CALENDAR) 4 else 3

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(Icons.Outlined.Add, contentDescription = "记账") },
                text = { Text("记一笔") },
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    BottomNavAction(
                        icon = Icons.Outlined.ReceiptLong,
                        label = "总览",
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.weight(1f),
                    )
                    BottomNavAction(
                        icon = Icons.Outlined.BarChart,
                        label = "账单",
                        onClick = { scope.launch { listState.animateScrollToItem(billSectionIndex) } },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.weight(1.15f))
                    BottomNavAction(
                        icon = Icons.Outlined.PendingActions,
                        label = "待确认",
                        badge = state.pendingCount.takeIf { it > 0 }?.toString(),
                        onClick = onPendingClick,
                        modifier = Modifier.weight(1f),
                    )
                    BottomNavAction(
                        icon = Icons.Outlined.Settings,
                        label = "设置",
                        onClick = onSettingsClick,
                        modifier = Modifier.weight(1f),
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = padding.calculateBottomPadding() + 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "hero") {
                HeroSection(
                    state = state,
                    onPendingClick = onPendingClick,
                    modifier = Modifier.statusBarsPadding(),
                )
            }
            item(key = "filters") {
                FilterSection(
                    state = state,
                    onSearchChange = onSearchChange,
                    onRangeChange = onRangeChange,
                )
            }
            item(key = "workbench") {
                WorkbenchSection(
                    state = state,
                    onRangeChange = onRangeChange,
                    onPendingClick = onPendingClick,
                    onSettingsClick = onSettingsClick,
                )
            }
            if (state.selectedRange == LedgerRangeFilter.CALENDAR) {
                item(key = "calendar") {
                    CalendarSection(
                        state = state,
                        onPreviousMonthClick = onPreviousMonthClick,
                        onNextMonthClick = onNextMonthClick,
                        onCalendarDateClick = onCalendarDateClick,
                    )
                }
            }
            item(key = "bills") {
                BillCycleSection(billCycles = state.billCycles)
            }
            item(key = "insights") {
                InsightSection(insights = state.insights)
            }
            item(key = "trend") {
                TrendSection(
                    title = state.trendTitle,
                    subtitle = state.trendSubtitle,
                    points = state.trendPoints,
                )
            }
            item(key = "category") {
                CategorySection(categoryBreakdown = state.categoryBreakdown)
            }
            item(key = "entries_header") {
                SectionHeader(
                    eyebrow = "流水",
                    title = "账单明细",
                    subtitle = state.entriesSummary,
                )
            }
            if (state.entries.isEmpty()) {
                item(key = "empty") {
                    EmptyLedgerCard(selectedRange = state.selectedRange)
                }
            } else {
                items(state.entries, key = { it.id }) { entry ->
                    LedgerEntryCard(
                        entry = entry,
                        onClick = { onEntryClick(entry.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroSection(
    state: LedgerUiState,
    onPendingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val warmBackground = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFE082),
            Color(0xFFFFF3BF),
            MaterialTheme.colorScheme.surface,
        ),
    )
    val chipColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    val netColor = if (state.netPositive) Color(0xFF0B6E4F) else Color(0xFFC65D21)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(warmBackground)
            .padding(22.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(140.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        text = state.periodSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    modifier = Modifier.clickable(onClick = onPendingClick),
                    shape = RoundedCornerShape(20.dp),
                    color = chipColor,
                ) {
                    Text(
                        text = if (state.pendingCount == 0) "待确认 0" else "待确认 ${state.pendingCount}",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = chipColor,
            ) {
                Text(
                    text = state.highlightMessage,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroMetricCard(label = "收入", value = state.incomeSummary, modifier = Modifier.weight(1f))
                HeroMetricCard(label = "支出", value = state.expenseSummary, modifier = Modifier.weight(1f))
                HeroMetricCard(
                    label = "结余",
                    value = state.netSummary,
                    valueColor = netColor,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = state.rangeSummary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun FilterSection(
    state: LedgerUiState,
    onSearchChange: (String) -> Unit,
    onRangeChange: (LedgerRangeFilter) -> Unit,
) {
    val scrollState = rememberScrollState()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(
                eyebrow = "筛选",
                title = "账本时间轴",
                subtitle = "搜索、切换账期，再去看分类和趋势会更顺手",
            )
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
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LedgerRangeFilter.entries.forEach { range ->
                    FilterChip(
                        selected = state.selectedRange == range,
                        onClick = { onRangeChange(range) },
                        label = { Text(range.label()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkbenchSection(
    state: LedgerUiState,
    onRangeChange: (LedgerRangeFilter) -> Unit,
    onPendingClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val actions = listOf(
        WorkbenchItem("日历回看", "逐天看收支", Icons.Outlined.CalendarMonth, null, state.selectedRange == LedgerRangeFilter.CALENDAR) {
            onRangeChange(LedgerRangeFilter.CALENDAR)
        },
        WorkbenchItem("本周账单", "7 天概览", Icons.Outlined.DateRange, null, state.selectedRange == LedgerRangeFilter.WEEK) {
            onRangeChange(LedgerRangeFilter.WEEK)
        },
        WorkbenchItem("本月账单", "预算视角", Icons.Outlined.BarChart, null, state.selectedRange == LedgerRangeFilter.MONTH) {
            onRangeChange(LedgerRangeFilter.MONTH)
        },
        WorkbenchItem("年度账单", "看长期趋势", Icons.Outlined.Timeline, null, state.selectedRange == LedgerRangeFilter.YEAR) {
            onRangeChange(LedgerRangeFilter.YEAR)
        },
        WorkbenchItem("待确认", "复核自动入账", Icons.Outlined.PendingActions, state.pendingCount.takeIf { it > 0 }?.toString(), false, onPendingClick),
        WorkbenchItem("自动记账", "能力与来源配置", Icons.Outlined.Settings, null, false, onSettingsClick),
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = "工作台",
            title = "多功能入口",
            subtitle = "把常用动作放在首页，少跳转也能完成查看和处理",
        )
        actions.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item ->
                    WorkbenchActionCard(item = item, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WorkbenchActionCard(
    item: WorkbenchItem,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (item.selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val border = if (item.selected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    Surface(
        modifier = modifier.clickable(onClick = item.onClick),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = border,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                item.badge?.let { badge ->
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFE4B2)) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(text = item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalendarSection(
    state: LedgerUiState,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    onCalendarDateClick: (LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = "日历",
            title = "每日收支",
            subtitle = state.selectedDateSummary ?: "在月历里看每天的收入和支出缩略",
        )
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
                        text = state.calendarMonthLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row {
                        MonthSwitchIcon(
                            icon = Icons.Outlined.ChevronLeft,
                            contentDescription = "上个月",
                            onClick = onPreviousMonthClick,
                        )
                        MonthSwitchIcon(
                            icon = Icons.Outlined.ChevronRight,
                            contentDescription = "下个月",
                            onClick = onNextMonthClick,
                        )
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
                state.calendarDays.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        week.forEach { day ->
                            CalendarDayCell(
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
}

@Composable
private fun MonthSwitchIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .padding(start = 8.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(8.dp),
        )
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
        day.isToday -> Color(0xFFFFF1C9)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val labelColor = when {
        day.isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = day.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (day.isToday || day.isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (day.isCurrentMonth) labelColor else labelColor.copy(alpha = 0.45f),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                day.incomeLabel?.let { income ->
                    Text(
                        text = income,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF0B6E4F),
                    )
                }
                day.expenseLabel?.let { expense ->
                    Text(
                        text = expense,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFC65D21),
                    )
                }
                if (!day.hasEntries && day.incomeLabel == null && day.expenseLabel == null) {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun BillCycleSection(
    billCycles: List<LedgerBillCycleUiModel>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = "账单",
            title = "周 / 月 / 年账单",
            subtitle = "同一首页同时保留短期节奏和长期趋势，切换成本更低",
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(billCycles) { cycle ->
                BillCycleCard(cycle = cycle)
            }
        }
    }
}

@Composable
private fun BillCycleCard(
    cycle: LedgerBillCycleUiModel,
) {
    Card(
        modifier = Modifier.width(272.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = cycle.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = cycle.periodLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricBlock(label = "收入", value = cycle.incomeSummary, modifier = Modifier.weight(1f))
                MetricBlock(label = "支出", value = cycle.expenseSummary, modifier = Modifier.weight(1f))
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = cycle.netSummary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (cycle.netPositive) Color(0xFF0B6E4F) else Color(0xFFC65D21),
                )
                Text(
                    text = cycle.entriesLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InsightSection(
    insights: List<LedgerInsightUiModel>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = "洞察",
            title = "首页速览",
            subtitle = "把预算感、效率感和自动记账健康度放到一屏里",
        )
        insights.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { insight ->
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = insight.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = insight.value,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = insight.supporting,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendSection(
    title: String,
    subtitle: String,
    points: List<LedgerTrendPointUiModel>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = "趋势",
            title = title,
            subtitle = subtitle,
        )
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    points.forEach { point ->
                        TrendBar(point = point)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendBar(
    point: LedgerTrendPointUiModel,
) {
    val barColor = if (point.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val minRatio = if (point.amountLabel == "0") 0f else 0.16f
    val height = 18.dp + 96.dp * point.ratio.coerceAtLeast(minRatio)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = point.amountLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (point.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(height)
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

@Composable
private fun CategorySection(
    categoryBreakdown: List<LedgerCategoryBreakdownUiModel>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            eyebrow = "分类",
            title = "支出分布",
            subtitle = "更快看清钱主要花在哪些分类",
        )
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (categoryBreakdown.isEmpty()) {
                    Text(
                        text = "当前账期还没有可展示的支出分类，新增几笔后这里会自动补全分布。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    categoryBreakdown.forEachIndexed { index, category ->
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
                            LinearProgressIndicator(
                                progress = { category.ratioValue },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                            )
                        }
                        if (index != categoryBreakdown.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerEntryCard(
    entry: LedgerItemUiModel,
    onClick: () -> Unit,
) {
    val amountColor = if (entry.direction == MoneyDirection.INCOME) Color(0xFF0B6E4F) else Color(0xFFC65D21)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = entry.amount,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EntryTag(
                    text = entry.category,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                EntryTag(
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

@Composable
private fun EntryTag(
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

@Composable
private fun EmptyLedgerCard(
    selectedRange: LedgerRangeFilter,
) {
    val message = when (selectedRange) {
        LedgerRangeFilter.CALENDAR -> "所选日期还没有记录。你可以切换其它日期，或者先手动补记一笔。"
        LedgerRangeFilter.WEEK -> "本周还没有账单。等通知自动入账，或先补记几笔常用消费。"
        LedgerRangeFilter.MONTH -> "本月账单还是空的，记几笔后趋势和分类卡片都会开始工作。"
        LedgerRangeFilter.YEAR -> "今年还没有账本记录，年度趋势会在你累计数据后逐步成型。"
        LedgerRangeFilter.TODAY -> "今天还没有流水，自动入账或手动补记都会出现在这里。"
        LedgerRangeFilter.ALL -> "本地账本还没有记录，先创建第一笔，首页就会逐渐丰满起来。"
    }

    Card(shape = RoundedCornerShape(28.dp)) {
        Text(
            text = message,
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BottomNavAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(imageVector = icon, contentDescription = label)
            badge?.let {
                Surface(
                    modifier = Modifier.padding(start = 12.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SectionHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class WorkbenchItem(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val badge: String?,
    val selected: Boolean,
    val onClick: () -> Unit,
)

private fun LedgerRangeFilter.label(): String = when (this) {
    LedgerRangeFilter.TODAY -> "今日"
    LedgerRangeFilter.WEEK -> "本周"
    LedgerRangeFilter.MONTH -> "本月"
    LedgerRangeFilter.YEAR -> "本年"
    LedgerRangeFilter.CALENDAR -> "日历"
    LedgerRangeFilter.ALL -> "全部"
}
