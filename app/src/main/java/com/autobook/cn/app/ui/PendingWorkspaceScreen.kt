package com.autobook.cn.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autobook.cn.feature.pending.model.PendingItemUiModel
import com.autobook.cn.feature.pending.model.PendingUiState

/**
 * 待确认主页面只负责承接复核任务，不再混在首页里。
 *
 * 参数说明：
 * - state：待确认列表的界面状态。
 * - onConfirm：确认入账回调。
 * - onEdit：编辑后入账回调。
 * - onIgnore：忽略回调。
 * - onOpenLedger：空状态下前往账本页的回调。
 * - onCreateEntry：空状态下快速记一笔的回调。
 * - modifier：外层修饰符。
 */
@Composable
fun PendingWorkspaceScreen(
    state: PendingUiState,
    onConfirm: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onIgnore: (Long) -> Unit,
    onOpenLedger: () -> Unit,
    onCreateEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.items.isEmpty()) {
            item(key = "pending_empty") {
                PendingEmptyCard(
                    onOpenLedger = onOpenLedger,
                    onCreateEntry = onCreateEntry,
                    modifier = Modifier.padding(top = mainPageTopInset()),
                )
            }
        } else {
            item(key = "pending_summary") {
                PendingSummaryCard(
                    pendingCount = state.items.size,
                    modifier = Modifier.padding(top = mainPageTopInset()),
                )
            }
            items(items = state.items, key = { item -> item.id }) { item ->
                PendingResolveCard(
                    item = item,
                    onConfirm = { onConfirm(item.id) },
                    onEdit = { onEdit(item.id) },
                    onIgnore = { onIgnore(item.id) },
                )
            }
        }
    }
}

/** 顶部摘要卡把数量和处理建议放到一眼就能看到的位置。 */
@Composable
private fun PendingSummaryCard(
    pendingCount: Int,
    modifier: Modifier = Modifier,
) {
    val cardShape = AutobookUiTokens.panelShape
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.PendingActions,
                            contentDescription = "待确认",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "待确认",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (pendingCount > 0) "还有 $pendingCount 笔需要复核" else "当前无需处理待确认记录",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = if (pendingCount > 0) {
                    "建议优先处理这部分低置信度记录，避免分类和月度统计出现偏差。"
                } else {
                    "自动入账链路目前比较顺畅，你可以回到账本继续查看最近流水。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 单条待确认卡强化原始通知查看、分类建议和主要操作层级。 */
@Composable
private fun PendingResolveCard(
    item: PendingItemUiModel,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onIgnore: () -> Unit,
) {
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
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.merchant,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = item.amount,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            PendingHintCard(
                rawNotificationText = item.rawNotificationText,
                categoryHint = item.categoryHint,
                orderId = item.orderId,
                directionHint = item.directionHint,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (item.canDirectConfirm) {
                            onConfirm()
                        } else {
                            onEdit()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(item.primaryActionLabel)
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (item.canDirectConfirm) "编辑后入账" else "继续编辑")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryActionChip(
                    label = "不入账",
                    onClick = onIgnore,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 建议分类和通知原文集中展示，降低用户判断成本。 */
@Composable
private fun PendingHintCard(
    rawNotificationText: String?,
    categoryHint: String,
    orderId: String?,
    directionHint: String?,
) {
    var expanded by rememberSaveable(rawNotificationText) { mutableStateOf(false) }
    Card(
        shape = AutobookUiTokens.innerPanelShape,
        border = autobookCardBorder(),
        colors = CardDefaults.cardColors(containerColor = autobookSoftFillColor()),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "建议分类：$categoryHint",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            directionHint?.let { hint ->
                Text(
                    text = "提示：$hint",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            orderId?.takeIf { it.isNotBlank() }?.let { id ->
                Text(
                    text = "订单号：$id",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            rawNotificationText?.takeIf { it.isNotBlank() }?.let { rawText ->
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (expanded) "收起通知原文" else "展开通知原文")
                }
                if (expanded) {
                    // 原文采用完整多行展示，便于用户对照通知内容自行判断是否入账。
                    Text(
                        text = rawText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 空状态使用“说明 + 行动建议”的结构，而不是只放一句提示。 */
@Composable
private fun PendingEmptyCard(
    onOpenLedger: () -> Unit,
    onCreateEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = AutobookUiTokens.panelShape
    Card(
        modifier = modifier,
        shape = cardShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(highlighted = true),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = autobookSoftFillColor(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.DoneAll,
                            contentDescription = "完成",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = "当前无需处理待确认",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "回到账本看看最近流水，或者手动补记一笔。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onOpenLedger,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("去账本看看")
                }
                Button(
                    onClick = onCreateEntry,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("记一笔")
                }
            }
        }
    }
}

/** 次级操作统一用轻量胶囊按钮表现，避免和主按钮抢权重。 */
@Composable
private fun SecondaryActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chipShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier.autobookClippedClickable(shape = chipShape, onClick = onClick),
        shape = chipShape,
        border = autobookCardBorder(),
        color = autobookSoftFillColor(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
