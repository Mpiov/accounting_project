package com.autobook.cn.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 用途说明：
 * - 快速记账动作面板负责承接 FAB 点击后的两类高频入口。
 *
 * 参数说明：
 * - onDismiss：关闭面板时的回调。
 * - onExpenseClick：点击“记支出”时的回调。
 * - onIncomeClick：点击“记收入”时的回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionSheet(
    onDismiss: () -> Unit,
    onExpenseClick: () -> Unit,
    onIncomeClick: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "快速记账",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard(
                    title = "记支出",
                    summary = "直接进入支出表单",
                    icon = AutobookIconAsset.Expense,
                    onClick = onExpenseClick,
                    modifier = Modifier.weight(1f),
                )
                ActionCard(
                    title = "记收入",
                    summary = "直接进入收入表单",
                    icon = AutobookIconAsset.Income,
                    onClick = onIncomeClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 用途说明：
 * - 单个动作卡用于承载快速记账入口，强调“可直接点击”的操作属性。
 *
 * 参数说明：
 * - title：动作标题。
 * - summary：动作说明。
 * - icon：动作图标，统一使用 IconPark 资源，避免旧弹层和主页面图标风格不一致。
 * - onClick：点击卡片后的回调。
 * - modifier：外层修饰符，便于在父布局中分配宽度。
 */
@Composable
private fun ActionCard(
    title: String,
    summary: String,
    icon: AutobookIconAsset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = AutobookUiTokens.compactPanelShape
    Card(
        modifier = modifier.autobookClippedClickable(shape = cardShape, onClick = onClick),
        shape = cardShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = autobookSoftFillColor(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AutobookIcon(
                        asset = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
