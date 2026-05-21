package com.autobook.cn.app.viewmodel

import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.PendingEntry
import com.autobook.cn.feature.pending.model.PendingUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PendingViewModelSupportTest {
    /**
     * 用途说明：
     * - 锁定待确认页默认状态为加载中。
     * - Room 首次返回前不能展示空状态，否则真实存在待确认时会先闪“无待确认”。
     */
    @Test
    fun pendingUiStateDefaultsToLoading() {
        val state = PendingUiState()

        assertTrue(state.isLoading)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun toPendingItemUiModelKeepsDirectConfirmForKnownDirection() {
        val item = testPendingEntry(direction = MoneyDirection.EXPENSE)
            .toPendingItemUiModel(rawNotificationText = null)

        assertTrue(item.canDirectConfirm)
        assertTrue(item.isDirectionKnown)
        assertTrue(item.amount.startsWith("-"))
        assertEquals("确认入账", item.primaryActionLabel)
        assertNull(item.directionHint)
    }

    @Test
    fun toPendingItemUiModelPrefixesIncomeAmountWithPlusSign() {
        val item = testPendingEntry(direction = MoneyDirection.INCOME)
            .toPendingItemUiModel(rawNotificationText = null)

        assertTrue(item.isIncome)
        assertTrue(item.amount.startsWith("+"))
    }

    @Test
    fun toPendingItemUiModelRedirectsUnknownDirectionToEditFlow() {
        val item = testPendingEntry(direction = MoneyDirection.UNKNOWN)
            .toPendingItemUiModel(rawNotificationText = null)

        assertFalse(item.canDirectConfirm)
        assertFalse(item.isDirectionKnown)
        assertTrue(item.amount.startsWith("±"))
        assertEquals("先补方向", item.primaryActionLabel)
        assertTrue(item.directionHint!!.contains("不会计入预算和收支汇总"))
    }

    /**
     * 构造待确认测试数据。
     *
     * 参数说明：
     * - direction：待确认项方向，用于覆盖“可直接确认”和“需先编辑”两条分支。
     */
    private fun testPendingEntry(direction: MoneyDirection): PendingEntry {
        return PendingEntry(
            id = 1L,
            amount = 18.8,
            direction = direction,
            channel = "测试渠道",
            timestamp = 1_760_000_000_000L,
            confidence = ParseConfidence(0.75f),
            categoryHint = "餐饮",
            note = "测试商户",
            rawSource = "notification",
            parsePath = "local-parser",
            modelVersion = "test-v1",
            reason = "测试原因",
        )
    }
}
