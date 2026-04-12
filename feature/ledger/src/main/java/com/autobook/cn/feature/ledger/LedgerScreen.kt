package com.autobook.cn.feature.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class LedgerRangeFilter {
    TODAY,
    MONTH,
    ALL,
}

data class LedgerItemUiModel(
    val id: Long,
    val merchant: String,
    val amount: String,
    val subtitle: String,
    val category: String,
    val note: String?,
    val isManual: Boolean,
)

data class LedgerUiState(
    val incomeSummary: String = "¥0.00",
    val expenseSummary: String = "¥0.00",
    val pendingCount: Int = 0,
    val searchQuery: String = "",
    val selectedRange: LedgerRangeFilter = LedgerRangeFilter.TODAY,
    val entries: List<LedgerItemUiModel> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    state: LedgerUiState,
    onSearchChange: (String) -> Unit,
    onRangeChange: (LedgerRangeFilter) -> Unit,
    onPendingClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit,
    onEntryClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("账本") },
                actions = {
                    IconButton(onClick = onAddClick) {
                        Icon(Icons.Outlined.Add, contentDescription = "新增")
                    }
                    IconButton(onClick = onPendingClick) {
                        Icon(Icons.Outlined.PendingActions, contentDescription = "待确认")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    title = "当前收入",
                    value = state.incomeSummary,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    title = "当前支出",
                    value = state.expenseSummary,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "待确认 ${state.pendingCount} 条",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜索商户、分类或备注") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LedgerRangeFilter.entries.forEach { range ->
                    FilterChip(
                        selected = state.selectedRange == range,
                        onClick = { onRangeChange(range) },
                        label = {
                            Text(
                                when (range) {
                                    LedgerRangeFilter.TODAY -> "今日"
                                    LedgerRangeFilter.MONTH -> "本月"
                                    LedgerRangeFilter.ALL -> "全部"
                                },
                            )
                        },
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.entries.isEmpty()) {
                    item {
                        Card {
                            Text(
                                text = "还没有符合筛选条件的记账记录。你可以手动新增，或者等待通知自动入账。",
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    items(state.entries, key = { it.id }) { entry ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEntryClick(entry.id) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(entry.merchant, style = MaterialTheme.typography.titleMedium)
                                    Text(entry.subtitle, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = buildString {
                                            append(entry.category)
                                            if (entry.isManual) append(" · 手动")
                                            if (!entry.note.isNullOrBlank()) append(" · ${entry.note}")
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                    )
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
