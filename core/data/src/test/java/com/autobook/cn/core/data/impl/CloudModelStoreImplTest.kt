package com.autobook.cn.core.data.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 用途说明：
 * - 验证云端模型权重的轻量序列化格式，确保 DataStore 落盘后可以无损恢复。
 */
class CloudModelStoreImplTest {
    @Test
    fun `double map serialization keeps feature keys and finite values`() {
        val rawWeights = mapOf(
            "bias" to -2.0,
            "source.trusted_payment" to 1.23456789,
            "中文=特征\n键" to 0.5,
            "bad" to Double.NaN,
        )

        val restored = deserializeDoubleMap(serializeDoubleMap(rawWeights))

        assertEquals(-2.0, restored["bias"])
        assertEquals(1.23456789, restored["source.trusted_payment"] ?: 0.0, 0.00000001)
        assertEquals(0.5, restored["中文=特征\n键"])
        assertEquals(false, restored.containsKey("bad"))
    }
}
