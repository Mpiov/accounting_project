package com.autobook.cn.core.data.parser

import com.autobook.cn.core.data.TransactionExtractor
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.TransactionCandidate
import java.util.Locale
import javax.inject.Inject

/** 基于本地规则从通知正文中提取交易候选。 */
class LocalTransactionExtractor @Inject constructor() : TransactionExtractor {
    private val amountRegex = Regex("""(?:人民币|RMB|CNY|￥|¥)\s*([0-9]+(?:\.[0-9]{1,2})?)|([0-9]+(?:\.[0-9]{1,2})?)\s*(?:元|块)""")
    private val fallbackNumberRegex = Regex("""([0-9]+(?:\.[0-9]{1,2})?)""")
    private val orderIdRegex = Regex("""(?:订单号|交易单号|单号|订单)\s*[:：]?\s*([A-Za-z0-9-]{6,})""")

    override suspend fun extract(event: NotificationEvent): OperationResult<TransactionCandidate> {
        // 标题和正文在不同应用里承载的信息不一致，这里合并后统一解析。
        val body = listOfNotNull(event.title, event.text)
            .joinToString(separator = " ")
            .trim()
        if (body.isBlank()) {
            return OperationResult.RetryableFailure("Notification body is empty")
        }

        val amount = extractAmount(body)
            ?: return OperationResult.TerminalFailure("Amount was not parseable")

        val sourceApp = if (event.sourceApp == NotificationSourceApp.UNKNOWN) {
            NotificationSourceClassifier.detect(event.packageName)
        } else {
            event.sourceApp
        }
        val directionDecision = detectDirection(
            text = event.text.orEmpty(),
            title = event.title.orEmpty(),
            sourceApp = sourceApp,
        )
        val orderId = orderIdRegex.find(body)?.groupValues?.getOrNull(1)
        val categoryHint = detectCategory(body)
        val merchant = detectMerchant(event = event, body = body, sourceApp = sourceApp)
        val confidence = computeConfidence(
            sourceApp = sourceApp,
            body = body,
            amount = amount,
            directionDecision = directionDecision,
            orderId = orderId,
            merchant = merchant,
        )

        return OperationResult.Success(
            TransactionCandidate(
                amount = amount,
                direction = directionDecision.direction,
                merchant = merchant,
                channel = channelLabel(sourceApp),
                timestamp = event.postedAt,
                orderId = orderId,
                confidence = ParseConfidence(confidence.score),
                confidenceReason = confidence.reason,
                categoryHint = categoryHint,
                rawSource = event.rawSource,
                parsePath = "local-parser",
                sourceApp = sourceApp,
            ),
        )
    }

    /**
     * 根据正文中的方向关键词推断收支方向。
     *
     * 参数说明：
     * - text：通知正文，是方向判断的首要依据。
     * - title：通知标题，仅在正文没有提供方向线索时作为补充参考。
     * - sourceApp：来源应用，用于过滤掉“微信支付”“支付宝”这类仅表示渠道、不表示方向的标题。
     */
    private fun detectDirection(
        text: String,
        title: String,
        sourceApp: NotificationSourceApp,
    ): DirectionDecision {
        val incomeKeywords = listOf("收款", "收入", "入账", "到账", "存入", "转入", "退款")
        val expenseKeywords = listOf("支出", "付款", "支付", "扣款", "消费", "转出", "转账")
        val normalizedText = normalizeDirectionContent(
            rawContent = text,
            sourceApp = sourceApp,
        )
        val normalizedTitle = normalizeDirectionContent(
            rawContent = title,
            sourceApp = sourceApp,
        )
        val channelOnlyTitle = channelLabel(sourceApp).lowercase(Locale.ROOT)

        // 方向判断优先使用正文，避免“微信支付/支付宝”这类渠道标题把收款通知误判成支出。
        val textDecision = detectDirectionFromContent(
            normalizedContent = normalizedText,
            incomeKeywords = incomeKeywords,
            expenseKeywords = expenseKeywords,
            unknownReason = "正文未命中收支方向关键词",
        )
        if (!textDecision.shouldForceLowConfidence || normalizedTitle.isBlank()) {
            return textDecision
        }

        if (normalizedTitle == channelOnlyTitle) {
            return DirectionDecision(
                direction = MoneyDirection.UNKNOWN,
                reason = "正文未提供方向线索，标题仅表示渠道",
                shouldForceLowConfidence = true,
            )
        }

        return detectDirectionFromContent(
            normalizedContent = normalizedTitle,
            incomeKeywords = incomeKeywords,
            expenseKeywords = expenseKeywords,
            unknownReason = "标题和正文都未命中收支方向关键词",
        )
    }

