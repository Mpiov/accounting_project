package com.autobook.cn.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.TransactionCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** 验证极速记账页的方向切换、分类选择和快速保存链路。 */
class QuickEntryViewModelTest {
    @Test
    fun updateDirectionRefreshesCategoriesAndClearsIncompatibleSelection() {
        val viewModel = QuickEntryViewModel(
            savedStateHandle = SavedStateHandle(),
            ledgerRepository = RecordingQuickEntryLedgerRepository(),
        )

        viewModel.selectCategory("expense_food")
        viewModel.updateDirection(MoneyDirection.INCOME)

        val state = viewModel.uiState.value
        assertEquals(MoneyDirection.INCOME, state.direction)
        assertNull(state.selectedCategoryId)
        assertEquals("工资", state.categories.first().label)
    }

    @Test
    fun saveUsesSelectedCategoryAsMerchantFallback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val recordingLedgerRepository = RecordingQuickEntryLedgerRepository()
            val viewModel = QuickEntryViewModel(
                savedStateHandle = SavedStateHandle(mapOf("direction" to MoneyDirection.EXPENSE.name)),
                ledgerRepository = recordingLedgerRepository,
            )

            viewModel.selectCategory("expense_food")
            viewModel.appendDigit(8)
            viewModel.appendDigit(8)
            viewModel.updateNote("工作日晚餐")
            viewModel.save()
            advanceUntilIdle()

            val savedEntry = recordingLedgerRepository.lastAddedEntry
            assertNotNull(savedEntry)
            assertEquals("餐饮", savedEntry?.merchant)
            assertEquals("餐饮", savedEntry?.category)
            assertEquals("极速记账", savedEntry?.channel)
            assertEquals("quick-entry", savedEntry?.tracePath)
            assertEquals(true, viewModel.uiState.value.finished)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

/** 记录极速记账最后一次写入结果，便于验证 ViewModel 是否按预期组装了账单实体。 */
private class RecordingQuickEntryLedgerRepository : LedgerRepository {
    var lastAddedEntry: LedgerEntry? = null

    override fun observeEntries(): Flow<List<LedgerEntry>> = emptyFlow()

    override suspend fun add(entry: LedgerEntry): Long {
        lastAddedEntry = entry
        return 1L
    }

    override suspend fun update(entry: LedgerEntry) = Unit

    override suspend fun delete(id: Long) = Unit

    override suspend fun getById(id: Long): LedgerEntry? = null

    override suspend fun findPotentialDuplicate(candidate: TransactionCandidate): LedgerEntry? = null

    override suspend fun findPotentialImageImportDuplicate(
        timestamp: Long?,
        amount: Double,
        direction: MoneyDirection,
    ): LedgerEntry? = null

    override suspend fun findPotentialImageImportDuplicateWithoutTimestamp(
        amount: Double,
        direction: MoneyDirection,
    ): LedgerEntry? = null

    override suspend fun exportCsv(): String = ""
}
