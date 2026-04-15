package com.autobook.cn.core.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 图片/截图导入链路里的交易时间解析工具。
 *
 * 用途说明：
 * - 统一解析云端大模型或端侧 OCR/LLM 返回的交易时间文本，避免每条链路各自维护一套时间格式兼容逻辑。
 * - 当前优先兼容常见账单时间格式，并在缺少年份或日期时基于参考时间补齐，尽量把识别出的时间稳定落成毫秒时间戳。
 */
object ImageImportTimeParser {
    /**
     * 把结构化时间文本解析成毫秒时间戳。
     *
     * 参数说明：
     * - rawValue：模型或规则层返回的时间文本。
     * - referenceTimestamp：用于补全年份或日期的参考时间，默认使用当前时间。
     *
     * 返回值说明：
     * - 解析成功时返回毫秒时间戳；无法稳定识别时返回 `null`。
     */
    fun parseToTimestamp(
        rawValue: String?,
        referenceTimestamp: Long = System.currentTimeMillis(),
    ): Long? {
        val normalizedValue = normalize(rawValue) ?: return null
        val referenceDateTime = Instant.ofEpochMilli(referenceTimestamp)
            .atZone(DEFAULT_ZONE_ID)
            .toLocalDateTime()

        FULL_DATE_TIME_FORMATTERS.forEach { formatter ->
            runCatching {
                return LocalDateTime.parse(normalizedValue, formatter).toEpochMillis()
            }
        }

        MONTH_DAY_TIME_FORMATTERS.forEach { formatter ->
            runCatching {
                val parsedDateTime = LocalDateTime.parse(
                    "${referenceDateTime.year}-$normalizedValue",
                    formatter,
                )
                return parsedDateTime.toEpochMillis()
            }
        }

        TIME_ONLY_FORMATTERS.forEach { formatter ->
            runCatching {
                val parsedTime = LocalTime.parse(normalizedValue, formatter)
                return LocalDateTime.of(referenceDateTime.toLocalDate(), parsedTime).toEpochMillis()
            }
        }

        return null
    }

    /**
     * 从原始 OCR 文本中提取最像“交易时间”的片段并转成时间戳。
     *
     * 参数说明：
     * - rawText：待扫描的 OCR 原文。
     * - referenceTimestamp：用于补全年份或日期的参考时间。
     */
    fun extractFromText(
        rawText: String?,
        referenceTimestamp: Long = System.currentTimeMillis(),
    ): Long? {
        val text = rawText?.trim().orEmpty()
        if (text.isBlank()) {
            return null
        }
        TIME_TEXT_PATTERNS.forEach { pattern ->
            pattern.findAll(text).forEach { matchResult ->
                parseToTimestamp(
                    rawValue = matchResult.value,
                    referenceTimestamp = referenceTimestamp,
                )?.let { parsedTimestamp ->
                    return parsedTimestamp
                }
            }
        }
        return null
    }

    /**
     * 统一清洗模型输出里的时间文本。
     *
     * 参数说明：
     * - rawValue：待清洗的原始字符串。
     */
    private fun normalize(rawValue: String?): String? {
        val trimmedValue = rawValue?.trim().orEmpty()
        if (trimmedValue.isBlank()) {
            return null
        }
        return trimmedValue
            .replace('T', ' ')
            .replace('/', '-')
            .replace("年", "-")
            .replace("月", "-")
            .replace("日", "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}

/**
 * 把 `LocalDateTime` 转成系统默认时区下的毫秒时间戳。
 */
private fun LocalDateTime.toEpochMillis(): Long {
    return atZone(DEFAULT_ZONE_ID).toInstant().toEpochMilli()
}

/**
 * 账单时间通常以本地时区展示，这里统一使用系统默认时区落盘。
 */
private val DEFAULT_ZONE_ID: ZoneId = ZoneId.systemDefault()

/**
 * 完整日期时间格式列表。
 */
private val FULL_DATE_TIME_FORMATTERS = listOf(
    DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss", Locale.CHINA),
    DateTimeFormatter.ofPattern("yyyy-M-d HH:mm", Locale.CHINA),
)

/**
 * 缺少年份时的日期时间格式列表。
 */
private val MONTH_DAY_TIME_FORMATTERS = listOf(
    DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss", Locale.CHINA),
    DateTimeFormatter.ofPattern("yyyy-M-d HH:mm", Locale.CHINA),
)

/**
 * 只有时间时的格式列表。
 */
private val TIME_ONLY_FORMATTERS = listOf(
    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.CHINA),
    DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA),
)

/**
 * OCR 文本里的时间片段提取规则。
 *
 * 用途说明：
 * - 先尽量命中完整日期时间，再退回到月日时间或纯时分秒。
 * - 规则保持保守，避免把订单号或金额误识别成时间。
 */
private val TIME_TEXT_PATTERNS = listOf(
    Regex("""\d{4}[-/年]\d{1,2}[-/月]\d{1,2}(?:日)?\s+\d{1,2}:\d{2}(?::\d{2})?"""),
    Regex("""\d{1,2}[-/月]\d{1,2}(?:日)?\s+\d{1,2}:\d{2}(?::\d{2})?"""),
    Regex("""\d{1,2}:\d{2}:\d{2}"""),
    Regex("""\d{1,2}:\d{2}"""),
)
