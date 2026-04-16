package com.autobook.cn.core.data

import com.autobook.cn.core.data.impl.ImageImportCandidatePostProcessor
import com.autobook.cn.core.model.ImageImportAnalysisPath
import com.autobook.cn.core.model.ImageImportCandidate
import com.autobook.cn.core.model.ImageImportSuggestion
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.ParseConfidence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 验证图片候选后处理器只在“时间 + 金额 + 方向”一致时才执行判重。 */
class ImageImportCandidatePostProcessorTest {
    @Test
    fun `deduplicates in-batch candidates only when timestamp amount and direction all match`() = runTest {
        val postProcessor = ImageImportCandidatePostProcessor(
            ledgerRepository = FakeImageImportLedgerRepository(),
        )

        val result = postProcessor.postProcess(
            ImageImportSuggestion(
                tracePath = ImageImportAnalysisPath.CLOUD_LLM_PROXY,
                message = "测试批内去重",
                candidates = listOf(
                    ImageImportCandidate(
                        amount = 32.5,
                        timestamp = 1_744_598_810_000L,
                        direction = MoneyDirection.EXPENSE,
                        merchant = "商户 A",
                        orderId = "ORDER-A",
                        confidence = ParseConfidence(0.9f),
                    ),
                    ImageImportCandidate(
                        amount = 32.5,
                        timestamp = 1_744_598_819_999L,
                        direction = MoneyDirection.EXPENSE,
                        merchant = "商户 B",
                        orderId = "ORDER-B",
                        confidence = ParseConfidence(0.8f),
                    ),
                ),
            ),
        )

        assertEquals(1, result.candidates.size)
        assertEquals(1, result.duplicateFilteredCount)
        assertEquals("商户 A", result.merchant)
        assertEquals(1_744_598_810_000L, result.timestamp)
    }

    @Test
    fun `does not deduplicate image candidates when timestamp is missing`() = runTest {
        val postProcessor = ImageImportCandidatePostProcessor(
            ledgerRepository = FakeImageImportLedgerRepository(),
        )

        val result = postProcessor.postProcess(
            ImageImportSuggestion(
                tracePath = ImageImportAnalysisPath.CLOUD_LLM_PROXY,
                message = "测试缺时间不判重",
                candidates = listOf(
                    ImageImportCandidate(
                        amount = 32.5,
                        timestamp = null,
                        direction = MoneyDirection.EXPENSE,
                        merchant = "商户 A",
                        confidence = ParseConfidence(0.9f),
                    ),
                    ImageImportCandidate(
                        amount = 32.5,
                        timestamp = null,
                        direction = MoneyDirection.EXPENSE,
                        merchant = "商户 B",
                        confidence = ParseConfidence(0.8f),
                    ),
                ),
            ),
        )

        assertEquals(2, result.candidates.size)
        assertEquals(0, result.duplicateFilteredCount)
        assertNull(result.timestamp)
    }

    @Test
    fun `filters historical duplicate only when repository confirms same timestamp amount and direction`() = runTest {
        val postProcessor = ImageImportCandidatePostProcessor(
            ledgerRepository = FakeImageImportLedgerRepository(
                historicalDuplicateMinute = 1_744_598_800_000L,
            ),
        )

        val result = postProcessor.postProcess(
            ImageImportSuggestion(
                tracePath = ImageImportAnalysisPath.CLOUD_LLM_PROXY,
                message = "测试历史判重",
                candidates = listOf(
                    ImageImportCandidate(
                        amount = 32.5,
                        timestamp = 1_744_598_800_000L,
                        direction = MoneyDirection.EXPENSE,
                        merchant = "命中历史重复",
                        confidence = ParseConfidence(0.9f),
                    ),
                    ImageImportCandidate(
                        amount = 19.9,
                        timestamp = 1_744_598_860_000L,
                        direction = MoneyDirection.EXPENSE,
                        merchant = "保留候选",
                        confidence = ParseConfidence(0.7f),
                    ),
                ),
            ),
        )

        assertEquals(1, result.candidates.size)
        assertEquals(1, result.duplicateFilteredCount)
        assertEquals("保留候选", result.merchant)
        assertTrue(result.message.contains("去除重复 1 条"))
    }

    @Test
    fun `downgrades medium high candidate to pending when timestamp is missing but local same amount direction exists`() = runTest {
        val postProcessor = ImageImportCandidatePostProcessor(
            ledgerRepository = FakeImageImportLedgerRepository(
                ambiguousDuplicateAmount = 32.5,
                ambiguousDuplicateDirection = MoneyDirection.EXPENSE,
            ),
        )

        val result = postProcessor.postProcess(
            ImageImportSuggestion(
                tracePath = ImageImportAnalysisPath.CLOUD_LLM_PROXY,
                message = "测试缺时间降级待确认",
                candidates = listOf(
                    ImageImportCandidate(
                        amount = 32.5,
                        timestamp = null,
                        direction = MoneyDirection.EXPENSE,
                        merchant = "待确认候选",
                        confidence = ParseConfidence(0.93f),
                    ),
                ),
            ),
        )

        assertEquals(1, result.candidates.size)
        assertEquals(0, result.duplicateFilteredCount)
        assertEquals(0.84f, result.confidence.score)
        assertTrue(result.note.contains("已降级为待确认"))
        assertTrue(result.message.contains("因缺少时间降级待确认 1 条"))
    }
}

/** 提供最小账本仓储替身，用于控制图片候选后处理器的历史判重结果。 */
private class FakeImageImportLedgerRepository(
    private val historicalDuplicateMinute: Long? = null,
    private val ambiguousDuplicateAmount: Double? = null,
    private val ambiguousDuplicateDirection: MoneyDirection? = null,
) : LedgerRepository {
    override fun observeEntries(): Flow<List<LedgerEntry>> = emptyFlow()

    override suspend fun add(entry: LedgerEntry): Long = 1L

    override suspend fun update(entry: LedgerEntry) = Unit

    override suspend fun delete(id: Long) = Unit

    override suspend fun getById(id: Long): LedgerEntry? = null

    override suspend fun findPotentialDuplicate(candidate: com.autobook.cn.core.model.TransactionCandidate): LedgerEntry? = null

    override suspend fun findPotentialImageImportDuplicate(
        timestamp: Long?,
        amount: Double,
        direction: MoneyDirection,
    ): LedgerEntry? {
        if (timestamp == null || historicalDuplicateMinute == null) {
            return null
        }
        val expectedMinuteBucket = historicalDuplicateMinute / 60_000L
        val actualMinuteBucket = timestamp / 60_000L
        if (expectedMinuteBucket != actualMinuteBucket) {
            return null
        }
        return LedgerEntry(
            id = 1L,
            amount = amount,
            direction = direction,
            merchant = "历史重复记录",
            channel = "图片导入",
            timestamp = timestamp,
            category = "待分类",
            traceSource = "test",
            tracePath = "test",
            traceModelVersion = "test",
        )
    }

    override suspend fun findPotentialImageImportDuplicateWithoutTimestamp(
        amount: Double,
        direction: MoneyDirection,
    ): LedgerEntry? {
        if (ambiguousDuplicateAmount != amount || ambiguousDuplicateDirection != direction) {
            return null
        }
        return LedgerEntry(
            id = 2L,
            amount = amount,
            direction = direction,
            merchant = "缺时间疑似重复",
            channel = "图片导入",
            timestamp = 1_744_598_800_000L,
            category = "待分类",
            traceSource = "test",
            tracePath = "test",
            traceModelVersion = "test",
        )
    }

    override suspend fun exportCsv(): String = ""
}
