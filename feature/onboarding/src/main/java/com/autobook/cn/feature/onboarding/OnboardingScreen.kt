package com.autobook.cn.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.ScreenshotMonitor
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class OnboardingUiState(
    val notificationAccess: Boolean = false,
    val accessibilityAccess: Boolean = false,
    val autoAccountingEnabled: Boolean = true,
    val cloudAnalysisEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,
)

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onOpenNotificationSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onToggleAutoAccounting: (Boolean) -> Unit,
    onToggleCloudAnalysis: (Boolean) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Autobook",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "先完成权限与策略设置，然后就可以进入自动记账主流程。你也可以稍后再到设置页调整。",
            style = MaterialTheme.typography.bodyLarge,
        )
        PermissionCard(
            title = "通知读取",
            summary = "读取微信、支付宝和银行卡短信通知，提取交易线索。",
            enabled = state.notificationAccess,
            icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
            actionLabel = "去开启",
            onAction = onOpenNotificationSettings,
        )
        PermissionCard(
            title = "无障碍自动化",
            summary = "仅在需要时拉起通知栏，不做其他自动化操作。",
            enabled = state.accessibilityAccess,
            icon = { Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null) },
            actionLabel = "去开启",
            onAction = onOpenAccessibilitySettings,
        )
        PermissionCard(
            title = "截图分析",
            summary = "当前版本先预留能力入口，后续接入 MediaProjection 和 OCR。",
            enabled = false,
            icon = { Icon(Icons.Outlined.ScreenshotMonitor, contentDescription = null) },
            actionLabel = "后续接入",
            onAction = {},
        )
        ToggleCard(
            title = "自动记账总开关",
            summary = "关闭后仍可手动记账，但系统不会自动入账。",
            checked = state.autoAccountingEnabled,
            icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
            onCheckedChange = onToggleAutoAccounting,
        )
        ToggleCard(
            title = "云端分析",
            summary = "默认关闭，仅在后续接入后端时作为补充回退路径。",
            checked = state.cloudAnalysisEnabled,
            icon = { Icon(Icons.Outlined.Cloud, contentDescription = null) },
            onCheckedChange = onToggleCloudAnalysis,
        )
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.onboardingCompleted) "继续使用" else "进入账本")
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    summary: String,
    enabled: Boolean,
    icon: @Composable () -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = summary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (enabled) "状态：已开启" else "状态：未开启",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun ToggleCard(
    title: String,
    summary: String,
    checked: Boolean,
    icon: @Composable () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = summary, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
