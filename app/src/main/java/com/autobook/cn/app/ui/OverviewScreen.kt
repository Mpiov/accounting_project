package com.autobook.cn.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.feature.ledger.LedgerItemUiModel
import com.autobook.cn.feature.ledger.LedgerRangeFilter
import com.autobook.cn.feature.ledger.LedgerUiState
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 用途说明：
 * - 首页只保留总览、预算和最近记录三块高频内容，帮助用户在首屏快速判断当前账单状态。
 *
 * 参数说明：
 * - state：首页所需的账本聚合状态。
 * - onRangeChange：切换首页统计范围时的回调。
 * - onOpenPending：从首页提醒卡进入待确认页的回调。
 * - onOpenAllRecords：打开全部记录页的回调。
 * - onOpenBudgetSettings：打开预算设置页的回调。
 * - onOpenEntry：打开单条账单详情的回调。
 * - onCreateEntry：空状态下直接记一笔的回调。
 * - modifier：外层修饰符，便于被导航壳层注入布局约束。
 */
@Composable
fun OverviewScreen(
    state: LedgerUiState,
    onRangeChange: (LedgerRangeFilter) -> Unit,
    onOpenPending: () -> Unit,
    onOpenAllRecords: () -> Unit,
    onOpenBudgetSettings: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onCreateEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "hero") {
            OverviewHeroCard(
                state = state,
                modifier = Modifier.padding(top = mainPageTopInset()),
            )
        }
        item(key = "range_switcher") {
            OverviewRangeSwitcher(
                selectedRange = state.selectedRange,
                onRangeChange = onRangeChange,
            )
        }
        if (state.pendingCount > 0) {
            item(key = "pending_notice") {
                PendingReminderCard(
                    pendingCount = state.pendingCount,
                    onOpenPending = onOpenPending,
                )
            }
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

/**
 * 用途说明：
 * - 在首页显眼位置提醒用户还有待确认记录，形成“打开 App 就能看见”的体验闭环。
 * - 这张卡只负责提醒与引导，不会替用户做强制跳转。
 *
 * 参数说明：
 * - pendingCount：当前待确认记录数量，用于渲染标题和副文案。
 * - onOpenPending：点击提醒卡后进入待确认页的回调。
 */
@Composable
private fun PendingReminderCard(
    pendingCount: Int,
    onOpenPending: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPending),
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = CircleShape,
                    ),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "有 $pendingCount 条待确认记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = buildPendingReminderSummary(pendingCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Surface(
                shape = AutobookUiTokens.chipShape,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
            ) {
                Text(
                    text = "去处理",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/**
 * 用途说明：
 * - 顶部总览卡集中展示当前范围的总标题、摘要和三项关键金额。
 *
 * 参数说明：
 * - state：首页聚合状态，用于渲染标题、摘要和金额。
 * - modifier：外层修饰符，主要用于承接状态栏间距。
 */
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
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/**
 * 用途说明：
 * - 顶部范围切换条用于在本年、本月、本周、本日之间快速切换统计视角。
 *
 * 参数说明：
 * - selectedRange：当前选中的统计范围。
 * - onRangeChange：点击某个范围后的回调。
 */
@Composable
private fun OverviewRangeSwitcher(
    selectedRange: LedgerRangeFilter,
    onRangeChange: (LedgerRangeFilter) -> Unit,
) {
    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
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
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    label = { Text(range.overviewLabel()) },
                )
            }
        }
    }
}

/**
 * 用途说明：
 * - 预算卡展示本月预算进度，并提供预算设置入口。
 *
 * 参数说明：
 * - state：首页聚合状态，用于读取预算金额、剩余额度和进度。
 * - onOpenSettings：点击预算设置入口后的回调。
 */
