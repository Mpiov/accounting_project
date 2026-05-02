package com.autobook.cn.app.viewmodel

import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsOverviewSupportTest {
    @Test
    fun buildSettingsOverviewCalculatesCountsAndCurrentMonthTotals() {
        val today = LocalDate.of(2026, 4, 13)
        val entries = listOf(
            testEntry(
                id = 1L,
                date = today,
                amount = 15.0,
                direction = MoneyDirection.EXPENSE,
                traceSource = "manual",
                editedManually = true,
            ),
            testEntry(
                id = 2L,
                date = today.minusDays(2),
                amount = 3000.0,
                direction = MoneyDirection.INCOME,
                traceSource = "notification",
            ),
            testEntry(
                id = 3L,
                date = today.minusMonths(1),
                amount = 88.0,
                direction = MoneyDirection.EXPENSE,
                traceSource = "notification",
            ),
        )

        val overview = buildSettingsOverview(
            entries = entries,
            pendingCount = 2,
            today = today,
        )

        assertEquals(3, overview.totalEntries)
        assertEquals(1, overview.manualEntries)
        assertEquals(2, overview.autoEntries)
        assertEquals(2, overview.pendingEntries)
        assertEquals("¥3,000.00", overview.currentMonthIncome)
        assertEquals("¥15.00", overview.currentMonthExpense)
    }

    private fun testEntry(
        id: Long,
        date: LocalDate,
        amount: Double,
        direction: MoneyDirection = MoneyDirection.EXPENSE,
        traceSource: String,
        editedManually: Boolean = false,
    ): LedgerEntry {
        return LedgerEntry(
            id = id,
            amount = amount,
            direction = direction,
            merchant = "测试商户$id",
            channel = "测试渠道",
            timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            category = "测试",
            traceSource = traceSource,
            tracePath = "test-path",
            traceModelVersion = "test-v1",
            editedManually = editedManually,
        )
    }
}
