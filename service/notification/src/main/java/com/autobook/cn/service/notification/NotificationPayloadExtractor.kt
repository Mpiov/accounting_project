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
    /**
     * 提取并标准化当前通知的所有候选 payload。
     *
     * 用途说明：
     * - 按最新规则，`textLines` 代表同一应用下聚合在一条通知里的多条候选消息时，
     *   不再把所有行直接拼成一段正文，而是只保留首行和尾行，视作两条独立通知。
     * - 中间行会被主动过滤；后续如果首尾里出现重复，交给数据库去重层处理。
     * - 如果没有 `textLines`，则继续回退到“单条最佳正文”策略。
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
        val linePayloads = buildLinePayloads(
            normalizedTitle = normalizedTitle,
            textLines = textLines,
        )
        if (linePayloads.isNotEmpty()) {
            // 纯空 payload 继续进入链路只会制造“空通知处理失败”的噪音记录，因此这里直接丢弃。
            return linePayloads.filter(NotificationPayload::hasMeaningfulContent)
        }

        val normalizedText = selectBestBodyCandidate(
            candidates = listOf(
                bigText,
                text,
                tickerText,
            ),
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

    /**
     * 基于 `textLines` 构造首行/尾行两条独立 payload。
     *
     * 用途说明：
     * - 按最新方案，同一条聚合通知里只保留第一行和最后一行，把它们分别送进后续解析链路。
     * - 中间行全部过滤，不再尝试整段拼接。
     * - 如果首行和尾行清洗后相同，则自动折叠成一条，避免同一回调内部立即制造重复。
     *
     * 参数说明：
     * - normalizedTitle：已经归一化后的标题。
     * - textLines：原始多行正文集合。
     */
    private fun buildLinePayloads(
        normalizedTitle: String?,
        textLines: List<String>,
    ): List<NotificationPayload> {
        val cleanedLines = textLines
            .mapNotNull(::normalize)
            .distinct()
            .filterNot { line -> line == normalizedTitle }
        if (cleanedLines.isEmpty()) {
            return emptyList()
        }

        val boundaryLines = listOfNotNull(
            cleanedLines.firstOrNull(),
            cleanedLines.lastOrNull(),
        ).distinct()
        return boundaryLines.map { line ->
            NotificationPayload(
                title = normalizedTitle,
                text = line,
            )
        }
    }
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
