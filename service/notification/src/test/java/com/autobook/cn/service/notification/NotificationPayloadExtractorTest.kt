package com.autobook.cn.service.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 验证通知字段标准化逻辑，重点覆盖微信等通知正文字段不稳定的场景。 */
class NotificationPayloadExtractorTest {
    @Test
    fun `uses big text when regular text is missing`() {
        val payload = NotificationPayloadExtractor.extract(
            title = "微信支付",
            bigTitle = null,
            text = null,
            bigText = "已支付￥1.00",
            textLines = emptyList(),
            tickerText = null,
        )

        assertEquals("微信支付", payload.title)
        assertEquals("已支付￥1.00", payload.text)
    }

    @Test
    fun `uses text lines as body candidates without splitting notification`() {
        val payloads = NotificationPayloadExtractor.extractAll(
            title = "微信支付",
            bigTitle = null,
            text = "你有一条新消息",
            bigText = null,
            textLines = listOf("已支付￥1.00", "中间行应该被过滤", "订单号123456"),
            tickerText = null,
        )

        assertEquals(1, payloads.size)
        assertEquals("微信支付", payloads.single().title)
        assertEquals("已支付￥1.00", payloads.single().text)
    }

    @Test
    fun `collapses duplicated transaction text lines into one payload`() {
        val payloads = NotificationPayloadExtractor.extractAll(
            title = "银行卡通知",
            bigTitle = null,
            text = null,
            bigText = null,
            textLines = listOf("支出人民币18.00元", "支出人民币18.00元"),
            tickerText = null,
        )

        assertEquals(1, payloads.size)
        assertEquals("支出人民币18.00元", payloads.single().text)
    }

    @Test
    fun `prefers most complete bank text line from multiple notification fields`() {
        val payloads = NotificationPayloadExtractor.extractAll(
            title = "动账通知",
            bigTitle = null,
            text = "尾号3026卡消费13.20元",
            bigText = null,
            textLines = listOf(
                "尾号3026卡消费13.20元",
                "尾号3026卡5月15日11:20POS支出(无卡支付)13.20元。请点击查看详情。",
            ),
            tickerText = null,
        )

        assertEquals(1, payloads.size)
        assertEquals("动账通知", payloads.single().title)
        assertEquals("尾号3026卡5月15日11:20POS支出(无卡支付)13.20元。请点击查看详情。", payloads.single().text)
    }

    @Test
    fun `falls back to ticker text when extras are empty`() {
        val payload = NotificationPayloadExtractor.extract(
            title = null,
            bigTitle = null,
            text = null,
            bigText = null,
            textLines = emptyList(),
            tickerText = "微信支付：已支付￥1.00",
        )

        assertNull(payload.title)
        assertEquals("微信支付：已支付￥1.00", payload.text)
    }

    @Test
    fun `drops duplicated body that is identical to title`() {
        val payload = NotificationPayloadExtractor.extract(
            title = "微信支付",
            bigTitle = null,
            text = "微信支付",
            bigText = null,
            textLines = emptyList(),
            tickerText = null,
        )

        assertEquals("微信支付", payload.title)
        assertNull(payload.text)
    }

    @Test
    fun `drops fully empty payloads`() {
        val payloads = NotificationPayloadExtractor.extractAll(
            title = null,
            bigTitle = null,
            text = null,
            bigText = null,
            textLines = emptyList(),
            tickerText = null,
        )

        assertTrue(payloads.isEmpty())
    }
}
