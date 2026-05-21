package com.autobook.cn.app.viewmodel

import com.autobook.cn.core.model.MoneyDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EntryEditorTemplateSupportTest {
    /**
     * 用途说明：
     * - 验证快速记账和待确认编辑页把未知方向默认归一成支出。
     *
     * 参数说明：
     * - 无参数，直接构造 UNKNOWN 方向进行断言。
     */
    @Test
    fun defaultExpenseWhenUnknownFallsBackToExpense() {
        val resolvedDirection = MoneyDirection.UNKNOWN.defaultExpenseWhenUnknown()

        assertEquals(MoneyDirection.EXPENSE, resolvedDirection)
    }

    /**
     * 用途说明：
     * - 验证已明确选择收入时不会被默认支出逻辑覆盖。
     *
     * 参数说明：
     * - 无参数，直接构造 INCOME 方向进行断言。
     */
    @Test
    fun defaultExpenseWhenUnknownKeepsKnownIncome() {
        val resolvedDirection = MoneyDirection.INCOME.defaultExpenseWhenUnknown()

        assertEquals(MoneyDirection.INCOME, resolvedDirection)
    }

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
