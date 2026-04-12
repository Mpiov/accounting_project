package com.autobook.cn.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.autobook.cn.app.viewmodel.LedgerViewModel
import com.autobook.cn.app.viewmodel.OnboardingViewModel
import com.autobook.cn.app.viewmodel.PendingViewModel
import com.autobook.cn.core.common.SettingsIntents
import com.autobook.cn.feature.ledger.LedgerScreen
import com.autobook.cn.feature.onboarding.OnboardingScreen
import com.autobook.cn.feature.pending.PendingScreen

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
                onPendingClick = { navController.navigate(AutobookDestination.Pending.route) },
            )
        }
        composable(AutobookDestination.Pending.route) {
            val viewModel: PendingViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            PendingScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onConfirm = viewModel::confirmPending,
                onIgnore = viewModel::ignorePending,
            )
        }
    }
}
