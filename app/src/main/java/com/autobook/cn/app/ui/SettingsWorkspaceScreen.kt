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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autobook.cn.app.viewmodel.SettingsUiState
import com.autobook.cn.core.model.LocalModelInstallStatus
import com.autobook.cn.core.model.NotificationSourceApp

/**
 * 主壳层下的设置页，负责承接配置、留存和数据工具。
 *
 * 参数说明：
 * - state：设置页聚合后的界面状态。
 * - onAutoAccountingChanged：自动记账总开关变更回调。
 * - onCloudAnalysisChanged：云端分析开关变更回调。
 * - onCaptureEnabledChanged：截图分析开关变更回调。
 * - onOcrEnabledChanged：OCR 开关变更回调。
 * - onLocalLlmConfidenceBoostChanged：本地语义增强开关变更回调。
 * - onSelectedLocalModelChanged：切换本地模型回调。
 * - onDownloadSelectedLocalModel：下载模型回调。
 * - onRefreshLocalModelState：刷新模型状态回调。
 * - onNotificationRetentionChanged：通知保留天数回调。
 * - onScreenshotRetentionChanged：截图保留天数回调。
 * - onSourceEnabledChanged：通知来源启停回调。
 * - onSaveCurrentMonthBudget：保存本月预算回调，空字符串代表清除。
 * - onLoadExportPreview：生成 CSV 预览回调。
 * - modifier：外层修饰符。
 */
@Composable
fun SettingsWorkspaceScreen(
    state: SettingsUiState,
    onAutoAccountingChanged: (Boolean) -> Unit,
    onCloudAnalysisChanged: (Boolean) -> Unit,
    onCaptureEnabledChanged: (Boolean) -> Unit,
    onOcrEnabledChanged: (Boolean) -> Unit,
    onLocalLlmConfidenceBoostChanged: (Boolean) -> Unit,
    onSelectedLocalModelChanged: (String) -> Unit,
    onDownloadSelectedLocalModel: () -> Unit,
    onRefreshLocalModelState: () -> Unit,
    onNotificationRetentionChanged: (Int) -> Unit,
    onScreenshotRetentionChanged: (Int) -> Unit,
    onSourceEnabledChanged: (NotificationSourceApp, Boolean) -> Unit,
    onSaveCurrentMonthBudget: (String) -> Unit,
    onLoadExportPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "settings_hero") {
            SettingsHeroCard(
                state = state,
                modifier = Modifier.statusBarsPadding(),
            )
        }
        item(key = "budget_card") {
            BudgetPlannerCard(
                state = state,
                onSaveCurrentMonthBudget = onSaveCurrentMonthBudget,
            )
        }
        item(key = "ability_section") {
            SettingsSectionTitle(
                title = "记账能力",
                summary = "把自动记账相关的核心能力集中管理，减少开关分散造成的理解成本。",
            )
        }
        item(key = "ability_card") {
            Card(shape = RoundedCornerShape(26.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ToggleRow(
                        title = "自动记账总开关",
                        summary = "关闭后通知仍会被解析，但不会直接自动入账。",
                        checked = state.autoAccountingEnabled,
                        onCheckedChange = onAutoAccountingChanged,
                    )
                    ToggleRow(
                        title = "云端分析",
                        summary = "仅在本地解析不足时作为补充路径。",
                        checked = state.cloudAnalysisEnabled,
                        onCheckedChange = onCloudAnalysisChanged,
                    )
                    ToggleRow(
                        title = "截图分析",
                        summary = "开启后会调起本地截图链路供 OCR 补全文本。",
                        checked = state.captureEnabled,
                        onCheckedChange = onCaptureEnabledChanged,
                    )
                    ToggleRow(
                        title = "OCR 识别",
                        summary = "使用端内 OCR 识别截图文字，不依赖云端。",
                        checked = state.ocrEnabled,
                        onCheckedChange = onOcrEnabledChanged,
                    )
                    ToggleRow(
                        title = "本地语义增强（LLM 预留）",
                        summary = "用于中置信度记录的二次复核，后续可直接替换为端侧模型。",
                        checked = state.localLlmConfidenceBoostEnabled,
                        onCheckedChange = onLocalLlmConfidenceBoostChanged,
                    )
                }
            }
        }
        item(key = "source_section") {
            SettingsSectionTitle(
                title = "来源管理",
                summary = "只保留真正需要监听的来源，能让待确认列表和统计更干净。",
            )
        }
        item(key = "source_card") {
            Card(shape = RoundedCornerShape(26.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NotificationSourceApp.entries
                        .filterNot { it == NotificationSourceApp.UNKNOWN }
                        .forEach { source ->
                            ToggleRow(
                                title = source.toDisplayLabel(),
                                summary = "控制 ${source.toDisplayLabel()} 的自动记账入口。",
                                checked = source in state.enabledSources,
                                onCheckedChange = { enabled -> onSourceEnabledChanged(source, enabled) },
                            )
                        }
                }
            }
        }
        item(key = "retention_section") {
            SettingsSectionTitle(
                title = "数据留存",
                summary = "按风险和排查需求分别控制原文与截图保留时长。",
            )
        }
        item(key = "retention_card") {
            Card(shape = RoundedCornerShape(26.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RetentionSlider(
                        title = "通知原文保留天数",
                        value = state.notificationRetentionDays,
                        range = 1..90,
                        onValueChange = onNotificationRetentionChanged,
                    )
                    RetentionSlider(
                        title = "截图保留天数",
                        value = state.screenshotRetentionDays,
                        range = 1..30,
                        onValueChange = onScreenshotRetentionChanged,
                    )
                }
            }
        }
        item(key = "model_section") {
            SettingsSectionTitle(
                title = "本地模型",
                summary = "模型管理单独成组，避免和普通开关混在一起。",
            )
        }
        item(key = "model_card") {
            LocalModelStatusCard(
                state = state,
                onSelectedLocalModelChanged = onSelectedLocalModelChanged,
                onDownloadSelectedLocalModel = onDownloadSelectedLocalModel,
                onRefreshLocalModelState = onRefreshLocalModelState,
            )
        }
        item(key = "overview_section") {
            SettingsSectionTitle(
                title = "数据工具",
                summary = "把账本概览和导出入口放在一组，更符合“配置 + 工具”的页面定位。",
            )
        }
        item(key = "overview_card") {
            DataOverviewCard(state = state)
        }
        item(key = "export_card") {
            ExportPreviewCard(
                state = state,
                onLoadExportPreview = onLoadExportPreview,
            )
        }
    }
}

