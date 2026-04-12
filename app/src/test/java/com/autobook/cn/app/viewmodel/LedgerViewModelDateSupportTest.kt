package com.autobook.cn.app.viewmodel

import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.feature.ledger.LedgerRangeFilter
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LedgerViewModelDateSupportTest {
    @Test
    fun filterByRangeReturnsOnlySelectedCalendarDate() {
        val selectedDate = LocalDate.of(2026, 4, 13)
        val entries = listOf(
            testEntry(id = 1L, date = selectedDate),
            testEntry(id = 2L, date = selectedDate.minusDays(1)),
            testEntry(id = 3L, date = selectedDate.plusDays(1)),
        )

        val result = entries.filterByRange(
            range = LedgerRangeFilter.CALENDAR,
            selectedDate = selectedDate,
            today = selectedDate,
        )

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun buildLedgerCalendarDaysCreatesSixWeekGridAndMarksSelection() {
        val selectedDate = LocalDate.of(2026, 4, 13)
        val days = buildLedgerCalendarDays(
            entries = listOf(testEntry(id = 1L, date = selectedDate)),
            displayedMonth = YearMonth.of(2026, 4),
            selectedDate = selectedDate,
            today = selectedDate,
        )

        assertEquals(42, days.size)
        assertEquals(LocalDate.of(2026, 3, 30), days.first().date)
        assertEquals(LocalDate.of(2026, 5, 10), days.last().date)
        assertTrue(days.any { it.date == selectedDate && it.isSelected && it.hasEntries && it.isToday })
        assertFalse(days.first().isCurrentMonth)
    }

    private fun testEntry(
        id: Long,
        date: LocalDate,
    ): LedgerEntry {
        return LedgerEntry(
            id = id,
            amount = 10.0,
            direction = MoneyDirection.EXPENSE,
            merchant = "测试商户$id",
            channel = "手动记账",
            timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            category = "测试",
            traceSource = "manual",
            tracePath = "manual-entry",
            traceModelVersion = "manual-v1",
            editedManually = true,
        )
    }
}
