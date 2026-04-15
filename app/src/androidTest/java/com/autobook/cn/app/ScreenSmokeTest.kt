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
import com.autobook.cn.app.viewmodel.SettingsUiState
import com.autobook.cn.feature.ledger.LedgerUiState
import com.autobook.cn.feature.onboarding.OnboardingScreen
import com.autobook.cn.feature.onboarding.OnboardingUiState
import com.autobook.cn.feature.pending.PendingUiState
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

        composeRule.onNodeWithText("Autobook").assertIsDisplayed()
        composeRule.onNodeWithText("进入账本").assertIsDisplayed()
    }

    @Test
    fun overviewScreenRenders() {
        composeRule.setContent {
            OverviewScreen(
                state = LedgerUiState(),
                onRangeChange = {},
                onOpenAllRecords = {},
                onOpenBudgetSettings = {},
                onOpenEntry = {},
                onCreateEntry = {},
            )
        }

        composeRule.onNodeWithText("总览").assertIsDisplayed()
    }

    @Test
    fun ledgerWorkspaceScreenRenders() {
        composeRule.setContent {
            LedgerWorkspaceScreen(
                state = LedgerUiState(
                    selectedRange = com.autobook.cn.feature.ledger.LedgerRangeFilter.CALENDAR,
                    calendarMonthLabel = "2026年4月",
                    selectedDateSummary = "4月13日 星期一 · 0 条记录",
                    calendarDays = List(42) { index ->
                        com.autobook.cn.feature.ledger.LedgerCalendarDayUiModel(
                            date = LocalDate.of(2026, 4, 1).plusDays(index.toLong()),
                            label = ((index % 30) + 1).toString(),
                            isCurrentMonth = true,
                            hasEntries = index % 3 == 0,
                            isSelected = index == 12,
                            isToday = index == 12,
                        )
                    },
                ),
                onSearchChange = {},
                onSearchSubmit = {},
                onRangeChange = {},
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
                onAutoAccountingChanged = {},
                onCloudAnalysisChanged = {},
                onCaptureEnabledChanged = {},
                onOcrEnabledChanged = {},
                onLocalLlmConfidenceBoostChanged = {},
                onSelectedLocalModelChanged = {},
                onDownloadSelectedLocalModel = {},
                onRefreshLocalModelState = {},
                onNotificationRetentionChanged = {},
                onScreenshotRetentionChanged = {},
                onSourceEnabledChanged = { _, _ -> },
                onSaveCurrentMonthBudget = {},
                onLoadExportPreview = {},
            )
        }

        composeRule.onNodeWithText("记账能力").assertIsDisplayed()
        composeRule.onNodeWithText("账本导出").assertIsDisplayed()
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
                onApplyTemplate = {},
                onSave = {},
                onDelete = {},
            )
        }

        composeRule.onNodeWithText("新建手动记账").assertIsDisplayed()
        composeRule.onNodeWithText("快捷模板").assertIsDisplayed()
    }
}
