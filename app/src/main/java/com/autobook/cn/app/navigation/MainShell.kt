package com.autobook.cn.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.autobook.cn.app.ui.ChartDashboardScreen
import com.autobook.cn.app.ui.LedgerBoardScreen
import com.autobook.cn.app.ui.OverviewScreen
import com.autobook.cn.app.ui.PendingWorkspaceScreen
import com.autobook.cn.app.ui.QuickActionSheet
import com.autobook.cn.app.ui.SettingsWorkspaceScreen
import com.autobook.cn.app.viewmodel.ChartViewModel
import com.autobook.cn.app.viewmodel.LedgerBoardMode
import com.autobook.cn.app.viewmodel.LedgerBoardViewModel
import com.autobook.cn.app.viewmodel.LedgerViewModel
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
    val shellNavController = rememberNavController()
    val navBackStackEntry by shellNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentTab = MainShellTab.match(currentDestination) ?: MainShellTab.Overview
    var showQuickActionSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                MainShellTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { shellNavController.navigateToMainTab(tab) },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab != MainShellTab.Settings) {
                FloatingActionButton(
                    onClick = { showQuickActionSheet = true },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "记一笔",
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = shellNavController,
            startDestination = AutobookDestination.MainOverview.route,
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
                    onOpenAllRecords = { rootNavController.navigate(AutobookDestination.RecordsAll.route) },
                    onOpenBudgetSettings = { rootNavController.navigate(AutobookDestination.BudgetSettings.route) },
                    onOpenEntry = { ledgerId ->
                        rootNavController.navigate(AutobookDestination.EntryEdit.createRoute(ledgerId))
                    },
                    onCreateEntry = { rootNavController.navigate(AutobookDestination.EntryCreate.createRoute()) },
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
                    onIgnore = viewModel::ignorePending,
                    onOpenLedger = { shellNavController.navigateToMainTab(MainShellTab.Ledger) },
                    onCreateEntry = { showQuickActionSheet = true },
                )
            }
            composable(AutobookDestination.MainSettings.route) {
                val viewModel: SettingsViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                SettingsWorkspaceScreen(
                    state = state,
                    onAutoAccountingChanged = viewModel::setAutoAccountingEnabled,
                    onCloudAnalysisChanged = viewModel::setCloudAnalysisEnabled,
                    onCaptureEnabledChanged = viewModel::setCaptureEnabled,
                    onOcrEnabledChanged = viewModel::setOcrEnabled,
                    onLocalLlmConfidenceBoostChanged = viewModel::setLocalLlmConfidenceBoostEnabled,
                    onSelectedLocalModelChanged = viewModel::setSelectedLocalModelId,
                    onDownloadSelectedLocalModel = viewModel::downloadSelectedLocalModel,
                    onRefreshLocalModelState = viewModel::refreshLocalModelState,
                    onNotificationRetentionChanged = viewModel::setNotificationRetentionDays,
                    onScreenshotRetentionChanged = viewModel::setScreenshotRetentionDays,
                    onSourceEnabledChanged = viewModel::setSourceEnabled,
                    onSaveCurrentMonthBudget = viewModel::saveCurrentMonthBudget,
                    onLoadExportPreview = viewModel::loadExportPreview,
                )
            }
        }
    }

    if (showQuickActionSheet) {
        QuickActionSheet(
            onDismiss = { showQuickActionSheet = false },
            onExpenseClick = {
                showQuickActionSheet = false
                rootNavController.navigate(
                    AutobookDestination.EntryCreate.createRoute(direction = MoneyDirection.EXPENSE),
                )
            },
            onIncomeClick = {
                showQuickActionSheet = false
                rootNavController.navigate(
                    AutobookDestination.EntryCreate.createRoute(direction = MoneyDirection.INCOME),
                )
            },
        )
    }
}

/**
 * 用途说明：
 * - 一级页面枚举，同时绑定底部导航展示信息。
 */
private enum class MainShellTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Overview(
        route = AutobookDestination.MainOverview.route,
        label = "总览",
        icon = Icons.Outlined.ReceiptLong,
    ),
    Ledger(
        route = AutobookDestination.MainLedger.route,
        label = "账本",
        icon = Icons.Outlined.BarChart,
    ),
    Charts(
        route = AutobookDestination.MainCharts.route,
        label = "图表",
        icon = Icons.Outlined.Timeline,
    ),
    Pending(
        route = AutobookDestination.MainPending.route,
        label = "待确认",
        icon = Icons.Outlined.PendingActions,
    ),
    Settings(
        route = AutobookDestination.MainSettings.route,
        label = "设置",
        icon = Icons.Outlined.Settings,
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
    navigate(tab.route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
    }
}
