package com.autobook.cn.core.data.impl

import com.autobook.cn.core.data.LocalLlmAnalyzer
import com.autobook.cn.core.model.HIGH_CONFIDENCE_THRESHOLD
import com.autobook.cn.core.model.MEDIUM_CONFIDENCE_THRESHOLD
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.TransactionCandidate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 轻量本地语义分析器。
 *
 * 用途说明：
 * - 作为真端侧 LLM 不可用时的 baseline/fallback，实现中置信度银行转账通知的兜底语义复核。
 * - 通过银行词、转入转出语义、账户尾号等信号，模拟端侧模型对规则结果做一次复核。
 *
 * 参数说明：
 * - 当前实现无构造参数；分析规则全部内置在本地，后续接入真模型时可在不改调用方的前提下替换。
 */
@Singleton
class HeuristicLocalLlmAnalyzer @Inject constructor() : LocalLlmAnalyzer {
    /**
     * 对规则层已提取出的中置信度候选做一次本地语义增强。
     *
     * 参数说明：
     * - event：原始通知事件，提供标题、正文等原始语义。
     * - seedCandidate：规则层已提取出的基础候选，提供金额、初判方向等先验信息。
     */
    override suspend fun analyze(
        event: NotificationEvent,
        seedCandidate: TransactionCandidate,
    ): OperationResult<TransactionCandidate> {
        val mergedText = listOfNotNull(event.title, event.text)
            .joinToString(separator = " ")
            .lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (mergedText.isBlank()) {
            return OperationResult.RetryableFailure("Local heuristic analyzer received empty text")
        }

        val bankSignals = detectBankSignals(mergedText = mergedText, seedCandidate = seedCandidate)
        if (bankSignals.isEmpty()) {
            return OperationResult.RetryableFailure("Local heuristic analyzer found no bank transfer signals")
        }

        val direction = detectDirection(mergedText = mergedText, seedCandidate = seedCandidate)
        if (direction == MoneyDirection.UNKNOWN) {
            return OperationResult.RetryableFailure("Local heuristic analyzer could not confirm direction")
        }

        val inferredMerchant = detectCounterparty(mergedText = mergedText)
            ?: seedCandidate.merchant
        val inferredCategory = if (TRANSFER_KEYWORDS.any(mergedText::contains)) {
            "转账"
        } else {
            seedCandidate.categoryHint
        }
        val promotedScore = buildPromotedScore(
            baseScore = seedCandidate.confidence.score,
            bankSignals = bankSignals.size,
            direction = direction,
            mergedText = mergedText,
        )

        return OperationResult.Success(
            seedCandidate.copy(
                direction = direction,
                merchant = inferredMerchant,
                categoryHint = inferredCategory,
                confidence = ParseConfidence(promotedScore),
                confidenceReason = buildConfidenceReason(
                    bankSignals = bankSignals,
                    direction = direction,
                    inferredMerchant = inferredMerchant,
                ),
                parsePath = "${seedCandidate.parsePath}+local-heuristic",
                modelVersion = "heuristic-local-validation-v1",
                llmSuggestedAutoPost = promotedScore >= HEURISTIC_AUTO_POST_THRESHOLD,
                sourceApp = normalizeSourceApp(seedCandidate.sourceApp, mergedText),
            ),
        )
    }

    /**
     * 聚合银行语义信号。
     *
     * 参数说明：
     * - mergedText：已经合并标题与正文并做过标准化的通知文本。
     * - seedCandidate：规则层给出的基础候选，用于补充来源先验。
     */
    private fun detectBankSignals(
        mergedText: String,
        seedCandidate: TransactionCandidate,
    ): List<String> {
        val signals = mutableListOf<String>()
        if (BANK_KEYWORDS.any(mergedText::contains)) {
            signals += "bank-keyword"
        }
        if (ACCOUNT_KEYWORDS.any(mergedText::contains)) {
            signals += "account-keyword"
        }
        if (TRANSFER_KEYWORDS.any(mergedText::contains)) {
            signals += "transfer-keyword"
        }
        if (seedCandidate.sourceApp == NotificationSourceApp.BANK_SMS) {
            signals += "bank-source"
        }
        return signals
    }

    /**
     * 从文本语义中再次确认交易方向。
     *
     * 参数说明：
     * - mergedText：标准化后的通知文本。
     * - seedCandidate：规则层的基础候选，用于在文本语义不完整时兜底。
     */
    private fun detectDirection(
        mergedText: String,
        seedCandidate: TransactionCandidate,
    ): MoneyDirection {
        val hitIncome = INCOME_KEYWORDS.any(mergedText::contains)
        val hitExpense = EXPENSE_KEYWORDS.any(mergedText::contains)
        return when {
            hitExpense && !hitIncome -> MoneyDirection.EXPENSE
            hitIncome && !hitExpense -> MoneyDirection.INCOME
            seedCandidate.direction != MoneyDirection.UNKNOWN -> seedCandidate.direction
            else -> MoneyDirection.UNKNOWN
        }
    }