/** 顶部总卡展示设置页定位和当前账本概览。 */
@Composable
private fun SettingsHeroCard(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.SettingsSuggest,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "设置与工具",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "把开关、预算和导出整合在一页，方便集中管理。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroMetric(label = "总记录", value = state.totalEntries.toString(), modifier = Modifier.weight(1f))
                HeroMetric(label = "待确认", value = state.pendingEntries.toString(), modifier = Modifier.weight(1f))
                HeroMetric(label = "自动入账", value = state.autoEntries.toString(), modifier = Modifier.weight(1f))
            }
            Text(
                text = "最近一笔：${state.latestEntrySummary}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 预算卡把预算值、剩余额度和保存入口放在同一张卡里。 */
@Composable
private fun BudgetPlannerCard(
    state: SettingsUiState,
    onSaveCurrentMonthBudget: (String) -> Unit,
) {
    var budgetDraft by rememberSaveable(state.currentMonthBudgetInput) { mutableStateOf(state.currentMonthBudgetInput) }
    var budgetError by rememberSaveable { mutableStateOf<String?>(null) }

    Card(shape = RoundedCornerShape(26.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "${state.currentMonthLabel}预算",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = state.currentMonthBudgetUsageSummary,
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
                label = { Text("输入预算金额，留空后保存可清除") },
                singleLine = true,
            )
            budgetError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroMetric(
                    label = "预算",
                    value = state.currentMonthBudgetLabel,
                    modifier = Modifier.weight(1f),
                )
                HeroMetric(
                    label = "剩余",
                    value = state.currentMonthBudgetRemainingLabel,
                    modifier = Modifier.weight(1f),
                    highlight = !state.currentMonthBudgetExceeded,
                )
            }
            LinearProgressIndicator(
                progress = { state.currentMonthBudgetUsageRatio },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val normalizedInput = budgetDraft.trim()
                    if (normalizedInput.isNotBlank() && normalizedInput.toDoubleOrNull() == null) {
                        budgetError = "请输入有效数字，或留空后点击保存。"
                        return@Button
                    }
                    onSaveCurrentMonthBudget(normalizedInput)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存本月预算")
            }
        }
    }
}

