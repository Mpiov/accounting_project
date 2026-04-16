package com.autobook.cn.core.data.parser

import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationSourceApp
import java.text.Normalizer
import java.util.Locale
import kotlin.LazyThreadSafetyMode.SYNCHRONIZED

/**
 * 标准化后的通知文本窗口。
 *
 * 用途说明：
 * - 统一承载“标题 + 正文”归一化结果，并显式保存用于评分的前 80 字窗口。
 * - 置信度打分、关键词扫描和结构化字段提取都只看这个窗口，和文档保持一致。
 *
 * 参数说明：
 * - mergedText：原始标题与正文合并后的完整文本。
 * - normalizedText：做过全半角归一、小写化与空白折叠后的完整文本。
 * - scoringText：本轮评分真正参与扫描的前 80 字窗口。
 * - isAggregatedSummary：是否命中了聚合通知前缀，例如 `[5条]`。
 */
internal data class NotificationTextWindow(
    val mergedText: String,
    val normalizedText: String,
    val scoringText: String,
    val isAggregatedSummary: Boolean,
)

/**
 * 金额抽取结果。
 *
 * 参数说明：
 * - amount：解析出的金额。
 * - kind：当前金额证据强度；用于后续金额组加分和“金额可靠”门槛判断。
 */
internal data class AmountParseResult(
    val amount: Double,
    val kind: AmountSignalKind,
)

/** 金额证据强度。 */
internal enum class AmountSignalKind {
    WITH_CURRENCY,
    WITH_UNIT,
    BARE_NUMBER,
}

/**
 * 来源规则命中结果。
 *
 * 参数说明：
 * - sourceClass：当前命中的来源语义类别。
 * - channelLabel：给候选记录展示的渠道标签。
 * - matchedPattern：命中的包名模式；仅用于调试说明。
 */
internal data class ResolvedSourceRule(
    val sourceClass: NotificationSourceClass,
    val channelLabel: String,
    val matchedPattern: String? = null,
)

/** 置信度规则层内部使用的来源语义类别。 */
internal enum class NotificationSourceClass {
    TRUSTED_PAYMENT,
    TRUSTED_BANK,
    TRUSTED_FINANCE,
    UNKNOWN,
}

/**
 * 方向识别结果。
 *
 * 参数说明：
 * - direction：当前识别出的收支方向。
 * - hitIncome：是否命中过收入词。
 * - hitExpense：是否命中过支出词。
 * - reason：便于回填到置信度解释中的简短说明。
 */
internal data class DirectionDecision(
    val direction: MoneyDirection,
    val hitIncome: Boolean,
    val hitExpense: Boolean,
    val reason: String,
)

/**
 * 语义特征汇总。
 *
 * 用途说明：
 * - 把 AC 自动机、多组正则和来源规则统一沉淀成一份可解释的特征快照。
 * - 后续打分器和高置信度硬门槛都只消费这份结构，避免逻辑再散回解析器入口。
 */
internal data class NotificationSemanticFeatures(
    val scoringText: String,
    val amountKind: AmountSignalKind,
    val directionDecision: DirectionDecision,
    val hasTailNumber: Boolean,
    val hasStrongAccountKeyword: Boolean,
    val hasGenericAccountKeyword: Boolean,
    val hasExplicitTime: Boolean,
    val hasTransactionSceneKeyword: Boolean,
    val hasCounterpartyKeyword: Boolean,
    val hasExtractedMerchant: Boolean,
    val hasCurrencyKeyword: Boolean,
    val hasBankEntityKeyword: Boolean,
    val hasFinanceSettledKeyword: Boolean,
    val hasMarketingKeyword: Boolean,
    val hasAssetValuationKeyword: Boolean,
    val hasSettlementEvidenceKeyword: Boolean,
    val hasFinanceEntityKeyword: Boolean,
    val matchedPatterns: Set<String>,
) {
    /** 文档里的“金额可靠”更接近带币种或单位的明确金额，不把裸数字视为高可信证据。 */
    val hasReliableAmount: Boolean
        get() = amountKind != AmountSignalKind.BARE_NUMBER

    /** 银行动账高置信度要求的“强结构信号”。 */
    val hasStrongBankStructureSignal: Boolean
        get() = hasTailNumber || hasStrongAccountKeyword || hasGenericAccountKeyword || hasExplicitTime || hasTransactionSceneKeyword

    /** 方向冲突会触发风险扣分。 */
    val hasDirectionConflict: Boolean
        get() = directionDecision.hitIncome && directionDecision.hitExpense

    /** 资产估值类只有在没有结算证据时才应该被识别为风险消息。 */
    val hasUnsettledAssetValuationRisk: Boolean
        get() = hasAssetValuationKeyword && !hasSettlementEvidenceKeyword
}