    /**
     * 在做方向判断前，先去掉纯渠道词与聚合计数，避免“微信支付”误触发“支付”关键词。
     *
     * 参数说明：
     * - rawContent：原始标题或正文。
     * - sourceApp：来源应用，用于识别对应的渠道标签。
     */
    private fun normalizeDirectionContent(
        rawContent: String,
        sourceApp: NotificationSourceApp,
    ): String {
        val normalized = rawContent.lowercase(Locale.ROOT).trim()
        if (normalized.isBlank()) {
            return normalized
        }

        val channelAliases = channelAliases(sourceApp)
        return channelAliases.fold(normalized) { content, alias ->
            content
                // 先去掉“[6条]”这类聚合前缀，避免把计数和正文混在一起判断。
                .replace(AGGREGATED_NOTIFICATION_PREFIX_REGEX, "")
                // 再去掉独立出现的渠道词，保留真正描述收支行为的正文。
                .replace(alias, " ")
        }.replace(WHITESPACE_REGEX, " ").trim()
    }

    /**
     * 对单段文本执行方向关键词检测，供正文和标题复用同一套判定逻辑。
     *
     * 参数说明：
     * - normalizedContent：已经标准化的小写文本片段。
     * - incomeKeywords：收入方向关键词集合。
     * - expenseKeywords：支出方向关键词集合。
     * - unknownReason：方向未知时返回的原因描述。
     */
    private fun detectDirectionFromContent(
        normalizedContent: String,
        incomeKeywords: List<String>,
        expenseKeywords: List<String>,
        unknownReason: String,
    ): DirectionDecision {
        val hitIncome = incomeKeywords.any(normalizedContent::contains)
        val hitExpense = expenseKeywords.any(normalizedContent::contains)
        return when {
            hitIncome && !hitExpense -> {
                DirectionDecision(
                    direction = MoneyDirection.INCOME,
                    reason = "命中收入方向关键词",
                    shouldForceLowConfidence = false,
                )
            }

            hitExpense && !hitIncome -> {
                DirectionDecision(
                    direction = MoneyDirection.EXPENSE,
                    reason = "命中支出方向关键词",
                    shouldForceLowConfidence = false,
                )
            }

            hitIncome && hitExpense -> {
                DirectionDecision(
                    direction = MoneyDirection.UNKNOWN,
                    reason = "同时命中收入和支出关键词，方向冲突",
                    shouldForceLowConfidence = true,
                )
            }

            else -> {
                DirectionDecision(
                    direction = MoneyDirection.UNKNOWN,
                    reason = unknownReason,
                    shouldForceLowConfidence = true,
                )
            }
        }
    }

    private fun detectMerchant(
        event: NotificationEvent,
        body: String,
        sourceApp: NotificationSourceApp,
    ): String {
        // 商户名优先从正文模式中提取，失败时再退回标题或来源名称。
        val merchantPatterns = listOf(
            Regex("""向(.+?)付款"""),
            Regex("""商户[:：]?\s*(.+?)(?:\s|$)"""),
            Regex("""付款给(.+?)(?:\s|$)"""),
        )
        val detected = merchantPatterns
            .firstNotNullOfOrNull { pattern -> pattern.find(body)?.groupValues?.getOrNull(1)?.trim() }
        return when {
            !detected.isNullOrBlank() -> detected
            !event.title.isNullOrBlank() -> event.title.orEmpty()
            else -> channelLabel(sourceApp)
        }
    }

