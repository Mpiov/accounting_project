package com.autobook.cn.core.data

import com.autobook.cn.core.data.parser.LocalTransactionExtractor
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 验证本地解析器能从典型通知中抽取金额与分类。 */
class LocalTransactionExtractorTest {
    @Test
    fun `extracts amount channel and category from wechat notification`() = runTest {
        val result = LocalTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "支付成功 88.80 元 订单号 202404120001 奶茶店",
                postedAt = 123L,
                sourceApp = NotificationSourceApp.WECHAT,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(88.8, candidate.amount)
        assertEquals("微信支付", candidate.channel)
        assertEquals("餐饮", candidate.categoryHint)
        assertEquals("202404120001", candidate.orderId)
    }

    @Test
    fun `downgrades to low confidence when direction is unknown`() = runTest {
        val result = LocalTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "交易成功 88.80 元 订单号 202404120001 奶茶店",
                postedAt = 456L,
                sourceApp = NotificationSourceApp.WECHAT,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(MoneyDirection.UNKNOWN, candidate.direction)
        assertTrue(candidate.confidence.score < 0.6f)
        assertTrue(candidate.confidenceReason.contains("低置信度"))
    }

    @Test
    fun `prefers actual payment amount over aggregated notification count`() = runTest {
        val result = LocalTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "[5条]微信支付: 已支付¥0.01",
                postedAt = 789L,
                sourceApp = NotificationSourceApp.WECHAT,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(0.01, candidate.amount)
        assertEquals(MoneyDirection.EXPENSE, candidate.direction)
    }

    @Test
    fun `treats wechat qr code collection as income instead of direction conflict`() = runTest {
        val result = LocalTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "[6条]微信支付: 个人收款码到账¥0.01",
                postedAt = 999L,
                sourceApp = NotificationSourceApp.WECHAT,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(0.01, candidate.amount)
        assertEquals(MoneyDirection.INCOME, candidate.direction)
        assertTrue(candidate.confidence.score >= 0.85f)
    }
}