/**
 * 最终置信度判定结果。
 *
 * 参数说明：
 * - score：供 `ParseConfidence` 使用的最终分数。
 * - reason：面向日志、待确认页和调试注入的解释文本。
 */
internal data class ConfidenceDecision(
    val score: Float,
    val reason: String,
)

/**
 * 规则树预热入口。
 *
 * 用途说明：
 * - AC 自动机需要先建 trie 和 fail 指针，这里提供一个显式预热方法，便于在 app 启动时提前完成。
 * - 所有自动机都通过 `lazy(SYNCHRONIZED)` 只会构建一次；后续通知解析阶段只读复用。
 */
object NotificationRuleWarmup {
    fun warmUp() {
        SOURCE_RULE_MATCHER
        FINANCE_PRODUCT_MATCHER
        SEMANTIC_TAG_MATCHER
        CATEGORY_MATCHER
        AMOUNT_CONTEXT_MATCHER
    }
}

/**
 * 统一负责标题/正文归一与 80 字窗口裁剪。
 */
internal object NotificationTextWindowBuilder {
    fun build(
        title: String?,
        text: String?,
    ): NotificationTextWindow {
        val merged = listOfNotNull(title, text)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(separator = " ")
        val normalized = Normalizer.normalize(merged, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE_REGEX, " ")
            .trim()
        return NotificationTextWindow(
            mergedText = merged,
            normalizedText = normalized,
            scoringText = normalized.take(SCORING_WINDOW_LENGTH),
            isAggregatedSummary = AGGREGATED_NOTIFICATION_PREFIX_REGEX.containsMatchIn(normalized),
        )
    }
}

/**
 * 来源规则表。
 *
 * 用途说明：
 * - 用 AC 自动机替代散落的 `if / else` 包名判断，便于持续补充来源模式。
 * - 同时允许用“零钱通/余额宝/收益到账”这类文本信号把支付大包名细分成金融收益来源。
 */
internal object NotificationSourceRuleTable {
    fun resolve(
        packageName: String,
        sourceApp: NotificationSourceApp,
        textWindow: NotificationTextWindow,
    ): ResolvedSourceRule {
        val normalizedPackage = packageName.lowercase(Locale.ROOT)
        val financeProduct = FINANCE_PRODUCT_MATCHER.find(textWindow.scoringText)
            .firstOrNull()
            ?.pattern
        if (financeProduct != null && sourceApp != NotificationSourceApp.BANK_SMS) {
            return ResolvedSourceRule(
                sourceClass = NotificationSourceClass.TRUSTED_FINANCE,
                channelLabel = financeProduct,
                matchedPattern = financeProduct,
            )
        }

        val matchedRule = SOURCE_RULE_MATCHER.find(normalizedPackage)
            .maxWithOrNull(
                compareBy<AhoCorasickAutomaton.Match<SourceRule>> { it.value.priority }
                    .thenBy { it.pattern.length },
            )
            ?.value

        return when {
            matchedRule != null -> matchedRule.toResolvedRule()
            sourceApp == NotificationSourceApp.WECHAT -> DEFAULT_WECHAT_RULE.toResolvedRule()
            sourceApp == NotificationSourceApp.ALIPAY -> DEFAULT_ALIPAY_RULE.toResolvedRule()
            sourceApp == NotificationSourceApp.BANK_SMS -> DEFAULT_BANK_RULE.toResolvedRule()
            else -> UNKNOWN_SOURCE_RULE.toResolvedRule()
        }
    }
}

