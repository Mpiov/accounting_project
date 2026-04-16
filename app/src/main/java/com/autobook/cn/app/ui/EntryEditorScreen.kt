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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.autobook.cn.app.viewmodel.EntryEditorUiState
import com.autobook.cn.app.viewmodel.EntryTemplateUiModel
import com.autobook.cn.core.model.ImageImportCandidate
import com.autobook.cn.core.model.MoneyDirection

/**
 * 用途说明：
 * - 新建、编辑账本记录与处理待确认项目共用这一张编辑页。
 * - 同时整合快捷模板、图片导入候选选择和底部固定保存动作，减少多链路记账时的跳转成本。
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
 * - onImportFromImage：触发图片导入流程的回调。
 * - onSelectImageImportCandidate：切换图片识别候选的回调。
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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                        )
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
            // 保存按钮固定到底部，避免长表单和图片识别候选共同出现时需要反复滚动寻找主操作。
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ) {
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(if (state.imageImportRequiresManualConfirmation) "确认并保存" else "保存")
                }
            }
        },
        // 编辑页本体保持透明，让暖纸背景能延续到表单区域。
        containerColor = Color.Transparent,
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
                            text = "无论当前是云端大模型建议还是本地图片分析建议，都不会自动入账。请手动核对金额、分类、商户、备注后再保存。",
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
        }
    }
}

/**
 * 用途说明：
 * - 展示图片导入记账入口、导入提示、过滤摘要和多候选切换区域。
 * - 把云端大模型建议与端侧 OCR/本地模型建议统一收敛到同一块 UI，避免用户理解两套流程。
 *
 * 参数说明：
 * - state：编辑页当前状态，包含图片导入链路产生的候选、提示和过滤统计。
 * - onImportFromImage：触发选图与分析流程的回调。
 * - onSelectImageImportCandidate：切换当前候选结果的回调。
 */
@Composable
private fun ImageImportSection(
    state: EntryEditorUiState,
    onImportFromImage: () -> Unit,
    onSelectImageImportCandidate: (Int) -> Unit,
) {
    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("图片导入记账", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "上传图片后，会调用云端AI进行分析",
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

/**
 * 用途说明：
 * - 在多候选图片识别结果之间切换，并展示当前候选的金额、对象、状态与证据。
 * - 这样可以把多模态建议与本地建议都纳入同一个确认流程，减少误记风险。
 *
 * 参数说明：
 * - candidates：当前可选的图片导入候选列表。
 * - selectedIndex：当前选中的候选下标。
 * - onSelectCandidate：点击某个候选后的切换回调。
 */
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
    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
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