/** 通用小节标题，统一设置页的阅读节奏。 */
@Composable
private fun SettingsSectionTitle(
    title: String,
    summary: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 标题、说明和开关统一成一行布局，减少样式分叉。 */
@Composable
private fun ToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/** 留存天数使用独立区块展示，避免滑杆互相抢视线。 */
@Composable
private fun RetentionSlider(
    title: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$title：$value 天",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
    }
}

/** 本地模型卡保留下载、刷新和切换能力。 */
@Composable
private fun LocalModelStatusCard(
    state: SettingsUiState,
    onSelectedLocalModelChanged: (String) -> Unit,
    onDownloadSelectedLocalModel: () -> Unit,
    onRefreshLocalModelState: () -> Unit,
) {
    Card(shape = RoundedCornerShape(26.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.availableLocalModels.forEach { model ->
                TextButton(
                    onClick = { onSelectedLocalModelChanged(model.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = buildModelRowLabel(
                            displayName = model.displayName,
                            selected = model.id == state.selectedLocalModelId,
                            recommended = model.isRecommended,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(
                text = state.localModelStatusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.localModelDownloadProgressPercent != null) {
                LinearProgressIndicator(
                    progress = { state.localModelDownloadProgressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.selectedLocalModelPath?.let { path ->
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDownloadSelectedLocalModel,
                    enabled = state.localModelInstallStatus != LocalModelInstallStatus.DOWNLOADING,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when (state.localModelInstallStatus) {
                            LocalModelInstallStatus.READY -> "重新校验"
                            LocalModelInstallStatus.DOWNLOADING -> "下载中"
                            LocalModelInstallStatus.FAILED -> "重新下载"
                            LocalModelInstallStatus.NOT_DOWNLOADED -> "下载模型"
                        },
                    )
                }
                TextButton(
                    onClick = onRefreshLocalModelState,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("刷新状态")
                }
            }
        }
    }
}

/** 数据概览卡突出核心统计，而不是大段纯文本。 */
@Composable
private fun DataOverviewCard(
    state: SettingsUiState,
) {
    Card(shape = RoundedCornerShape(26.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroMetric(label = "本月收入", value = state.currentMonthIncome, modifier = Modifier.weight(1f), highlight = true)
                HeroMetric(label = "本月支出", value = state.currentMonthExpense, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroMetric(label = "手动补记", value = state.manualEntries.toString(), modifier = Modifier.weight(1f))
                HeroMetric(label = "待确认", value = state.pendingEntries.toString(), modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 导出卡提供清晰的入口和预览摘要。 */
@Composable
private fun ExportPreviewCard(
    state: SettingsUiState,
    onLoadExportPreview: () -> Unit,
) {
    Card(shape = RoundedCornerShape(26.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.DataUsage,
                            contentDescription = "导出",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Text(
                    text = "账本导出",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "生成当前账本的 CSV 预览，方便后续分享、导出或备份。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.exportPreviewSummary.isNotBlank()) {
                Text(
                    text = state.exportPreviewSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onLoadExportPreview,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("生成 CSV 预览")
            }
            if (state.exportPreview.isNotBlank()) {
                Text(
                    text = buildPreviewSnippet(state.exportPreview),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = PREVIEW_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 顶部和概览区共用的指标卡。 */
@Composable
private fun HeroMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 生成模型行文案，强调当前选中项和推荐项。 */
private fun buildModelRowLabel(
    displayName: String,
    selected: Boolean,
    recommended: Boolean,
): String {
    val prefix = if (selected) "当前选择" else "切换到"
    val suffix = if (recommended) "（推荐）" else ""
    return "$prefix $displayName$suffix"
}

/** 预览区只展示前若干行，避免设置页被过长文本撑爆。 */
private fun buildPreviewSnippet(csv: String): String {
    return csv.lineSequence().take(PREVIEW_MAX_LINES).joinToString("\n")
}

/** 设置页里的来源名称映射。 */
private fun NotificationSourceApp.toDisplayLabel(): String = when (this) {
    NotificationSourceApp.WECHAT -> "微信支付"
    NotificationSourceApp.ALIPAY -> "支付宝"
    NotificationSourceApp.BANK_SMS -> "银行卡短信"
    NotificationSourceApp.UNKNOWN -> "未知来源"
}

private const val PREVIEW_MAX_LINES = 8
