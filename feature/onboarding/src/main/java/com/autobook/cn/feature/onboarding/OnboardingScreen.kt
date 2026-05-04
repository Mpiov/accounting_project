package com.autobook.cn.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** 引导页的界面状态。 */
data class OnboardingUiState(
    val notificationAccess: Boolean = false,
    val accessibilityAccess: Boolean = false,
    val autoAccountingEnabled: Boolean = true,
    val cloudAnalysisEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
)

/**
 * 首次引导页图标。
 *
 * 参数说明：
 * - iconRes：模块内 IconPark VectorDrawable 资源 id，用于和主 App 图标保持同一套 outline 风格。
 */
@Composable
private fun OnboardingIcon(iconRes: Int) {
    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
    )
}

/** 首次启动引导页，聚合权限说明与核心策略开关。 */
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
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "轻松记",
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
            icon = { OnboardingIcon(R.drawable.ic_onboarding_notifications) },
            actionLabel = "去开启",
            onAction = onOpenNotificationSettings,
        )
        PermissionCard(
            title = "无障碍自动化",
            summary = "仅在需要时拉起通知栏，不做其他自动化操作。",
            enabled = state.accessibilityAccess,
            icon = { OnboardingIcon(R.drawable.ic_onboarding_permission) },
            actionLabel = "去开启",
            onAction = onOpenAccessibilitySettings,
        )
        PermissionCard(
            title = "截图分析",
            summary = "当前版本先预留能力入口，后续补齐更稳定的截图授权与规则校验流程。",
            enabled = false,
            icon = { OnboardingIcon(R.drawable.ic_onboarding_screenshot) },
            actionLabel = "后续接入",
            onAction = {},
        )
        ToggleCard(
            title = "自动记账总开关",
            summary = "关闭后仍可手动记账，但系统不会自动入账。",
            checked = state.autoAccountingEnabled,
            icon = { OnboardingIcon(R.drawable.ic_onboarding_notifications) },
            onCheckedChange = onToggleAutoAccounting,
        )
        ToggleCard(
            title = "云端分析",
            summary = "默认开启，在本地解析不足时作为补充回退路径。",
            checked = state.cloudAnalysisEnabled,
            icon = { OnboardingIcon(R.drawable.ic_onboarding_cloud) },
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
    /** 权限型卡片，展示状态并引导用户跳转系统设置。 */
    Card {
        Row(
            modifier =
                Modifier
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
    /** 策略型开关卡片，用于展示可直接在应用内调整的设置。 */
    Card {
        Row(
            modifier =
                Modifier
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
