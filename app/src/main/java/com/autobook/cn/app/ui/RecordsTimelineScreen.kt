package com.autobook.cn.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autobook.cn.app.viewmodel.RecordTimelineItemUiModel

/**
 * 用途说明：
 * - 详细记录页与搜索结果页共用时间线浏览界面，负责展示按时间排序的记录列表。
 *
 * 参数说明：
 * - title：顶部标题。
 * - summary：顶部摘要说明。
 * - items：时间线记录列表。
 * - selectedEntryId：当前高亮的记录 id；为空时不展示定位卡片。
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
    selectedEntryId: Long? = null,
    onSelectEntry: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selectedIndex = remember(selectedEntryId, items) {
        items.indexOfFirst { it.id == selectedEntryId }.takeIf { it >= 0 }
    }

    LaunchedEffect(selectedIndex) {
        selectedIndex?.let { listState.animateScrollToItem(it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
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
            selectedEntryId?.let { selectedId ->
                items.firstOrNull { it.id == selectedId }?.let { selected ->
                    item(key = "selected") {
                        SelectedEntryCard(entry = selected)
                    }
                }
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
                    selected = item.id == selectedEntryId,
                    onClick = { onSelectEntry(item.id) },
                )
            }
        }
    }
}

/**
 * 用途说明：
 * - 顶部摘要卡用于说明当前列表范围或搜索结果概况。
 *
 * 参数说明：
 * - summary：摘要文本。
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
 * - 当外部指定了当前焦点记录时，顶部固定展示一张定位卡，方便用户快速确认当前上下文。
 *
 * 参数说明：
 * - entry：当前高亮的记录。
 */
@Composable
private fun SelectedEntryCard(
    entry: RecordTimelineItemUiModel,
) {
    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(emphasized = true),
        colors = autobookCardColors(highlighted = true),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "当前定位",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${entry.category} · ${entry.timeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = entry.amountLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
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
 * - selected：当前记录是否处于选中状态。
 * - onClick：点击记录后的回调。
 */
@Composable
private fun TimelineEntryCard(
    item: RecordTimelineItemUiModel,
    showDate: Boolean,
    showTopConnector: Boolean,
    showBottomConnector: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val connectorColor = autobookTimelineConnectorColor()
    val dotOuterColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val dotInnerColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (showDate) {
            Text(
                text = item.dateLabel,
                modifier = Modifier.padding(start = 34.dp, top = 10.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

                    // 连线从节点圆心向上下延展，保证分组内的所有卡片都能真正首尾相连。
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

                    // 选中态通过放大外圈和提高颜色对比度来强化定位，不再依赖灰色底区分。
                    drawCircle(
                        color = dotOuterColor,
                        radius = if (selected) 8.5.dp.toPx() else 8.dp.toPx(),
                        center = Offset(centerX, centerY),
                    )
                    drawCircle(
                        color = dotInnerColor,
                        radius = if (selected) 3.8.dp.toPx() else 3.4.dp.toPx(),
                        center = Offset(centerX, centerY),
                    )
                }
            }
            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                shape = AutobookUiTokens.compactPanelShape,
                border = autobookCardBorder(emphasized = selected),
                colors = autobookCardColors(highlighted = selected),
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
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
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
