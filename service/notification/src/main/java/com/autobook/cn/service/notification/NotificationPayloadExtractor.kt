package com.autobook.cn.service.notification

/**
 * 通知解析前的标题与正文标准化结果。
 *
 * 参数说明：
 * - title：归一化后的通知标题，优先保留最稳定的标题字段。
 * - text：归一化后的通知正文，会从折叠态、展开态和多行样式中选出最适合交易解析的一段文本。
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
    /**
     * 提取并标准化当前通知的候选 payload。
     *
     * 用途说明：
     * - 一条 Android 通知对象默认只对应一条业务通知，`textLines` 只作为正文候选来源。
     * - 不再把多行正文拆成多条 payload，避免云闪付、银行 App 的同一笔交易被首尾行重复入待确认。
     * - 多个字段同时存在时会按信息完整度选出最佳正文，减少金额和关键词重复带来的噪声。
     *
     * 参数说明：
     * - title：通知常规标题，对应 `android.title`。
     * - bigTitle：通知展开态标题，对应 `android.title.big`。
     * - text：通知常规正文，对应 `android.text`。
     * - bigText：通知展开态正文，对应 `android.bigText`。
     * - textLines：收件箱样式通知的多行正文，对应 `android.textLines`。
     * - tickerText：部分通知仅暴露在 ticker 中的兜底文本。
     */
    fun extractAll(
        title: String?,
        bigTitle: String?,
        text: String?,
        bigText: String?,
        textLines: List<String>,
        tickerText: String?,
    ): List<NotificationPayload> {
        val normalizedTitle = firstNonBlank(title, bigTitle)
        val normalizedText = selectBestBodyCandidate(
            candidates = buildList {
                // 展开态正文通常比折叠态更完整，所以在同分时优先保留它。
                add(
                    bigText,
                )
                // 折叠态正文是用户最常看到的一行，也作为稳定候选参与比较。
                add(
                    text,
                )
                // 多行样式里的每一行只参与“选正文”，不再代表多条通知。
                addAll(textLines)
                // ticker 是最末级兜底，部分旧通知样式只在这里暴露原文。
                add(
                    tickerText,
                )
            },
            title = normalizedTitle,
        )
        return listOf(
            NotificationPayload(
                title = normalizedTitle,
                text = normalizedText,
            ),
        ).filter(NotificationPayload::hasMeaningfulContent)
    }

    /**
     * 提取当前通知最主要的一条 payload。
     *
     * 用途说明：
     * - 保留这个旧接口，便于单元测试和潜在旧调用点继续工作。
     * - 新的通知监听主链路已经改为调用 `extractAll()`。
     */
    fun extract(
        title: String?,
        bigTitle: String?,
        text: String?,
        bigText: String?,
        textLines: List<String>,
        tickerText: String?,
    ): NotificationPayload {
        return extractAll(
            title = title,
            bigTitle = bigTitle,
            text = text,
            bigText = bigText,
            textLines = textLines,
            tickerText = tickerText,
        ).firstOrNull()
            ?: NotificationPayload(
                title = null,
                text = null,
            )
    }

    /**
     * 选择最适合交易解析的一段正文。
     *
     * 参数说明：
     * - candidates：正文候选集合，包含折叠态、展开态、多行样式和 ticker 文本。
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
     * 为正文候选打分，优先保留对金额、方向、时间和银行卡尾号判断更有帮助的文本。
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
        // 时间信息可以帮助用户定位账单，也能让重复判断更稳定。
        if (TIME_HINT_REGEX.containsMatchIn(candidate)) {
            score += 20
        }
        // 银行卡尾号是银行/云闪付重复通知的重要同笔交易证据。
        if (candidate.contains("尾号")) {
            score += 15
        }
        // 纯引导点击文案不应盖过真正的交易正文。
        if (DETAIL_NOISE_KEYWORDS.any(candidate::contains)) {
            score -= 25
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

/**
 * 判断当前 payload 是否至少携带了标题或正文。
 *
 * 用途说明：
 * - 某些厂商会额外分发一条标题和正文都为空的壳通知。
 * - 这类 payload 对交易识别没有帮助，反而会污染日志、数据库和排障结论。
 */
private fun NotificationPayload.hasMeaningfulContent(): Boolean {
    return !title.isNullOrBlank() || !text.isNullOrBlank()
}

/** 金额命中通常意味着这段文字更像交易正文，而不是单纯渠道名。 */
private val AMOUNT_REGEX = Regex("""(?:￥|¥|RMB|CNY)?\s*\d+(?:\.\d{1,2})?""")

/** 这些关键词能帮助后续规则判断收支方向，因此在挑选正文时给予更高优先级。 */
private val DIRECTION_KEYWORDS = listOf("支付", "付款", "收款", "到账", "入账", "转账", "扣款")

/** 时间线索能让银行动账通知从普通摘要行里脱颖而出。 */
private val TIME_HINT_REGEX = Regex("""(?:\d{1,2}:\d{2}|年|月|日|时|分)""")

/** 这些提示词通常是操作引导，不应优先成为交易正文。 */
private val DETAIL_NOISE_KEYWORDS = listOf("点击查看", "查看详情", "详情")
