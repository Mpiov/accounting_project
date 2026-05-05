package com.autobook.cn.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.autobook.cn.app.ui.EntryEditorScreen
import com.autobook.cn.app.ui.LedgerWorkspaceScreen
import com.autobook.cn.app.ui.OverviewScreen
import com.autobook.cn.app.ui.PendingWorkspaceScreen
import com.autobook.cn.app.ui.SettingsWorkspaceScreen
import com.autobook.cn.app.viewmodel.EntryEditorUiState
import com.autobook.cn.app.viewmodel.OverviewUiState
import com.autobook.cn.app.viewmodel.SettingsUiState
import com.autobook.cn.feature.ledger.model.LedgerCalendarDayUiModel
import com.autobook.cn.feature.ledger.model.LedgerRangeFilter
import com.autobook.cn.feature.ledger.model.LedgerUiState
import com.autobook.cn.feature.onboarding.OnboardingScreen
import com.autobook.cn.feature.onboarding.OnboardingUiState
import com.autobook.cn.feature.pending.model.PendingUiState
import java.time.LocalDate
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

        composeRule.onNodeWithText("轻松记").assertIsDisplayed()
        composeRule.onNodeWithText("进入账本").assertIsDisplayed()
    }

    @Test
    fun overviewScreenRenders() {
        composeRule.setContent {
            OverviewScreen(
                state = OverviewUiState(pendingCount = 3),
                onRangeChange = {},
                onOpenPending = {},
                onOpenAllRecords = {},
                onOpenBudgetSettings = {},
                onOpenEntry = {},
                onCreateEntry = {},
            )
        }

        composeRule.onNodeWithText("总览").assertIsDisplayed()
        composeRule.onNodeWithText("有 3 条待确认记录").assertIsDisplayed()
    }

    @Test
    fun ledgerWorkspaceScreenRenders() {
        composeRule.setContent {
            LedgerWorkspaceScreen(
                state = LedgerUiState(
                    selectedRange = LedgerRangeFilter.CALENDAR,
                    calendarMonthLabel = "2026年4月",
                    calendarDays = List(42) { index ->
                        LedgerCalendarDayUiModel(
                            date = LocalDate.of(2026, 4, 1).plusDays(index.toLong()),
                            label = ((index % 30) + 1).toString(),
                            isCurrentMonth = true,
                            hasEntries = index % 3 == 0,
                            isSelected = index == 12,
                            isToday = index == 12,
                        )
                    },
                ),
                onBack = {},
                onSearchChange = {},
                onPreviousMonthClick = {},
                onNextMonthClick = {},
                onCalendarDateClick = { _ -> },
                onEntryClick = {},
            )
        }

        composeRule.onNodeWithText("2026年4月").assertIsDisplayed()
        composeRule.onNodeWithText("账单时间线").assertIsDisplayed()
    }

    @Test
    fun pendingWorkspaceScreenRenders() {
        composeRule.setContent {
            PendingWorkspaceScreen(
                state = PendingUiState(),
                onConfirm = {},
                onEdit = {},
                onIgnore = {},
                onOpenLedger = {},
                onCreateEntry = {},
            )
        }

        composeRule.onNodeWithText("待确认").assertIsDisplayed()
    }

    @Test
    fun settingsWorkspaceScreenRenders() {
        composeRule.setContent {
            SettingsWorkspaceScreen(
                state = SettingsUiState(),
                homeResetKey = 0,
                onAutoAccountingChanged = {},
                onCloudAnalysisChanged = {},
                onAuthPhoneChanged = {},
                onAuthCodeChanged = {},
                onRequestLoginCode = {},
                onLoginOrRegister = {},
                onLogout = {},
                onStartYearlyMembership = {},
                onRefreshMembership = {},
                onRefreshMembershipAfterPayment = {},
                onPullCloudModel = {},
                onUserImprovementPlanChanged = {},
                onCaptureEnabledChanged = {},
                onNotificationRetentionChanged = {},
                onScreenshotRetentionChanged = {},
                onSourceEnabledChanged = { _, _ -> },
            )
        }

        composeRule.onNodeWithText("记账能力").assertIsDisplayed()
        composeRule.onNodeWithText("会员中心").assertIsDisplayed()
    }

    @Test
    fun entryEditorRenders() {
        composeRule.setContent {
            EntryEditorScreen(
                state = EntryEditorUiState(),
                onBack = {},
                onAmountChange = {},
                onEntryTimeChange = {},
                onCategoryChange = {},
                onNoteChange = {},
                onDirectionChange = {},
                onApplyTemplate = {},
                onImportFromImage = {},
                onSelectImageImportCandidate = {},
                onSave = {},
                onDelete = {},
            )
        }

        composeRule.onNodeWithText("新建手动记账").assertIsDisplayed()
        composeRule.onNodeWithText("快捷模板").assertIsDisplayed()
    }
}
