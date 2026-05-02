package com.autobook.cn.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.autobook.cn.app.capture.PageCaptureBookkeepingService
import com.autobook.cn.app.viewmodel.SettingsNotificationSource
import com.autobook.cn.app.viewmodel.SettingsUiState
import com.autobook.cn.core.common.PermissionStatusChecker
import com.autobook.cn.core.common.SettingsIntents

/**
 * 主壳层下的设置页。
 *
 * 用途说明：
 * - 承接自动记账、应用通知、截图记账入口、来源管理和数据留存等设置项。
 * - 这份实现保留当前项目已经接线的功能，同时统一设置页 `Switch` 的可用态与禁用态视觉语义。
 *
 * 参数说明：
 * - state：设置页聚合后的界面状态。
 * - onAutoAccountingChanged：自动记账总开关变更回调。
 * - onCloudAnalysisChanged：云端 AI 分析开关变更回调。
 * - onAuthPhoneChanged：手机号输入变更回调。
 * - onAuthCodeChanged：短信验证码输入变更回调。
 * - onRequestLoginCode：请求验证码回调。
 * - onLoginOrRegister：登录或注册回调。
 * - onLogout：退出登录回调。
 * - onCloudLearningChanged：会员全局云端学习开关变更回调。
 * - onRefreshMembership：刷新会员状态回调。
 * - onStartMonthlyMembership：创建月卡订单回调。
 * - onStartYearlyMembership：创建年卡订单回调。
 * - onSyncCloudLearning：立即同步云端学习回调。
 * - onCaptureEnabledChanged：截图记账入口启停状态回写回调。
 * - onNotificationRetentionChanged：通知保留天数回调。
 * - onScreenshotRetentionChanged：截图保留天数回调。
 * - onSourceEnabledChanged：设置页来源桶启停回调。
 * - onSaveCurrentMonthBudget：保留当前签名，后续可继续承接预算保存。
 * - modifier：外层修饰符。
 */