@Composable
private fun BudgetOverviewCard(
    state: LedgerUiState,
    onOpenSettings: () -> Unit,
) {
    val monthLabel = YearMonth.now().format(DateTimeFormatter.ofPattern("MM月", Locale.CHINA))

    Card(
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
                    text = "${monthLabel}总预算",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    modifier = Modifier.clickable(onClick = onOpenSettings),
                    shape = AutobookUiTokens.chipShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = "去设置",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BudgetRingChart(
                    ratio = state.budgetUsageRatio,
                    remainingText = if (state.budgetAmountLabel == null) {
                        "0%"
                    } else {
                        "${((1 - state.budgetUsageRatio) * 100).toInt().coerceAtLeast(0)}%"
                    },
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

/**
 * 用途说明：
 * - 环形图以“剩余预算”为主语义，帮助用户快速判断本月还剩多少可用额度。
 *
 * 参数说明：
 * - ratio：已使用预算比例，范围建议为 0 到 1。
 * - remainingText：环形图中心展示的剩余百分比文本。
 */
@Composable
private fun BudgetRingChart(
    ratio: Float,
    remainingText: String,
) {
    val baseRingColor = autobookSoftFillColor()
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

/**
 * 用途说明：
 * - 单行展示预算信息的标签和值，保持预算卡内部信息规整。
 *
 * 参数说明：
 * - label：左侧标签文本。
 * - value：右侧对应值。
 */
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

/**
 * 用途说明：
 * - 最近记录标题区提供标题和“详细记录”跳转入口。
 *
 * 参数说明：
 * - title：左侧标题。
 * - actionLabel：右侧动作文案。
 * - onActionClick：点击右侧动作后的回调。
 */
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

/**
 * 用途说明：
 * - 首页最近记录用时间线样式呈现，帮助用户快速按时间回看最新几笔账单。
 *
 * 参数说明：
 * - entries：首页最近记录列表。
 * - onOpenEntry：点击某条记录后的回调。
 */
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

/**
 * 用途说明：
 * - 单条最近记录项使用“节点 + 连线 + 方圆卡片”的布局，保证时间线连续、节点居中。
 *
 * 参数说明：
 * - entry：当前账单条目。
 * - showTopConnector：是否绘制圆点上方连线。
 * - showBottomConnector：是否绘制圆点下方连线。
 * - onClick：点击当前记录后的回调。
 */
@Composable
private fun OverviewTimelineItem(
    entry: LedgerItemUiModel,
    showTopConnector: Boolean,
    showBottomConnector: Boolean,
    onClick: () -> Unit,
) {
    val connectorColor = autobookTimelineConnectorColor()
    val cardBorder = autobookCardBorder()
    val dotOuterColor = MaterialTheme.colorScheme.primaryContainer
    val dotInnerColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .fillMaxHeight()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val strokeWidth = 2.dp.toPx()

                // 连线只从圆点中心向上下延展，这样上下卡片之间即使留一点呼吸感也不会断线。
                if (showTopConnector) {
                    drawLine(
                        color = connectorColor,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, centerY),
                        strokeWidth = strokeWidth,
                    )
                }
                if (showBottomConnector) {
                    drawLine(
                        color = connectorColor,
                        start = Offset(centerX, centerY),
                        end = Offset(centerX, size.height),
                        strokeWidth = strokeWidth,
                    )
                }

                // 外圈和内芯拆开绘制，能让时间线节点更稳，也更容易看清定位点。
                drawCircle(
                    color = dotOuterColor,
                    radius = 8.dp.toPx(),
                    center = Offset(centerX, centerY),
                )
                drawCircle(
                    color = dotInnerColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(centerX, centerY),
                )
            }
        }
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            shape = AutobookUiTokens.compactPanelShape,
            border = cardBorder,
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
                    color = if (entry.direction == MoneyDirection.INCOME) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                )
            }
        }
    }
}

/**
 * 用途说明：
 * - 顶部三项统计块使用更方一点的小卡片，减少“卡片里套卡片”的松散感。
 *
 * 参数说明：
 * - label：指标标签。
 * - value：指标金额文本。
 * - modifier：外层修饰符，便于在父布局中分配宽度。
 */
@Composable
private fun SummaryMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = AutobookUiTokens.innerPanelShape,
        border = autobookCardBorder(),
        color = autobookSoftFillColor(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = summaryMetricFontSize(value)),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 用途说明：
 * - 空账本时给用户一个直接的起步入口，不展示多余说明。
 *
 * 参数说明：
 * - onCreateEntry：点击空状态卡片后的回调。
 */
@Composable
private fun EmptyOverviewCard(
    onCreateEntry: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCreateEntry),
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
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
                text = "有了记录之后，这里会自动显示预算和最近账单。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 用途说明：
 * - 根据当前范围生成顶部一句话摘要，让首页标题下方信息更直接。
 *
 * 参数说明：
 * - state：首页聚合状态，用于判断当前统计范围。
 */
private fun buildOverviewSummary(state: LedgerUiState): String {
    return when (state.selectedRange) {
        LedgerRangeFilter.YEAR -> "看全年收支与结余变化"
        LedgerRangeFilter.MONTH -> "看本月收支状态"
        LedgerRangeFilter.WEEK -> "看本周开销变化"
        LedgerRangeFilter.TODAY -> "看今天的收支情况"
        else -> "看最近账单情况"
    }
}

/**
 * 用途说明：
 * - 根据待确认数量生成首页提醒卡的副文案，让用户知道“不强跳，但值得尽快处理”。
 *
 * 参数说明：
 * - pendingCount：当前待确认记录数量。
 */
private fun buildPendingReminderSummary(pendingCount: Int): String {
    return if (pendingCount == 1) {
        "打开待确认页补充核对后，这条记录才会正式进入账本统计。"
    } else {
        "这些记录还没正式入账，建议稍后集中确认，避免总览统计出现偏差。"
    }
}

/**
 * 用途说明：
 * - 当总览卡金额文本较长时，轻微缩小字号，优先保证数字仍能完整显示在一行。
 *
 * 参数说明：
 * - value：当前指标金额文本。
 */
private fun summaryMetricFontSize(value: String) = when {
    value.length >= 16 -> 9.sp
    value.length >= 14 -> 10.sp
    value.length >= 11 -> 12.sp
    else -> 14.sp
}

/**
 * 用途说明：
 * - 首页只展示四个最高频的统计范围按钮。
 */
private fun overviewRanges(): List<LedgerRangeFilter> = listOf(
    LedgerRangeFilter.YEAR,
    LedgerRangeFilter.MONTH,
    LedgerRangeFilter.WEEK,
    LedgerRangeFilter.TODAY,
)

/**
 * 用途说明：
 * - 将账本范围映射为首页显示文案。
 */
private fun LedgerRangeFilter.overviewLabel(): String = when (this) {
    LedgerRangeFilter.YEAR -> "本年"
    LedgerRangeFilter.MONTH -> "本月"
    LedgerRangeFilter.WEEK -> "本周"
    LedgerRangeFilter.TODAY -> "本日"
    LedgerRangeFilter.CALENDAR -> "日历"
    LedgerRangeFilter.ALL -> "全部"
}
