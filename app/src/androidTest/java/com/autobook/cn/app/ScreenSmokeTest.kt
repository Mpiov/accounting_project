package com.autobook.cn.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.autobook.cn.app.ui.EntryEditorScreen
import com.autobook.cn.app.ui.SettingsScreen
import com.autobook.cn.app.viewmodel.EntryEditorUiState
import com.autobook.cn.app.viewmodel.SettingsUiState
import com.autobook.cn.feature.ledger.LedgerScreen
import com.autobook.cn.feature.ledger.LedgerUiState
import com.autobook.cn.feature.onboarding.OnboardingScreen
import com.autobook.cn.feature.onboarding.OnboardingUiState
import com.autobook.cn.feature.pending.PendingScreen
import com.autobook.cn.feature.pending.PendingUiState
import org.junit.Rule
import org.junit.Test

/** 基础烟雾测试，确保主要页面至少能够正常渲染。 */
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
                onSearchChange = {},
                onRangeChange = {},
                onPendingClick = {},
                onSettingsClick = {},
                onAddClick = {},
                onEntryClick = {},
            )
        }

        composeRule.onNodeWithText("账本").assertIsDisplayed()
    }

    @Test
    fun pendingScreenRenders() {
        composeRule.setContent {
            PendingScreen(
                state = PendingUiState(),
                onBack = {},
                onConfirm = {},
                onEdit = {},
                onIgnore = {},
                onFalsePositive = {},
            )
        }

        composeRule.onNodeWithText("待确认").assertIsDisplayed()
    }

    @Test
    fun settingsScreenRenders() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                onBack = {},
                onAutoAccountingChanged = {},
                onCloudAnalysisChanged = {},
                onCaptureEnabledChanged = {},
                onOcrEnabledChanged = {},
                onNotificationRetentionChanged = {},
                onScreenshotRetentionChanged = { },
                onSourceEnabledChanged = { _, _ -> },
                onLoadExportPreview = {},
            )
        }

        composeRule.onNodeWithText("设置").assertIsDisplayed()
    }

    @Test
    fun entryEditorRenders() {
        composeRule.setContent {
            EntryEditorScreen(
                state = EntryEditorUiState(),
                onBack = {},
                onAmountChange = {},
                onMerchantChange = {},
                onCategoryChange = {},
                onNoteChange = {},
                onOrderIdChange = {},
                onDirectionChange = {},
                onSave = {},
                onDelete = {},
            )
        }

        composeRule.onNodeWithText("新建手动记账").assertIsDisplayed()
    }
}
