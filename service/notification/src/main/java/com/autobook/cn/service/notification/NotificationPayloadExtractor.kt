package com.autobook.cn.service.notification

/**
 * 通知解析前的标题与正文标准化结果。
 *
 * 参数说明：
 * - title：归一化后的通知标题，优先保留最稳定的标题字段。
 * - text：归一化后的通知正文，会尽量选出最适合交易解析的一段文本。
 */
internal data class NotificationPayload(
    val title: String?,
    val text: String?,
)

/**
 * 从不同厂商、不同样式的通知字段中，挑出一份最适合后续记账解析的标题与正文。
 *
 * 参数说明：
 * - title：通知常规标题，对应 `android.title`。
 * - bigTitle：通知展开态标题，对应 `android.title.big`。
 * - text：通知常规正文，对应 `android.text`。
 * - bigText：通知展开态正文，对应 `android.bigText`。
 * - textLines：收件箱样式通知的多行正文，对应 `android.textLines`。
 * - tickerText：部分通知仅暴露在 ticker 中的兜底文本。
 */
internal object NotificationPayloadExtractor {
    fun extract(
        title: String?,
        bigTitle: String?,
        text: String?,
        bigText: String?,
        textLines: List<String>,
        tickerText: String?,
    ): NotificationPayload {
        val normalizedTitle = firstNonBlank(title, bigTitle)
        val normalizedText = selectBestBodyCandidate(
            candidates = listOf(
                bigText,
                textLines.joinToString(separator = " ").takeIf { it.isNotBlank() },
                text,
                tickerText,
            ),
            title = normalizedTitle,
        )
        return NotificationPayload(
            title = normalizedTitle,
            text = normalizedText,
        )
    }

    /**
     * 选择最适合交易解析的一段正文。
     *
     * 参数说明：
     * - candidates：按优先级排列的正文候选集合，前面的字段通常结构更完整。
     * - title：已归一化标题，用于过滤和标题完全相同的无效正文。
     */
    private fun selectBestBodyCandidate(
        candidates: List<String?>,
        title: String?,
    ): String? {
        val cleanedCandidates = candidates
            .mapNotNull(::normalize)
            .distinct()
            // 标题和正文完全相同时没有新增信息，直接剔除避免重复拼接。
            .filterNot { candidate -> candidate == title }
        return cleanedCandidates.maxByOrNull(::scoreBodyCandidate)
    }

    /**
     * 为正文候选打分，优先保留对金额、方向判断更有帮助的文本。
     *
     * 参数说明：
     * - candidate：单个正文候选字符串。
     */
    private fun scoreBodyCandidate(candidate: String): Int {
        var score = candidate.length
        // 金额是交易解析的第一关，有金额的文本优先级明显更高。
        if (AMOUNT_REGEX.containsMatchIn(candidate)) {
            score += 100
        }
        // 方向关键词可以显著提高后续自动入账概率，因此给予额外权重。
        if (DIRECTION_KEYWORDS.any(candidate::contains)) {
            score += 30
        }
        return score
    }

    /**
     * 依次挑出第一个非空白文本。
     *
     * 参数说明：
     * - values：待筛选的文本列表。
     */
    private fun firstNonBlank(vararg values: String?): String? = values.firstNotNullOfOrNull(::normalize)

    /**
     * 去掉首尾空白，并把空字符串统一折叠为 null。
     *
     * 参数说明：
     * - value：待清洗的原始文本。
     */
    private fun normalize(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}

/** 金额命中通常意味着这段文字更像交易正文，而不是单纯渠道名。 */
private val AMOUNT_REGEX = Regex("""(?:￥|¥|RMB|CNY)?\s*\d+(?:\.\d{1,2})?""")

/** 这些关键词能帮助后续规则判断收支方向，因此在挑选正文时给予更高优先级。 */
private val DIRECTION_KEYWORDS = listOf("支付", "付款", "收款", "到账", "入账", "转账", "扣款")
