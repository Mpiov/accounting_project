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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.autobook.cn.feature.ledger.LedgerItemUiModel
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
        item(key = "pending_banner") {
            PendingSummaryBanner(
                pendingCount = state.pendingCount,
                onOpenPending = onOpenPending,
            )
        }
        item(key = "quick_actions") {
            QuickActionGrid(
                onCreateEntry = onCreateEntry,
                onOpenLedger = onOpenLedger,
                onOpenPending = onOpenPending,
                onOpenSettings = onOpenSettings,
            )
        }
        item(key = "insights") {
            InsightSummarySection(insights = state.insights.take(2))
        }
        item(key = "recent_entries_header") {
            OverviewSectionHeader(
                title = "最近记录",
                summary = "只保留最近几笔，方便快速回看，不再把完整流水塞进首页。",
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
                    text = state.highlightMessage,
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

/** 待确认横条把待处理任务从首页中突出出来，但不再与完整列表混排。 */
@Composable
private fun PendingSummaryBanner(
    pendingCount: Int,
    onOpenPending: () -> Unit,
) {
    val containerColor = if (pendingCount > 0) {
        Color(0xFFFFF3D8)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPending),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.PendingActions,
                        contentDescription = "待确认",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (pendingCount > 0) "还有 $pendingCount 笔待确认记录" else "当前没有待确认记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (pendingCount > 0) {
                        "建议优先处理，能让统计和总览更准确。"
                    } else {
                        "自动入账链路目前比较顺畅，可以去账本继续查看。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "查看",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 四宫格快捷动作缩短首页高频操作路径。 */
@Composable
private fun QuickActionGrid(
    onCreateEntry: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val actions = listOf(
        QuickActionItem("记一笔", "快速补记", Icons.Outlined.ReceiptLong, onCreateEntry),
        QuickActionItem("账本", "看流水与分析", Icons.Outlined.BarChart, onOpenLedger),
        QuickActionItem("待确认", "处理低置信度", Icons.Outlined.PendingActions, onOpenPending),
        QuickActionItem("设置", "配置与工具", Icons.Outlined.Settings, onOpenSettings),
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OverviewSectionHeader(
            title = "快捷动作",
            summary = "把高频动作前置到首页，减少在长页面里来回找入口。",
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
        ) {
            actions.forEach { action ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.48f)
                        .clickable(onClick = action.onClick),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = action.title,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        Text(
                            text = action.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = action.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 总览页只展示少量洞察卡，避免再次把分析区塞回首页。 */
@Composable
private fun InsightSummarySection(
    insights: List<LedgerInsightUiModel>,
) {
    if (insights.isEmpty()) {
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OverviewSectionHeader(
            title = "本月提醒",
            summary = "只保留最值得看的两条信息，节奏更轻一些。",
        )
        insights.forEach { insight ->
            Card(shape = RoundedCornerShape(24.dp)) {
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** 快捷动作的数据承载模型。 */
private data class QuickActionItem(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)
