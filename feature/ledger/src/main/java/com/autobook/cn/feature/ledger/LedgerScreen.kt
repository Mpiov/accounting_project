package com.autobook.cn.feature.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class LedgerItemUiModel(
    val id: Long,
    val merchant: String,
    val amount: String,
    val subtitle: String,
)

data class LedgerUiState(
    val incomeToday: String = "¥0.00",
    val expenseToday: String = "¥0.00",
    val pendingCount: Int = 0,
    val entries: List<LedgerItemUiModel> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    state: LedgerUiState,
    onPendingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("账本首页") },
                actions = {
                    IconButton(onClick = onPendingClick) {
                        Icon(Icons.Outlined.PendingActions, contentDescription = "待确认")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    title = "今日收入",
                    value = state.incomeToday,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = "今日支出",
                    value = state.expenseToday,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "待确认 ${state.pendingCount} 笔",
                style = MaterialTheme.typography.titleMedium,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.entries.isEmpty()) {
                    item {
                        Card {
                            Text(
                                text = "当前还没有自动入账记录，通知到达后会出现在这里。",
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    items(state.entries, key = { it.id }) { entry ->
                        Card {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(entry.merchant, style = MaterialTheme.typography.titleMedium)
                                    Text(entry.subtitle, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(entry.amount, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
