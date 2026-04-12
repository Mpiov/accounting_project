package com.autobook.cn.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.autobook.cn.app.ui.EntryEditorScreen
import com.autobook.cn.app.ui.SettingsScreen
import com.autobook.cn.app.viewmodel.EntryEditorViewModel
import com.autobook.cn.app.viewmodel.LedgerViewModel
import com.autobook.cn.app.viewmodel.OnboardingViewModel
import com.autobook.cn.app.viewmodel.PendingViewModel
import com.autobook.cn.app.viewmodel.SettingsViewModel
import com.autobook.cn.core.common.SettingsIntents
import com.autobook.cn.feature.ledger.LedgerScreen
import com.autobook.cn.feature.onboarding.OnboardingScreen
import com.autobook.cn.feature.pending.PendingScreen

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
                    navController.navigate(AutobookDestination.Ledger.route) {
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
        composable(AutobookDestination.Ledger.route) {
            val viewModel: LedgerViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LedgerScreen(
                state = state,
                onSearchChange = viewModel::setSearchQuery,
                onRangeChange = viewModel::setRange,
                onPendingClick = { navController.navigate(AutobookDestination.Pending.route) },
                onSettingsClick = { navController.navigate(AutobookDestination.Settings.route) },
                onAddClick = { navController.navigate(AutobookDestination.EntryCreate.route) },
                onEntryClick = { entryId ->
                    navController.navigate(AutobookDestination.EntryEdit.createRoute(entryId))
                },
            )
        }
        composable(AutobookDestination.Pending.route) {
            val viewModel: PendingViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            PendingScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onConfirm = viewModel::confirmPending,
                onEdit = { pendingId ->
                    navController.navigate(AutobookDestination.PendingResolve.createRoute(pendingId))
                },
                onIgnore = viewModel::ignorePending,
                onFalsePositive = viewModel::markFalsePositive,
            )
        }
        composable(AutobookDestination.Settings.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            SettingsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onAutoAccountingChanged = viewModel::setAutoAccountingEnabled,
                onCloudAnalysisChanged = viewModel::setCloudAnalysisEnabled,
                onCaptureEnabledChanged = viewModel::setCaptureEnabled,
                onOcrEnabledChanged = viewModel::setOcrEnabled,
                onNotificationRetentionChanged = viewModel::setNotificationRetentionDays,
                onScreenshotRetentionChanged = viewModel::setScreenshotRetentionDays,
                onSourceEnabledChanged = viewModel::setSourceEnabled,
                onLoadExportPreview = viewModel::loadExportPreview,
            )
        }
        composable(AutobookDestination.EntryCreate.route) {
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
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}
