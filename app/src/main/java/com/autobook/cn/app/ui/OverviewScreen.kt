@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.autobook.cn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.feature.ledger.LedgerInsightUiModel
import com.autobook.cn.feature.ledger.LedgerCategoryBreakdownUiModel
import com.autobook.cn.feature.ledger.LedgerItemUiModel
import com.autobook.cn.feature.ledger.LedgerTrendPointUiModel
import com.autobook.cn.feature.ledger.LedgerUiState

/**
 * 总览页只负责展示关键指标和高频动作。
 *
 * 参数说明：
 * - state：来自账本聚合层的界面状态。
 * - onCreateEntry：点击“记一笔”时的回调。
 * - onOpenLedger：进入账本页的回调。
 * - onOpenPending：进入待确认页的回调。
 * - onOpenSettings：进入设置页的回调。
 * - onOpenEntry：打开某条账本记录详情的回调。
 * - modifier：外层修饰符。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewScreen(
    state: LedgerUiState,
    onCreateEntry: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    /** 首页只切换“看什么”，不把跳转动作堆在首屏。 */
    var selectedPanel by rememberSaveable { mutableStateOf(OverviewPanel.SUMMARY) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "overview_hero") {
            OverviewHeroCard(
                state = state,
                modifier = Modifier.statusBarsPadding(),
            )
        }
        item(key = "overview_panel_switcher") {
            OverviewPanelSwitcher(
                selectedPanel = selectedPanel,
                onPanelSelected = { panel -> selectedPanel = panel },
            )
        }
        item(key = "overview_panel_content") {
            when (selectedPanel) {
                OverviewPanel.SUMMARY -> {
                    OverviewSummaryPanel(
                        state = state,
                        onOpenSettings = onOpenSettings,
                    )
                }

                OverviewPanel.TREND -> {
                    OverviewTrendPanel(
                        title = state.trendTitle,
                        points = state.trendPoints,
                        highlightMessage = state.highlightMessage,
                        onOpenLedger = onOpenLedger,
                    )
                }

                OverviewPanel.CATEGORY -> {
                    OverviewCategoryPanel(
                        categories = state.categoryBreakdown,
                        onOpenLedger = onOpenLedger,
                    )
                }
            }
        }
        item(key = "recent_entries_header") {
            OverviewSectionHeader(
                title = "最近记录",
                summary = "保留最近几笔，回看更直接。",
            )
        }
        if (state.entries.isEmpty()) {
            item(key = "overview_empty") {
                EmptyOverviewCard(onCreateEntry = onCreateEntry)
            }
        } else {
            items(
                items = state.entries.take(3),
                key = { entry -> entry.id },
            ) { entry ->
                OverviewEntryCard(
                    entry = entry,
                    onClick = { onOpenEntry(entry.id) },
                )
            }
        }
    }
}

/**
 * 顶部总卡承接本月收支和一句重点提示。
 *
 * 这里把金额字号略微下调，并限制为单行，避免用户提到的大金额换行问题再次出现。
 */