@Composable
fun SettingsWorkspaceScreen(
    state: SettingsUiState,
    onAutoAccountingChanged: (Boolean) -> Unit,
    onCloudAnalysisChanged: (Boolean) -> Unit,
    onAuthPhoneChanged: (String) -> Unit,
    onAuthCodeChanged: (String) -> Unit,
    onRequestLoginCode: () -> Unit,
    onLoginOrRegister: () -> Unit,
    onLogout: () -> Unit,
    onCloudLearningChanged: (Boolean) -> Unit,
    onRefreshMembership: () -> Unit,
    onStartMonthlyMembership: () -> Unit,
    onStartYearlyMembership: () -> Unit,
    onSyncCloudLearning: () -> Unit,
    onCaptureEnabledChanged: (Boolean) -> Unit,
    onNotificationRetentionChanged: (Int) -> Unit,
    onScreenshotRetentionChanged: (Int) -> Unit,
    onSourceEnabledChanged: (SettingsNotificationSource, Boolean) -> Unit,
    onSaveCurrentMonthBudget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotificationAccess by remember(context) {
        mutableStateOf(PermissionStatusChecker.hasNotificationAccess(context))
    }
    var hasAppNotificationsEnabled by remember(context) {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var hasAccessibilityAccess by remember(context) {
        mutableStateOf(
            PermissionStatusChecker.hasAccessibilityAccess(
                context = context,
                serviceClassName = ACCESSIBILITY_SERVICE_CLASS_NAME,
            ),
        )
    }
    /**
     * 应用通知设置页打开中的本地保护状态。
     *
     * 用途说明：
     * - “应用通知”这一项跳转的是系统页面，不是应用内真正可持久化的开关。
     * - 连续点击时用它拦住重复跳转，避免短时间内多次拉起系统页。
     */
    var appNotificationSettingsLaunching by rememberSaveable {
        mutableStateOf(false)
    }
    /**
     * 统一打开“应用通知”系统设置入口。
     *
     * 用途说明：
     * - 把重复点击保护和系统设置兜底逻辑收口到一个回调里，页面内其它位置直接复用。
     */
    val openAppNotificationSettings = fun() {
        if (appNotificationSettingsLaunching) {
            return
        }
        appNotificationSettingsLaunching = true
        val opened = openAppNotificationSettingsSafely(context)
        if (!opened) {
            appNotificationSettingsLaunching = false
        }
    }
    /**
     * Android 13+ 通知权限申请器。
     *
     * 用途说明：
     * - 截图记账入口最终依赖前台服务通知显示，因此在缺权限时要先申请通知权限。
     */
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            enableCaptureShortcut(
                context = context,
                onCaptureEnabledChanged = onCaptureEnabledChanged,
                requestNotificationPermission = { },
            )
        } else {
            onCaptureEnabledChanged(false)
            Toast.makeText(
                context,
                "未授予通知权限，截图记账入口不会显示在通知栏。",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 从系统设置页返回后刷新权限状态，并释放应用通知入口的防抖标记。
                hasNotificationAccess = PermissionStatusChecker.hasNotificationAccess(context)
                hasAppNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                hasAccessibilityAccess = PermissionStatusChecker.hasAccessibilityAccess(
                    context = context,
                    serviceClassName = ACCESSIBILITY_SERVICE_CLASS_NAME,
                )
                appNotificationSettingsLaunching = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "ability_section") {
            SettingsSectionTitle(
                title = "记账能力",
                summary = "自动记账相关开关。",
                modifier = Modifier.padding(top = mainPageTopInset()),
            )
        }
        item(key = "ability_card") {
            Card(
                shape = AutobookUiTokens.panelShape,
                border = autobookCardBorder(),
                colors = autobookCardColors(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AutoAccountingToggleSection(
                        autoAccountingEnabled = state.autoAccountingEnabled,
                        hasNotificationAccess = hasNotificationAccess,
                        onCheckedChange = onAutoAccountingChanged,
                        onOpenNotificationSettings = {
                            context.startActivity(
                                SettingsIntents.notificationAccess().apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                            )
                        },
                    )
                    AppNotificationToggleSection(
                        appNotificationsEnabled = hasAppNotificationsEnabled,
                        onOpenAppNotificationSettings = openAppNotificationSettings,
                    )
                    ToggleRow(
                        title = "云端 AI 分析",
                        summary = "开启后，允许图片导入与相关分析链路走云端模型。",
                        checked = state.cloudAnalysisEnabled,
                        onCheckedChange = onCloudAnalysisChanged,
                    )
                }
            }
        }

        item(key = "auth_section") {
            SettingsSectionTitle(
                title = "账号",
                summary = "手机号登录后，会员状态由服务器确认。",
            )
        }
        item(key = "auth_card") {
            PhoneAuthCard(
                state = state,
                onAuthPhoneChanged = onAuthPhoneChanged,
                onAuthCodeChanged = onAuthCodeChanged,
                onRequestLoginCode = onRequestLoginCode,
                onLoginOrRegister = onLoginOrRegister,
                onLogout = onLogout,
            )
        }

        item(key = "membership_section") {
            SettingsSectionTitle(
                title = "会员与云端学习",
                summary = "会员可拉取服务器全局模型；通知原文上传用于改进下一版全局模型。",
            )
        }
        item(key = "membership_card") {
            MembershipCloudLearningCard(
                state = state,
                onCloudLearningChanged = onCloudLearningChanged,
                onRefreshMembership = onRefreshMembership,
                onStartMonthlyMembership = onStartMonthlyMembership,
                onStartYearlyMembership = onStartYearlyMembership,
                onSyncCloudLearning = onSyncCloudLearning,
            )
        }

        item(key = "capture_section") {
            SettingsSectionTitle(
                title = "截图能力",
                summary = "当前页面截图记账的权限入口与启停控制。",
            )
        }
        item(key = "capture_card") {
            CapturePermissionCard(
                captureEnabled = state.captureEnabled,
                appNotificationsEnabled = hasAppNotificationsEnabled,
                accessibilityEnabled = hasAccessibilityAccess,
                onEnableCapture = {
                    enableCaptureShortcut(
                        context = context,
                        onCaptureEnabledChanged = onCaptureEnabledChanged,
                        requestNotificationPermission = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    )
                },
                onDisableCapture = {
                    disableCaptureShortcut(
                        context = context,
                        onCaptureEnabledChanged = onCaptureEnabledChanged,
                    )
                },
                onOpenAccessibilitySettings = {
                    context.startActivity(
                        SettingsIntents.accessibilityAccess().apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                },
                onOpenAppNotificationSettings = openAppNotificationSettings,
            )
        }

        item(key = "source_section") {
            SettingsSectionTitle(
                title = "来源管理",
                summary = "控制要监听的通知来源。",
            )
        }
        item(key = "source_card") {
            Card(
                shape = AutobookUiTokens.panelShape,
                border = autobookCardBorder(),
                colors = autobookCardColors(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SETTINGS_VISIBLE_SOURCES.forEach { source ->
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
                summary = "控制通知原文与截图保留时长。",
            )
        }
        item(key = "retention_card") {
            Card(
                shape = AutobookUiTokens.panelShape,
                border = autobookCardBorder(),
                colors = autobookCardColors(),
            ) {
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
    }
}

/**
 * 手机号账号卡。
 *
 * 用途说明：
 * - 提供手机号验证码登录/注册入口。
 * - 当前真实账号服务器未配置，按钮会触发占位仓储并展示明确提示。
 *
 * 参数说明：
 * - state：设置页 UI 状态。
 * - onAuthPhoneChanged：手机号输入回调。
 * - onAuthCodeChanged：验证码输入回调。
 * - onRequestLoginCode：请求验证码回调。
 * - onLoginOrRegister：登录或注册回调。
 * - onLogout：退出登录回调。
 */
@Composable
private fun PhoneAuthCard(
    state: SettingsUiState,
    onAuthPhoneChanged: (String) -> Unit,
    onAuthCodeChanged: (String) -> Unit,
    onRequestLoginCode: () -> Unit,
    onLoginOrRegister: () -> Unit,
    onLogout: () -> Unit,
) {
    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = state.authSummary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = state.authPhoneInput,
                onValueChange = onAuthPhoneChanged,
                label = { Text("手机号") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.authCodeInput,
                onValueChange = onAuthCodeChanged,
                label = { Text("短信验证码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onRequestLoginCode,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("获取验证码")
                }
                Button(
                    onClick = onLoginOrRegister,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("登录/注册")
                }
            }
            if (state.authLoggedIn) {
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("退出登录")
                }
            }
        }
    }
}

/**
 * 应用通知行使用系统通知开关的实时状态展示。
 *
 * 参数说明：
 * - appNotificationsEnabled：当前应用通知是否处于开启状态。
 * - onOpenAppNotificationSettings：点击后跳转到应用通知设置页。
 */
@Composable
private fun AppNotificationToggleSection(
    appNotificationsEnabled: Boolean,
    onOpenAppNotificationSettings: () -> Unit,
) {
    ToggleRow(
        title = "应用通知",
        summary = if (appNotificationsEnabled) {
            "应用通知已开启，识别结果提示和截图记账入口都可以正常显示。"
        } else {
            "应用通知已关闭，识别结果提示和截图记账入口都无法显示。"
        },
        checked = appNotificationsEnabled,
        readOnly = true,
        onRowClick = onOpenAppNotificationSettings,
    )
}

/**
 * 自动记账总开关按“权限状态 + 功能状态”联合展示。
 *
 * 参数说明：
 * - autoAccountingEnabled：当前持久化的自动记账开关状态。
 * - hasNotificationAccess：通知监听权限是否已开启。
 * - onCheckedChange：切换自动记账开关时的回调。
 * - onOpenNotificationSettings：缺少权限时跳转到系统通知监听设置页。
 */
@Composable
private fun AutoAccountingToggleSection(
    autoAccountingEnabled: Boolean,
    hasNotificationAccess: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val summary = when {
        !hasNotificationAccess -> "需要先开启通知监听权限，自动记账才能使用。"
        autoAccountingEnabled -> "通知监听权限已开启，自动记账功能已开启。"
        else -> "通知监听权限已开启，自动记账功能已关闭。"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleRow(
            title = "自动记账总开关",
            summary = summary,
            checked = hasNotificationAccess && autoAccountingEnabled,
            enabled = hasNotificationAccess,
            onCheckedChange = onCheckedChange,
        )
        if (!hasNotificationAccess) {
            Button(
                onClick = onOpenNotificationSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("去开启通知监听权限")
            }
        }
    }
}

/**
 * 会员与云端学习设置卡。
 *
 * 用途说明：
 * - 展示会员状态、云端学习开关、购买入口和手动同步入口。
 * - 当前支付和同步后端尚未配置，按钮会调用占位仓储并展示提示。
 *
 * 参数说明：
 * - state：设置页 UI 状态。
 * - onCloudLearningChanged：云端学习开关回调。
 * - onRefreshMembership：刷新会员状态回调。
 * - onStartMonthlyMembership：开通月卡回调。
 * - onStartYearlyMembership：开通年卡回调。
 * - onSyncCloudLearning：立即同步云端学习回调。
 */
@Composable
private fun MembershipCloudLearningCard(
    state: SettingsUiState,
    onCloudLearningChanged: (Boolean) -> Unit,
    onRefreshMembership: () -> Unit,
    onStartMonthlyMembership: () -> Unit,
    onStartYearlyMembership: () -> Unit,
    onSyncCloudLearning: () -> Unit,
) {
    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = state.membershipSummary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ToggleRow(
                title = "云端学习增强",
                summary = state.cloudLearningSummary,
                checked = state.cloudLearningEnabled,
                onCheckedChange = onCloudLearningChanged,
            )
            Text(
                text = if (state.cloudLearningEffectiveEnabled) {
                    "当前会上传通知原文、解析结果和反馈标签，并拉取服务器模型。"
                } else {
                    "当前不会上传通知原文，非会员只使用普通规则算法。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onStartMonthlyMembership,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("开通月卡")
                }
                OutlinedButton(
                    onClick = onStartYearlyMembership,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("开通年卡")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onRefreshMembership,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("刷新会员")
                }
                Button(
                    onClick = onSyncCloudLearning,
                    enabled = state.cloudLearningEffectiveEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("立即同步")
                }
            }
            if (state.membershipActionMessage.isNotBlank()) {
                Text(
                    text = state.membershipActionMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 通用小节标题。
 *
 * 参数说明：
 * - title：小节标题。
 * - summary：小节摘要。
 * - modifier：外层修饰符。
 */
@Composable
private fun SettingsSectionTitle(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

/**
 * 截图能力卡。
 *
 * 用途说明：
 * - 展示无障碍权限、应用通知和截图记账入口当前状态。
 * - 提供开启/关闭截图记账入口，以及跳转系统设置的操作入口。
 *
 * 参数说明：
 * - captureEnabled：截图记账入口当前是否已开启。
 * - appNotificationsEnabled：应用通知当前是否已开启。
 * - accessibilityEnabled：无障碍权限当前是否已开启。
 * - onEnableCapture：点击开启截图记账后的回调。
 * - onDisableCapture：点击关闭截图记账后的回调。
 * - onOpenAccessibilitySettings：点击后跳转到系统无障碍设置页。
 * - onOpenAppNotificationSettings：点击后跳转到应用通知设置页。
 */
@Composable
private fun CapturePermissionCard(
    captureEnabled: Boolean,
    appNotificationsEnabled: Boolean,
    accessibilityEnabled: Boolean,
    onEnableCapture: () -> Unit,
    onDisableCapture: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
) {
    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "当前页面截图记账",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    !accessibilityEnabled -> "需要先开启无障碍自动化，截图能力才能使用。"
                    captureEnabled -> "当前可以通过通知栏入口执行截图记账。"
                    else -> "当前截图记账功能已关闭。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PermissionStatusRow(
                label = "无障碍权限",
                enabled = accessibilityEnabled,
            )
            PermissionStatusRow(
                label = "应用通知",
                enabled = appNotificationsEnabled,
            )
            if (accessibilityEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onEnableCapture,
                        enabled = !captureEnabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("开启截图记账")
                    }
                    OutlinedButton(
                        onClick = onDisableCapture,
                        enabled = captureEnabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("关闭截图记账")
                    }
                }
                if (!appNotificationsEnabled) {
                    OutlinedButton(
                        onClick = onOpenAppNotificationSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("去开启应用通知")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onOpenAccessibilitySettings,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("去开启无障碍权限")
                    }
                    OutlinedButton(
                        onClick = onOpenAppNotificationSettings,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("去开启应用通知")
                    }
                }
            }
        }
    }
}

/**
 * 权限状态行。
 *
 * 参数说明：
 * - label：权限名称。
 * - enabled：权限是否已开启。
 */
@Composable
private fun PermissionStatusRow(
    label: String,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (enabled) "已开启" else "未开启",
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * 统一处理“启用截图记账入口”的前置校验与状态回写。
 *
 * 参数说明：
 * - context：当前界面上下文，用于检查通知权限并启动前台服务。
 * - onCaptureEnabledChanged：根据真实启停结果回写截图入口开关状态。
 * - requestNotificationPermission：当缺少 Android 13+ 通知权限时触发系统权限申请。
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
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        openAppNotificationSettingsSafely(context)
        onCaptureEnabledChanged(false)
        return
    }
    if (shouldRequestNotificationPermission(context)) {
        requestNotificationPermission()
        return
    }
    onCaptureEnabledChanged(false)
    Toast.makeText(
        context,
        "请先在系统中打开应用通知，再启用截图记账入口。",
        Toast.LENGTH_LONG,
    ).show()
}

/**
 * 统一处理“关闭截图记账入口”逻辑。
 *
 * 参数说明：
 * - context：当前界面上下文，用于发送停止常驻入口服务的 Intent。
 * - onCaptureEnabledChanged：用于同步关闭设置项中的截图入口状态。
 */
private fun disableCaptureShortcut(
    context: Context,
    onCaptureEnabledChanged: (Boolean) -> Unit,
) {
    onCaptureEnabledChanged(false)
    context.startService(PageCaptureBookkeepingService.createStopPersistentIntent(context))
}

/**
 * 安全打开“应用通知”系统设置页。
 *
 * 用途说明：
 * - 优先跳到应用通知设置；如果当前设备没有这个入口，再回退到应用详情页。
 * - 统一做可用性判断和异常兜底，避免系统设置跳转失败时直接崩溃。
 *
 * 返回值说明：
 * - `true`：已经成功发起系统设置页跳转。
 * - `false`：当前设备没有可用入口，或启动系统设置页时发生异常。
 */
private fun openAppNotificationSettingsSafely(context: Context): Boolean {
    val notificationSettingsIntent = SettingsIntents.appNotifications(context).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val targetIntent = when {
        notificationSettingsIntent.resolveActivity(context.packageManager) != null -> notificationSettingsIntent
        appDetailsIntent.resolveActivity(context.packageManager) != null -> appDetailsIntent
        else -> null
    }
    if (targetIntent == null) {
        Toast.makeText(
            context,
            "当前设备暂时无法直接打开应用通知设置，请手动到系统设置中查看。",
            Toast.LENGTH_LONG,
        ).show()
        return false
    }
    return runCatching {
        context.startActivity(targetIntent)
        true
    }.getOrElse {
        Toast.makeText(
            context,
            "打开应用通知设置失败，请稍后重试。",
            Toast.LENGTH_LONG,
        ).show()
        false
    }
}

/**
 * 判断当前设备是否允许把截图记账入口通知正常展示到通知栏。
 *
 * 参数说明：
 * - context：当前界面上下文，用于读取通知总开关和运行时权限状态。
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
 * 判断当前是否应该发起 Android 13+ 的通知权限申请。
 *
 * 参数说明：
 * - context：当前界面上下文，用于读取应用通知状态。
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

/**
 * 真正启动“截图记账”常驻前台服务。
 *
 * 参数说明：
 * - context：当前界面上下文，用于校验无障碍能力并启动前台服务。
 *
 * 返回值说明：
 * - `true`：前置条件满足，已发起常驻入口服务。
 * - `false`：前置条件不满足，已中断启动并提示用户处理。
 */
private fun startPersistentCaptureEntry(context: Context): Boolean {
    if (!PermissionStatusChecker.hasAccessibilityAccess(
            context = context,
            serviceClassName = ACCESSIBILITY_SERVICE_CLASS_NAME,
        )
    ) {
        Toast.makeText(
            context,
            "请先开启无障碍自动化，截图记账才能使用。",
            Toast.LENGTH_LONG,
        ).show()
        context.startActivity(
            SettingsIntents.accessibilityAccess().apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

/**
 * 标题、说明和开关统一成一行布局。
 *
 * 用途说明：
 * - 普通开关统一使用设置页约定的 `Switch` 配色。
 * - 只读场景下，右侧 `Switch` 也保持和普通开关一致的点击手感，但实际动作会转交给整行跳转逻辑。
 *
 * 参数说明：
 * - title：当前设置项标题。
 * - summary：当前设置项说明。
 * - checked：当前开关状态。
 * - enabled：当前设置项是否允许交互。
 * - readOnly：是否为只读开关展示。
 * - onRowClick：整行点击回调，常用于跳转系统设置。
 * - onCheckedChange：真正的开关切换回调。
 */
@Composable
private fun ToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    onRowClick: (() -> Unit)? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val rowModifier = if (onRowClick != null) {
        Modifier.autobookClippedClickable(
            shape = AutobookUiTokens.innerPanelShape,
            enabled = enabled,
            onClick = onRowClick,
        )
    } else {
        Modifier
    }
    /**
     * 统一收口右侧开关的点击行为。
     *
     * 用途说明：
     * - 普通场景下继续沿用真实的开关切换回调，行为与自动记账总开关保持一致。
     * - 只读场景下不直接改写状态，而是把点击动作转交给整行跳转回调，让右侧开关的交互手感也和普通开关一致。
     */
    val effectiveOnCheckedChange = when {
        readOnly && onRowClick != null -> {
            { _: Boolean ->
                onRowClick()
            }
        }
        else -> onCheckedChange
    }
    Row(
        modifier = rowModifier.fillMaxWidth(),
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
            enabled = enabled,
            onCheckedChange = effectiveOnCheckedChange,
            // 明确约束设置页开关的禁用态与可用态底色，避免默认样式出现“可点更灰、不可点更白”的反向观感。
            colors = settingsToggleSwitchColors(),
        )
    }
}

/**
 * 设置页开关配色。
 *
 * 用途说明：
 * - 统一设置页右侧 `Switch` 的视觉语义，让“白色背景 = 可点击”，“灰色背景 = 不可点击”。
 * - 保留开启态的主色强调，同时把所有禁用态收敛为灰色，减少误判。
 *
 * 参数说明：
 * - 无。
 */
@Composable
private fun settingsToggleSwitchColors() = run {
    val surfaceColor = MaterialTheme.colorScheme.surface
    /**
     * 禁用态继续沿用灰色语义，但把灰度再压深一点，确保“不能按”能被一眼识别出来。
     */
    val disabledTrackColor =
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.26f).compositeOver(surfaceColor)

    SwitchDefaults.colors(
        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
        uncheckedTrackColor = MaterialTheme.colorScheme.surface,
        uncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.58f),
        // 禁用态统一压到更深一点的灰色系，和当前页面“灰色 = 不能按”的规则保持一致。
        disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
        disabledUncheckedTrackColor = disabledTrackColor,
        disabledUncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f),
        disabledCheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
        disabledCheckedTrackColor = disabledTrackColor,
        disabledCheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f),
    )
}

