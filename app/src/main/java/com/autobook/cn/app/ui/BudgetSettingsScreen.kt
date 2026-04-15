package com.autobook.cn.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autobook.cn.app.viewmodel.SettingsUiState

/**
 * 独立预算设置页。
 *
 * 用途说明：
 * - 从首页预算卡进入，避免把用户带到底部设置页里找入口。
 * - 页面只做一件事：修改当前月份预算。
 *
 * 参数说明：
 * - state：设置页聚合状态，用于展示当前月份预算。
 * - onBack：返回回调。
 * - onSaveBudget：保存预算回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSaveBudget: (String) -> Unit,
) {
    var budgetDraft by rememberSaveable(state.currentMonthBudgetInput) { mutableStateOf(state.currentMonthBudgetInput) }
    var budgetError by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置预算") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = state.currentMonthLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "只保留本月预算编辑，保存后首页会自动刷新。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = budgetDraft,
                        onValueChange = {
                            budgetDraft = it
                            budgetError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("输入本月预算，留空可清除") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                    )
                    budgetError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BudgetSummaryBlock(
                            label = "当前预算",
                            value = state.currentMonthBudgetLabel,
                            modifier = Modifier.weight(1f),
                        )
                        BudgetSummaryBlock(
                            label = "本月支出",
                            value = state.currentMonthExpense,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Button(
                        onClick = {
                            val input = budgetDraft.trim()
                            if (input.isNotBlank() && input.toDoubleOrNull() == null) {
                                budgetError = "请输入数字金额。"
                                return@Button
                            }
                            onSaveBudget(input)
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("保存预算")
                    }
                }
            }
        }
    }
}

/** 预算设置页的小摘要块。 */
@Composable
private fun BudgetSummaryBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
