package com.autobook.cn.app.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * - onIgnore：不入账回调，用于把误识别的待确认项移出可见列表。
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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

/**
 * 顶部摘要卡。
 *
 * 用途说明：
 * - 只展示待复核数量，不再附带建议说明，避免用户进入页面后被非操作信息打断。
 *
 * 参数说明：
 * - pendingCount：当前待确认记录数量。
 * - modifier：外层修饰符。
 */
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
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AutobookIcon(
                            asset = AutobookIconAsset.Pending,
                            contentDescription = "待确认",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
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
        }
    }
}
/**
 * 单条待确认卡。
 *
 * 用途说明：
 * - 取消建议分类和提示，把用户判断依据集中到默认展开的通知原文。
 * - 操作区压缩成一行三个选择：修改后入账、确认入账、不入账，降低每条卡片的纵向占用。
 *
 * 参数说明：
 * - item：当前待确认项。
 * - onConfirm：确认入账回调。
 * - onEdit：进入编辑页回调。
 * - onIgnore：不入账回调，用于把误识别反馈给训练样本并从可见列表移除。
 */
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
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        text = item.title,
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
                    color = pendingAmountColor(item),
                )
            }
            PendingRawNotificationCard(rawNotificationText = item.rawNotificationText)
            PendingDecisionActionRow(
                canConfirm = item.canDirectConfirm,
                onEdit = onEdit,
                onConfirm = onConfirm,
                onIgnore = onIgnore,
            )
        }
    }
}

/**
 * 用途说明：
 * - 根据待确认项方向决定金额颜色，保证收入为绿、支出为红、未知方向为中性色。
 *
 * 参数说明：
 * - item：当前待确认列表项，包含方向是否已知以及是否收入两个展示字段。
 */
@Composable
private fun pendingAmountColor(item: PendingItemUiModel) = when {
    item.isIncome -> AutobookIncomeColor
    item.isDirectionKnown -> AutobookExpenseColor
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * 通知原文卡。
 *
 * 用途说明：
 * - 原文默认展开，用户不用再额外点击才能核对信息。
 * - 缺少原文时给出短占位，避免卡片突然缺一块造成视觉跳动。
 *
 * 参数说明：
 * - rawNotificationText：原始通知文本。
 */
@Composable
private fun PendingRawNotificationCard(
    rawNotificationText: String?,
) {
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
                text = "通知原文",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = rawNotificationText?.takeIf { it.isNotBlank() } ?: "暂无通知原文",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 待确认项底部决策操作行。
 *
 * 用途说明：
 * - 用一行三个按钮承载用户最常见的三种决策：修改后入账、确认入账、不入账。
 * - “确认入账”保留实心主按钮样式，“修改后入账”和“不入账”使用描边按钮，明确主次关系。
 *
 * 参数说明：
 * - canConfirm：当前待确认项是否已经具备直接入账条件；方向未知时禁用确认，避免写入无效账本。
 * - onEdit：点击“修改后入账”后进入编辑页。
 * - onConfirm：点击“确认入账”后转成正式账本。
 * - onIgnore：点击“不入账”后标记为误识别并移出可见列表。
 */
@Composable
private fun PendingDecisionActionRow(
    canConfirm: Boolean,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
    onIgnore: () -> Unit,
) {
    // 三个按钮保持三等分，避免操作区左右轻重不一；仅压缩内边距和文字样式来容纳“五字按钮”。
    val compactButtonPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedButton(
            onClick = onEdit,
            contentPadding = compactButtonPadding,
            modifier = Modifier.weight(1f),
        ) {
            PendingActionButtonText(label = "修改后入账")
        }
        Button(
            onClick = onConfirm,
            enabled = canConfirm,
            contentPadding = compactButtonPadding,
            modifier = Modifier.weight(1f),
        ) {
            PendingActionButtonText(label = "确认入账")
        }
        OutlinedButton(
            onClick = onIgnore,
            contentPadding = compactButtonPadding,
            modifier = Modifier.weight(1f),
        ) {
            PendingActionButtonText(label = "不入账")
        }
    }
}

/**
 * 待确认操作按钮文字。
 *
 * 用途说明：
 * - 统一把一行动作按钮里的文字限制为单行，避免“修改后入账”在窄屏或字体放大时拆成两行。
 * - 使用较小的 `labelSmall`，让三按钮操作区保持清爽紧凑。
 *
 * 参数说明：
 * - label：按钮展示文案。
 */
@Composable
private fun PendingActionButtonText(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        AutobookIcon(
                            asset = AutobookIconAsset.DoneAll,
                            contentDescription = "完成",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
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