/**
 * 金额提取器。
 *
 * 用途说明：
 * - 在评分窗口内优先挑选“最像真实交易金额”的一项。
 * - 继续保留对聚合通知计数 `[5条]` 的抑制，避免把条数误识别成金额。
 */
internal object NotificationAmountParser {
    fun extract(text: String): AmountParseResult? {
        val structured = CURRENCY_AMOUNT_REGEX.findAll(text)
            .mapNotNull { match ->
                val rawAmount = match.groupValues.drop(1).firstOrNull(String::isNotBlank) ?: return@mapNotNull null
                val amount = rawAmount.toDoubleOrNull() ?: return@mapNotNull null
                val kind = if (match.value.contains("元") || match.value.contains("块")) {
                    AmountSignalKind.WITH_UNIT
                } else {
                    AmountSignalKind.WITH_CURRENCY
                }
                AmountCandidate(
                    amount = amount,
                    kind = kind,
                    score = scoreAmountCandidate(text = text, matchRange = match.range, matchedText = match.value),
                )
            }
            .toList()
        val bestStructured = structured.maxByOrNull(AmountCandidate::score)
        if (bestStructured != null) {
            return AmountParseResult(bestStructured.amount, bestStructured.kind)
        }

        return BARE_AMOUNT_REGEX.findAll(text)
            .mapNotNull { match ->
                val amount = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                AmountCandidate(
                    amount = amount,
                    kind = AmountSignalKind.BARE_NUMBER,
                    score = scoreAmountCandidate(text = text, matchRange = match.range, matchedText = match.value),
                )
            }
            .filter { candidate -> candidate.score > 0 }
            .maxByOrNull(AmountCandidate::score)
            ?.let { AmountParseResult(amount = it.amount, kind = it.kind) }
    }

    /**
     * 复用“带币种优先、靠近交易上下文优先、聚合计数降权”的策略。
     */
    private fun scoreAmountCandidate(
        text: String,
        matchRange: IntRange,
        matchedText: String,
    ): Int {
        var score = 0
        if (matchedText.any { it == '￥' || it == '¥' } || matchedText.contains("rmb") || matchedText.contains("cny") || matchedText.contains("人民币")) {
            score += 120
        }
        if (matchedText.contains('.') || matchedText.contains("元") || matchedText.contains("块")) {
            score += 40
        }

        val contextStart = (matchRange.first - 8).coerceAtLeast(0)
        val contextEnd = (matchRange.last + 9).coerceAtMost(text.length)
        val context = text.substring(contextStart, contextEnd)
        if (AMOUNT_CONTEXT_MATCHER.find(context).isNotEmpty()) {
            score += 35
        }

        val afterText = text.substring((matchRange.last + 1).coerceAtMost(text.length))
        if (afterText.startsWith("条")) {
            score -= 160
        }
        if (context.contains("[") && context.contains("条]")) {
            score -= 80
        }
        return score
    }
}

/**
 * 方向探测器。
 *
 * 用途说明：
 * - 使用 AC 自动机批量匹配收入/支出词，并在匹配前移除“微信支付/支付宝”这类纯渠道词。
 * - 避免 `微信支付` 里的“支付”把收款通知误判成支出。
 */