    /**
     * 尝试从银行通知文本中抽取对手方或摘要对象。
     *
     * 参数说明：
     * - mergedText：标准化后的通知文本。
     */
    private fun detectCounterparty(mergedText: String): String? {
        return COUNTERPARTY_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(mergedText)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }

    /**
     * 计算本地 LLM 提升后的置信度。
     *
     * 参数说明：
     * - baseScore：规则层原始置信度。
     * - bankSignals：本次命中的银行语义信号数量。
     * - direction：本地语义分析再次确认后的交易方向。
     * - mergedText：标准化后的通知文本，用于判断是否含账户尾号等额外证据。
     */
    private fun buildPromotedScore(
        baseScore: Float,
        bankSignals: Int,
        direction: MoneyDirection,
        mergedText: String,
    ): Float {
        var score = baseScore.coerceAtLeast(MEDIUM_CONFIDENCE_THRESHOLD)
        score += (bankSignals * 0.04f)
        if (direction != MoneyDirection.UNKNOWN) {
            score += 0.05f
        }
        if (ACCOUNT_TAIL_REGEX.containsMatchIn(mergedText)) {
            score += 0.03f
        }
        if (AMOUNT_SETTLEMENT_KEYWORDS.any(mergedText::contains)) {
            score += 0.02f
        }
        return score.coerceIn(MEDIUM_CONFIDENCE_THRESHOLD, 0.93f)
    }

    /**
     * 生成提升后的置信度说明，便于后续审计与待确认页展示。
     *
     * 参数说明：
     * - bankSignals：本次命中的银行语义信号列表。
     * - direction：本次确认出的交易方向。
     * - inferredMerchant：本次推断出的对手方名称。
     */
    private fun buildConfidenceReason(
        bankSignals: List<String>,
        direction: MoneyDirection,
        inferredMerchant: String,
    ): String {
        val directionLabel = when (direction) {
            MoneyDirection.INCOME -> "转入"
            MoneyDirection.EXPENSE -> "转出"
            MoneyDirection.UNKNOWN -> "未知"
        }
        return "Heuristic local semantic validation promoted candidate after bank semantic checks: " +
            "direction=$directionLabel, merchant=$inferredMerchant, signals=${bankSignals.joinToString()}"
    }

    /**
     * 当规则层来源仍未知时，根据银行语义再次规范来源类型。
     *
     * 参数说明：
     * - sourceApp：规则层候选当前的来源类型。
     * - mergedText：标准化后的通知文本。
     */
    private fun normalizeSourceApp(
        sourceApp: NotificationSourceApp,
        mergedText: String,
    ): NotificationSourceApp {
        return if (sourceApp == NotificationSourceApp.UNKNOWN && BANK_KEYWORDS.any(mergedText::contains)) {
            NotificationSourceApp.BANK_SMS
        } else {
            sourceApp
        }
    }
}

/** 端侧银行通知常见的银行名称或账户体系关键词。 */
private val BANK_KEYWORDS = listOf(
    "银行",
    "工行",
    "农行",
    "中行",
    "建行",
    "招行",
    "交行",
    "邮储",
    "浦发",
    "平安银行",
    "兴业银行",
    "民生银行",
    "华夏银行",
)

/** 出现在银行通知中的账户语义提示。 */
private val ACCOUNT_KEYWORDS = listOf(
    "账户",
    "卡号",
    "尾号",
    "借记卡",
    "信用卡",
)

/** 可直接说明转账语义的关键词。 */
private val TRANSFER_KEYWORDS = listOf(
    "转账",
    "转出",
    "转入",
    "汇款",
    "汇出",
    "汇入",
)

/** 能较强说明资金已完成结算的关键词。 */
private val AMOUNT_SETTLEMENT_KEYWORDS = listOf(
    "成功",
    "已汇出",
    "已转出",
    "已到账",
    "到账",
)

/** 说明入账方向的关键词。 */
private val INCOME_KEYWORDS = listOf(
    "转入",
    "收入",
    "入账",
    "到账",
    "收款",
)

/** 说明出账方向的关键词。 */
private val EXPENSE_KEYWORDS = listOf(
    "转出",
    "支出",
    "扣款",
    "付款",
    "汇出",
)

/** 银行通知里常见的账户尾号表达，用于增强语义确信度。 */
private val ACCOUNT_TAIL_REGEX = Regex("""(?:尾号|卡号)\s*\d{2,6}""")

/** 针对常见银行转账文案抽取对手方名称的模式。 */
private val COUNTERPARTY_PATTERNS = listOf(
    Regex("""向(.+?)转(?:账|出)"""),
    Regex("""收款人[:：]?\s*(.+?)(?:\s|$)"""),
    Regex("""付款对象[:：]?\s*(.+?)(?:\s|$)"""),
    Regex("""交易对手[:：]?\s*(.+?)(?:\s|$)"""),
)

/** 启发式 fallback 只有在分值足够高时才建议自动入账，避免兜底实现过于激进。 */
private const val HEURISTIC_AUTO_POST_THRESHOLD = HIGH_CONFIDENCE_THRESHOLD
