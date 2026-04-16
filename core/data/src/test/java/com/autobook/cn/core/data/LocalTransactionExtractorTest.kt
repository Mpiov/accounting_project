package com.autobook.cn.core.data

import com.autobook.cn.core.data.parser.LocalTransactionExtractor
import com.autobook.cn.core.model.ConfidenceLevel
import com.autobook.cn.core.model.HIGH_CONFIDENCE_THRESHOLD
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.coroutines.test.runTest

/** 验证本地通知解析器已经切到新的 AC 自动机置信度规则。 */
class LocalTransactionExtractorTest {
    @Test
    fun `wechat short payment reaches high confidence`() = runTest {
        val result = LocalTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "已支付￥0.01",
                postedAt = 123L,
                sourceApp = NotificationSourceApp.WECHAT,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(0.01, candidate.amount)
        assertEquals(MoneyDirection.EXPENSE, candidate.direction)
        assertEquals("微信支付", candidate.channel)
        assertEquals(ConfidenceLevel.HIGH, candidate.confidence.level)
        assertTrue(candidate.confidence.score >= HIGH_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun `bank debit notification reaches high confidence`() = runTest {
        val result = LocalTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.chinamworld.main",
                title = "动账提醒",
                text = "您尾号2134的储蓄账户4月15日22时23分收入人民币1.00元。点击查看>>",
                postedAt = 456L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(1.0, candidate.amount)
        assertEquals(MoneyDirection.INCOME, candidate.direction)
        assertEquals(NotificationSourceApp.BANK_SMS, candidate.sourceApp)
        assertEquals(ConfidenceLevel.HIGH, candidate.confidence.level)
    }

    @Test
    fun `finance settled notification stays medium after removing settled group`() = runTest {
        val result = LocalTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "零钱通",
                text = "零钱通利息到账0.01元",
                postedAt = 789L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals("零钱通", candidate.channel)
        assertEquals("收益", candidate.categoryHint)
        assertEquals(ConfidenceLevel.MEDIUM, candidate.confidence.level)
    }

    @Test
    fun `asset valuation notification is blocked to low confidence`() = runTest {
        val result = LocalTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "零钱通",
                text = "今日持仓收益 +12.30 元",
                postedAt = 999L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(MoneyDirection.UNKNOWN, candidate.direction)
        assertEquals(ConfidenceLevel.LOW, candidate.confidence.level)
        assertTrue(candidate.confidenceReason.contains("命中资产估值风险词"))
    }

    @Test
    fun `unknown but strongly structured notification can reach high confidence`() = runTest {
        val result = LocalTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.example.notice",
                title = "账户提醒",
                text = "您尾号2134的储蓄账户4月15日22时23分收入人民币1.00元",
                postedAt = 111L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(NotificationSourceApp.UNKNOWN, candidate.sourceApp)
        assertEquals(ConfidenceLevel.HIGH, candidate.confidence.level)
        assertTrue(candidate.confidence.score >= HIGH_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun `aggregated payment summary now follows pure score threshold`() = runTest {
        val result = LocalTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "[5条]微信支付: 已支付¥0.01",
                postedAt = 222L,
                sourceApp = NotificationSourceApp.WECHAT,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(MoneyDirection.EXPENSE, candidate.direction)
        assertEquals(ConfidenceLevel.HIGH, candidate.confidence.level)
        assertTrue(candidate.confidence.score >= HIGH_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun `expense keyword 支出 is recognized as expense direction`() = runTest {
        val result = LocalTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.example.notice",
                title = "账户通知",
                text = "本次支出人民币1.00元",
                postedAt = 333L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(MoneyDirection.EXPENSE, candidate.direction)
    }
}