internal object NotificationDirectionDetector {
    fun detect(
        scoringText: String,
        sourceApp: NotificationSourceApp,
    ): DirectionDecision {
        val normalized = stripChannelNoise(
            text = scoringText,
            sourceApp = sourceApp,
        )
        val matchedTags = SEMANTIC_TAG_MATCHER.distinctValues(normalized)
        val hitIncome = SemanticTag.INCOME in matchedTags
        val hitExpense = SemanticTag.EXPENSE in matchedTags
        val direction = when {
            hitIncome && !hitExpense -> MoneyDirection.INCOME
            hitExpense && !hitIncome -> MoneyDirection.EXPENSE
            else -> MoneyDirection.UNKNOWN
        }
        val reason = when {
            hitIncome && hitExpense -> "同时命中收入和支出词"
            hitIncome -> "命中收入方向词"
            hitExpense -> "命中支出方向词"
            else -> "未命中方向词"
        }
        return DirectionDecision(
            direction = direction,
            hitIncome = hitIncome,
            hitExpense = hitExpense,
            reason = reason,
        )
    }

    private fun stripChannelNoise(
        text: String,
        sourceApp: NotificationSourceApp,
    ): String {
        val aliases = when (sourceApp) {
            NotificationSourceApp.WECHAT -> listOf("微信支付", "微信")
            NotificationSourceApp.ALIPAY -> listOf("支付宝")
            NotificationSourceApp.BANK_SMS -> listOf("银行卡短信")
            NotificationSourceApp.UNKNOWN -> emptyList()
        }
        var normalized = text.replace(AGGREGATED_NOTIFICATION_PREFIX_REGEX, "")
        aliases.forEach { alias ->
            normalized = normalized.replace(alias, " ")
        }
        return normalized.replace(WHITESPACE_REGEX, " ").trim()
    }
}

/** 商户和对手方提取器。 */
internal object NotificationMerchantExtractor {
    fun extract(
        text: String,
        fallbackTitle: String?,
        channelLabel: String,
    ): String {
        val merchant = COUNTERPARTY_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf(String::isNotBlank)
        }
        return when {
            !merchant.isNullOrBlank() -> merchant
            !fallbackTitle.isNullOrBlank() && fallbackTitle.trim() !in GENERIC_NOTIFICATION_TITLES && fallbackTitle.trim() != channelLabel -> fallbackTitle.trim()
            else -> channelLabel
        }
    }
}

/** 分类提示器。 */
internal object NotificationCategoryClassifier {
    fun detect(
        text: String,
        hasFinanceSettledKeyword: Boolean,
    ): String {
        if (hasFinanceSettledKeyword) {
            return "收益"
        }

        val matchedCategory = CATEGORY_MATCHER.find(text)
            .groupBy { it.value }
            .maxByOrNull { it.value.size }
            ?.key
        return when (matchedCategory) {
            CategoryTag.DINING -> "餐饮"
            CategoryTag.TRANSPORT -> "交通"
            CategoryTag.SHOPPING -> "购物"
            CategoryTag.SALARY -> "工资"
            CategoryTag.TRANSFER -> "转账"
            null -> "待分类"
        }
    }
}

/**
 * 语义特征分析器。
 *
 * 用途说明：
 * - 所有关键词匹配都走 AC 自动机，只保留时间、尾号这类更适合正则的结构信号。
 */
