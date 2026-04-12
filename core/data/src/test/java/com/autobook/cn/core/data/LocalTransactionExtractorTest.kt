package com.autobook.cn.core.data

import com.autobook.cn.core.data.parser.LocalTransactionExtractor
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
}
