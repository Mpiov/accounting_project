package com.autobook.cn.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.autobook.cn.R
import com.autobook.cn.app.capture.PageCaptureBookkeepingService
import com.autobook.cn.app.viewmodel.SettingsNotificationSource
import com.autobook.cn.app.viewmodel.SettingsUiState
import com.autobook.cn.core.common.PermissionStatusChecker
import com.autobook.cn.core.common.SettingsIntents
import com.autobook.cn.core.model.AppReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 主壳层下的设置页。
 *
 * 用途说明：
 * - 承接自动记账、应用通知、截图记账入口、来源管理和数据留存等设置项。
 * - 这份实现保留当前项目已经接线的功能，同时统一设置页 `Switch` 的可用态与禁用态视觉语义。
 *
 * 参数说明：
 * - state：设置页聚合后的界面状态。
 * - homeResetKey：设置首页重置标记；从其它底部 Tab 回到设置页时递增，用于清空二级页。
 * - onAutoAccountingChanged：自动记账总开关变更回调。
 * - onAutoLedgerSuccessNotificationChanged：自动入账成功通知开关变更回调。
 * - onCloudAnalysisChanged：云端 AI 分析开关变更回调。
 * - onAuthPhoneChanged：邮箱输入变更回调；参数名暂沿用旧账号契约。
 * - onAuthCodeChanged：邮箱验证码输入变更回调。
 * - onRequestLoginCode：请求验证码回调。
 * - onLoginOrRegister：登录或注册回调。
 * - onLogout：退出登录回调。
 * - onStartYearlyMembership：创建年卡订单回调。
 * - onRefreshMembership：从服务器刷新会员状态回调。
 * - onRefreshMembershipAfterPayment：用户付款后主动检查会员是否生效的回调。
 * - onPullCloudModel：只拉取服务器最新全局模型回调。
 * - onUserImprovementPlanChanged：用户改善计划开关回调。
 * - onCaptureEnabledChanged：截图记账入口启停状态回写回调。
 * - onNotificationRetentionChanged：通知保留天数回调。
 * - onScreenshotRetentionChanged：截图保留天数回调。
 * - onSourceEnabledChanged：设置页来源桶启停回调。
 * - modifier：外层修饰符。
 */