internal object NotificationSemanticAnalyzer {
    fun analyze(
        textWindow: NotificationTextWindow,
        amountResult: AmountParseResult,
        directionDecision: DirectionDecision,
        merchant: String,
        channelLabel: String,
    ): NotificationSemanticFeatures {
        val matches = SEMANTIC_TAG_MATCHER.find(textWindow.scoringText)
        val matchedTags = matches.mapTo(linkedSetOf(), AhoCorasickAutomaton.Match<SemanticTag>::value)
        val matchedPatterns = matches.mapTo(linkedSetOf(), AhoCorasickAutomaton.Match<SemanticTag>::pattern)
        return NotificationSemanticFeatures(
            scoringText = textWindow.scoringText,
            amountKind = amountResult.kind,
            directionDecision = directionDecision,
            hasTailNumber = ACCOUNT_TAIL_REGEX.containsMatchIn(textWindow.scoringText),
            hasStrongAccountKeyword = SemanticTag.ACCOUNT_STRONG in matchedTags,
            hasGenericAccountKeyword = SemanticTag.ACCOUNT_GENERIC in matchedTags,
            hasExplicitTime = EXPLICIT_TIME_PATTERNS.any { pattern -> pattern.containsMatchIn(textWindow.scoringText) },
            hasTransactionSceneKeyword = SemanticTag.TRANSACTION_SCENE in matchedTags,
            hasCounterpartyKeyword = SemanticTag.COUNTERPARTY in matchedTags,
            hasExtractedMerchant = merchant.isNotBlank() && merchant != "通知" && merchant != "银行卡短信" && merchant != channelLabel,
            hasCurrencyKeyword = amountResult.kind == AmountSignalKind.WITH_CURRENCY || SemanticTag.CURRENCY in matchedTags,
            hasBankEntityKeyword = SemanticTag.BANK_ENTITY in matchedTags,
            hasFinanceSettledKeyword = SemanticTag.FINANCE_SETTLED in matchedTags,
            hasMarketingKeyword = SemanticTag.MARKETING in matchedTags,
            hasAssetValuationKeyword = SemanticTag.ASSET_VALUATION in matchedTags,
            hasSettlementEvidenceKeyword = SemanticTag.SETTLEMENT_EVIDENCE in matchedTags,
            hasFinanceEntityKeyword = SemanticTag.FINANCE_ENTITY in matchedTags,
            matchedPatterns = matchedPatterns,
        )
    }
}

/**
 * 统一打分器。
 *
 * 用途说明：
 * - 先计算来源先验和语义组加分，再叠加风险扣分。
 * - 当前按用户最新口径实现为：每组内每一行最多加一次，同组不同行允许叠加。
 */
internal object NotificationConfidenceScorer {
    fun score(
        sourceRule: ResolvedSourceRule,
        features: NotificationSemanticFeatures,
    ): ConfidenceDecision {
        var score = sourceRule.sourceClass.priorScore
        val reasonParts = mutableListOf("来源=${sourceRule.sourceClass.name}(${formatScore(sourceRule.sourceClass.priorScore)})")

        val amountBonus = when (features.amountKind) {
            AmountSignalKind.WITH_CURRENCY -> 0.18f
            AmountSignalKind.WITH_UNIT -> 0.14f
            AmountSignalKind.BARE_NUMBER -> 0.06f
        }
        score += amountBonus
        reasonParts += "金额组+${formatScore(amountBonus)}"

        if (features.directionDecision.hitIncome) {
            score += 0.18f
            reasonParts += "方向组(收入)+0.18"
        }
        if (features.directionDecision.hitExpense) {
            score += 0.18f
            reasonParts += "方向组(支出)+0.18"
        }

        var accountBonus = 0f
        if (features.hasTailNumber) {
            accountBonus += 0.14f
        }
        if (features.hasStrongAccountKeyword) {
            accountBonus += 0.12f
        }
        if (features.hasGenericAccountKeyword) {
            accountBonus += 0.08f
        }
        if (accountBonus > 0f) {
            score += accountBonus
            reasonParts += "账户组+${formatScore(accountBonus)}"
        }

        if (features.hasExplicitTime) {
            score += 0.06f
            reasonParts += "时间组+0.06"
        }
        if (features.hasTransactionSceneKeyword) {
            score += 0.10f
            reasonParts += "动账组+0.10"
        }

        var counterpartyBonus = 0f
        if (features.hasCounterpartyKeyword) {
            counterpartyBonus += 0.04f
        }
        if (features.hasExtractedMerchant) {
            counterpartyBonus += 0.02f
        }
        counterpartyBonus = counterpartyBonus.coerceAtMost(0.06f)
        if (counterpartyBonus > 0f) {
            score += counterpartyBonus
            reasonParts += "对手方组+${formatScore(counterpartyBonus)}"
        }

        if (features.hasCurrencyKeyword) {
            score += 0.04f
            reasonParts += "币种组+0.04"
        }
        if (features.hasBankEntityKeyword) {
            score += 0.06f
            reasonParts += "银行实体组+0.06"
        }
        if (features.hasFinanceEntityKeyword) {
            score += 0.06f
            reasonParts += "金融实体组+0.06"
        }
        if (features.hasFinanceSettledKeyword) {
            score += 0.10f
            reasonParts += "收益结算组+0.10"
        }

        if (features.hasDirectionConflict) {
            score -= 0.20f
            reasonParts += "方向冲突-0.20"
        }
        if (!features.hasReliableAmount) {
            score -= 0.25f
            reasonParts += "金额不可靠-0.25"
        }
        if (features.hasMarketingKeyword) {
            score -= 0.25f
            reasonParts += "营销资讯-0.25"
        }
        if (features.hasUnsettledAssetValuationRisk) {
            reasonParts += "命中资产估值风险词"
        }

        return ConfidenceDecision(
            // 用户当前要求取消总分上限，因此这里只保留 0 分下界，不再对高分做统一截顶。
            score = score.coerceAtLeast(0f),
            reason = reasonParts.joinToString(separator = "；"),
        )
    }

