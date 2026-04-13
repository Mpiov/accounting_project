package com.autobook.cn.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autobook.cn.app.viewmodel.SettingsUiState
import com.autobook.cn.core.model.NotificationSourceApp

/** 设置页，集中管理自动记账、回退能力和数据留存策略。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onAutoAccountingChanged: (Boolean) -> Unit,
    onCloudAnalysisChanged: (Boolean) -> Unit,
    onCaptureEnabledChanged: (Boolean) -> Unit,
    onOcrEnabledChanged: (Boolean) -> Unit,
    onNotificationRetentionChanged: (Int) -> Unit,
    onScreenshotRetentionChanged: (Int) -> Unit,
    onSourceEnabledChanged: (NotificationSourceApp, Boolean) -> Unit,
    onLoadExportPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("返回")
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
            SettingSwitchCard(
                title = "自动记账总开关",
                summary = "关闭后通知仍会被识别，但不会直接自动入账。",
                checked = state.autoAccountingEnabled,
                onCheckedChange = onAutoAccountingChanged,
            )
            SettingSwitchCard(
                title = "云端分析",
                summary = "默认关闭，仅在本地解析失败时才作为补充路径。",
                checked = state.cloudAnalysisEnabled,
                onCheckedChange = onCloudAnalysisChanged,
            )
            SettingSwitchCard(
                title = "截图分析",
                summary = "默认关闭。开启后会在本地拉起通知栏截图，供 OCR 补全文本。",
                checked = state.captureEnabled,
                onCheckedChange = onCaptureEnabledChanged,
            )
            SettingSwitchCard(
                title = "OCR 识别",
                summary = "默认关闭。开启后使用端内 OCR 识别截图文字，不依赖云端。",
                checked = state.ocrEnabled,
                onCheckedChange = onOcrEnabledChanged,
            )
            RetentionCard(
                title = "通知原文保留天数",
                value = state.notificationRetentionDays,
                range = 1..90,
                onValueChange = onNotificationRetentionChanged,
            )
            RetentionCard(
                title = "截图保留天数",
                value = state.screenshotRetentionDays,
                range = 1..30,
                onValueChange = onScreenshotRetentionChanged,
            )
            DataOverviewCard(state = state)
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("通知来源", style = MaterialTheme.typography.titleMedium)
                    NotificationSourceApp.entries
                        .filterNot { it == NotificationSourceApp.UNKNOWN }
                        .forEach { source ->
                            SettingSwitchRow(
                                title = source.label(),
                                checked = source in state.enabledSources,
                                onCheckedChange = { enabled -> onSourceEnabledChanged(source, enabled) },
                            )
                        }
                }
            }
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("账本导出", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "生成一份当前账本的 CSV 预览，方便后续接入分享、导出或备份。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (state.exportPreviewSummary.isNotBlank()) {
                        Text(
                            text = state.exportPreviewSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onLoadExportPreview) {
                            Text("生成 CSV 预览")
                        }
                        if (state.exportPreview.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(state.exportPreview))
                                },
                            ) {
                                Text("复制 CSV")
                            }
                        }
                    }
                    if (state.exportPreview.isNotBlank()) {
                        Text(
                            text = buildPreviewSnippet(state.exportPreview),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (state.exportPreview.lineSequence().count() > PREVIEW_MAX_LINES) {
                            Text(
                                text = "仅展示前 $PREVIEW_MAX_LINES 行，复制按钮可获取完整 CSV。",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchCard(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    /** 统一的开关卡片样式，避免页面内重复拼装。 */
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingSwitchRow(title = title, checked = checked, onCheckedChange = onCheckedChange)
            Text(summary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    /** 单行标题加开关，用于卡片内复用。 */
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RetentionCard(
    title: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    /** 留存天数卡片，使用滑杆在预设区间内调整。 */
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("$title：$value 天", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = range.last - range.first - 1,
            )
        }
    }
}

@Composable
private fun DataOverviewCard(
    state: SettingsUiState,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("本地账本概览", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewMetric(title = "总记录", value = state.totalEntries.toString(), modifier = Modifier.weight(1f))
                OverviewMetric(title = "手动补记", value = state.manualEntries.toString(), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewMetric(title = "自动入账", value = state.autoEntries.toString(), modifier = Modifier.weight(1f))
                OverviewMetric(title = "待确认", value = state.pendingEntries.toString(), modifier = Modifier.weight(1f))
            }
            Text("本月收入：${state.currentMonthIncome}", style = MaterialTheme.typography.bodyMedium)
            Text("本月支出：${state.currentMonthExpense}", style = MaterialTheme.typography.bodyMedium)
            Text("最近一笔：${state.latestEntrySummary}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun OverviewMetric(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

private fun buildPreviewSnippet(csv: String): String {
    return csv.lineSequence()
        .take(PREVIEW_MAX_LINES)
        .joinToString("\n")
}

private const val PREVIEW_MAX_LINES = 12

/** 将来源枚举映射为设置页可读文案。 */
private fun NotificationSourceApp.label(): String = when (this) {
    NotificationSourceApp.WECHAT -> "微信支付"
    NotificationSourceApp.ALIPAY -> "支付宝"
    NotificationSourceApp.BANK_SMS -> "银行卡短信"
    NotificationSourceApp.UNKNOWN -> "未知来源"
}
