package com.autobook.cn.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.feature.ledger.LedgerItemUiModel
import com.autobook.cn.feature.ledger.LedgerRangeFilter
import com.autobook.cn.feature.ledger.LedgerUiState
import java.time.YearMonth

/**
 * 总览页。
 *
 * 用途说明：
 * - 首页只保留“看账单状态 + 看预算 + 看最近几笔”三类高频信息。
 * - 删除待确认横条、快捷动作宫格和解释型长文案，保证首屏更简洁。
 *
 * 参数说明：
 * - state：总览页界面状态。
 * - onRangeChange：切换本年、本月、本周、本日账单的回调。
 * - onOpenAllRecords：进入详细记录页的回调。
 * - onOpenBudgetSettings：进入预算设置页的回调。
 * - onOpenEntry：打开单条账单的回调。
 * - onCreateEntry：空状态下记一笔的回调。
 * - modifier：外层修饰符。
 */
@Composable
fun OverviewScreen(
    state: LedgerUiState,
    onRangeChange: (LedgerRangeFilter) -> Unit,
    onOpenAllRecords: () -> Unit,
    onOpenBudgetSettings: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onCreateEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "hero") {
            OverviewHeroCard(
                state = state,
                modifier = Modifier.statusBarsPadding(),
            )
        }
        item(key = "range_switcher") {
            OverviewRangeSwitcher(
                selectedRange = state.selectedRange,
                onRangeChange = onRangeChange,
            )
        }
        item(key = "budget") {
            BudgetOverviewCard(
                state = state,
                onOpenSettings = onOpenBudgetSettings,
            )
        }
        item(key = "recent_header") {
            RecordsHeader(
                title = "最近记录",
                actionLabel = "详细记录",
                onActionClick = onOpenAllRecords,
            )
        }
        if (state.entries.isEmpty()) {
            item(key = "empty") {
                EmptyOverviewCard(onCreateEntry = onCreateEntry)
            }
        } else {
            item(key = "recent_timeline") {
                RecentRecordsTimeline(
                    entries = state.entries.take(4),
                    onOpenEntry = onOpenEntry,
                )
            }
        }
    }
}

/** 顶部总览卡只保留标题与三项关键金额。 */
@Composable
private fun OverviewHeroCard(
    state: LedgerUiState,
    modifier: Modifier = Modifier,
) {
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.surface,
        ),
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier
                .background(backgroundBrush)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "总览",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.periodTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = buildOverviewSummary(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryMetricCard(
                    label = "收入",
                    value = state.incomeSummary,
                    modifier = Modifier.weight(1f),
                )
                SummaryMetricCard(
                    label = "支出",
                    value = state.expenseSummary,
                    modifier = Modifier.weight(1f),
                )
                SummaryMetricCard(
                    label = "结余",
                    value = state.netSummary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 首页账期切换按钮。 */
@Composable
private fun OverviewRangeSwitcher(
    selectedRange: LedgerRangeFilter,
    onRangeChange: (LedgerRangeFilter) -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            overviewRanges().forEach { range ->
                FilterChip(
                    selected = selectedRange == range,
                    onClick = { onRangeChange(range) },
                    label = { Text(range.overviewLabel()) },
                )
            }
        }
    }
}

/** 预算卡改成圆环图 + 设置入口的并排布局。 */
@Composable
private fun BudgetOverviewCard(
    state: LedgerUiState,
    onOpenSettings: () -> Unit,
) {
    val monthLabel = YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("MM月", java.util.Locale.CHINA))
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${monthLabel}总预算",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    modifier = Modifier.clickable(onClick = onOpenSettings),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                ) {
                    Text(
                        text = "去设置",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiary,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                BudgetRingChart(
                    ratio = state.budgetUsageRatio,
                    remainingText = if (state.budgetAmountLabel == null) "0%" else "${((1 - state.budgetUsageRatio) * 100).toInt().coerceAtLeast(0)}%",
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BudgetInfoRow(label = "剩余预算", value = state.budgetRemainingLabel)
                    BudgetInfoRow(label = "本月预算", value = state.budgetAmountLabel ?: "未设置")
                    BudgetInfoRow(label = "本月支出", value = state.budgetSpentLabel)
                }
            }
        }
    }
}

/** 预算圆环图。 */
@Composable
private fun BudgetRingChart(
    ratio: Float,
    remainingText: String,
) {
    val baseRingColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val strokeWidth = 16.dp.toPx()
            drawArc(
                color = baseRingColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(size.width, size.height),
                topLeft = Offset.Zero,
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * (1f - ratio.coerceIn(0f, 1f)),
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(size.width, size.height),
                topLeft = Offset.Zero,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "剩余",
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
            )
            Text(
                text = remainingText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 预算信息行。 */
@Composable
private fun BudgetInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 最近记录标题区。 */
@Composable
private fun RecordsHeader(
    title: String,
    actionLabel: String,
    onActionClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = actionLabel,
            modifier = Modifier.clickable(onClick = onActionClick),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 首页只展示少量最近记录，并用时间线样式呈现。 */
@Composable
private fun RecentRecordsTimeline(
    entries: List<LedgerItemUiModel>,
    onOpenEntry: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        entries.forEachIndexed { index, entry ->
            OverviewTimelineItem(
                entry = entry,
                showTopConnector = index != 0,
                showBottomConnector = index != entries.lastIndex,
                onClick = { onOpenEntry(entry.id) },
            )
        }
    }
}

/** 总览页时间线单项。 */
@Composable
private fun OverviewTimelineItem(
    entry: LedgerItemUiModel,
    showTopConnector: Boolean,
    showBottomConnector: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(98.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(
                        color = when {
                            showTopConnector || showBottomConnector -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                            else -> Color.Transparent
                        },
                    ),
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            )
        }
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
}

/** 金额指标卡，字号统一压低以减少换行。 */
@Composable
private fun SummaryMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 空账本时只保留一个简洁的行动提示。 */
@Composable
private fun EmptyOverviewCard(
    onCreateEntry: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCreateEntry),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "先记一笔，开始积累账单",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "有了记录之后，这里会自动展示预算和最近账单。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 首页顶部一句话摘要。 */
private fun buildOverviewSummary(state: LedgerUiState): String {
    return when (state.selectedRange) {
        LedgerRangeFilter.YEAR -> "看全年收支与结余变化"
        LedgerRangeFilter.MONTH -> "看本月收支状态"
        LedgerRangeFilter.WEEK -> "看本周开销变化"
        LedgerRangeFilter.TODAY -> "看今天的收支情况"
        else -> "看最近账单情况"
    }
}

/** 首页展示的 4 个账期按钮。 */
private fun overviewRanges(): List<LedgerRangeFilter> = listOf(
    LedgerRangeFilter.YEAR,
    LedgerRangeFilter.MONTH,
    LedgerRangeFilter.WEEK,
    LedgerRangeFilter.TODAY,
)

/** 总览页账期按钮文案。 */
private fun LedgerRangeFilter.overviewLabel(): String = when (this) {
    LedgerRangeFilter.YEAR -> "本年"
    LedgerRangeFilter.MONTH -> "本月"
    LedgerRangeFilter.WEEK -> "本周"
    LedgerRangeFilter.TODAY -> "本日"
    LedgerRangeFilter.CALENDAR -> "日历"
    LedgerRangeFilter.ALL -> "全部"
}