    private fun formatScore(score: Float): String = String.format(Locale.ROOT, "%.2f", score)
}

private data class SourceRule(
    val sourceClass: NotificationSourceClass,
    val channelLabel: String,
    val packagePatterns: List<String>,
    val priority: Int,
) {
    fun toResolvedRule(): ResolvedSourceRule = ResolvedSourceRule(
        sourceClass = sourceClass,
        channelLabel = channelLabel,
        matchedPattern = packagePatterns.firstOrNull(),
    )
}

private data class AmountCandidate(
    val amount: Double,
    val kind: AmountSignalKind,
    val score: Int,
)

private enum class SemanticTag {
    INCOME,
    EXPENSE,
    ACCOUNT_STRONG,
    ACCOUNT_GENERIC,
    TRANSACTION_SCENE,
    COUNTERPARTY,
    CURRENCY,
    BANK_ENTITY,
    FINANCE_SETTLED,
    MARKETING,
    ASSET_VALUATION,
    SETTLEMENT_EVIDENCE,
    FINANCE_ENTITY,
}

private enum class CategoryTag {
    DINING,
    TRANSPORT,
    SHOPPING,
    SALARY,
    TRANSFER,
}

private val NotificationSourceClass.priorScore: Float
    get() = when (this) {
        NotificationSourceClass.TRUSTED_PAYMENT -> 0.52f
        NotificationSourceClass.TRUSTED_BANK -> 0.48f
        NotificationSourceClass.TRUSTED_FINANCE -> 0.34f
        NotificationSourceClass.UNKNOWN -> 0.25f
    }

private val DEFAULT_WECHAT_RULE = SourceRule(
    sourceClass = NotificationSourceClass.TRUSTED_PAYMENT,
    channelLabel = "微信支付",
    packagePatterns = listOf("tencent.mm"),
    priority = 90,
)

private val DEFAULT_ALIPAY_RULE = SourceRule(
    sourceClass = NotificationSourceClass.TRUSTED_PAYMENT,
    channelLabel = "支付宝",
    packagePatterns = listOf("alipay"),
    priority = 90,
)

private val DEFAULT_BANK_RULE = SourceRule(
    sourceClass = NotificationSourceClass.TRUSTED_BANK,
    channelLabel = "银行卡短信",
    packagePatterns = listOf("sms"),
    priority = 80,
)

