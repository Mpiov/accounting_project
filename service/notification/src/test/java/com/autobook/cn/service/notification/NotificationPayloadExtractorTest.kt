package com.autobook.cn.service.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `prefers multiline text when it carries amount information`() {
        val payload = NotificationPayloadExtractor.extract(
            title = "微信支付",
            bigTitle = null,
            text = "你有一条新消息",
            bigText = null,
            textLines = listOf("已支付￥1.00", "订单号123456"),
            tickerText = null,
        )

        assertEquals("已支付￥1.00 订单号123456", payload.text)
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
}
