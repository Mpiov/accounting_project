package com.autobook.cn.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autobook.cn.app.viewmodel.RecordTimelineItemUiModel
import com.autobook.cn.core.model.MoneyDirection

/**
 * 用途说明：
 * - 详细记录页负责展示按时间排序的完整记录列表。
 *
 * 参数说明：
 * - title：顶部导航标题。
 * - summary：顶部摘要卡文案。
 * - items：时间线记录列表。
 * - onSelectEntry：点击某条记录后的回调。
 * - onBack：返回上一级页面的回调。
 * - modifier：外层修饰符，方便导航壳层注入布局。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsTimelineScreen(
    title: String,
    summary: String,
    items: List<RecordTimelineItemUiModel>,
    onSelectEntry: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AutobookIcon(
                            asset = AutobookIconAsset.Back,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        // 时间线页改为透明底，让统一纸张背景继续可见。
        containerColor = Color.Transparent,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "summary") {
                RecordsSummaryCard(summary = summary)
            }
            itemsIndexed(items = items, key = { _, item -> item.id }) { index, item ->
                val showDate = index == 0 || items[index - 1].dateLabel != item.dateLabel
                val showTopConnector = index != 0 && items[index - 1].dateLabel == item.dateLabel
                val showBottomConnector = index != items.lastIndex && items[index + 1].dateLabel == item.dateLabel

                TimelineEntryCard(
                    item = item,
                    showDate = showDate,
                    showTopConnector = showTopConnector,
                    showBottomConnector = showBottomConnector,
                    onClick = { onSelectEntry(item.id) },
                )
            }
        }
    }
}

/**
 * 用途说明：
 * - 顶部摘要卡展示 ViewModel 按当前筛选上下文生成的说明。
 *
 * 参数说明：
 * - summary：当前记录列表的摘要文案。
 */
@Composable
private fun RecordsSummaryCard(
    summary: String,
) {
    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Text(
            text = summary,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 用途说明：
 * - 单条时间线记录负责渲染日期分组标题、左侧时间线节点以及右侧账单卡片。
 *
 * 参数说明：
 * - item：当前记录项。
 * - showDate：是否在该条记录上方展示日期标题。
 * - showTopConnector：是否绘制圆点上方连线。
 * - showBottomConnector：是否绘制圆点下方连线。
 * - onClick：点击记录后的回调。
 */
@Composable
private fun TimelineEntryCard(
    item: RecordTimelineItemUiModel,
    showDate: Boolean,
    showTopConnector: Boolean,
    showBottomConnector: Boolean,
    onClick: () -> Unit,
) {
    val connectorColor = autobookTimelineConnectorColor()
    val itemDirection = if (item.isIncome) MoneyDirection.INCOME else MoneyDirection.EXPENSE
    val timelineModifier = if (showTopConnector || showBottomConnector) {
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val railX = recordTimelineRailCenterX.toPx()
                val dateHeight = if (showDate) recordTimelineDateHeaderHeight.toPx() else 0f
                val nodeY = dateHeight + recordTimelineRowMinHeight.toPx() / 2f
                val startY = if (showTopConnector) 0f else nodeY
                val endY = if (showBottomConnector) size.height else nodeY

                // 完整记录页同样由条目容器负责补齐整段连线，日期标题和卡片间距不会再把链条切断。
                drawLine(
                    color = connectorColor,
                    start = Offset(railX, startY),
                    end = Offset(railX, endY),
                    strokeWidth = 2.dp.toPx(),
                )
            }
    } else {
        Modifier.fillMaxWidth()
    }

    Box(modifier = timelineModifier) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (showDate) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(recordTimelineDateHeaderHeight)
                        .padding(start = 50.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = item.dateLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = recordTimelineRowMinHeight)
                    .clickable(onClick = onClick)
                    .padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // 详细记录页复用全局分类图标节点，让它和总览页最近记录保持同一套视觉语言。
                    LedgerCategoryIconNode(
                        category = item.category,
                        direction = itemDirection,
                    )
                }
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
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
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = item.sourceLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = item.amountLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isIncome) {
                                    AutobookIncomeColor
                                } else {
                                    AutobookExpenseColor
                                },
                            )
                            Text(
                                text = item.timeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 详细记录时间线轨道的横向中心。
 *
 * 用途说明：
 * - 节点列与首页最近记录一致固定为 40dp，因此长链横向中心保持 20dp。
 */
private val recordTimelineRailCenterX = 20.dp

/**
 * 详细记录日期标题区高度。
 *
 * 用途说明：
 * - 固定日期标题高度后，连线可以准确跨过日期分组标题，避免不同日期之间出现断链。
 */
private val recordTimelineDateHeaderHeight = 32.dp

/**
 * 详细记录单条流水的最小高度。
 *
 * 用途说明：
 * - 用稳定高度估算节点中心点，让父级连线和圆形节点在视觉上持续对齐。
 */
private val recordTimelineRowMinHeight = 86.dp
