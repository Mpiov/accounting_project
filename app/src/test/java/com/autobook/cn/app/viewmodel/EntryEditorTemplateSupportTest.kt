package com.autobook.cn.app.viewmodel

import com.autobook.cn.core.model.MoneyDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EntryEditorTemplateSupportTest {
    @Test
    fun applyTemplatePrefillsSafeFieldsWithoutTouchingAmount() {
        val state = EntryEditorUiState(
            amount = "52.80",
            category = "待分类",
            note = "",
            direction = MoneyDirection.EXPENSE,
        )

        val updated = state.applyEntryTemplate("salary")

        assertEquals("52.80", updated.amount)
        assertEquals("工资", updated.category)
        assertEquals("固定收入", updated.note)
        assertEquals(MoneyDirection.INCOME, updated.direction)
    }

    @Test
    fun applyTemplateKeepsExistingMerchantAndNote() {
        val state = EntryEditorUiState(
            note = "海底捞\n聚餐",
        )

        val updated = state.applyEntryTemplate("taxi")

        assertEquals("海底捞\n聚餐", updated.note)
        assertEquals("交通", updated.category)
        assertEquals(MoneyDirection.EXPENSE, updated.direction)
    }
}
