package com.autobook.cn.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.autobook.cn.app.ui.AutobookIcon
import com.autobook.cn.app.ui.AutobookIconAsset
import com.autobook.cn.app.ui.ChartDashboardScreen
import com.autobook.cn.app.ui.LedgerBoardScreen
import com.autobook.cn.app.ui.OverviewScreen
import com.autobook.cn.app.ui.PendingWorkspaceScreen
import com.autobook.cn.app.ui.SettingsWorkspaceScreen
import com.autobook.cn.app.viewmodel.ChartViewModel
import com.autobook.cn.app.viewmodel.LedgerBoardMode
import com.autobook.cn.app.viewmodel.LedgerBoardViewModel
import com.autobook.cn.app.viewmodel.LedgerViewModel
import com.autobook.cn.app.viewmodel.PendingBadgeViewModel
import com.autobook.cn.app.viewmodel.PendingViewModel
import com.autobook.cn.app.viewmodel.SettingsViewModel
import com.autobook.cn.core.model.MoneyDirection

/**
 * 用途说明：
 * - 主壳层负责承载一级页面与底部导航。
 * - 本轮调整后，账本 Tab 改成月份总览页，再由月份总览跳入月份详情页。
 *
 * 参数说明：
 * - rootNavController：根导航控制器，用于打开二级详情页。
 * - modifier：外层修饰符，便于根层统一注入布局约束。
 */
