package com.autobook.cn.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.autobook.cn.app.ui.BudgetSettingsScreen
import com.autobook.cn.app.ui.EntryEditorScreen
import com.autobook.cn.app.ui.LedgerWorkspaceScreen
import com.autobook.cn.app.ui.RecordsTimelineScreen
import com.autobook.cn.app.viewmodel.EntryEditorViewModel
import com.autobook.cn.app.viewmodel.LedgerMonthDetailViewModel
import com.autobook.cn.app.viewmodel.OnboardingViewModel
import com.autobook.cn.app.viewmodel.RecordsBrowserViewModel
import com.autobook.cn.app.viewmodel.SettingsViewModel
import com.autobook.cn.core.common.SettingsIntents
import com.autobook.cn.feature.onboarding.OnboardingScreen

/** 应用主导航图，负责把各个页面和对应 ViewModel 串起来。 */
@Composable
fun AutobookApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = AutobookDestination.Onboarding.route,
    ) {
        composable(AutobookDestination.Onboarding.route) {
            val viewModel: OnboardingViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LifecycleResumeEffect(Unit) {
                // 每次回到前台都刷新权限状态，避免用户从系统设置页返回后界面不更新。
                viewModel.refreshPermissionStatus()
                onPauseOrDispose { }
            }

            LaunchedEffect(state.onboardingCompleted) {
                if (state.onboardingCompleted) {
                    navController.navigate(AutobookDestination.MainShell.route) {
                        popUpTo(AutobookDestination.Onboarding.route) { inclusive = true }
                    }
                }
            }

            OnboardingScreen(
                state = state,
                onOpenNotificationSettings = { context.startActivity(SettingsIntents.notificationAccess()) },
                onOpenAccessibilitySettings = { context.startActivity(SettingsIntents.accessibilityAccess()) },
                onToggleAutoAccounting = viewModel::setAutoAccountingEnabled,
                onToggleCloudAnalysis = viewModel::setCloudAnalysisEnabled,
                onContinue = viewModel::completeOnboarding,
            )
        }
        composable(AutobookDestination.MainShell.route) {
            MainShell(rootNavController = navController)
        }
        composable(AutobookDestination.BudgetSettings.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            BudgetSettingsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onSaveBudget = viewModel::saveCurrentMonthBudget,
            )
        }
        composable(
            route = AutobookDestination.LedgerMonthDetail.route,
            arguments = listOf(
                navArgument("yearMonth") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            val viewModel: LedgerMonthDetailViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LedgerWorkspaceScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onSearchChange = viewModel::updateSearchQuery,
                onPreviousMonthClick = viewModel::showPreviousMonth,
                onNextMonthClick = viewModel::showNextMonth,
                onCalendarDateClick = viewModel::selectCalendarDate,
                onEntryClick = { ledgerId ->
                    navController.navigate(AutobookDestination.EntryEdit.createRoute(ledgerId))
                },
            )
        }
        composable(AutobookDestination.RecordsAll.route) {
            val viewModel: RecordsBrowserViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            RecordsTimelineScreen(
                title = state.title,
                summary = state.summary,
                items = state.items,
                onSelectEntry = { ledgerId ->
                    navController.navigate(AutobookDestination.EntryEdit.createRoute(ledgerId))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = AutobookDestination.SearchResults.route,
            arguments = listOf(
                navArgument("query") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            val viewModel: RecordsBrowserViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            var selectedEntryId by remember(state.items) {
                mutableLongStateOf(state.items.firstOrNull()?.id ?: 0L)
            }

            LaunchedEffect(state.items) {
                if (selectedEntryId == 0L) {
                    selectedEntryId = state.items.firstOrNull()?.id ?: 0L
                }
            }

            RecordsTimelineScreen(
                title = state.title,
                summary = state.summary,
                items = state.items,
                selectedEntryId = selectedEntryId.takeIf { it != 0L },
                onSelectEntry = { ledgerId -> selectedEntryId = ledgerId },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = AutobookDestination.EntryCreate.route,
            arguments = listOf(
                navArgument("direction") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("templateId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            val viewModel: EntryEditorViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            EntryEditorRoute(
                state = state,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = AutobookDestination.EntryEdit.route,
            arguments = listOf(navArgument("ledgerId") { type = NavType.StringType }),
        ) {
            val viewModel: EntryEditorViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            EntryEditorRoute(
                state = state,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = AutobookDestination.PendingResolve.route,
            arguments = listOf(navArgument("pendingId") { type = NavType.StringType }),
        ) {
            val viewModel: EntryEditorViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            EntryEditorRoute(
                state = state,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun EntryEditorRoute(
    state: com.autobook.cn.app.viewmodel.EntryEditorUiState,
    viewModel: EntryEditorViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(state.finished) {
        // 保存或删除完成后消费一次性状态，再统一返回上一个页面。
        if (state.finished) {
            viewModel.consumeFinished()
            onBack()
        }
    }

    EntryEditorScreen(
        state = state,
        onBack = onBack,
        onAmountChange = viewModel::updateAmount,
        onMerchantChange = viewModel::updateMerchant,
        onCategoryChange = viewModel::updateCategory,
        onNoteChange = viewModel::updateNote,
        onOrderIdChange = viewModel::updateOrderId,
        onDirectionChange = viewModel::updateDirection,
        onApplyTemplate = viewModel::applyTemplate,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}
