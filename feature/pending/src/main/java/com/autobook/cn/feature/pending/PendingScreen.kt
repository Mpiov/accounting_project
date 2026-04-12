package com.autobook.cn.feature.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 待确认列表单项的展示模型。 */
data class PendingItemUiModel(
    val id: Long,
    val merchant: String,
    val amount: String,
    val subtitle: String,
    val reason: String,
    val categoryHint: String,
    val orderId: String?,
)

/** 待确认页的界面状态。 */
data class PendingUiState(
    val items: List<PendingItemUiModel> = emptyList(),
)

/** 待确认页，承接低置信度或疑似重复的记录。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingScreen(
    state: PendingUiState,
    onBack: () -> Unit,
    onConfirm: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onIgnore: (Long) -> Unit,
    onFalsePositive: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("待确认") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.items.isEmpty()) {
                item {
                    Card {
                        Text(
                            text = "当前没有待确认记录。",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(state.items, key = { it.id }) { item ->
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.merchant, style = MaterialTheme.typography.titleMedium)
                                Text(item.subtitle, style = MaterialTheme.typography.bodyMedium)
                                Text(item.amount, style = MaterialTheme.typography.headlineSmall)
                                Text("建议分类：${item.categoryHint}", style = MaterialTheme.typography.labelLarge)
                                item.orderId?.let { Text("订单号：$it", style = MaterialTheme.typography.labelLarge) }
                                Text("原因：${item.reason}", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { onConfirm(item.id) }, modifier = Modifier.weight(1f)) {
                                    Text("确认入账")
                                }
                                TextButton(
                                    onClick = { onEdit(item.id) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("编辑后入账")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(
                                    onClick = { onIgnore(item.id) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("忽略")
                                }
                                TextButton(
                                    onClick = { onFalsePositive(item.id) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("标记误报")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