@Composable
fun MainShell(
    rootNavController: NavHostController,
    modifier: Modifier = Modifier,
) {
    // 主壳层只订阅待确认数量，避免每个一级页面都常驻完整待确认列表和通知原文。
    val pendingBadgeViewModel: PendingBadgeViewModel = hiltViewModel()
    val pendingCount by pendingBadgeViewModel.pendingCount.collectAsStateWithLifecycle()
    val shellNavController = rememberNavController()
    val navBackStackEntry by shellNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentTab = MainShellTab.match(currentDestination) ?: MainShellTab.Overview
    var settingsHomeResetKey by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // 主壳层使用不透明背景，设置页快速滚动时不再透出底层内容参与合成。
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            /**
             * 底部导航统一包一层暖色面板。
             *
             * 用途说明：
             * - 默认 `NavigationBar` 在 Material3 下容易带出偏灰的系统表面色，和当前暖纸主题不一致。
             * - 这里显式改成暖白底 + 细描边，让导航条更像浮在纸面上的工具条。
             */
            Surface(
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                // 底部导航使用不透明白底，避免设置页快速滑动时持续触发半透明混合。
                color = MaterialTheme.colorScheme.surface,
                border = com.autobook.cn.app.ui.autobookCardBorder(),
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    MainShellTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = {
                                if (tab == MainShellTab.Settings && currentTab != MainShellTab.Settings) {
                                    // 从其它底部页回到设置页时，清掉设置页内部二级页状态，让用户重新看到设置首页。
                                    settingsHomeResetKey += 1
                                }
                                shellNavController.navigateToMainTab(tab)
                            },
                            icon = {
                                MainShellNavigationIcon(
                                    tab = tab,
                                    pendingCount = pendingCount,
                                )
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentTab != MainShellTab.Settings) {
                FloatingActionButton(
                    onClick = {
                        rootNavController.navigate(
                            AutobookDestination.EntryCreate.createRoute(direction = MoneyDirection.EXPENSE),
                        )
                    },
                ) {
                    AutobookIcon(
                        asset = AutobookIconAsset.Add,
                        contentDescription = "记一笔",
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = shellNavController,
            startDestination = AutobookDestination.MainOverview.route,
            enterTransition = { AutobookNavTransitions.enter() },
            exitTransition = { AutobookNavTransitions.exit() },
            popEnterTransition = { AutobookNavTransitions.popEnter() },
            popExitTransition = { AutobookNavTransitions.popExit() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(AutobookDestination.MainOverview.route) {
                val viewModel: LedgerViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                OverviewScreen(
                    state = state,
                    onRangeChange = viewModel::setRange,
                    onOpenPending = { shellNavController.navigateToMainTab(MainShellTab.Pending) },
                    onOpenAllRecords = { rootNavController.navigate(AutobookDestination.RecordsAll.route) },
                    onOpenBudgetSettings = { rootNavController.navigateToBudgetSettings() },
                    onOpenEntry = { ledgerId ->
                        rootNavController.navigate(AutobookDestination.EntryEdit.createRoute(ledgerId))
                    },
                    onCreateEntry = {
                        rootNavController.navigate(
                            AutobookDestination.EntryCreate.createRoute(direction = MoneyDirection.EXPENSE),
                        )
                    },
                )
            }
            composable(AutobookDestination.MainLedger.route) {
                val viewModel: LedgerBoardViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                LedgerBoardScreen(
                    state = state,
                    onSelectYear = viewModel::selectYear,
                    onSelectMode = viewModel::selectMode,
                    onOpenMonth = { yearMonth ->
                        rootNavController.navigate(AutobookDestination.LedgerMonthDetail.createRoute(yearMonth))
                    },
                    onSelectYearRow = { year ->
                        viewModel.selectYear(year)
                        viewModel.selectMode(LedgerBoardMode.MONTH)
                    },
                )
            }
            composable(AutobookDestination.MainCharts.route) {
                val viewModel: ChartViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                ChartDashboardScreen(state = state)
            }
            composable(AutobookDestination.MainPending.route) {
                val viewModel: PendingViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                PendingWorkspaceScreen(
                    state = state,
                    onConfirm = viewModel::confirmPending,
                    onEdit = { pendingId ->
                        rootNavController.navigate(AutobookDestination.PendingResolve.createRoute(pendingId))
                    },
                    onOpenLedger = { shellNavController.navigateToMainTab(MainShellTab.Ledger) },
                    onCreateEntry = {
                        rootNavController.navigate(
                            AutobookDestination.EntryCreate.createRoute(direction = MoneyDirection.EXPENSE),
                        )
                    },
                )
            }
            composable(AutobookDestination.MainSettings.route) {
                val viewModel: SettingsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                SettingsWorkspaceScreen(
                    state = state,
                    homeResetKey = settingsHomeResetKey,
                    onAutoAccountingChanged = viewModel::setAutoAccountingEnabled,
                    onCloudAnalysisChanged = viewModel::setCloudAnalysisEnabled,
                    onAuthPhoneChanged = viewModel::setAuthPhoneInput,
                    onAuthCodeChanged = viewModel::setAuthCodeInput,
                    onRequestLoginCode = viewModel::requestLoginCode,
                    onLoginOrRegister = viewModel::loginOrRegisterWithCode,
                    onLogout = viewModel::logout,
                    onCloudLearningChanged = viewModel::setCloudLearningEnabled,
                    onRefreshMembership = viewModel::refreshMembership,
                    onStartMonthlyMembership = viewModel::startMonthlyMembershipPayment,
                    onStartYearlyMembership = viewModel::startYearlyMembershipPayment,
                    onSyncCloudLearning = viewModel::syncCloudLearningNow,
                    onPullCloudModel = viewModel::pullCloudModelNow,
                    onCaptureEnabledChanged = viewModel::setCaptureEnabled,
                    onNotificationRetentionChanged = viewModel::setNotificationRetentionDays,
                    onScreenshotRetentionChanged = viewModel::setScreenshotRetentionDays,
                    onSourceEnabledChanged = viewModel::setSourceEnabled,
                )
            }
        }
    }

}

/**
 * 用途说明：
 * - 底部导航图标统一封装在这里，便于只给“待确认”Tab 加数量徽标。
 *
 * 参数说明：
 * - tab：当前正在渲染的底部导航项。
 * - pendingCount：当前待确认记录数量，用于决定是否展示徽标。
 */
@Composable
private fun MainShellNavigationIcon(
    tab: MainShellTab,
    pendingCount: Int,
) {
    if (tab != MainShellTab.Pending || pendingCount <= 0) {
        AutobookIcon(asset = tab.icon, contentDescription = tab.label)
        return
    }

    BadgedBox(
        badge = {
            Badge {
                // 徽标文案做上限裁剪，避免数量过大时挤压底部导航布局。
                Text(text = pendingBadgeLabel(pendingCount))
            }
        },
    ) {
        AutobookIcon(asset = tab.icon, contentDescription = tab.label)
    }
}

/**
 * 用途说明：
 * - 一级页面枚举，同时绑定底部导航展示信息。
 */
private enum class MainShellTab(
    val route: String,
    val label: String,
    val icon: AutobookIconAsset,
) {
    Overview(
        route = AutobookDestination.MainOverview.route,
        label = "总览",
        icon = AutobookIconAsset.Overview,
    ),
    Ledger(
        route = AutobookDestination.MainLedger.route,
        label = "账本",
        icon = AutobookIconAsset.Ledger,
    ),
    Charts(
        route = AutobookDestination.MainCharts.route,
        label = "图表",
        icon = AutobookIconAsset.Charts,
    ),
    Pending(
        route = AutobookDestination.MainPending.route,
        label = "待确认",
        icon = AutobookIconAsset.Pending,
    ),
    Settings(
        route = AutobookDestination.MainSettings.route,
        label = "设置",
        icon = AutobookIconAsset.Settings,
    ),
    ;

    companion object {
        /**
         * 用途说明：
         * - 根据当前导航节点匹配正在展示的一级页面。
         *
         * 参数说明：
         * - destination：当前导航节点。
         */
        fun match(destination: NavDestination?): MainShellTab? {
            return entries.firstOrNull { tab ->
                destination?.hierarchy?.any { it.route == tab.route } == true
            }
        }
    }
}

/**
 * 用途说明：
 * - 切换底部 Tab 时保留各页面滚动位置与内部状态。
 *
 * 参数说明：
 * - tab：目标一级页面。
 */
private fun NavHostController.navigateToMainTab(tab: MainShellTab) {
    if (currentDestination?.hierarchy?.any { it.route == tab.route } == true) {
        return
    }
    navigate(tab.route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
    }
}

/**
 * 用途说明：
 * - 进入预算设置页时使用 `singleTop`，避免用户快速连点把同一路由重复压栈。
 */
private fun NavHostController.navigateToBudgetSettings() {
    navigate(AutobookDestination.BudgetSettings.route) {
        launchSingleTop = true
    }
}

/**
 * 用途说明：
 * - 将待确认数量格式化为适合底部导航徽标的短文本。
 *
 * 参数说明：
 * - pendingCount：待确认记录总数。
 */
private fun pendingBadgeLabel(pendingCount: Int): String {
    return if (pendingCount > 99) "99+" else pendingCount.toString()
}
