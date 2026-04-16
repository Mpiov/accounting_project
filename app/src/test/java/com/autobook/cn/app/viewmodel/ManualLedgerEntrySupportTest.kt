package com.autobook.cn.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.autobook.cn.core.model.MoneyDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 验证手动建账共用组装逻辑，避免完整编辑页和极速记账页出现保存规则漂移。 */
class ManualLedgerEntrySupportTest {
    @Test
    fun buildManualLedgerEntryRequiresMerchantWhenPolicyRequiresIt() {
        val result = buildManualLedgerEntry(
            draft = ManualLedgerEntryDraft(
                amountInput = "18.60",
                direction = MoneyDirection.EXPENSE,
                merchant = "",
                category = "餐饮",
            ),
            merchantPolicy = ManualEntryMerchantPolicy.REQUIRE_EXPLICIT_MERCHANT,
            nowProvider = { 1_700_000_000_000L },
        )

        assertTrue(result is ManualLedgerEntryBuildFailure)
        assertEquals("请输入商户或备注对象", (result as ManualLedgerEntryBuildFailure).message)
    }

    @Test
    fun buildManualLedgerEntryUsesCategoryAsMerchantFallbackForQuickEntry() {
        val result = buildManualLedgerEntry(
            draft = ManualLedgerEntryDraft(
                amountInput = "88",
                direction = MoneyDirection.EXPENSE,
                merchant = "",
                category = "餐饮",
                note = "工作日晚餐",
                channel = "极速记账",
                tracePath = "quick-entry",
                traceModelVersion = "quick-entry-v1",
            ),
            merchantPolicy = ManualEntryMerchantPolicy.USE_CATEGORY_AS_FALLBACK,
            nowProvider = { 1_700_000_000_000L },
        )

        assertTrue(result is ManualLedgerEntryBuildSuccess)
        val entry = (result as ManualLedgerEntryBuildSuccess).entry
        assertEquals("餐饮", entry.merchant)
        assertEquals("餐饮", entry.category)
        assertEquals("极速记账", entry.channel)
        assertEquals("quick-entry", entry.tracePath)
        assertEquals(1_700_000_000_000L, entry.timestamp)
    }

    @Test
    fun readMoneyDirectionArgumentParsesKnownDirection() {
        val savedStateHandle = SavedStateHandle(mapOf("direction" to "INCOME"))

        val result = savedStateHandle.readMoneyDirectionArgument("direction")

        assertEquals(MoneyDirection.INCOME, result)
    }
}