private val UNKNOWN_SOURCE_RULE = SourceRule(
    sourceClass = NotificationSourceClass.UNKNOWN,
    channelLabel = "通知",
    packagePatterns = emptyList(),
    priority = 0,
)

private val SOURCE_RULES = listOf(
    DEFAULT_WECHAT_RULE,
    DEFAULT_ALIPAY_RULE,
    SourceRule(
        sourceClass = NotificationSourceClass.TRUSTED_BANK,
        channelLabel = "银行卡通知",
        packagePatterns = listOf(
            "chinamworld",
            "ccb",
            "icbc",
            "abchina",
            "boc",
            "cmbchina",
            "cmb",
            "bankcomm",
            "psbc",
            "spdb",
            "pingan",
            "cib",
            "cmbc",
            "hxb",
            "bank",
            "mms",
            "messaging",
            "sms",
        ),
        priority = 80,
    ),
    SourceRule(
        sourceClass = NotificationSourceClass.TRUSTED_FINANCE,
        channelLabel = "金融收益通知",
        packagePatterns = listOf(
            "antfortune",
            "eastmoney",
            "xueqiu",
            "tonghuashun",
            "fund",
            "securities",
            "stock",
            "wallet",
        ),
        priority = 70,
    ),
)

private val SOURCE_RULE_MATCHER by lazy(SYNCHRONIZED) {
    AhoCorasickAutomaton.Builder<SourceRule>().apply {
        SOURCE_RULES.forEach { rule ->
            rule.packagePatterns.forEach { pattern ->
                add(pattern, rule)
            }
        }
    }.build()
}

private val FINANCE_PRODUCT_MATCHER by lazy(SYNCHRONIZED) {
    AhoCorasickAutomaton.Builder<String>().apply {
        add("零钱通", "零钱通")
        add("余额宝", "余额宝")
        add("理财通", "理财通")
    }.build()
}

private val SEMANTIC_TAG_MATCHER by lazy(SYNCHRONIZED) {
    AhoCorasickAutomaton.Builder<SemanticTag>().apply {
        listOf("收入", "入账", "到账", "转入", "收款", "利息到账", "收益到账", "分红到账", "派息到账")
            .forEach { add(it, SemanticTag.INCOME) }
        listOf("支付", "付款", "扣款", "消费", "支出", "转出", "转账支出")
            .forEach { add(it, SemanticTag.EXPENSE) }
        listOf("储蓄账户", "储蓄卡", "借记卡", "信用卡")
            .forEach { add(it, SemanticTag.ACCOUNT_STRONG) }
        listOf("账户", "银行卡", "卡号")
            .forEach { add(it, SemanticTag.ACCOUNT_GENERIC) }
        listOf("动账提醒", "账户通知", "交易提醒", "到账通知", "入账通知", "收款通知", "余额变动")
            .forEach { add(it, SemanticTag.TRANSACTION_SCENE) }
        listOf("商户", "收款方", "收款人", "付款对象", "交易对手")
            .forEach { add(it, SemanticTag.COUNTERPARTY) }
        listOf("人民币", "rmb", "cny", "￥", "¥")
            .forEach { add(it, SemanticTag.CURRENCY) }
        listOf("建设银行", "建行", "工商银行", "工行", "农业银行", "农行", "中国银行", "中行", "招商银行", "招行", "交通银行", "交行", "邮储", "浦发", "平安银行", "兴业银行", "民生银行", "华夏银行")
            .forEach { add(it, SemanticTag.BANK_ENTITY) }
        listOf("利息到账", "收益到账", "派息到账", "分红到账", "赎回到账")
            .forEach { add(it, SemanticTag.FINANCE_SETTLED) }
        listOf("活动", "福利", "优惠", "推荐", "立即参与", "点击领取", "去看看", "广告", "资讯")
            .forEach { add(it, SemanticTag.MARKETING) }
        listOf("浮盈", "持仓收益", "净值估算", "市值", "涨跌幅", "收益率", "行情")
            .forEach { add(it, SemanticTag.ASSET_VALUATION) }
        listOf("到账", "入账", "卖出成交", "赎回到账", "派息到账", "利息到账", "收益到账")
            .forEach { add(it, SemanticTag.SETTLEMENT_EVIDENCE) }
        listOf("零钱通", "余额宝", "理财通", "基金", "理财", "券商", "证券", "股票")
            .forEach { add(it, SemanticTag.FINANCE_ENTITY) }
    }.build()
}

