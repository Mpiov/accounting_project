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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autobook.cn.app.viewmodel.EntryTemplateUiModel
import com.autobook.cn.app.viewmodel.EntryEditorUiState
import com.autobook.cn.core.model.ImageImportCandidate
import com.autobook.cn.core.model.MoneyDirection

/** 手动新建、编辑账本记录、处理待确认项共用的编辑页。 */
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
    onImportFromImage: () -> Unit,
    onSelectImageImportCandidate: (Int) -> Unit,
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 手动编辑页只展示可直接落账的方向，未知方向交给待确认流程要求用户二选一。
                listOf(MoneyDirection.EXPENSE, MoneyDirection.INCOME).forEach { direction ->
                    FilterChip(
                        selected = state.direction == direction,
                        onClick = { onDirectionChange(direction) },
                        label = { Text(if (direction == MoneyDirection.EXPENSE) "支出" else "收入") },
                    )
                }
            }
            ImageImportSection(
                state = state,
                onImportFromImage = onImportFromImage,
                onSelectImageImportCandidate = onSelectImageImportCandidate,
            )
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
            if (state.imageImportRequiresManualConfirmation) {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "图片分析结果仅供参考",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "无论当前是 gemini-3.1-pro-preview 建议还是本地图片分析建议，都不会自动入账。请手动核对金额、分类、商户、备注后再保存。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.imageImportRequiresManualConfirmation) "确认并保存" else "保存")
            }
        }
    }
}

@Composable
private fun ImageImportSection(
    state: EntryEditorUiState,
    onImportFromImage: () -> Unit,
    onSelectImageImportCandidate: (Int) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("图片导入记账", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "当前已接入真实选图入口。关闭云端分析并开启 OCR 后，可直接走端内 ML Kit OCR + 本地 Qwen 生成建议；开启云端分析时走经 `api.wenapi.shop` 反代的 gemini-3.1-pro-preview 链路。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onImportFromImage,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("选择图片并生成建议")
            }
            state.importedImageRef?.let { imageRef ->
                Text(
                    text = "当前图片：$imageRef",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.imageImportMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.filteredOutCandidateCount > 0) {
                Card {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "本地过滤摘要",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = buildFilteredSummary(
                                filteredOutCount = state.filteredOutCandidateCount,
                                duplicateFilteredCount = state.duplicateFilteredCount,
                                inactiveFilteredCount = state.inactiveFilteredCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (state.imageImportCandidates.isNotEmpty()) {
                CandidateSelectorSection(
                    candidates = state.imageImportCandidates,
                    selectedIndex = state.selectedImageImportCandidateIndex,
                    onSelectCandidate = onSelectImageImportCandidate,
                )
            }
            state.imageImportTracePath?.let { tracePath ->
                Text(
                    text = "分析路径：$tracePath",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateSelectorSection(
    candidates: List<ImageImportCandidate>,
    selectedIndex: Int?,
    onSelectCandidate: (Int) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("候选结果", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                candidates.forEachIndexed { index, candidate ->
                    FilterChip(
                        selected = selectedIndex == index,
                        onClick = { onSelectCandidate(index) },
                        label = {
                            Text(buildCandidateChipLabel(index = index, candidate = candidate))
                        },
                    )
                }
            }
            val selectedCandidate = selectedIndex?.let(candidates::getOrNull)
            if (selectedCandidate != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "当前候选：${buildCandidateDetail(selectedCandidate)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (selectedCandidate.stateReason.isNotBlank()) {
                        AssistChip(
                            onClick = {},
                            label = { Text("状态：${selectedCandidate.stateReason}") },
                        )
                    }
                    if (selectedCandidate.evidence.isNotBlank()) {
                        Text(
                            text = "证据：${selectedCandidate.evidence}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateSection(
    templates: List<EntryTemplateUiModel>,
    onApplyTemplate: (String) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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

/** 将过滤统计拼成简洁可读的一句话。 */
private fun buildFilteredSummary(
    filteredOutCount: Int,
    duplicateFilteredCount: Int,
    inactiveFilteredCount: Int,
): String {
    val parts = buildList {
        add("共剔除 $filteredOutCount 条候选")
        if (duplicateFilteredCount > 0) {
            add("重复 $duplicateFilteredCount 条")
        }
        if (inactiveFilteredCount > 0) {
            add("已退还/已撤销 $inactiveFilteredCount 条")
        }
    }
    return parts.joinToString(separator = "，")
}

/** 生成候选筛选 chip 文案。 */
private fun buildCandidateChipLabel(
    index: Int,
    candidate: ImageImportCandidate,
): String {
    val amountLabel = candidate.amount?.let { "¥${"%.2f".format(it)}" } ?: "金额待确认"
    val merchantLabel = candidate.merchant.ifBlank { "未知对象" }
    return "${index + 1}. $merchantLabel $amountLabel"
}

/** 生成候选详细说明。 */
private fun buildCandidateDetail(candidate: ImageImportCandidate): String {
    val directionLabel = when (candidate.direction) {
        MoneyDirection.INCOME -> "收入"
        MoneyDirection.EXPENSE -> "支出"
        MoneyDirection.UNKNOWN -> "方向待确认"
    }
    val confidenceLabel = "置信度 ${"%.2f".format(candidate.confidence.score)}"
    return listOfNotNull(
        candidate.merchant.ifBlank { null },
        candidate.category.takeIf { it.isNotBlank() },
        directionLabel,
        confidenceLabel,
    ).joinToString(separator = " · ")
}
