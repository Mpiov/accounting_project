package com.autobook.cn.core.data

import com.autobook.cn.core.data.parser.LocalTransactionExtractor
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.OperationResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalTransactionExtractorTest {
    @Test
    fun `extracts amount and channel from mock notification`() = runTest {
        val result = LocalTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "支出 88.80 元 成功",
                postedAt = 123L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(88.8, candidate.amount)
        assertEquals("WeChat Pay", candidate.channel)
    }
}
