package com.autobook.cn.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.autobook.cn.core.data.ImageImportAnalyzer
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.model.ImageImportAnalysisPath
import com.autobook.cn.core.model.ImageImportCandidate
import com.autobook.cn.core.model.ImageImportSuggestion
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.PendingEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** 验证图片导入建议能够正确预填编辑页表单。 */
class EntryEditorImageImportSupportTest {
    @Test
    fun `import from image fills form with suggestion and image ref`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = EntryEditorViewModel(
                savedStateHandle = SavedStateHandle(),
                imageImportAnalyzer = object : ImageImportAnalyzer {
                    override suspend fun analyze(imageRef: String): OperationResult<ImageImportSuggestion> {
                        return OperationResult.Success(
                            ImageImportSuggestion(
                                amount = 66.6,
                                direction = MoneyDirection.INCOME,
                                merchant = "图片导入商户",
                                category = "工资",
                                note = "图片导入备注",
                                orderId = "IMG-ORDER-001",
                                confidence = ParseConfidence(0.91f),
                                tracePath = ImageImportAnalysisPath.CLOUD_LLM_PROXY,
                                message = "图片导入建议已生成",
                                candidates = listOf(
                                    ImageImportCandidate(
                                        amount = 66.6,
                                        direction = MoneyDirection.INCOME,
                                        merchant = "图片导入商户",
                                        category = "工资",
                                        note = "图片导入备注",
                                        orderId = "IMG-ORDER-001",
                                        confidence = ParseConfidence(0.91f),
                                        evidence = "到账 66.60 元",
                                    ),
                                    ImageImportCandidate(
                                        amount = 28.8,
                                        direction = MoneyDirection.EXPENSE,
                                        merchant = "第二候选商户",
                                        category = "餐饮",
                                        note = "第二候选备注",
                                        orderId = "IMG-ORDER-002",
                                        confidence = ParseConfidence(0.73f),
                                        evidence = "支付 28.80 元",
                                    ),
                                ),
                                filteredOutCandidateCount = 2,
                                duplicateFilteredCount = 1,
                                inactiveFilteredCount = 1,
                            ),
                        )
                    }
                },
                ledgerRepository = fakeLedgerRepository(),
                pendingRepository = fakePendingRepository(),
            )

            viewModel.importFromImage("content://demo/image-1")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("66.6", state.amount)
            assertEquals(MoneyDirection.INCOME, state.direction)
            assertEquals("图片导入商户", state.merchant)
            assertEquals("工资", state.category)
            assertEquals("图片导入备注", state.note)
            assertEquals("IMG-ORDER-001", state.orderId)
            assertEquals("content://demo/image-1", state.importedImageRef)
            assertEquals(ImageImportAnalysisPath.CLOUD_LLM_PROXY, state.imageImportTracePath)
            assertEquals(2, state.imageImportCandidates.size)
            assertEquals(0, state.selectedImageImportCandidateIndex)
            assertEquals(2, state.filteredOutCandidateCount)
            assertEquals(1, state.duplicateFilteredCount)
            assertEquals(1, state.inactiveFilteredCount)
            assertEquals(true, state.imageImportRequiresManualConfirmation)

            viewModel.selectImageImportCandidate(1)
            advanceUntilIdle()

            val switchedState = viewModel.uiState.value
            assertEquals("28.8", switchedState.amount)
            assertEquals(MoneyDirection.EXPENSE, switchedState.direction)
            assertEquals("第二候选商户", switchedState.merchant)
            assertEquals("餐饮", switchedState.category)
            assertEquals("第二候选备注", switchedState.note)
            assertEquals("IMG-ORDER-002", switchedState.orderId)
            assertEquals(1, switchedState.selectedImageImportCandidateIndex)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

/** 提供不会访问真实数据库的账本仓储替身。 */
private fun fakeLedgerRepository(): LedgerRepository {
    return object : LedgerRepository {
        override fun observeEntries(): Flow<List<LedgerEntry>> = emptyFlow()
        override suspend fun add(entry: LedgerEntry): Long = 1L
        override suspend fun update(entry: LedgerEntry) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun getById(id: Long): LedgerEntry? = null
        override suspend fun findPotentialDuplicate(candidate: com.autobook.cn.core.model.TransactionCandidate): LedgerEntry? = null
        override suspend fun findPotentialImageImportDuplicate(
            orderId: String?,
            amount: Double,
            direction: com.autobook.cn.core.model.MoneyDirection,
            merchant: String,
        ): LedgerEntry? = null
        override suspend fun exportCsv(): String = ""
    }
}

/** 提供不会访问真实数据库的待确认仓储替身。 */
private fun fakePendingRepository(): PendingRepository {
    return object : PendingRepository {
        override fun observeEntries(): Flow<List<PendingEntry>> = emptyFlow()
        override suspend fun add(entry: PendingEntry): Long = 1L
        override suspend fun update(entry: PendingEntry) = Unit
        override suspend fun getById(id: Long): PendingEntry? = null
        override suspend fun confirm(id: Long): PendingEntry? = null
        override suspend fun ignore(id: Long) = Unit
        override suspend fun markFalsePositive(id: Long) = Unit
    }
}