/**
 * 留存天数滑杆。
 *
 * 参数说明：
 * - title：当前滑杆标题。
 * - value：当前持久化值。
 * - range：允许调整的区间。
 * - onValueChange：滑杆提交后的回调。
 */
@Composable
private fun RetentionSlider(
    title: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    var sliderValue by rememberSaveable(value) { mutableStateOf(value.toFloat()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$title：${sliderValue.toInt()} 天",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Slider(
            value = sliderValue,
            onValueChange = { changedValue ->
                // 拖动时先只更新本地显示，避免每一帧都写入持久层。
                sliderValue = changedValue
            },
            onValueChangeFinished = {
                val committedValue = sliderValue.toInt().coerceIn(range.first, range.last)
                if (committedValue != value) {
                    onValueChange(committedValue)
                }
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = 0,
        )
    }
}

/**
 * 设置页里的来源名称映射。
 *
 * 用途说明：
 * - 把来源枚举转换成设置页里用户可理解的中文文案。
 */
private fun SettingsNotificationSource.toDisplayLabel(): String = when (this) {
    SettingsNotificationSource.WECHAT -> "微信支付"
    SettingsNotificationSource.ALIPAY -> "支付宝"
    SettingsNotificationSource.OTHER -> "其它来源"
}

/**
 * 设置页允许展示的通知来源白名单。
 *
 * 用途说明：
 * - 明确把来源管理限制成产品上真正暴露给用户的 3 个来源，避免把 `UNKNOWN` 之类的内部兜底枚举带到界面上。
 */
private val SETTINGS_VISIBLE_SOURCES = listOf(
    SettingsNotificationSource.WECHAT,
    SettingsNotificationSource.ALIPAY,
    SettingsNotificationSource.OTHER,
)

private const val ACCESSIBILITY_SERVICE_CLASS_NAME =
    "com.autobook.cn.service.automation.AutobookAccessibilityService"
