package com.autobook.cn.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.drawBehind
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
import com.autobook.cn.app.viewmodel.OverviewUiState
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.feature.ledger.model.LedgerItemUiModel
import com.autobook.cn.feature.ledger.model.LedgerRangeFilter
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
    state: OverviewUiState,
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
        if (state.recentEntries.isEmpty()) {
            item(key = "empty") {
                EmptyOverviewCard(onCreateEntry = onCreateEntry)
            }
        } else {
            item(key = "recent_timeline") {
                RecentRecordsTimeline(
                    entries = state.recentEntries.take(8),
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
 * - pendingCount：当前待确认记录数量，用于渲染标题。
 * - onOpenPending：点击提醒卡后进入待确认页的回调。
 */
@Composable
private fun PendingReminderCard(
    pendingCount: Int,
    onOpenPending: () -> Unit,
) {
    val cardShape = AutobookUiTokens.panelShape
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .autobookClippedClickable(shape = cardShape, onClick = onOpenPending),
        shape = cardShape,
        border = autobookCardBorder(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
    state: OverviewUiState,
    modifier: Modifier = Modifier,
) {
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            // 总览卡占据首页首屏大面积区域，因此只使用极浅米白渐变，不再使用明显色块。
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f),
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryMetricCard(
                    label = "收入",
                    value = state.incomeSummary,
                    valueColor = AutobookIncomeColor,
                    modifier = Modifier.weight(1f),
                )
                SummaryMetricCard(
                    label = "支出",
                    value = state.expenseSummary,
                    valueColor = AutobookExpenseColor,
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
    state: OverviewUiState,
    onOpenSettings: () -> Unit,
) {
    val monthLabel = YearMonth.now().format(DateTimeFormatter.ofPattern("MM月", Locale.CHINA))
    val settingsButtonShape = AutobookUiTokens.chipShape

    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    modifier = Modifier.autobookClippedClickable(shape = settingsButtonShape, onClick = onOpenSettings),
                    shape = settingsButtonShape,
                    // 预算设置入口统一使用浅棕主题填充，避免绿色按钮破坏总览页的整体风格。
                    color = autobookSoftFillColor(),
                ) {
                    Text(
                        text = "去设置",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
    val connectorColor = autobookTimelineConnectorColor()
    val timelineModifier = if (entries.size > 1) {
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val railX = overviewTimelineRailCenterX.toPx()
                val railInset = overviewTimelineRailVerticalInset.toPx().coerceAtMost(size.height / 2f)

                // 长链改为由父容器一次性绘制，避免每条记录各画一段时在卡片间距处断开。
                drawLine(
                    color = connectorColor,
                    start = Offset(railX, railInset),
                    end = Offset(railX, size.height - railInset),
                    strokeWidth = 2.dp.toPx(),
                )
            }
    } else {
        Modifier.fillMaxWidth()
    }

    Column(
        modifier = timelineModifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        entries.forEach { entry ->
            OverviewTimelineItem(
                entry = entry,
                onClick = { onOpenEntry(entry.id) },
            )
        }
    }
}

/**
 * 用途说明：
 * - 单条最近记录项只负责节点和右侧卡片，连续长链由父容器统一绘制。
 *
 * 参数说明：
 * - entry：当前账单条目。
 * - onClick：点击当前记录后的回调。
 */
@Composable
private fun OverviewTimelineItem(
    entry: LedgerItemUiModel,
    onClick: () -> Unit,
) {
    val cardBorder = autobookCardBorder()
    val dotOuterColor = MaterialTheme.colorScheme.primaryContainer
    val dotInnerColor = MaterialTheme.colorScheme.primary
    val nodeCoverColor = MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 86.dp)
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
            Canvas(modifier = Modifier.size(22.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)

                // 节点只负责遮住父级长链并提供定位点，连线本身由父容器统一绘制。
                drawCircle(
                    color = nodeCoverColor,
                    radius = 10.dp.toPx(),
                    center = center,
                )
                drawCircle(
                    color = dotOuterColor,
                    radius = 8.dp.toPx(),
                    center = center,
                )
                drawCircle(
                    color = dotInnerColor,
                    radius = 3.5.dp.toPx(),
                    center = center,
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
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.title != entry.category) {
                        Text(
                            text = entry.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                        AutobookIncomeColor
                    } else {
                        AutobookExpenseColor
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
 * - valueColor：指标金额颜色，用于在总览卡里强化收入与支出的区分。
 * - modifier：外层修饰符，便于在父布局中分配宽度。
 */
@Composable
private fun SummaryMetricCard(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
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
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 最近记录时间线轨道的横向中心。
 *
 * 用途说明：
 * - 左侧节点列固定 22dp 宽，因此长链中心固定在 11dp，父级画线和子节点才能严丝合缝。
 */
private val overviewTimelineRailCenterX = 11.dp

/**
 * 最近记录时间线首尾留白。
 *
 * 用途说明：
 * - 长链从第一枚节点附近开始，到最后一枚节点附近结束，避免线条顶到整块列表外沿。
 */
private val overviewTimelineRailVerticalInset = 43.dp

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
    val cardShape = AutobookUiTokens.panelShape
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .autobookClippedClickable(shape = cardShape, onClick = onCreateEntry),
        shape = cardShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
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
}
