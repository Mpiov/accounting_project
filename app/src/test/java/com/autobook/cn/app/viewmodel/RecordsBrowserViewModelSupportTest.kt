package com.autobook.cn.app.viewmodel

import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordsBrowserViewModelSupportTest {
    @Test
    fun allRecordsKeepsNewestSortWithoutCategoryFilter() {
        val today = LocalDate.of(2026, 5, 21)
        val state = buildRecordsBrowserUiStateForTest(
            entries = listOf(
                testEntry(id = 1L, sortId = 10L, date = today.minusDays(1), category = "餐饮"),
                testEntry(id = 2L, sortId = 30L, date = today, category = "交通"),
                testEntry(id = 3L, sortId = 20L, date = today, category = "购物"),
            ),
            today = today,
        )

        assertEquals("详细记录", state.title)
        assertEquals(listOf(2L, 3L, 1L), state.items.map { item -> item.id })
    }

    @Test
    fun monthlyExpenseCategoryOnlyUsesExactCategoryMatch() {
        val today = LocalDate.of(2026, 5, 21)
        val state = buildRecordsBrowserUiStateForTest(
            entries = listOf(
                testEntry(id = 1L, sortId = 40L, date = today, category = "交通", amount = 12.0),
                testEntry(id = 2L, sortId = 50L, date = today, category = "公共交通", amount = 8.0),
                testEntry(id = 3L, sortId = 60L, date = today.minusMonths(1), category = "交通", amount = 20.0),
                testEntry(id = 4L, sortId = 70L, date = today, category = "交通", direction = MoneyDirection.INCOME),
            ),
            direction = MoneyDirection.EXPENSE,
            period = RecordsBrowserPeriod.MONTH,
            category = "交通",
            today = today,
        )

        assertEquals("交通明细", state.title)
        assertEquals(listOf(1L), state.items.map { item -> item.id })
        assertTrue(state.summary.contains("共 1 笔"))
        assertTrue(state.summary.contains("12.00"))
    }

    @Test
    fun weeklyDirectionSummaryAllowsEmptyCategoryForNetDrilldown() {
        val today = LocalDate.of(2026, 5, 21)
        val state = buildRecordsBrowserUiStateForTest(
            entries = listOf(
                testEntry(id = 1L, sortId = 10L, date = today, category = "工资", amount = 1000.0, direction = MoneyDirection.INCOME),
                testEntry(id = 2L, sortId = 20L, date = today.minusDays(2), category = "退款", amount = 20.0, direction = MoneyDirection.INCOME),
                testEntry(id = 3L, sortId = 30L, date = today.minusWeeks(1), category = "工资", amount = 900.0, direction = MoneyDirection.INCOME),
                testEntry(id = 4L, sortId = 40L, date = today, category = "餐饮", amount = 18.0, direction = MoneyDirection.EXPENSE),
            ),
            direction = MoneyDirection.INCOME,
            period = RecordsBrowserPeriod.WEEK,
            category = "",
            today = today,
        )

        assertEquals("收入明细", state.title)
        assertEquals(listOf(2L, 1L), state.items.map { item -> item.id })
        assertTrue(state.summary.contains("共 2 笔"))
        assertTrue(state.summary.contains("1,020.00"))
    }

    @Test
    fun dayAndYearPeriodsUseCurrentDateAnchor() {
        val today = LocalDate.of(2026, 5, 21)
        val entries = listOf(
            testEntry(id = 1L, sortId = 10L, date = today, category = "交通"),
            testEntry(id = 2L, sortId = 20L, date = today.minusDays(1), category = "交通"),
            testEntry(id = 3L, sortId = 30L, date = LocalDate.of(2025, 5, 21), category = "交通"),
        )

        val dayState = buildRecordsBrowserUiStateForTest(
            entries = entries,
            direction = MoneyDirection.EXPENSE,
            period = RecordsBrowserPeriod.DAY,
            category = "交通",
            today = today,
        )
        val yearState = buildRecordsBrowserUiStateForTest(
            entries = entries,
            direction = MoneyDirection.EXPENSE,
            period = RecordsBrowserPeriod.YEAR,
            category = "交通",
            today = today,
        )

        assertEquals(listOf(1L), dayState.items.map { item -> item.id })
        assertEquals(listOf(2L, 1L), yearState.items.map { item -> item.id })
    }

    /**
     * 用途说明：
     * - 构造详细记录页筛选测试所需的账本记录。
     *
     * 参数说明：
     * - id：账单主键。
     * - sortId：用于验证最终展示倒序。
     * - date：账单发生日期。
     * - category：账单分类。
     * - amount：账单金额。
     * - direction：收支方向。
     */
    private fun testEntry(
        id: Long,
        sortId: Long,
        date: LocalDate,
        category: String,
        amount: Double = 10.0,
        direction: MoneyDirection = MoneyDirection.EXPENSE,
    ): LedgerEntry {
        return LedgerEntry(
            id = id,
            sortId = sortId,
            amount = amount,
            direction = direction,
            channel = "测试渠道",
            timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            category = category,
            note = "测试记录$id",
            traceSource = "manual",
            tracePath = "test",
            traceModelVersion = "test-v1",
            editedManually = true,
        )
    }
}
