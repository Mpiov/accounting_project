package com.autobook.cn.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.autobook.cn.feature.ledger.LedgerScreen
import com.autobook.cn.feature.ledger.LedgerUiState
import com.autobook.cn.feature.onboarding.OnboardingScreen
import com.autobook.cn.feature.onboarding.OnboardingUiState
import com.autobook.cn.feature.pending.PendingScreen
import com.autobook.cn.feature.pending.PendingUiState
import org.junit.Rule
import org.junit.Test

class ScreenSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun onboardingScreenRenders() {
        composeRule.setContent {
            OnboardingScreen(
                state = OnboardingUiState(),
                onOpenNotificationSettings = {},
                onOpenAccessibilitySettings = {},
                onToggleAutoAccounting = {},
                onToggleCloudAnalysis = {},
                onContinue = {},
            )
        }

        composeRule.onNodeWithText("Autobook").assertIsDisplayed()
        composeRule.onNodeWithText("进入账本").assertIsDisplayed()
    }

    @Test
    fun ledgerScreenRenders() {
        composeRule.setContent {
            LedgerScreen(
                state = LedgerUiState(),
                onPendingClick = {},
            )
        }

        composeRule.onNodeWithText("账本首页").assertIsDisplayed()
    }

    @Test
    fun pendingScreenRenders() {
        composeRule.setContent {
            PendingScreen(
                state = PendingUiState(),
                onBack = {},
                onConfirm = {},
                onIgnore = {},
            )
        }

        composeRule.onNodeWithText("待确认").assertIsDisplayed()
    }
}