@Composable
fun SettingsWorkspaceScreen(
    state: SettingsUiState,
    homeResetKey: Int,
    onAutoAccountingChanged: (Boolean) -> Unit,
    onAutoLedgerSuccessNotificationChanged: (Boolean) -> Unit,
    onCloudAnalysisChanged: (Boolean) -> Unit,
    onAuthPhoneChanged: (String) -> Unit,
    onAuthCodeChanged: (String) -> Unit,
    onRequestLoginCode: () -> Unit,
    onLoginOrRegister: () -> Unit,
    onLogout: () -> Unit,
    onStartYearlyMembership: () -> Unit,
    onRefreshMembership: () -> Unit,
    onRefreshMembershipAfterPayment: () -> Unit,
    onPullCloudModel: () -> Unit,
    onUserImprovementPlanChanged: (Boolean) -> Unit,
    onCaptureEnabledChanged: (Boolean) -> Unit,
    onNotificationRetentionChanged: (Int) -> Unit,
    onScreenshotRetentionChanged: (Int) -> Unit,
    onSourceEnabledChanged: (SettingsNotificationSource, Boolean) -> Unit,
    onCheckAppUpdate: () -> Unit,
    onDismissAppUpdatePrompt: () -> Unit,
    onFeedbackContentChanged: (String) -> Unit,
    onFeedbackContactChanged: (String) -> Unit,
    onSubmitUserFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionRefreshScope = rememberCoroutineScope()
    var permissionSnapshot by remember(context) { mutableStateOf(SettingsPermissionSnapshot()) }
    val hasNotificationAccess = permissionSnapshot.hasNotificationAccess
    val hasAppNotificationsEnabled = permissionSnapshot.hasAppNotificationsEnabled
    val hasAccessibilityAccess = permissionSnapshot.hasAccessibilityAccess
    val latestRefreshMembership by rememberUpdatedState(onRefreshMembership)
    val latestAuthLoggedIn by rememberUpdatedState(state.authLoggedIn)
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
     * 设置页内部二级页选择状态。
     *
     * 用途说明：
     * - 这里刻意使用普通 `remember`，不使用 `rememberSaveable`。
     * - 底部导航离开设置页后，NavHost 会尝试恢复可保存状态；如果这里可保存，就会回到旧二级页。
     * - 当前产品预期是“重新点击底部设置 = 回到设置总页”，因此二级页状态只在设置页当前可见期间保留。
     */
    var selectedDetailName by remember(homeResetKey) { mutableStateOf<String?>(null) }
    val selectedDetail = SettingsDetailDestination.entries.firstOrNull { detail ->
        detail.name == selectedDetailName
    }

    state.appUpdatePrompt?.let { release ->
        AppUpdatePromptDialog(
            release = release,
            onDismiss = onDismissAppUpdatePrompt,
            onOpenDownload = {
                val opened = openAppUpdateDownloadInBrowser(context, release.apkUrl)
                if (opened) {
                    onDismissAppUpdatePrompt()
                }
            },
        )
    }

    LaunchedEffect(state.authLoggedIn) {
        if (state.authLoggedIn) {
            // 设置页首次出现或刚登录成功时立即刷新服务器会员状态，避免后台已开通但本地仍显示免费用户。
            latestRefreshMembership()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && latestAuthLoggedIn) {
                // 从支付 App、系统设置或后台切回轻松记时重新拉取会员状态，让人工确认收款后的会员权益及时生效。
                latestRefreshMembership()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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

    if (selectedDetail == SettingsDetailDestination.PERMISSIONS) {
        LaunchedEffect(context, selectedDetail) {
            // 只有进入权限二级页时才读取系统权限，设置首页不再为了隐藏内容访问系统服务。
            permissionSnapshot = readSettingsPermissionSnapshot(context)
        }

        DisposableEffect(lifecycleOwner, context, selectedDetail) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    // 从系统设置页返回后异步刷新权限状态，避免系统设置读取阻塞页面恢复动画。
                    permissionRefreshScope.launch {
                        permissionSnapshot = readSettingsPermissionSnapshot(context)
                    }
                    appNotificationSettingsLaunching = false
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    if (selectedDetail != null) {
        SettingsDetailScreen(
            destination = selectedDetail,
            state = state,
            hasNotificationAccess = hasNotificationAccess,
            hasAppNotificationsEnabled = hasAppNotificationsEnabled,
            hasAccessibilityAccess = hasAccessibilityAccess,
            onBack = { selectedDetailName = null },
            onAutoAccountingChanged = onAutoAccountingChanged,
            onCloudAnalysisChanged = onCloudAnalysisChanged,
            onOpenNotificationSettings = {
                context.startActivity(
                    SettingsIntents.notificationAccess().apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            },
            onOpenAppNotificationSettings = openAppNotificationSettings,
            onCaptureEnabledChanged = onCaptureEnabledChanged,
            onRequestNotificationPermission = {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onOpenAccessibilitySettings = {
                context.startActivity(
                    SettingsIntents.accessibilityAccess().apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            },
            onStartYearlyMembership = onStartYearlyMembership,
            onRefreshMembershipAfterPayment = onRefreshMembershipAfterPayment,
            onAutoLedgerSuccessNotificationChanged = onAutoLedgerSuccessNotificationChanged,
            onUserImprovementPlanChanged = onUserImprovementPlanChanged,
            onNotificationRetentionChanged = onNotificationRetentionChanged,
            onScreenshotRetentionChanged = onScreenshotRetentionChanged,
            onSourceEnabledChanged = onSourceEnabledChanged,
            onFeedbackContentChanged = onFeedbackContentChanged,
            onFeedbackContactChanged = onFeedbackContactChanged,
            onSubmitUserFeedback = onSubmitUserFeedback,
            modifier = modifier,
        )
        return
    }

    SettingsHomeContent(
        state = state,
        onAuthPhoneChanged = onAuthPhoneChanged,
        onAuthCodeChanged = onAuthCodeChanged,
        onRequestLoginCode = onRequestLoginCode,
        onLoginOrRegister = onLoginOrRegister,
        onLogout = onLogout,
        onPullCloudModel = onPullCloudModel,
        onOpenDetail = { detail -> selectedDetailName = detail.name },
        onCheckAppUpdate = onCheckAppUpdate,
        modifier = modifier,
    )
}

/**
 * 设置页首页内容。
 *
 * 用途说明：
 * - 首页只保留头像账号、顶部统计和横条入口，具体开关挪到二级页面。
 *
 * 参数说明：
 * - state：设置页 UI 状态。
 * - onAuthPhoneChanged：邮箱输入变更回调。
 * - onAuthCodeChanged：验证码输入变更回调。
 * - onRequestLoginCode：请求验证码回调。
 * - onLoginOrRegister：登录或注册回调。
 * - onLogout：退出登录回调。
 * - onPullCloudModel：点击模型状态时拉取最新模型。
 * - onOpenDetail：打开二级设置页面。
 * - modifier：外层修饰符。
 */
@Composable
private fun SettingsHomeContent(
    state: SettingsUiState,
    onAuthPhoneChanged: (String) -> Unit,
    onAuthCodeChanged: (String) -> Unit,
    onRequestLoginCode: () -> Unit,
    onLoginOrRegister: () -> Unit,
    onLogout: () -> Unit,
    onPullCloudModel: () -> Unit,
    onOpenDetail: (SettingsDetailDestination) -> Unit,
    onCheckAppUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // 设置页是长列表，直接铺不透明背景可以减少底层内容参与绘制。
            .background(MaterialTheme.colorScheme.background)
            // 设置页内容规模固定，使用普通滚动列可以避免 LazyColumn 在快速滑动时反复组合大卡片。
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsProfileHero(
            state = state,
            onAuthPhoneChanged = onAuthPhoneChanged,
            onAuthCodeChanged = onAuthCodeChanged,
            onRequestLoginCode = onRequestLoginCode,
            onLoginOrRegister = onLoginOrRegister,
            onLogout = onLogout,
            onPullCloudModel = onPullCloudModel,
            modifier = Modifier.padding(top = mainPageTopInset()),
        )

        SettingsMenuCard {
            SettingsMenuRow(
                // 权限设置使用盾牌图标，避免用户把入口误解成音量或普通通知设置。
                icon = AutobookIconAsset.Permissions,
                title = "权限设置",
                summary = "通知监听、后台稳定性、截图入口",
                onClick = { onOpenDetail(SettingsDetailDestination.PERMISSIONS) },
            )
            SettingsMenuDivider()
            SettingsMenuRow(
                icon = AutobookIconAsset.Crown,
                title = "会员中心",
                summary = state.membershipSummary,
                onClick = { onOpenDetail(SettingsDetailDestination.MEMBERSHIP) },
            )
            SettingsMenuDivider()
            SettingsMenuRow(
                // 来源管理处理的是微信、支付宝、银行短信这类消息来源，因此统一使用消息图标。
                icon = AutobookIconAsset.Message,
                title = "来源管理",
                summary = "微信、支付宝、银行短信等来源开关",
                onClick = { onOpenDetail(SettingsDetailDestination.SOURCES) },
            )
            SettingsMenuDivider()
            SettingsMenuRow(
                icon = AutobookIconAsset.CloudSync,
                title = "用户改善计划",
                summary = USER_IMPROVEMENT_PLAN_SUMMARY,
                onClick = { onOpenDetail(SettingsDetailDestination.IMPROVEMENT_PLAN) },
            )
            SettingsMenuDivider()
            SettingsMenuRow(
                icon = AutobookIconAsset.Storage,
                title = "数据留存",
                summary = "通知原文和截图保留时间",
                onClick = { onOpenDetail(SettingsDetailDestination.RETENTION) },
            )
            SettingsMenuDivider()
            SettingsMenuRow(
                icon = AutobookIconAsset.Message,
                title = "用户反馈",
                summary = "提交问题、建议或自动记账异常",
                onClick = { onOpenDetail(SettingsDetailDestination.FEEDBACK) },
            )
            SettingsMenuDivider()
            SettingsMenuRow(
                // 版本检查只请求很小的 JSON 元数据；真正下载交给浏览器，避免后台自动占用服务器带宽。
                icon = AutobookIconAsset.CloudSync,
                title = "检查新版本",
                summary = if (state.appUpdateChecking) "正在检查..." else "发现新版后通过浏览器下载",
                onClick = onCheckAppUpdate,
            )
        }
    }
}

/**
 * 设置二级页目的地。
 *
 * 用途说明：
 * - 首页只展示横条入口，具体设置项按目的地拆到二级页，减少设置首页的组合和绘制压力。
 *
 * 参数说明：
 * - title：二级页标题。
 * - summary：二级页短说明。
 * - icon：首页横条和二级页头部使用的图标。
 */
private enum class SettingsDetailDestination(
    val title: String,
    val summary: String,
    val icon: AutobookIconAsset,
) {
    PERMISSIONS(
        title = "权限设置",
        summary = "通知监听、后台稳定性和截图入口",
        icon = AutobookIconAsset.Permissions,
    ),
    MEMBERSHIP(
        title = "会员中心",
        summary = "会员状态、有效期和套餐",
        icon = AutobookIconAsset.Crown,
    ),
    SOURCES(
        title = "来源管理",
        summary = "选择参与自动解析的通知来源",
        icon = AutobookIconAsset.Message,
    ),
    IMPROVEMENT_PLAN(
        title = "用户改善计划",
        summary = "控制有效反馈样本是否进入全局训练样本池",
        icon = AutobookIconAsset.CloudSync,
    ),
    RETENTION(
        title = "数据留存",
        summary = "控制原始通知和截图保留时间",
        icon = AutobookIconAsset.Storage,
    ),
    FEEDBACK(
        title = "用户反馈",
        summary = "提交问题、建议或自动记账异常",
        icon = AutobookIconAsset.Message,
    ),
}

/**
 * 设置二级页容器。
 *
 * 用途说明：
 * - 把首页不需要直接展示的开关和表单按组放到二级页，首页快速滑动时不再组合这些重控件。
 *
 * 参数说明：
 * - destination：当前二级页目的地。
 * - state：设置页 UI 状态。
 * - hasNotificationAccess：通知监听权限是否开启。
 * - hasAppNotificationsEnabled：应用通知是否开启。
 * - hasAccessibilityAccess：无障碍权限是否开启。
 * - onBack：返回设置首页回调。
 * - onAutoAccountingChanged：自动记账开关变更回调。
 * - onAutoLedgerSuccessNotificationChanged：自动入账成功通知开关变更回调。
 * - onCloudAnalysisChanged：旧云端分析开关回调；当前设置页不再暴露旧入口。
 * - onOpenNotificationSettings：打开通知监听系统设置回调。
 * - onOpenAppNotificationSettings：打开应用通知系统设置回调。
 * - onCaptureEnabledChanged：截图入口启停回调。
 * - onRequestNotificationPermission：申请运行时通知权限回调。
 * - onOpenAccessibilitySettings：打开无障碍系统设置回调。
 * - onStartYearlyMembership：开通年卡回调。
 * - onRefreshMembershipAfterPayment：用户付款后主动检查会员是否生效的回调。
 * - onUserImprovementPlanChanged：用户改善计划启停回调。
 * - onNotificationRetentionChanged：通知保留天数变更回调。
 * - onScreenshotRetentionChanged：截图保留天数变更回调。
 * - onSourceEnabledChanged：来源开关变更回调。
 * - modifier：外层修饰符。
 */
@Composable
private fun SettingsDetailScreen(
    destination: SettingsDetailDestination,
    state: SettingsUiState,
    hasNotificationAccess: Boolean,
    hasAppNotificationsEnabled: Boolean,
    hasAccessibilityAccess: Boolean,
    onBack: () -> Unit,
    onAutoAccountingChanged: (Boolean) -> Unit,
    onAutoLedgerSuccessNotificationChanged: (Boolean) -> Unit,
    onCloudAnalysisChanged: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    onCaptureEnabledChanged: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onStartYearlyMembership: () -> Unit,
    onRefreshMembershipAfterPayment: () -> Unit,
    onUserImprovementPlanChanged: (Boolean) -> Unit,
    onNotificationRetentionChanged: (Int) -> Unit,
    onScreenshotRetentionChanged: (Int) -> Unit,
    onSourceEnabledChanged: (SettingsNotificationSource, Boolean) -> Unit,
    onFeedbackContentChanged: (String) -> Unit,
    onFeedbackContactChanged: (String) -> Unit,
    onSubmitUserFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val membershipPlans = currentMembershipPlanOptions(today = state.membershipPromotionToday())
    var membershipPaymentPlanId by rememberSaveable(destination) { mutableStateOf<String?>(null) }
    val membershipPaymentPlan = membershipPlans.firstOrNull { plan ->
        // 支付二维码页只保存套餐 id，避免二级页重组时把完整 UI 对象塞进可保存状态。
        plan.id == membershipPaymentPlanId
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = mainPageTopInset(), bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsDetailHeader(
            destination = destination,
            onBack = {
                if (membershipPaymentPlan != null) {
                    membershipPaymentPlanId = null
                } else {
                    onBack()
                }
            },
            titleOverride = if (membershipPaymentPlan != null) "扫码支付" else null,
            summaryOverride = if (membershipPaymentPlan != null) "请按页面提示填写登录邮箱" else null,
        )
        when (destination) {
            SettingsDetailDestination.PERMISSIONS -> {
                SettingsGroupCard(
                    title = "记账能力",
                    summary = "控制通知监听自动记账和应用通知状态。",
                ) {
                    AutoAccountingToggleSection(
                        autoAccountingEnabled = state.autoAccountingEnabled,
                        hasNotificationAccess = hasNotificationAccess,
                        authLoggedIn = state.authLoggedIn,
                        membershipActive = state.membershipActive,
                        trialRemainingCount = state.autoLedgerTrialRemainingCount,
                        trialLimit = state.autoLedgerTrialLimit,
                        onCheckedChange = onAutoAccountingChanged,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                    )
                    AutoLedgerSuccessNotificationToggleSection(
                        enabled = state.autoLedgerSuccessNotificationEnabled,
                        onCheckedChange = onAutoLedgerSuccessNotificationChanged,
                    )
                    AppNotificationToggleSection(
                        appNotificationsEnabled = hasAppNotificationsEnabled,
                        onOpenAppNotificationSettings = onOpenAppNotificationSettings,
                    )
                }
                RecommendedSystemSettingsCard(
                    onOpenBatterySettings = { openBatteryUsageSettingsSafely(context) },
                    onOpenAutostartSettings = { openAutostartSettingsSafely(context) },
                )
                CapturePermissionCard(
                    captureEnabled = state.captureEnabled,
                    appNotificationsEnabled = hasAppNotificationsEnabled,
                    accessibilityEnabled = hasAccessibilityAccess,
                    onEnableCapture = {
                        enableCaptureShortcut(
                            context = context,
                            onCaptureEnabledChanged = onCaptureEnabledChanged,
                            requestNotificationPermission = onRequestNotificationPermission,
                        )
                    },
                    onDisableCapture = {
                        disableCaptureShortcut(
                            context = context,
                            onCaptureEnabledChanged = onCaptureEnabledChanged,
                        )
                    },
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenAppNotificationSettings = onOpenAppNotificationSettings,
                )
            }
            SettingsDetailDestination.MEMBERSHIP -> {
                if (membershipPaymentPlan != null) {
                    MembershipPaymentQrCard(
                        plan = membershipPaymentPlan,
                        accountEmail = state.authPaymentEmailText(),
                        actionMessage = state.membershipActionMessage,
                        onRefreshMembership = onRefreshMembershipAfterPayment,
                    )
                } else {
                    MembershipCenterCard(
                        state = state,
                        plans = membershipPlans,
                        onStartYearlyMembership = onStartYearlyMembership,
                        onOpenMembershipPayment = { selectedPlan ->
                            membershipPaymentPlanId = selectedPlan.id
                        },
                    )
                }
            }
            SettingsDetailDestination.SOURCES -> {
                SettingsSourceManagementCard(
                    enabledSources = state.enabledSources,
                    onSourceEnabledChanged = onSourceEnabledChanged,
                )
            }
            SettingsDetailDestination.IMPROVEMENT_PLAN -> {
                UserImprovementPlanCard(
                    enabled = state.userImprovementPlanEnabled,
                    onCheckedChange = onUserImprovementPlanChanged,
                )
            }
            SettingsDetailDestination.RETENTION -> {
                SettingsRetentionCard(
                    notificationRetentionDays = state.notificationRetentionDays,
                    screenshotRetentionDays = state.screenshotRetentionDays,
                    onNotificationRetentionChanged = onNotificationRetentionChanged,
                    onScreenshotRetentionChanged = onScreenshotRetentionChanged,
                )
            }
            SettingsDetailDestination.FEEDBACK -> {
                UserFeedbackCard(
                    authLoggedIn = state.authLoggedIn,
                    content = state.feedbackContentInput,
                    contact = state.feedbackContactInput,
                    submitting = state.feedbackSubmitting,
                    onContentChanged = onFeedbackContentChanged,
                    onContactChanged = onFeedbackContactChanged,
                    onSubmit = onSubmitUserFeedback,
                )
            }
        }
    }
}

/**
 * 二级页标题栏。
 *
 * 参数说明：
 * - destination：当前二级页目的地。
 * - onBack：返回首页回调。
 * - titleOverride：临时子页面标题，为空时使用目的地默认标题。
 * - summaryOverride：临时子页面说明，为空时使用目的地默认说明。
 */
@Composable
private fun SettingsDetailHeader(
    destination: SettingsDetailDestination,
    onBack: () -> Unit,
    titleOverride: String? = null,
    summaryOverride: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            AutobookIcon(
                asset = AutobookIconAsset.Back,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AutobookIcon(
                asset = destination.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = titleOverride ?: destination.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = summaryOverride ?: destination.summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 设置首页横条列表容器。
 *
 * 参数说明：
 * - content：横条内容。
 */
/**
 * 用途说明：
 * - 设置页用户反馈表单。
 *
 * 参数说明：
 * - authLoggedIn：当前是否已登录，未登录时禁止提交，保证云端反馈能绑定账号。
 * - content：反馈正文输入内容。
 * - contact：额外联系方式输入内容。
 * - submitting：当前是否正在提交，避免重复点击。
 * - onContentChanged：反馈正文变化回调。
 * - onContactChanged：联系方式变化回调。
 * - onSubmit：提交按钮回调。
 */
@Composable
private fun UserFeedbackCard(
    authLoggedIn: Boolean,
    content: String,
    contact: String,
    submitting: Boolean,
    onContentChanged: (String) -> Unit,
    onContactChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val trimmedContent = content.trim()
    val canSubmit = authLoggedIn && !submitting && trimmedContent.length >= USER_FEEDBACK_MIN_LENGTH
    SettingsGroupCard(
        title = "用户反馈",
        summary = "告诉我们问题、建议或自动记账异常",
    ) {
        OutlinedTextField(
            value = content,
            onValueChange = onContentChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("反馈内容") },
            placeholder = { Text("例如：某条通知没有自动入账，或某个页面体验不好") },
            minLines = 5,
            maxLines = 8,
            enabled = !submitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        )
        OutlinedTextField(
            value = contact,
            onValueChange = onContactChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("联系方式（选填）") },
            placeholder = { Text("可填写微信号、邮箱或手机号") },
            singleLine = true,
            enabled = !submitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Text(
            text = if (authLoggedIn) {
                "反馈会随登录邮箱、App 版本和设备信息一起提交，方便定位问题。"
            } else {
                "请先登录邮箱账号，再提交反馈。"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (submitting) "提交中..." else "提交反馈")
        }
    }
}

@Composable
private fun SettingsMenuCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            content = content,
        )
    }
}

/**
 * 设置首页横条入口。
 *
 * 参数说明：
 * - icon：左侧图标。
 * - title：入口标题。
 * - summary：入口摘要。
 * - onClick：点击跳转回调。
 */
@Composable
private fun SettingsMenuRow(
    icon: AutobookIconAsset,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .autobookClippedClickable(
                shape = AutobookUiTokens.innerPanelShape,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AutobookIcon(
                asset = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AutobookIcon(
            asset = AutobookIconAsset.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 设置横条分割线。
 *
 * 用途说明：
 * - 用很浅的线条分隔首页入口和二级页动作项，保持参考截图那种轻列表感。
 */
@Composable
private fun SettingsMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.36f),
    )
}

/**
 * App 新版本提示弹窗。
 *
 * 用途说明：
 * - 展示服务端返回的新版本信息，并让用户主动选择是否跳转浏览器下载 APK。
 * - 当前不在 App 内下载文件，避免额外安装权限和小带宽服务器压力。
 *
 * 参数说明：
 * - release：服务端返回的新版本元数据。
 * - onDismiss：关闭弹窗回调。
 * - onOpenDownload：用户确认下载时的浏览器跳转回调。
 */
@Composable
private fun AppUpdatePromptDialog(
    release: AppReleaseInfo,
    onDismiss: () -> Unit,
    onOpenDownload: () -> Unit,
) {
    val versionName = release.latestVersionName.ifBlank { release.latestVersionCode.toString() }
    val notes = release.releaseNotes.takeIf { items -> items.isNotEmpty() }
        ?: listOf("修复问题并优化体验")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (release.forceUpdate) "需要更新到 $versionName" else "发现新版本 $versionName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "安装包大小：${formatApkSize(release.apkSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                notes.forEach { note ->
                    Text(
                        text = "• $note",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (release.apkUrl.isBlank()) {
                    Text(
                        text = "下载链接尚未配置，请稍后再试。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onOpenDownload,
                enabled = release.apkUrl.isNotBlank(),
            ) {
                Text("立即更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后再说")
            }
        },
    )
}

/**
 * 来源管理二级页内容。
 *
 * 参数说明：
 * - enabledSources：当前已启用来源集合。
 * - onSourceEnabledChanged：来源启停回调。
 */
@Composable
private fun SettingsSourceManagementCard(
    enabledSources: Set<SettingsNotificationSource>,
    onSourceEnabledChanged: (SettingsNotificationSource, Boolean) -> Unit,
) {
    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            SETTINGS_VISIBLE_SOURCES.forEachIndexed { index, source ->
                ToggleRow(
                    title = source.toDisplayLabel(),
                    summary = source.toSourceSummary(),
                    // 每个来源本质都是一类消息入口，和首页来源管理保持同一图标语义。
                    leadingIcon = AutobookIconAsset.Message,
                    checked = source in enabledSources,
                    onCheckedChange = { enabled -> onSourceEnabledChanged(source, enabled) },
                )
                if (index != SETTINGS_VISIBLE_SOURCES.lastIndex) {
                    SettingsMenuDivider()
                }
            }
        }
    }
}

/**
 * 用户改善计划二级页内容。
 *
 * 用途说明：
 * - 让用户明确控制是否把通知原文贡献到全局训练样本池。
 * - 该开关只影响样本上传，不影响会员模型能否使用。
 *
 * 参数说明：
 * - enabled：当前用户改善计划是否开启。
 * - onCheckedChange：开关变化后的持久化回调。
 */
@Composable
private fun UserImprovementPlanCard(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsGroupCard(
        title = "用户改善计划",
        summary = USER_IMPROVEMENT_PLAN_SUMMARY,
    ) {
        ToggleRow(
            title = "用户改善计划",
            summary = USER_IMPROVEMENT_PLAN_SUMMARY,
            leadingIcon = AutobookIconAsset.CloudSync,
            checked = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * 数据留存二级页内容。
 *
 * 参数说明：
 * - notificationRetentionDays：通知原文保留天数。
 * - screenshotRetentionDays：截图保留天数。
 * - onNotificationRetentionChanged：通知保留天数变更回调。
 * - onScreenshotRetentionChanged：截图保留天数变更回调。
 */
@Composable
private fun SettingsRetentionCard(
    notificationRetentionDays: Int,
    screenshotRetentionDays: Int,
    onNotificationRetentionChanged: (Int) -> Unit,
    onScreenshotRetentionChanged: (Int) -> Unit,
) {
    SettingsGroupCard(
        title = "数据留存",
        summary = "保留时间越短，本地占用越低；需要追溯时可以适当调长。",
    ) {
        RetentionSlider(
            title = "通知原文",
            value = notificationRetentionDays,
            range = 7..180,
            onValueChange = onNotificationRetentionChanged,
        )
        RetentionSlider(
            title = "截图",
            value = screenshotRetentionDays,
            range = 1..30,
            onValueChange = onScreenshotRetentionChanged,
        )
    }
}

/**
 * 设置页顶部身份区。
 *
 * 用途说明：
 * - 借鉴参考图里“先给用户身份和关键状态，再进入设置列表”的结构。
 * - 顶部只使用浅米白和白色，不做大面积深色块，让页面第一眼更轻。
 *
 * 参数说明：
 * - state：设置页 UI 状态，用于展示登录、会员、统计和模型状态。
 * - onAuthPhoneChanged：邮箱输入变更回调。
 * - onAuthCodeChanged：验证码输入变更回调。
 * - onRequestLoginCode：请求邮箱验证码回调。
 * - onLoginOrRegister：提交登录或注册回调。
 * - onLogout：退出登录回调。
 * - onPullCloudModel：点击模型状态时手动拉取最新模型。
 * - modifier：外层修饰符，主要承接顶部安全间距。
 */
@Composable
private fun SettingsProfileHero(
    state: SettingsUiState,
    onAuthPhoneChanged: (String) -> Unit,
    onAuthCodeChanged: (String) -> Unit,
    onRequestLoginCode: () -> Unit,
    onLoginOrRegister: () -> Unit,
    onLogout: () -> Unit,
    onPullCloudModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(highlighted = true),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsHeroAvatar()
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = state.authAccountDisplayText(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.authLoggedIn) {
                    OutlinedButton(onClick = onLogout) {
                        Text("退出")
                    }
                } else {
                    SettingsStatusPill(
                        text = if (state.cloudLearningEffectiveEnabled) "会员" else "免费版",
                        emphasized = state.cloudLearningEffectiveEnabled,
                    )
                }
            }
            if (!state.authLoggedIn) {
                SettingsHeroAuthForm(
                    state = state,
                    onAuthPhoneChanged = onAuthPhoneChanged,
                    onAuthCodeChanged = onAuthCodeChanged,
                    onRequestLoginCode = onRequestLoginCode,
                    onLoginOrRegister = onLoginOrRegister,
                )
            }
            SettingsHeroStatsRow(
                state = state,
                onPullCloudModel = onPullCloudModel,
            )
        }
    }
}

/**
 * 设置页默认头像。
 *
 * 用途说明：
 * - 先提供稳定的本地默认头像，后续接头像上传时只需要替换这里的展示来源。
 */
@Composable
private fun SettingsHeroAvatar() {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AutobookIcon(
            asset = AutobookIconAsset.Account,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(34.dp),
        )
    }
}

/**
 * 顶部登录注册表单。
 *
 * 参数说明：
 * - state：设置页 UI 状态，用于读取邮箱、验证码和倒计时。
 * - onAuthPhoneChanged：邮箱输入变更回调。
 * - onAuthCodeChanged：验证码输入变更回调。
 * - onRequestLoginCode：请求验证码回调。
 * - onLoginOrRegister：登录或注册回调。
 */
@Composable
private fun SettingsHeroAuthForm(
    state: SettingsUiState,
    onAuthPhoneChanged: (String) -> Unit,
    onAuthCodeChanged: (String) -> Unit,
    onRequestLoginCode: () -> Unit,
    onLoginOrRegister: () -> Unit,
) {
    val authEmail = state.authPhoneInput.trim()
    val normalizedCode = state.authCodeInput.filter(Char::isDigit)
    val codeButtonText = when {
        state.authCodeCooldownSeconds > 0 -> "${state.authCodeCooldownSeconds}秒"
        state.authCodeRequesting -> "发送中"
        else -> "验证码"
    }
    val canRequestCode = AUTH_EMAIL_REGEX.matches(authEmail) &&
        !state.authCodeRequesting &&
        state.authCodeCooldownSeconds <= 0
    val canLogin = AUTH_EMAIL_REGEX.matches(authEmail) &&
        normalizedCode.length == SMS_CODE_LENGTH &&
        !state.authLoginInProgress

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        OutlinedTextField(
            value = state.authPhoneInput,
            onValueChange = onAuthPhoneChanged,
            label = { Text("邮箱") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.authCodeInput,
                onValueChange = onAuthCodeChanged,
                label = { Text("验证码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onRequestLoginCode,
                enabled = canRequestCode,
                modifier = Modifier.height(56.dp),
            ) {
                Text(codeButtonText)
            }
        }
        Button(
            onClick = onLoginOrRegister,
            enabled = canLogin,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.authLoginInProgress) "登录中" else "登录/注册")
        }
    }
}

/**
 * 顶部三项核心统计。
 *
 * 参数说明：
 * - state：设置页 UI 状态，用于读取记账统计和模型状态。
 * - onPullCloudModel：点击模型状态时手动拉取服务器模型。
 */
@Composable
private fun SettingsHeroStatsRow(
    state: SettingsUiState,
    onPullCloudModel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsHeroStatItem(
            label = "记账天数",
            value = state.bookkeepingDayCount.toString(),
            modifier = Modifier.weight(1f),
        )
        SettingsHeroStatItem(
            label = "记账笔数",
            value = state.bookkeepingEntryCount.toString(),
            modifier = Modifier.weight(1f),
        )
        SettingsHeroModelStatusItem(
            state = state,
            onClick = onPullCloudModel,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 顶部模型状态单元。
 *
 * 用途说明：
 * - 会员展示模型更新时间；非会员展示自动记账体验剩余次数，避免把用户不理解的模型权限放在首屏。
 *
 * 参数说明：
 * - state：设置页 UI 状态，用于判断会员与模型更新时间。
 * - onClick：点击后拉取服务器最新模型。
 * - modifier：外层布局修饰符。
 */
@Composable
private fun SettingsHeroModelStatusItem(
    state: SettingsUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statShape = AutobookUiTokens.innerPanelShape
    val clickableModifier = if (state.membershipActive) {
        Modifier.autobookClippedClickable(shape = statShape, onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .height(66.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), statShape)
            .then(clickableModifier)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (state.membershipActive) {
                "模型状态"
            } else {
                "剩余 ${state.autoLedgerTrialRemainingCount} 次"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.membershipActive) {
            Text(
                text = "最近更新时间：",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.modelLastPulledLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = "自动记账",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 顶部统计单元。
 *
 * 参数说明：
 * - label：指标名称。
 * - value：指标主值。
 * - caption：指标副标题，可为空。
 * - onClick：点击回调；为空时仅展示。
 * - modifier：外层布局修饰符。
 */
@Composable
private fun SettingsHeroStatItem(
    label: String,
    value: String,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val statShape = AutobookUiTokens.innerPanelShape
    val statBaseModifier = modifier
        .height(66.dp)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), statShape)
    val statModifier = if (onClick != null) {
        statBaseModifier.autobookClippedClickable(shape = statShape, onClick = onClick)
    } else {
        statBaseModifier
    }
    Column(
        modifier = statModifier
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 顶部胶囊状态。
 *
 * 参数说明：
 * - text：状态文案。
 * - emphasized：是否使用主色强调。
 */
@Composable
private fun SettingsStatusPill(
    text: String,
    emphasized: Boolean,
) {
    Surface(
        shape = AutobookUiTokens.chipShape,
        color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = autobookCardBorder(emphasized = emphasized),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 设置页通用分组卡片。
 *
 * 用途说明：
 * - 把原先“标题在外 + 大卡片”的结构压缩成白色分组列表，降低视觉重量。
 * - 每组内部只保留必要说明，减少用户滑动时的阅读负担。
 *
 * 参数说明：
 * - title：分组标题。
 * - summary：分组说明。
 * - content：分组内的真实设置项。
 */
@Composable
private fun SettingsGroupCard(
    title: String,
    summary: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsGroupHeader(title = title, summary = summary)
            content()
        }
    }
}

/**
 * 设置分组标题。
 *
 * 参数说明：
 * - title：分组标题。
 * - summary：分组说明。
 */
@Composable
private fun SettingsGroupHeader(
    title: String,
    summary: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AutobookIcon(
                asset = resolveSettingsGroupIcon(title),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 根据分组标题选择图标。
 *
 * 参数说明：
 * - title：设置分组标题。
 */
private fun resolveSettingsGroupIcon(title: String): AutobookIconAsset {
    return when (title) {
        "记账能力" -> AutobookIconAsset.Sparkle
        "账号" -> AutobookIconAsset.Account
        "会员中心" -> AutobookIconAsset.Crown
        "会员状态" -> AutobookIconAsset.Crown
        "会员开通" -> AutobookIconAsset.Crown
        "扫码支付" -> AutobookIconAsset.Crown
        "截图能力" -> AutobookIconAsset.Screenshot
        "建议开启" -> AutobookIconAsset.Sparkle
        "来源管理" -> AutobookIconAsset.Message
        "用户改善计划" -> AutobookIconAsset.CloudSync
        "数据留存" -> AutobookIconAsset.Storage
        else -> AutobookIconAsset.Account
    }
}

/**
 * 权限页的系统建议设置卡。
 *
 * 用途说明：
 * - 把应用耗电和自启动放到“建议开启”里，表达它们会提升稳定性，但不是自动记账的硬性权限。
 * - 两个入口只做系统页面引导，不在本地伪造状态，因为不同 Android 厂商没有统一可读接口。
 *
 * 参数说明：
 * - onOpenBatterySettings：点击应用耗电设置后的跳转回调。
 * - onOpenAutostartSettings：点击自启动设置后的跳转回调。
 */
@Composable
private fun RecommendedSystemSettingsCard(
    onOpenBatterySettings: () -> Unit,
    onOpenAutostartSettings: () -> Unit,
) {
    SettingsGroupCard(
        title = "建议开启",
        summary = "减少后台限制，提升自动记账稳定性。",
    ) {
        SettingsActionRow(
            icon = AutobookIconAsset.Battery,
            title = "应用耗电设置",
            summary = "建议允许后台运行或关闭电池优化。",
            onClick = onOpenBatterySettings,
        )
        SettingsMenuDivider()
        SettingsActionRow(
            icon = AutobookIconAsset.Autostart,
            title = "自启动设置",
            summary = "建议允许自启动，手机重启后更稳定。",
            onClick = onOpenAutostartSettings,
        )
    }
}

/**
 * 设置页中只负责跳转的横条设置项。
 *
 * 用途说明：
 * - 用于无法可靠读取状态的系统页入口，例如耗电设置、自启动设置。
 * - 视觉上沿用设置首页横条，但不展示开关，避免用户误以为 App 能直接控制系统开关。
 *
 * 参数说明：
 * - icon：左侧语义图标。
 * - title：设置项名称。
 * - summary：设置项简短说明。
 * - onClick：点击后执行的跳转动作。
 */
@Composable
private fun SettingsActionRow(
    icon: AutobookIconAsset,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .autobookClippedClickable(
                shape = AutobookUiTokens.innerPanelShape,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AutobookIcon(
                asset = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AutobookIcon(
            asset = AutobookIconAsset.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
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
        leadingIcon = AutobookIconAsset.Notifications,
        checked = appNotificationsEnabled,
        readOnly = true,
        onRowClick = onOpenAppNotificationSettings,
    )
}

/**
 * 自动入账成功通知开关。
 *
 * 用途说明：
 * - 让用户选择自动入账成功后是否弹出系统通知。
 * - 关闭后不影响自动入账本身，只是不再打断用户。
 *
 * 参数说明：
 * - enabled：当前提醒开关状态。
 * - onCheckedChange：用户切换开关后的回调。
 */
@Composable
private fun AutoLedgerSuccessNotificationToggleSection(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ToggleRow(
        title = "入账成功提醒",
        summary = if (enabled) {
            "自动入账成功后弹出通知，点击可回到首页查看或修改。"
        } else {
            "自动入账成功后不再弹出通知。"
        },
        leadingIcon = AutobookIconAsset.Notifications,
        checked = enabled,
        onCheckedChange = onCheckedChange,
    )
}

/**
 * 自动记账总开关按“权限状态 + 登录状态 + 会员权益”联合展示。
 *
 * 参数说明：
 * - autoAccountingEnabled：当前持久化的自动记账开关状态。
 * - hasNotificationAccess：通知监听权限是否已开启。
 * - authLoggedIn：用户是否已经登录邮箱账号。
 * - membershipActive：服务器是否确认当前用户是会员。
 * - trialRemainingCount：免费用户剩余的自动入账体验次数。
 * - trialLimit：免费自动入账体验总次数。
 * - onCheckedChange：切换自动记账开关时的回调。
 * - onOpenNotificationSettings：缺少权限时跳转到系统通知监听设置页。
 */
@Composable
private fun AutoAccountingToggleSection(
    autoAccountingEnabled: Boolean,
    hasNotificationAccess: Boolean,
    authLoggedIn: Boolean,
    membershipActive: Boolean,
    trialRemainingCount: Int,
    trialLimit: Int,
    onCheckedChange: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val hasAutoLedgerEntitlement = membershipActive || trialRemainingCount > 0
    val switchEnabled = hasNotificationAccess && authLoggedIn && hasAutoLedgerEntitlement
    // 开关视觉状态必须同时反映权益门禁，避免用户在未登录或体验用完时误以为仍会自动入账。
    val checked = autoAccountingEnabled && switchEnabled
    val summary = when {
        !hasNotificationAccess -> "需要先开启通知监听权限，自动记账才能使用。"
        !authLoggedIn -> "请先登录邮箱账号，登录后可体验 $trialLimit 次自动入账。"
        membershipActive && autoAccountingEnabled -> "会员自动入账已开启，符合规则的账单可直接写入账本。"
        membershipActive -> "会员权益可用，打开后符合规则的账单可直接写入账本。"
        trialRemainingCount <= 0 -> "免费自动入账体验已用完，开通会员后可继续使用。"
        autoAccountingEnabled -> "自动记账已开启，剩余 $trialRemainingCount/$trialLimit 次自动入账体验。"
        else -> "已登录，剩余 $trialRemainingCount/$trialLimit 次自动入账体验。"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleRow(
            title = "自动记账总开关",
            summary = summary,
            leadingIcon = AutobookIconAsset.Sparkle,
            checked = checked,
            enabled = switchEnabled,
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
 * 会员套餐 UI 数据。
 *
 * 用途说明：
 * - 把套餐的展示文案、价格按钮和可购买状态集中维护，避免网格卡片和支付页各写一套价格。
 *
 * 参数说明：
 * - id：套餐稳定标识，用于 `rememberSaveable` 保存当前选中项。
 * - title：套餐名称。
 * - price：套餐价格文案。
 * - period：套餐副文案，例如限时说明或占位说明。
 * - badge：套餐角标文案，可为空。
 * - payButtonText：支付按钮文案。
 * - available：当前套餐是否开放购买。
 * - highlightSummary：套餐区顶部摘要，便于把活动剩余时间展示成真实倒计时。
 */
private data class MembershipPlanUi(
    val id: String,
    val title: String,
    val price: String,
    val period: String,
    val badge: String?,
    val payButtonText: String,
    val available: Boolean,
    val highlightSummary: String,
)

private const val MEMBERSHIP_YEARLY_PLAN_ID = "yearly"
private const val MEMBERSHIP_PROMOTION_DAYS = 30
private val MEMBERSHIP_PROMOTION_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
private val MEMBERSHIP_YEARLY_PROMOTION_START_DATE: LocalDate = LocalDate.of(2026, 5, 4)
private val MEMBERSHIP_YEARLY_PROMOTION_END_DATE: LocalDate =
    MEMBERSHIP_YEARLY_PROMOTION_START_DATE.plusDays(MEMBERSHIP_PROMOTION_DAYS.toLong())

/**
 * 当前会员套餐列表。
 *
 * 用途说明：
 * - 每次组合会员中心时按真实日期重新计算限时年卡的剩余天数，避免活动结束后仍展示“限时 30 天”。
 *
 * 参数说明：
 * - today：当前日期，默认取上海时区；测试或预览时可传入固定日期。
 */
private fun currentMembershipPlanOptions(
    today: LocalDate = LocalDate.now(MEMBERSHIP_PROMOTION_ZONE),
): List<MembershipPlanUi> {
    val yearlyPromotionPlan = currentYearlyPromotionPlan(today)
    return listOf(
        yearlyPromotionPlan,
        MembershipPlanUi(
            id = "monthly",
            title = "月卡会员",
            price = "占位",
            period = "敬请期待",
            badge = null,
            payButtonText = "暂未开放",
            available = false,
            highlightSummary = "月卡会员暂未开放。",
        ),
        MembershipPlanUi(
            id = "quarterly",
            title = "季卡会员",
            price = "占位",
            period = "敬请期待",
            badge = null,
            payButtonText = "暂未开放",
            available = false,
            highlightSummary = "季卡会员暂未开放。",
        ),
        MembershipPlanUi(
            id = "two_year",
            title = "2年VIP",
            price = "占位",
            period = "敬请期待",
            badge = null,
            payButtonText = "暂未开放",
            available = false,
            highlightSummary = "2 年 VIP 暂未开放。",
        ),
    )
}

/**
 * 当前年卡限时活动套餐。
 *
 * 用途说明：
 * - 根据上海时区的真实日期判断 19 元年卡是否仍在首发 30 天窗口内。
 *
 * 参数说明：
 * - today：当前日期。
 */
private fun currentYearlyPromotionPlan(today: LocalDate): MembershipPlanUi {
    val remainingDays = ChronoUnit.DAYS.between(today, MEMBERSHIP_YEARLY_PROMOTION_END_DATE)
    val beforeStart = today.isBefore(MEMBERSHIP_YEARLY_PROMOTION_START_DATE)
    val available = !beforeStart && remainingDays > 0
    val period = when {
        beforeStart -> "活动未开始"
        remainingDays > 0 -> "剩余 ${remainingDays.coerceAtMost(MEMBERSHIP_PROMOTION_DAYS.toLong())} 天"
        else -> "活动已结束"
    }
    val summary = when {
        beforeStart -> "首发 19 元年卡活动将于 ${MEMBERSHIP_YEARLY_PROMOTION_START_DATE.toChinaDateLabel()} 开始。"
        remainingDays > 0 -> "首发特惠剩余 $remainingDays 天，年卡会员仅需 19 元。"
        else -> "首发 19 元年卡活动已于 ${MEMBERSHIP_YEARLY_PROMOTION_END_DATE.toChinaDateLabel()} 结束。"
    }

    return MembershipPlanUi(
        id = MEMBERSHIP_YEARLY_PLAN_ID,
        title = "年卡会员",
        price = "￥19",
        period = period,
        badge = if (available) "新发布特惠" else null,
        payButtonText = when {
            available -> "立即支付￥19元"
            beforeStart -> "活动未开始"
            else -> "活动已结束"
        },
        available = available,
        highlightSummary = summary,
    )
}

/**
 * 会员中心设置卡。
 *
 * 用途说明：
 * - 展示当前会员状态、会员有效期和可购买的会员套餐。
 * - 套餐点击只负责切换选中态，真正的订单创建和二维码跳转统一收敛到底部支付按钮。
 *
 * 参数说明：
 * - state：设置页 UI 状态。
 * - plans：已按当前真实日期计算后的套餐列表。
 * - onStartYearlyMembership：点击年卡支付按钮后的订单创建回调。
 * - onOpenMembershipPayment：进入二维码支付页回调。
 */
@Composable
private fun MembershipCenterCard(
    state: SettingsUiState,
    plans: List<MembershipPlanUi>,
    onStartYearlyMembership: () -> Unit,
    onOpenMembershipPayment: (MembershipPlanUi) -> Unit,
) {
    var selectedPlanId by rememberSaveable { mutableStateOf(MEMBERSHIP_YEARLY_PLAN_ID) }
    val selectedPlan = plans.firstOrNull { plan ->
        // 选中项只保存 id，实际渲染时再从统一套餐表反查，避免价格文案在多个状态里漂移。
        plan.id == selectedPlanId
    } ?: plans.first()

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(
            shape = AutobookUiTokens.panelShape,
            border = autobookCardBorder(),
            colors = autobookCardColors(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsGroupHeader(
                    title = "会员状态",
                    summary = "查看当前账号是否已开通会员。",
                )
                MembershipStatusPanel(state = state)
            }
        }
        Card(
            shape = AutobookUiTokens.panelShape,
            border = autobookCardBorder(),
            colors = autobookCardColors(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsGroupHeader(
                    title = "会员开通",
                    summary = selectedPlan.highlightSummary,
                )
                MembershipPlanGrid(
                    plans = plans,
                    selectedPlanId = selectedPlanId,
                    onSelectPlan = { plan ->
                        selectedPlanId = plan.id
                    },
                )
                Button(
                    onClick = {
                        if (selectedPlan.id == MEMBERSHIP_YEARLY_PLAN_ID) {
                            onStartYearlyMembership()
                        }
                        onOpenMembershipPayment(selectedPlan)
                    },
                    enabled = selectedPlan.available,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = selectedPlan.payButtonText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * 会员状态面板。
 *
 * 参数说明：
 * - state：设置页 UI 状态，用于展示当前是否会员以及会员有效期。
 */
@Composable
private fun MembershipStatusPanel(state: SettingsUiState) {
    Surface(
        shape = AutobookUiTokens.innerPanelShape,
        color = if (state.membershipActive) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.56f)
        } else {
            autobookSoftFillColor()
        },
        border = autobookCardBorder(emphasized = state.membershipActive),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutobookIcon(
                asset = AutobookIconAsset.Crown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = state.membershipSummary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (state.membershipActive) {
                        "有效期至：${state.membershipExpiresAtLabel}"
                    } else {
                        "开通会员后自动记账不受次数限制"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 会员套餐横向展示区。
 *
 * 参数说明：
 * - plans：已按当前真实日期计算后的套餐列表。
 * - selectedPlanId：当前选中的套餐 id。
 * - onSelectPlan：点击可购买套餐后的选中回调。
 */
@Composable
private fun MembershipPlanGrid(
    plans: List<MembershipPlanUi>,
    selectedPlanId: String,
    onSelectPlan: (MembershipPlanUi) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        plans.forEach { plan ->
            MembershipPlanOption(
                plan = plan,
                selected = plan.id == selectedPlanId,
                onClick = {
                    onSelectPlan(plan)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 单个会员套餐选项。
 *
 * 参数说明：
 * - plan：套餐展示数据。
 * - selected：当前套餐是否被选中。
 * - onClick：点击套餐后的选中回调。
 * - modifier：外层布局修饰符。
 */
@Composable
private fun MembershipPlanOption(
    plan: MembershipPlanUi,
    selected: Boolean,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val optionFill = when {
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        plan.available -> MaterialTheme.colorScheme.surface
        else -> autobookSoftFillColor()
    }
    Surface(
        modifier = modifier
            .height(86.dp)
            // 占位套餐只展示未来能力，不给点击反馈，避免用户误以为当前可以购买。
            .then(
                if (plan.available) {
                    Modifier.autobookClippedClickable(shape = shape, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = optionFill,
        border = autobookCardBorder(emphasized = selected),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
        ) {
            if (plan.badge != null) {
                Text(
                    text = plan.badge,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (plan.badge == null) 2.dp else 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = plan.price,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (plan.available) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = plan.period,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 会员二维码支付页。
 *
 * 用途说明：
 * - 在用户点击“立即支付”后展示个人收款二维码，并用醒目的红色提示绑定人工发放会员所需的登录邮箱。
 * - 该页面只负责展示支付指引，订单创建由进入本页前的支付按钮触发。
 *
 * 参数说明：
 * - plan：当前准备支付的会员套餐。
 * - accountEmail：当前账号邮箱；未登录或无法识别时传入“未登录”。
 * - actionMessage：刷新会员状态后的短提示文案。
 * - onRefreshMembership：用户付款后主动刷新服务器会员状态的回调。
 */
@Composable
private fun MembershipPaymentQrCard(
    plan: MembershipPlanUi,
    accountEmail: String,
    actionMessage: String,
    onRefreshMembership: () -> Unit,
) {
    val hasPaymentEmail = accountEmail.contains("@")
    val warningText = if (hasPaymentEmail) {
        "付款备注/留言请填写：$accountEmail"
    } else {
        "付款前请先返回设置页登录，并在付款备注/留言中填写登录邮箱。"
    }

    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsGroupHeader(
                title = "扫码支付",
                summary = "${plan.title} ${plan.price}，付款后由后台人工开通。",
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AutobookUiTokens.innerPanelShape,
                color = autobookSoftFillColor(),
                border = autobookCardBorder(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = plan.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = plan.period,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = plan.price,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = autobookCardBorder(),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.membership_payment_qr),
                    contentDescription = "会员收款二维码",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(278.dp)
                        .padding(12.dp),
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AutobookUiTokens.innerPanelShape,
                color = Color(0xFFFFEBEE),
                border = autobookCardBorder(emphasized = true),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "重要：付款时请务必填写登录邮箱",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD32F2F),
                    )
                    Text(
                        text = warningText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFD32F2F),
                    )
                    Text(
                        text = "未填写或填写错误会导致无法人工发放会员。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD32F2F),
                    )
                }
            }
            Button(
                onClick = onRefreshMembership,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("我已付款，刷新会员状态")
            }
            if (actionMessage.isNotBlank()) {
                Text(
                    text = actionMessage,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "会员由人工发放，可能存在 2 到 3 小时延迟，请耐心等待。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsGroupHeader(
                title = "截图能力",
                summary = "当前页面截图记账的权限入口与启停控制。",
            )
            Text(
                text = when {
                    !accessibilityEnabled -> "需要先开启无障碍自动化，截图能力才能使用。"
                    captureEnabled -> "当前可以通过通知栏入口执行截图记账。"
                    else -> "当前截图记账功能已关闭。"
                },
                style = MaterialTheme.typography.labelSmall,
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
    val appDetailsIntent = createAppDetailsSettingsIntent(context).apply {
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
 * 安全打开“应用耗电设置”相关系统页。
 *
 * 用途说明：
 * - 优先进入系统电池优化页面，引导用户把 App 从后台限制中放出来。
 * - Android 没有稳定的单应用耗电设置标准入口，因此最后兜底到应用详情页。
 *
 * 参数说明：
 * - context：当前界面上下文，用于启动系统设置页和展示失败提示。
 *
 * 返回值说明：
 * - `true`：已经成功发起系统设置页跳转。
 * - `false`：当前设备没有任何可用入口，或启动系统设置页时发生异常。
 */
private fun openBatteryUsageSettingsSafely(context: Context): Boolean {
    val candidateIntents = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
        createAppDetailsSettingsIntent(context),
    )
    return openFirstAvailableSettingsIntent(
        context = context,
        candidateIntents = candidateIntents,
        failureMessage = "当前设备暂时无法直接打开应用耗电设置，请手动到系统设置中查看。",
    )
}

/**
 * 安全打开“自启动设置”相关系统页。
 *
 * 用途说明：
 * - 自启动没有 Android 标准 API，只能按常见厂商设置页逐个尝试。
 * - 如果厂商入口不存在，就兜底打开应用详情页，让用户能继续手动查找自启动/后台运行设置。
 *
 * 参数说明：
 * - context：当前界面上下文，用于启动系统设置页和展示失败提示。
 *
 * 返回值说明：
 * - `true`：已经成功发起系统设置页跳转。
 * - `false`：当前设备没有任何可用入口，或启动系统设置页时发生异常。
 */
private fun openAutostartSettingsSafely(context: Context): Boolean {
    val appPackageName = context.packageName
    val candidateIntents = listOf(
        createManufacturerSettingsIntent(
            targetPackageName = "com.miui.securitycenter",
            targetClassName = "com.miui.permcenter.autostart.AutoStartManagementActivity",
            appPackageName = appPackageName,
        ),
        createManufacturerSettingsIntent(
            targetPackageName = "com.huawei.systemmanager",
            targetClassName = "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            appPackageName = appPackageName,
        ),
        createManufacturerSettingsIntent(
            targetPackageName = "com.hihonor.systemmanager",
            targetClassName = "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            appPackageName = appPackageName,
        ),
        createManufacturerSettingsIntent(
            targetPackageName = "com.coloros.safecenter",
            targetClassName = "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            appPackageName = appPackageName,
        ),
        createManufacturerSettingsIntent(
            targetPackageName = "com.oplus.safecenter",
            targetClassName = "com.oplus.athena.startup.StartupAppListActivity",
            appPackageName = appPackageName,
        ),
        createManufacturerSettingsIntent(
            targetPackageName = "com.vivo.permissionmanager",
            targetClassName = "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            appPackageName = appPackageName,
        ),
        createManufacturerSettingsIntent(
            targetPackageName = "com.meizu.safe",
            targetClassName = "com.meizu.safe.permission.SmartBGActivity",
            appPackageName = appPackageName,
        ),
        createManufacturerSettingsIntent(
            targetPackageName = "com.samsung.android.sm_cn",
            targetClassName = "com.samsung.android.sm.ui.ram.AutoRunActivity",
            appPackageName = appPackageName,
        ),
        createAppDetailsSettingsIntent(context),
    )
    return openFirstAvailableSettingsIntent(
        context = context,
        candidateIntents = candidateIntents,
        failureMessage = "当前设备暂时无法直接打开自启动设置，请手动到系统设置中查看。",
    )
}

/**
 * 创建单应用详情页 Intent。
 *
 * 用途说明：
 * - 作为耗电、自启动、应用通知等系统页跳转失败后的统一兜底入口。
 * - 应用详情页通常可以继续进入权限、通知、耗电等厂商二级设置。
 *
 * 参数说明：
 * - context：当前界面上下文，用于读取当前 App 包名。
 */
private fun createAppDetailsSettingsIntent(context: Context): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
}

/**
 * 创建厂商私有设置页 Intent。
 *
 * 用途说明：
 * - 厂商自启动设置页没有标准 action，只能使用明确组件名。
 * - 同时带上当前 App 包名，部分系统会据此直接定位到本应用。
 *
 * 参数说明：
 * - targetPackageName：目标系统设置模块包名。
 * - targetClassName：目标系统设置 Activity 类名。
 * - appPackageName：当前 App 包名，作为厂商设置页定位参数。
 */
private fun createManufacturerSettingsIntent(
    targetPackageName: String,
    targetClassName: String,
    appPackageName: String,
): Intent {
    return Intent().apply {
        component = ComponentName(targetPackageName, targetClassName)
        // 不同厂商读取的 extra 名称不统一，因此同时写入常见命名，提升直接定位到本应用的概率。
        putExtra("package_name", appPackageName)
        putExtra("packageName", appPackageName)
    }
}

/**
 * 按顺序尝试打开系统设置页。
 *
 * 用途说明：
 * - 统一收口多系统、多厂商设置入口的启动逻辑。
 * - 每个 Intent 都单独捕获异常，前一个入口不可用时继续尝试下一个入口。
 *
 * 参数说明：
 * - context：当前界面上下文，用于启动系统 Activity 和展示失败提示。
 * - candidateIntents：按优先级排列的候选系统设置页。
 * - failureMessage：全部候选入口都失败时展示给用户的提示。
 *
 * 返回值说明：
 * - `true`：至少有一个候选设置页成功打开。
 * - `false`：所有候选设置页都无法打开。
 */
private fun openFirstAvailableSettingsIntent(
    context: Context,
    candidateIntents: List<Intent>,
    failureMessage: String,
): Boolean {
    candidateIntents.forEach { candidateIntent ->
        // 系统设置页位于应用外部，统一加新任务标记，避免从非 Activity 上下文启动时报错。
        candidateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching {
            context.startActivity(candidateIntent)
            true
        }.getOrDefault(false)
        if (opened) {
            return true
        }
    }
    Toast.makeText(
        context,
        failureMessage,
        Toast.LENGTH_LONG,
    ).show()
    return false
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
 * 设置页权限快照。
 *
 * 用途说明：
 * - 把三个系统权限状态聚合成一个不可变对象，便于异步刷新后一次性更新界面。
 *
 * 参数说明：
 * - hasNotificationAccess：通知监听服务权限是否已开启。
 * - hasAppNotificationsEnabled：应用通知总开关是否已开启。
 * - hasAccessibilityAccess：无障碍自动化服务权限是否已开启。
 */
private data class SettingsPermissionSnapshot(
    val hasNotificationAccess: Boolean = false,
    val hasAppNotificationsEnabled: Boolean = false,
    val hasAccessibilityAccess: Boolean = false,
)

/**
 * 异步读取设置页需要展示的系统权限状态。
 *
 * 用途说明：
 * - `Settings.Secure` 和通知管理器读取会访问系统服务，不再放在 Compose 首次组合阶段同步执行。
 * - 进入设置页时先渲染页面骨架，再把权限状态刷新回来，降低低端机或系统服务繁忙时的卡顿感。
 *
 * 参数说明：
 * - context：当前界面上下文，用于访问系统设置和通知管理器。
 */
private suspend fun readSettingsPermissionSnapshot(context: Context): SettingsPermissionSnapshot {
    return withContext(Dispatchers.IO) {
        SettingsPermissionSnapshot(
            hasNotificationAccess = PermissionStatusChecker.hasNotificationAccess(context),
            hasAppNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            hasAccessibilityAccess = PermissionStatusChecker.hasAccessibilityAccess(
                context = context,
                serviceClassName = ACCESSIBILITY_SERVICE_CLASS_NAME,
            ),
        )
    }
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
 * 打开 App 新版本下载地址。
 *
 * 用途说明：
 * - 把 APK 链接交给系统浏览器处理，避免 App 内下载、安装权限和 ROM 兼容问题。
 * - 仅允许 http/https 链接，避免服务端配置错误时拉起非预期协议。
 *
 * 参数说明：
 * - context：当前界面上下文，用于启动浏览器和展示失败提示。
 * - rawUrl：服务端返回的 APK 下载地址。
 *
 * 返回值说明：
 * - true：浏览器已成功拉起。
 * - false：链接为空、协议不支持或系统没有可处理的浏览器。
 */
private fun openAppUpdateDownloadInBrowser(
    context: Context,
    rawUrl: String,
): Boolean {
    val uri = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull()
    val scheme = uri?.scheme?.lowercase()
    if (uri == null || scheme !in setOf("http", "https")) {
        Toast.makeText(context, "下载链接尚未配置，请稍后再试。", Toast.LENGTH_LONG).show()
        return false
    }
    val opened = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        true
    }.getOrDefault(false)
    if (!opened) {
        Toast.makeText(context, "无法打开浏览器，请稍后重试。", Toast.LENGTH_LONG).show()
    }
    return opened
}

/**
 * 格式化 APK 文件大小。
 *
 * 用途说明：
 * - 让用户在浏览器下载前能预估流量消耗，尤其适配当前 4Mbps 服务器带宽场景。
 *
 * 参数说明：
 * - bytes：服务端返回的 APK 字节数。
 */
private fun formatApkSize(bytes: Long): String {
    if (bytes <= 0L) {
        return "未知"
    }
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 1.0) {
        "%.1f MB".format(Locale.US, megabytes)
    } else {
        "${(bytes / 1024L).coerceAtLeast(1L)} KB"
    }
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
 * - leadingIcon：行首图标；为空时保持纯文字行。
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
    leadingIcon: AutobookIconAsset? = null,
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
        modifier = rowModifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AutobookIcon(
                    asset = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (leadingIcon == null) 0.dp else 9.dp, end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = effectiveOnCheckedChange,
            // 设置页参考轻列表风格，开关稍微缩小，避免右侧控件压过文字和图标。
            modifier = Modifier.scale(0.78f),
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
        // 开启态使用更浅的棕色轨道，减少设置页右侧开关的视觉重量。
        checkedThumbColor = MaterialTheme.colorScheme.surface,
        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.74f),
        checkedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
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
 * 提取来源管理里的简短说明。
 *
 * 接收者说明：
 * - 当前设置页来源分组。
 */
private fun SettingsNotificationSource.toSourceSummary(): String = when (this) {
    SettingsNotificationSource.WECHAT -> "识别微信支付和微信收款相关通知"
    SettingsNotificationSource.ALIPAY -> "识别支付宝付款、收款和账单通知"
    SettingsNotificationSource.OTHER -> "识别银行短信等其它可解析来源"
}

/**
 * 提取设置页账号展示文本。
 *
 * 用途说明：
 * - ViewModel 当前返回的是完整说明句，这里只取邮箱主体，避免卡片里出现过长句子压坏布局。
 *
 * 接收者说明：
 * - 当前设置页 UI 状态。
 */
private fun SettingsUiState.authAccountDisplayText(): String {
    if (!authLoggedIn) {
        return "未登录"
    }
    return authSummary
        .removePrefix("已登录 ")
        .substringBefore("，")
        .ifBlank { authPhoneInput.trim() }
        .ifBlank { "已登录" }
}

/**
 * 提取支付页需要提醒用户填写的登录邮箱。
 *
 * 用途说明：
 * - 支付二维码页必须明确展示人工发放会员所需的账号邮箱，这里优先使用已登录摘要里的邮箱。
 * - 如果用户尚未登录，则退回到输入框里的邮箱草稿；仍然为空时显示“未登录”。
 *
 * 接收者说明：
 * - 当前设置页 UI 状态。
 */
private fun SettingsUiState.authPaymentEmailText(): String {
    val loggedInEmail = if (authLoggedIn) authAccountDisplayText() else ""
    return loggedInEmail
        .takeIf { email -> email.contains("@") }
        ?: authPhoneInput.trim().takeIf { email -> email.contains("@") }
        ?: "未登录"
}

/**
 * 把活动日期转换为中文短日期。
 *
 * 用途说明：
 * - 会员限时活动的开始和结束时间需要让用户看得懂，避免直接展示 ISO 日期显得生硬。
 *
 * 接收者说明：
 * - 当前需要展示的 `LocalDate`。
 */
private fun LocalDate.toChinaDateLabel(): String = "${year}年${monthValue}月${dayOfMonth}日"

/**
 * 获取会员活动倒计时使用的当前日期。
 *
 * 用途说明：
 * - 登录后优先使用服务端返回的时间计算剩余天数，减少手机本地时间被手动修改带来的显示误差。
 * - 未登录或服务端时间缺失时，才使用上海时区的本机日期做临时展示。
 *
 * 接收者说明：
 * - 当前设置页 UI 状态。
 */
private fun SettingsUiState.membershipPromotionToday(): LocalDate {
    val alignedServerTime = membershipServerTime?.let { serverTime ->
        val elapsedSinceRefresh = membershipServerRefreshedAt
            ?.let { refreshedAt -> System.currentTimeMillis() - refreshedAt }
            ?.coerceAtLeast(0L)
            ?: 0L
        // 用“服务端时间 + 本地流逝时间”估算当前服务端日期，避免倒计时一直停在刷新那一刻。
        serverTime + elapsedSinceRefresh
    }
    return alignedServerTime
        ?.let { serverTime -> Instant.ofEpochMilli(serverTime).atZone(MEMBERSHIP_PROMOTION_ZONE).toLocalDate() }
        ?: LocalDate.now(MEMBERSHIP_PROMOTION_ZONE)
}

/** 用途说明：用户反馈正文最小长度，和数据层校验保持一致。 */
private const val USER_FEEDBACK_MIN_LENGTH = 5

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
private const val SMS_CODE_LENGTH = 6
private const val USER_IMPROVEMENT_PLAN_SUMMARY = "开启后，有效反馈样本中的通知原文会被用于训练模型，我们保证您的数据不会泄露"
private val AUTH_EMAIL_REGEX = Regex("""^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$""", RegexOption.IGNORE_CASE)
