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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.autobook.cn.app.ui.ChartDashboardScreen
import com.autobook.cn.app.ui.LedgerWorkspaceScreen
import com.autobook.cn.app.ui.OverviewScreen
import com.autobook.cn.app.ui.PendingWorkspaceScreen
import com.autobook.cn.app.ui.QuickActionSheet
import com.autobook.cn.app.ui.SettingsWorkspaceScreen
import com.autobook.cn.app.viewmodel.ChartViewModel
import com.autobook.cn.app.viewmodel.LedgerViewModel
import com.autobook.cn.app.viewmodel.PendingViewModel
import com.autobook.cn.app.viewmodel.SettingsViewModel
import com.autobook.cn.core.model.MoneyDirection

/**
 * 主壳层负责承载一级页面与底部导航。
 *
 * 参数说明：
 * - rootNavController：根导航控制器，用于打开编辑页等二级页面。
 * - modifier：外层修饰符，便于由根导航统一注入。
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
                    Icon(imageVector = Icons.Outlined.Add, contentDescription = "记一笔")
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
                val viewModel: LedgerViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    // 说明：账本页默认从“今日”进入，更符合查当天账单的高频习惯。
                    viewModel.setRange(com.autobook.cn.feature.ledger.LedgerRangeFilter.TODAY)
                }

                LedgerWorkspaceScreen(
                    state = state,
                    onSearchChange = viewModel::setSearchQuery,
                    onSearchSubmit = { query ->
                        if (query.isNotBlank()) {
                            rootNavController.navigate(AutobookDestination.SearchResults.createRoute(query))
                        }
                    },
                    onRangeChange = viewModel::setRange,
                    onPreviousMonthClick = viewModel::showPreviousMonth,
                    onNextMonthClick = viewModel::showNextMonth,
                    onCalendarDateClick = viewModel::selectCalendarDate,
                    onEntryClick = { ledgerId ->
                        rootNavController.navigate(AutobookDestination.EntryEdit.createRoute(ledgerId))
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
                rootNavController.navigate(AutobookDestination.EntryCreate.createRoute(direction = MoneyDirection.EXPENSE))
            },
            onIncomeClick = {
                showQuickActionSheet = false
                rootNavController.navigate(AutobookDestination.EntryCreate.createRoute(direction = MoneyDirection.INCOME))
            },
        )
    }
}

/** 一级页面枚举同时绑定底部导航展示信息。 */
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
        /** 根据当前导航节点匹配正在展示的一级页面。 */
        fun match(destination: NavDestination?): MainShellTab? {
            return entries.firstOrNull { tab ->
                destination?.hierarchy?.any { it.route == tab.route } == true
            }
        }
    }
}

/** 切换底部 Tab 时保留各页滚动位置和内部状态。 */
private fun NavHostController.navigateToMainTab(tab: MainShellTab) {
    navigate(tab.route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
    }
}
