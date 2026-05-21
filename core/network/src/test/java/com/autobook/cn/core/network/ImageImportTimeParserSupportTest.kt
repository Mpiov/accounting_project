package com.autobook.cn.core.network

import com.autobook.cn.core.model.ImageImportTimeParser
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 图片导入时间解析的边界测试。
 *
 * 用途说明：
 * - 锁定“截图原文没有年份，但云端模型按 JSON schema 猜出完整年份”时的客户端兜底逻辑。
 * - 防止截图导入的本月账单被错误落到旧年份，导致首页和预算卡按本月统计时显示为 0。
 */
class ImageImportTimeParserSupportTest {
    @Test
    fun `uses reference year when source screenshot has no explicit year`() {
        val referenceTimestamp = epochMillisOf(2026, 5, 16, 9, 0)

        val parsedTimestamp = ImageImportTimeParser.parseToTimestamp(
            rawValue = "2025-05-15 11:20:00",
            referenceTimestamp = referenceTimestamp,
            sourceHasExplicitYear = false,
        )

        assertEquals(epochMillisOf(2026, 5, 15, 11, 20), parsedTimestamp)
    }

    @Test
    fun `keeps parsed year when source screenshot has explicit year`() {
        val referenceTimestamp = epochMillisOf(2026, 5, 16, 9, 0)

        val parsedTimestamp = ImageImportTimeParser.parseToTimestamp(
            rawValue = "2025-05-15 11:20:00",
            referenceTimestamp = referenceTimestamp,
            sourceHasExplicitYear = true,
        )

        assertEquals(epochMillisOf(2025, 5, 15, 11, 20), parsedTimestamp)
    }

    @Test
    fun `detects explicit year only from date-like text`() {
        assertEquals(true, ImageImportTimeParser.containsExplicitYear("2026年5月15日 11:20"))
        assertEquals(true, ImageImportTimeParser.containsExplicitYear("2026-05-15 11:20"))
        assertEquals(false, ImageImportTimeParser.containsExplicitYear("订单号 202605151120，5月15日 11:20"))
    }
}

/**
 * 构造本地时区毫秒时间戳。
 *
 * 参数说明：
 * - year：年份。
 * - month：月份。
 * - day：日期。
 * - hour：小时。
 * - minute：分钟。
 */
private fun epochMillisOf(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
): Long {
    return LocalDateTime.of(year, month, day, hour, minute)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