@Composable
private fun OverviewHeroCard(
    state: LedgerUiState,
    modifier: Modifier = Modifier,
) {
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFF0C2),
            Color(0xFFFFF9EE),
            MaterialTheme.colorScheme.surface,
        ),
    )
    val netColor = if (state.netPositive) Color(0xFF0B6E4F) else Color(0xFFC65D21)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(backgroundBrush)
            .padding(22.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(132.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.36f)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    text = buildOverviewSubtitle(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewMetricCard(
                    label = "收入",
                    value = state.incomeSummary,
                    valueColor = Color(0xFF0B6E4F),
                    modifier = Modifier.weight(1f),
                )
                OverviewMetricCard(
                    label = "支出",
                    value = state.expenseSummary,
                    valueColor = Color(0xFFC65D21),
                    modifier = Modifier.weight(1f),
                )
                OverviewMetricCard(
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

/** 单个指标卡复用相同视觉规则，并压缩金额字号防止换行。 */
@Composable
private fun OverviewMetricCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
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

/** 首页切换器只控制展示内容，不引入额外跳转动作。 */
@Composable
private fun OverviewPanelSwitcher(
    selectedPanel: OverviewPanel,
    onPanelSelected: (OverviewPanel) -> Unit,
) {
    Card(shape = RoundedCornerShape(24.dp)) {
        FlowRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OverviewPanel.entries.forEach { panel ->
                FilterChip(
                    selected = selectedPanel == panel,
                    onClick = { onPanelSelected(panel) },
                    label = { Text(panel.label) },
                )
            }
        }
    }
}

/** “概览”分段保留预算与两条重点提醒，避免首屏信息过多。 */
@Composable
private fun OverviewSummaryPanel(
    state: LedgerUiState,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CompactBudgetCard(
            amountLabel = state.budgetAmountLabel,
            spentLabel = state.budgetSpentLabel,
            remainingLabel = state.budgetRemainingLabel,
            usageSummary = state.budgetUsageSummary,
            usageRatio = state.budgetUsageRatio,
            exceeded = state.budgetExceeded,
            onOpenSettings = onOpenSettings,
        )
        InsightHighlightRow(insights = state.insights.take(2))
    }
}

/** “趋势”分段在首页只展示一个轻量图表和一句提示。 */
@Composable
private fun OverviewTrendPanel(
    title: String,
    points: List<LedgerTrendPointUiModel>,
    highlightMessage: String,
    onOpenLedger: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenLedger),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = highlightMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                points.takeLast(7).forEach { point ->
                    val barHeight = 16.dp + 72.dp * point.ratio.coerceAtLeast(if (point.amountLabel == "0") 0f else 0.14f)
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .background(
                                if (point.isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                            ),
                    )
                }
            }
            Text(
                text = "点击进入账本查看完整趋势",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** “分类”分段只保留最重要的三项支出分类，更符合首页的浏览节奏。 */
@Composable
private fun OverviewCategoryPanel(
    categories: List<LedgerCategoryBreakdownUiModel>,
    onOpenLedger: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenLedger),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "支出分类",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (categories.isEmpty()) {
                Text(
                    text = "先记几笔支出，这里会自动汇总主要分类。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                categories.take(3).forEach { category ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                text = category.ratioLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { category.ratioValue },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** 预算卡文案改成用户语气，删掉开发说明式提示。 */
@Composable
private fun CompactBudgetCard(
    amountLabel: String?,
    spentLabel: String,
    remainingLabel: String,
    usageSummary: String,
    usageRatio: Float,
    exceeded: Boolean,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSettings),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewMetricCard(
                    label = "预算",
                    value = amountLabel ?: "未设置",
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                OverviewMetricCard(
                    label = "已支出",
                    value = spentLabel,
                    valueColor = Color(0xFFC65D21),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = remainingLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (exceeded) Color(0xFFC65D21) else Color(0xFF0B6E4F),
            )
            LinearProgressIndicator(
                progress = { usageRatio },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = usageSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 把洞察变成两张小卡，保留重点但不讲太多说明。 */
@Composable
private fun InsightHighlightRow(
    insights: List<LedgerInsightUiModel>,
) {
    if (insights.isEmpty()) return

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        insights.forEach { insight ->
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = insight.supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 空状态不只提示“没有数据”，还明确告诉用户下一步该做什么。 */
@Composable
private fun EmptyOverviewCard(
    onCreateEntry: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCreateEntry),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "账本还是空的",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "先记第一笔，后面总览、趋势和分类卡片就会逐渐丰满起来。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "点击这里去记一笔",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 最近记录卡片保持轻量展示，同时保留进入详情的点击能力。 */
@Composable
private fun OverviewEntryCard(
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
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = entry.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
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
    }
}

/** 首页切换分段定义。 */
private enum class OverviewPanel(
    val label: String,
) {
    SUMMARY("概览"),
    TREND("趋势"),
    CATEGORY("分类"),
}

/** 把首页副标题改成更贴近用户理解的一句话。 */
private fun buildOverviewSubtitle(state: LedgerUiState): String {
    return when {
        state.entries.isEmpty() -> "还没有记录，先记一笔开始。"
        state.netPositive -> "这个月还有结余，收支状态比较轻松。"
        else -> "这个月支出偏高，记得回看趋势和分类。"
    }
}

/** 总览页通用区块标题，统一标题与说明层级。 */
@Composable
private fun OverviewSectionHeader(
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