private val CATEGORY_MATCHER by lazy(SYNCHRONIZED) {
    AhoCorasickAutomaton.Builder<CategoryTag>().apply {
        listOf("餐", "咖啡", "奶茶", "外卖").forEach { add(it, CategoryTag.DINING) }
        listOf("地铁", "公交", "打车", "滴滴", "加油").forEach { add(it, CategoryTag.TRANSPORT) }
        listOf("淘宝", "京东", "超市", "购物").forEach { add(it, CategoryTag.SHOPPING) }
        listOf("工资", "薪资", "奖金").forEach { add(it, CategoryTag.SALARY) }
        listOf("转账", "转入", "转出", "汇款").forEach { add(it, CategoryTag.TRANSFER) }
    }.build()
}

private val AMOUNT_CONTEXT_MATCHER by lazy(SYNCHRONIZED) {
    AhoCorasickAutomaton.Builder<String>().apply {
        listOf("支付", "付款", "收款", "到账", "入账", "扣款", "退款", "消费").forEach { add(it, it) }
    }.build()
}

private val COUNTERPARTY_PATTERNS = listOf(
    Regex("""向(.+?)付款"""),
    Regex("""向(.+?)转(?:账|出)"""),
    Regex("""商户[:：]?\s*(.+?)(?:\s|$)"""),
    Regex("""付款给(.+?)(?:\s|$)"""),
    Regex("""收款人[:：]?\s*(.+?)(?:\s|$)"""),
    Regex("""付款对象[:：]?\s*(.+?)(?:\s|$)"""),
    Regex("""交易对手[:：]?\s*(.+?)(?:\s|$)"""),
)

private val GENERIC_NOTIFICATION_TITLES = setOf(
    "微信支付",
    "支付宝",
    "动账提醒",
    "账户通知",
    "交易提醒",
    "到账通知",
    "入账通知",
    "收款通知",
    "余额变动",
    "零钱通",
    "余额宝",
)

private val EXPLICIT_TIME_PATTERNS = listOf(
    Regex("""\d{4}-\d{1,2}-\d{1,2}\s+\d{1,2}:\d{2}"""),
    Regex("""\d{1,2}月\d{1,2}日\d{1,2}(?:时|点)\d{1,2}分"""),
    Regex("""\d{1,2}:\d{2}"""),
)

private val CURRENCY_AMOUNT_REGEX = Regex(
    """(?:人民币|rmb|cny|￥|¥)\s*([0-9]+(?:\.[0-9]{1,2})?)|([0-9]+(?:\.[0-9]{1,2})?)\s*(?:元|块)""",
)

private val BARE_AMOUNT_REGEX = Regex("""([0-9]+(?:\.[0-9]{1,2})?)""")

private val ACCOUNT_TAIL_REGEX = Regex("""(?:尾号|卡号)\s*[0-9]{2,6}""")

private val WHITESPACE_REGEX = Regex("""\s+""")

private val AGGREGATED_NOTIFICATION_PREFIX_REGEX = Regex("""^\[\d+条]\s*""")

/**
 * 评分窗口长度。
 *
 * 用途说明：
 * - 当前按最新方案把统一评分窗口从 50 提升到 80，尽量让较长的银行动账通知保留更多有效上下文。
 * - 仍然保留截断窗口，避免后续规则层对超长聚合通知做全文重扫。
 */
private const val SCORING_WINDOW_LENGTH = 80
