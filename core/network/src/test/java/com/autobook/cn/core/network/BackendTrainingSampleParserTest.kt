package com.autobook.cn.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * 用途说明：
 * - 验证本地 JSONL 训练样本能转换成后端上传请求体。
 */
class BackendTrainingSampleParserTest {
    @Test
    fun `parseJsonl keeps raw notification text and nested features`() {
        val jsonl = """
            {"notificationId":42,"createdAt":1777651200000,"rawText":"向便利店支付18.80元","features":{"trustedPayment":true,"score":0.91},"feedbackSource":null}
        """.trimIndent()

        val samples = BackendTrainingSampleParser.parseJsonl(jsonl)
        val sample = samples.single()
        val features = sample["features"] as Map<*, *>

        assertEquals(42, (sample["notificationId"] as Number).toInt())
        assertEquals(1777651200000L, (sample["createdAt"] as Number).toLong())
        assertEquals("向便利店支付18.80元", sample["rawText"])
        assertEquals(true, features["trustedPayment"])
        assertEquals(0.91, (features["score"] as Number).toDouble(), 0.0001)
        assertNull(sample["feedbackSource"])
    }
}