    /**
     * 从通知文本中挑选最可信的一笔金额。
     *
     * 参数说明：
     * - body：已经合并标题与正文后的完整通知文本。
     */
    private fun extractAmount(body: String): Double? {
        val structuredCandidates = amountRegex.findAll(body)
            .mapNotNull { match ->
                val rawAmount = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return@mapNotNull null
                val value = rawAmount.toDoubleOrNull() ?: return@mapNotNull null
                AmountCandidate(
                    value = value,
                    score = scoreAmountCandidate(body = body, matchRange = match.range, matchedText = match.value),
                )
            }
            .toList()
        val bestStructured = structuredCandidates.maxByOrNull(AmountCandidate::score)
        if (bestStructured != null) {
            return bestStructured.value
        }

        // 兜底时再退回到纯数字匹配，但会显式避开“[5条]”这类聚合计数。
        return fallbackNumberRegex.findAll(body)
            .mapNotNull { match ->
                val value = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                AmountCandidate(
                    value = value,
                    score = scoreAmountCandidate(body = body, matchRange = match.range, matchedText = match.value),
                )
            }
            .filter { candidate -> candidate.score > 0 }
            .maxByOrNull(AmountCandidate::score)
            ?.value
    }

    /**
     * 为金额候选打分，优先选中带货币符号、带小数、且靠近支付关键词的文本片段。
     *
     * 参数说明：
     * - body：完整通知文本，用于截取匹配片段附近的上下文。
     * - matchRange：本次匹配在完整文本中的位置区间。
     * - matchedText：本次命中的原始文本。
     */
    private fun scoreAmountCandidate(
        body: String,
        matchRange: IntRange,
        matchedText: String,
    ): Int {
        var score = 0
        if (matchedText.any { it == '￥' || it == '¥' } || CURRENCY_KEYWORDS.any(matchedText::contains)) {
            score += 120
        }
        if (matchedText.contains('.') || matchedText.contains("元") || matchedText.contains("块")) {
            score += 40
        }

        val contextStart = (matchRange.first - 8).coerceAtLeast(0)
        val contextEnd = (matchRange.last + 9).coerceAtMost(body.length)
        val context = body.substring(contextStart, contextEnd)
        if (AMOUNT_CONTEXT_KEYWORDS.any(context::contains)) {
            score += 35
        }

        // “[5条]微信支付”里的 5 本质是聚合计数，不应该当成金额。
        val afterText = body.substring((matchRange.last + 1).coerceAtMost(body.length))
        if (afterText.startsWith("条")) {
            score -= 160
        }
        if (context.contains("[") && context.contains("条]")) {
            score -= 80
        }
        return score
    }

    private fun detectCategory(body: String): String {
        val normalized = body.lowercase(Locale.ROOT)
        return when {
            listOf("餐", "咖啡", "奶茶", "外卖").any(normalized::contains) -> "餐饮"
            listOf("地铁", "公交", "打车", "滴滴", "加油").any(normalized::contains) -> "交通"
            listOf("淘宝", "京东", "超市", "购物").any(normalized::contains) -> "购物"
            listOf("工资", "薪资", "奖金").any(normalized::contains) -> "工资"
            listOf("转账", "转入", "转出").any(normalized::contains) -> "转账"
            else -> "待分类"
        }
    }

