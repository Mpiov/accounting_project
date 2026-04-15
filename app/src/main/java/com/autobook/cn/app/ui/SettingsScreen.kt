package com.autobook.cn.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autobook.cn.app.viewmodel.SettingsUiState
import com.autobook.cn.app.capture.PageCaptureBookkeepingService
import com.autobook.cn.core.common.PermissionStatusChecker
import com.autobook.cn.core.common.SettingsIntents
import com.autobook.cn.core.model.LocalModelInstallStatus
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
    /** 切换本地语义增强的状态回调，enabled 为 true 时进入中置信度二次复核链路。 */
    onLocalLlmConfidenceBoostChanged: (Boolean) -> Unit,
    /** 切换当前选中的本地模型。 */
    onSelectedLocalModelChanged: (String) -> Unit,
    /** 下载当前选中的本地模型。 */
    onDownloadSelectedLocalModel: () -> Unit,
    /** 刷新本地模型状态。 */
    onRefreshLocalModelState: () -> Unit,
    onNotificationRetentionChanged: (Int) -> Unit,
    onScreenshotRetentionChanged: (Int) -> Unit,
    onSourceEnabledChanged: (NotificationSourceApp, Boolean) -> Unit,
    onLoadExportPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val enableCaptureShortcutAction: () -> Unit = {
        enableCaptureShortcut(
            context = context,
            onCaptureEnabledChanged = onCaptureEnabledChanged,
            requestNotificationPermission = { },
        )
    }
    val disableCaptureShortcutAction: () -> Unit = {
        disableCaptureShortcut(
            context = context,
            onCaptureEnabledChanged = onCaptureEnabledChanged,
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        /**
         * 通知权限回调说明：
         * - Android 13+ 只有在用户授予通知权限后，前台服务通知才能稳定显示在通知栏。
         * - 因此前台服务入口只在授权成功后继续启动，避免出现“服务启动了但通知没有显示”的误解。
         */
        if (granted) {
            enableCaptureShortcutAction()
        } else {
            onCaptureEnabledChanged(false)
            Toast.makeText(
                context,
                "未授予通知权限或应用通知已关闭，常驻入口不会显示在通知栏",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

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
                title = "图片导入：截图分析",
                summary = "当前不参与通知自动记账。该开关保留给后续图片预处理与截图辅助链路使用。",
                checked = state.captureEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        enableCaptureShortcutAction()
                    } else {
                        disableCaptureShortcutAction()
                    }
                },
            )
            SettingSwitchCard(
                title = "图片导入：OCR 识别",
                summary = "当前不参与通知自动记账。关闭云端分析后，图片导入会在该开关开启时使用端内 ML Kit OCR。",
                checked = state.ocrEnabled,
                onCheckedChange = onOcrEnabledChanged,
            )
            /** 中置信度通知时允许本地语义增强复盘关键字段，当前使用启发式占位分析，后续可直接替换为端侧本地 LLM；开关名强调预留态以免误解。 */
            SettingSwitchCard(
                title = "本地语义增强（LLM 预留）",
                summary = "在中置信度的银行转账通知上打开本地语义复核链路，当前启用启发式文本增强作为占位实现，后续可替换为真正的本地 LLM。",
                checked = state.localLlmConfidenceBoostEnabled,
                onCheckedChange = onLocalLlmConfidenceBoostChanged,
            )
            LocalModelCard(
                state = state,
                onSelectedLocalModelChanged = onSelectedLocalModelChanged,
                onDownloadSelectedLocalModel = onDownloadSelectedLocalModel,
                onRefreshLocalModelState = onRefreshLocalModelState,
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
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("截图快捷记账", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "启动后会在通知栏常驻一个“立即截图记账”入口。点击后会截取当前页面，交给图片分析链路，再按置信度决定自动入账或进入待确认。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                enableCaptureShortcut(
                                    context = context,
                                    onCaptureEnabledChanged = onCaptureEnabledChanged,
                                    requestNotificationPermission = {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                )
                            },
                        ) {
                            Text("启动常驻入口")
                        }
                        TextButton(
                            onClick = {
                                disableCaptureShortcutAction()
                            },
                        ) {
                            Text("关闭常驻入口")
                        }
                    }
                }
            }
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
private fun LocalModelCard(
    state: SettingsUiState,
    onSelectedLocalModelChanged: (String) -> Unit,
    onDownloadSelectedLocalModel: () -> Unit,
    onRefreshLocalModelState: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("本地模型", style = MaterialTheme.typography.titleMedium)
            Text(
                "当前推荐 Qwen3 1.7B GGUF，通过运行时下载到应用私有目录，不会打进 APK。当前已接入 llama.cpp + GGUF 真本地推理，模型就绪后会优先走端侧语义增强，并在失败时回退到启发式兜底。",
                style = MaterialTheme.typography.bodyMedium,
            )
            state.availableLocalModels.forEach { model ->
                val selected = model.id == state.selectedLocalModelId
                TextButton(
                    onClick = { onSelectedLocalModelChanged(model.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = buildModelRowLabel(model.displayName, selected, model.isRecommended),
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
            if (state.selectedLocalModelPath != null) {
                Text(
                    text = "模型文件：${state.selectedLocalModelPath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDownloadSelectedLocalModel,
                    enabled = state.localModelInstallStatus != LocalModelInstallStatus.DOWNLOADING,
                ) {
                    Text(
                        when (state.localModelInstallStatus) {
                            LocalModelInstallStatus.READY -> "重新校验模型"
                            LocalModelInstallStatus.DOWNLOADING -> "下载中"
                            LocalModelInstallStatus.FAILED -> "重新下载"
                            LocalModelInstallStatus.NOT_DOWNLOADED -> "下载选中模型"
                        },
                    )
                }
                TextButton(onClick = onRefreshLocalModelState) {
                    Text("刷新状态")
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

/**
 * 统一处理“启用截图快捷入口”的前置校验与状态回写。
 *
 * 参数说明：
 * - context：当前界面上下文，用于检查通知权限并启动前台服务。
 * - onCaptureEnabledChanged：根据真实启停结果回写截图快捷入口总开关。
 * - requestNotificationPermission：当缺少 Android 13+ 通知运行时权限时，触发系统权限申请。
 */
private fun enableCaptureShortcut(
    context: Context,
    onCaptureEnabledChanged: (Boolean) -> Unit,
    requestNotificationPermission: () -> Unit,
) {
    if (canPostForegroundNotification(context)) {
        val started = startPersistentCaptureEntry(context)
        onCaptureEnabledChanged(started)
        return
    }
    if (shouldRequestNotificationPermission(context)) {
        requestNotificationPermission()
        return
    }
    onCaptureEnabledChanged(false)
    Toast.makeText(
        context,
        "请先在系统中打开应用通知，再启用截图快捷记账。",
        Toast.LENGTH_LONG,
    ).show()
}

/**
 * 统一处理“关闭截图快捷入口”逻辑，确保前台服务和持久化开关一起关闭。
 *
 * 参数说明：
 * - context：当前界面上下文，用于发送停止常驻入口服务的 Intent。
 * - onCaptureEnabledChanged：用于同步关闭设置项中的截图快捷入口状态。
 */
private fun disableCaptureShortcut(
    context: Context,
    onCaptureEnabledChanged: (Boolean) -> Unit,
) {
    onCaptureEnabledChanged(false)
    context.startService(PageCaptureBookkeepingService.createStopPersistentIntent(context))
}

private const val PREVIEW_MAX_LINES = 12

/**
 * 判断当前设备是否允许把前台服务通知正常展示到通知栏。
 *
 * 用途说明：
 * - Android 13 以下没有 `POST_NOTIFICATIONS` 运行时权限，可直接展示通知。
 * - Android 13 及以上必须先拿到通知权限，否则“常驻入口”即使启动，通知也可能不出现在通知栏。
 *
 * 参数说明：
 * - context：当前界面上下文，用于读取通知运行时权限状态。
 */
private fun canPostForegroundNotification(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        return false
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return true
    }
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * 真正启动“截图快捷记账”常驻前台服务。
 *
 * 用途说明：
 * - 统一收口服务启动逻辑，避免权限判断和真正的服务拉起逻辑散落在按钮点击代码里。
 * - 后续如果需要补充更多前置校验，只需要改这一处。
 *
 * 参数说明：
 * - context：当前界面上下文，用于构造并启动前台服务 Intent。
 */
/**
 * 判断当前是否应该发起 Android 13+ 的通知权限申请。
 *
 * 参数说明：
 * - context：当前界面上下文，用于读取应用通知总开关和运行时权限状态。
 */
private fun shouldRequestNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return false
    }
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        return false
    }
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) != PackageManager.PERMISSION_GRANTED
}

private fun startPersistentCaptureEntry(context: Context): Boolean {
    /**
     * 当前页面截图链路依赖无障碍服务提供截图运行时。
     *
     * 用途说明：
     * - 如果用户还没在系统设置里真正开启无障碍服务，就提前拦住，不要先启动一个“点了必失败”的常驻通知。
     * - 这里直接引导去系统无障碍设置，减少“通知有了但截图能力不可用”的困惑。
     */
    if (!PermissionStatusChecker.hasAccessibilityAccess(
            context = context,
            serviceClassName = ACCESSIBILITY_SERVICE_CLASS_NAME,
        )
    ) {
        Toast.makeText(
            context,
            "请先开启无障碍自动化，截图快捷记账才能使用",
            Toast.LENGTH_LONG,
        ).show()
        context.startActivity(
            SettingsIntents.accessibilityAccess().apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        return false
    }
    ContextCompat.startForegroundService(
        context,
        PageCaptureBookkeepingService.createStartPersistentIntent(context),
    )
    return true
}

private const val ACCESSIBILITY_SERVICE_CLASS_NAME =
    "com.autobook.cn.service.automation.AutobookAccessibilityService"

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

/** 将来源枚举映射为设置页可读文案。 */
private fun NotificationSourceApp.label(): String = when (this) {
    NotificationSourceApp.WECHAT -> "微信支付"
    NotificationSourceApp.ALIPAY -> "支付宝"
    NotificationSourceApp.BANK_SMS -> "银行卡短信"
    NotificationSourceApp.UNKNOWN -> "未知来源"
}
