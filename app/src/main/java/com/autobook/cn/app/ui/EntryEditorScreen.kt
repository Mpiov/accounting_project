package com.autobook.cn.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autobook.cn.app.viewmodel.EntryEditorUiState
import com.autobook.cn.app.viewmodel.EntryTemplateUiModel
import com.autobook.cn.core.model.MoneyDirection

/**
 * 用途说明：
 * - 新建、编辑账本记录与处理待确认项目共用这一张编辑页。
 * - 本轮调整后，保存按钮固定在底部，保证快速记账时无需下滑也能直接提交。
 *
 * 参数说明：
 * - state：编辑页当前状态。
 * - onBack：返回上一级页面的回调。
 * - onAmountChange：金额输入变化回调。
 * - onMerchantChange：商户或对方输入变化回调。
 * - onCategoryChange：分类输入变化回调。
 * - onNoteChange：备注输入变化回调。
 * - onOrderIdChange：订单号输入变化回调。
 * - onDirectionChange：收支方向切换回调。
 * - onApplyTemplate：点击快捷模板后的回调。
 * - onSave：点击保存后的回调。
 * - onDelete：点击删除后的回调。
 * - modifier：外层修饰符，便于导航层注入布局约束。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryEditorScreen(
    state: EntryEditorUiState,
    onBack: () -> Unit,
    onAmountChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onOrderIdChange: (String) -> Unit,
    onDirectionChange: (MoneyDirection) -> Unit,
    onApplyTemplate: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("返回")
                    }
                },
                actions = {
                    if (state.canDelete) {
                        TextButton(onClick = onDelete) {
                            Text("删除")
                        }
                    }
                },
            )
        },
        bottomBar = {
            // 保存按钮固定到底部，避免快速记账页面因为表单较长而需要下滑寻找。
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.background,
            ) {
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text("保存")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 编辑页只保留可直接落账的两种方向，减少用户决策成本。
                listOf(MoneyDirection.EXPENSE, MoneyDirection.INCOME).forEach { direction ->
                    FilterChip(
                        selected = state.direction == direction,
                        onClick = { onDirectionChange(direction) },
                        label = { Text(if (direction == MoneyDirection.EXPENSE) "支出" else "收入") },
                    )
                }
            }
            TemplateSection(
                templates = state.templates,
                onApplyTemplate = onApplyTemplate,
            )
            OutlinedTextField(
                value = state.amount,
                onValueChange = onAmountChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("金额") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.merchant,
                onValueChange = onMerchantChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("商户 / 对方") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.category,
                onValueChange = onCategoryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("分类") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.orderId,
                onValueChange = onOrderIdChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("订单号（可选）") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注（可选）") },
                minLines = 3,
            )
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * 用途说明：
 * - 快捷模板区负责帮助用户一键填入常用的方向、分类与备注组合。
 *
 * 参数说明：
 * - templates：可用模板列表。
 * - onApplyTemplate：点击某个模板后的回调。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateSection(
    templates: List<EntryTemplateUiModel>,
    onApplyTemplate: (String) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("快捷模板", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "一键预填方向、分类和常用备注，不会覆盖金额和订单号；已填写的商户也会保留。",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                templates.forEach { template ->
                    SuggestionChip(
                        onClick = { onApplyTemplate(template.id) },
                        label = { Text(template.label) },
                    )
                }
            }
        }
    }
}
