package com.autobook.cn.app.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autobook.cn.app.viewmodel.RecordTimelineItemUiModel

/**
 * 详细记录页与搜索结果页共用时间线页面。
 *
 * 参数说明：
 * - title：顶部标题。
 * - summary：顶部摘要。
 * - items：时间线记录列表。
 * - selectedEntryId：当前选中的记录；为空时不展示置顶卡。
 * - onSelectEntry：点击时间线项后的回调。
 * - onBack：返回回调。
 * - modifier：外层修饰符。
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
                        Icon(imageVector = Icons.Outlined.ArrowBack, contentDescription = "返回")
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
                TimelineEntryCard(
                    item = item,
                    showDate = showDate,
                    selected = item.id == selectedEntryId,
                    onClick = { onSelectEntry(item.id) },
                )
            }
        }
    }
}

/** 顶部摘要卡只保留简短说明，不堆砌解释。 */
@Composable
private fun RecordsSummaryCard(
    summary: String,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Text(
            text = summary,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 搜索结果页把当前聚焦账单固定在顶部，方便对照时间线定位。 */
@Composable
private fun SelectedEntryCard(
    entry: RecordTimelineItemUiModel,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)),
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

/** 单条时间线项采用更接近手账清单的纵向结构。 */
@Composable
private fun TimelineEntryCard(
    item: RecordTimelineItemUiModel,
    showDate: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showDate) {
            Text(
                text = item.dateLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                )
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
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
