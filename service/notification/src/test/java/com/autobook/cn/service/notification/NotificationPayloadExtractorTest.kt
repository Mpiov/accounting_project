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
    fun `keeps first and last text lines as independent payloads`() {
        val payloads = NotificationPayloadExtractor.extractAll(
            title = "微信支付",
            bigTitle = null,
            text = "你有一条新消息",
            bigText = null,
            textLines = listOf("已支付￥1.00", "中间行应该被过滤", "订单号123456"),
            tickerText = null,
        )

        assertEquals(2, payloads.size)
        assertEquals("微信支付", payloads[0].title)
        assertEquals("已支付￥1.00", payloads[0].text)
        assertEquals("微信支付", payloads[1].title)
        assertEquals("订单号123456", payloads[1].text)
    }

    @Test
    fun `deduplicates identical first and last text lines`() {
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