    private fun computeConfidence(
        sourceApp: NotificationSourceApp,
        body: String,
        amount: Double,
        directionDecision: DirectionDecision,
        orderId: String?,
        merchant: String,
    ): ConfidenceDecision {
        // 先给不同来源一个基础分，再根据成功关键词、订单号等结构化线索加分。
        var score = when (sourceApp) {
            NotificationSourceApp.WECHAT -> 0.84f
            NotificationSourceApp.ALIPAY -> 0.84f
            NotificationSourceApp.BANK_SMS -> 0.78f
            NotificationSourceApp.UNKNOWN -> 0.62f
        }
        if (body.contains("成功")) score += 0.06f
        if (orderId != null) score += 0.04f
        if (merchant.isNotBlank()) score += 0.03f
        if (amount >= 0.01) score += 0.02f
        if (directionDecision.direction != MoneyDirection.UNKNOWN) {
            // 方向明确时补少量分值，让可解释的结构化线索比“仅有金额”更容易通过自动入账阈值。
            score += 0.02f
        }

        if (directionDecision.shouldForceLowConfidence) {
            return ConfidenceDecision(
                score = score.coerceAtMost(UNKNOWN_DIRECTION_MAX_SCORE),
                reason = "${directionDecision.reason}，已降级为低置信度待确认",
            )
        }

        return ConfidenceDecision(
            score = score.coerceAtMost(0.98f),
            reason = when {
                orderId != null -> "Matched amount, merchant, and order id"
                body.contains("成功") -> "Matched amount, success keyword, and clear direction"
                else -> directionDecision.reason
            },
        )
    }

    private fun channelLabel(sourceApp: NotificationSourceApp): String = when (sourceApp) {
        NotificationSourceApp.WECHAT -> "微信支付"
        NotificationSourceApp.ALIPAY -> "支付宝"
        NotificationSourceApp.BANK_SMS -> "银行卡短信"
        NotificationSourceApp.UNKNOWN -> "通知"
    }

    /**
     * 返回某个来源应用常见的渠道别名列表，用于在方向判断前剥离纯渠道词。
     *
     * 参数说明：
     * - sourceApp：当前通知来源。
     */
    private fun channelAliases(sourceApp: NotificationSourceApp): List<String> = when (sourceApp) {
        NotificationSourceApp.WECHAT -> listOf("微信支付", "微信")
        NotificationSourceApp.ALIPAY -> listOf("支付宝")
        NotificationSourceApp.BANK_SMS -> listOf("银行卡短信", "银行卡", "短信")
        NotificationSourceApp.UNKNOWN -> emptyList()
    }
}

/** 仅在解析器内部使用的置信度中间结果。 */
private data class ConfidenceDecision(
    val score: Float,
    val reason: String,
)

/** 方向判断的中间结果，供置信度计算阶段决定是否直接降级。 */
private data class DirectionDecision(
    val direction: MoneyDirection,
    val reason: String,
    val shouldForceLowConfidence: Boolean,
)

/** 金额提取阶段的中间结果，便于在多个候选之间选出最可信的一项。 */
private data class AmountCandidate(
    val value: Double,
    val score: Int,
)

/** 方向未知时直接压到中低分区间，确保不会命中自动入账阈值。 */
private const val UNKNOWN_DIRECTION_MAX_SCORE = 0.59f

/** 带货币标记的文本通常比裸数字更接近真实支付金额。 */
private val CURRENCY_KEYWORDS = listOf("人民币", "RMB", "CNY")

/** 这些词出现在金额附近时，通常说明该数字就是交易金额而不是计数。 */
private val AMOUNT_CONTEXT_KEYWORDS = listOf("支付", "付款", "收款", "到账", "入账", "扣款", "退款", "消费")

/** 微信这类聚合通知经常带 “[6条]” 前缀，方向判断前需要先剥离。 */
private val AGGREGATED_NOTIFICATION_PREFIX_REGEX = Regex("""^\[\d+条]\s*""")

/** 清洗方向判断文本时，把多余空白折叠成单空格，便于后续关键词匹配。 */
private val WHITESPACE_REGEX = Regex("""\s+""")
