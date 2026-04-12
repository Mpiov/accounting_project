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

data class PendingItemUiModel(
    val id: Long,
    val merchant: String,
    val amount: String,
    val subtitle: String,
    val reason: String,
)

data class PendingUiState(
    val items: List<PendingItemUiModel> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingScreen(
    state: PendingUiState,
    onBack: () -> Unit,
    onConfirm: (Long) -> Unit,
    onIgnore: (Long) -> Unit,
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
                                Text("原因：${item.reason}", style = MaterialTheme.typography.labelLarge)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { onConfirm(item.id) }, modifier = Modifier.weight(1f)) {
                                    Text("确认入账")
                                }
                                TextButton(
                                    onClick = { onIgnore(item.id) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("忽略")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
