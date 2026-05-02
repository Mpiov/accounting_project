package com.autobook.cn.core.data.parser

import java.util.Locale

/**
 * 免费版普通规则置信度计算器。
 *
 * 用途说明：
 * - 不使用机器学习权重、不使用模型文件，只按明确规则给出自动入账、待确认或过滤所需的置信度。
 * - 会员机器学习关闭或未获服务器确认时，通知主链路只能使用本计算器。
 */
internal object NotificationRuleConfidenceScorer {
    /** 用途说明：免费规则算法版本，用于写入候选记录。 */
    const val VERSION: String = "local-rules-v1"

    /**
     * 用途说明：
     * - 根据来源、金额、方向和风险特征计算普通规则置信度。
     *
     * 参数说明：
     * - sourceRule：来源规则命中结果。
     * - features：本地解析器抽取出的语义特征。
     */
    fun score(
        sourceRule: ResolvedSourceRule,
        features: NotificationSemanticFeatures,
    ): ConfidenceDecision {
        var score = sourceBaseScore(sourceRule.sourceClass)
        val reasons = mutableListOf("规则=$VERSION")

        score += amountScore(features.amountKind).also { reasons += "金额${formatSigned(it)}" }
        if (features.directionDecision.hitIncome || features.directionDecision.hitExpense) {
            score += DIRECTION_SCORE
            reasons += "方向${formatSigned(DIRECTION_SCORE)}"
        }
        if (features.hasStrongBankStructureSignal) {
            score += STRUCTURE_SCORE
            reasons += "结构${formatSigned(STRUCTURE_SCORE)}"
        }
        if (features.hasTransactionSceneKeyword) {
            score += TRANSACTION_SCENE_SCORE
            reasons += "动账语义${formatSigned(TRANSACTION_SCENE_SCORE)}"
        }
        if (features.hasFinanceSettledKeyword) {
            score += FINANCE_SETTLED_SCORE
            reasons += "收益结算${formatSigned(FINANCE_SETTLED_SCORE)}"
        }
        if (features.hasExtractedMerchant) {
            score += MERCHANT_SCORE
            reasons += "商户${formatSigned(MERCHANT_SCORE)}"
        }

        val guardedScore = applyRuleGuards(score, features, reasons)
        return ConfidenceDecision(
            // 普通规则直接产生 0 到 1 分数，边界夹取防止后续规则调整越界。
            score = guardedScore.coerceIn(0f, 1f),
            reason = reasons.joinToString(separator = "；"),
            featureSnapshotJson = buildRuleSnapshotJson(
                sourceRule = sourceRule,
                features = features,
                finalScore = guardedScore,
            ),
            modelVersion = VERSION,
        )
    }

    /**
     * 用途说明：
     * - 计算来源基础分。
     *
     * 参数说明：
     * - sourceClass：来源语义类别。
     */
    private fun sourceBaseScore(sourceClass: NotificationSourceClass): Float = when (sourceClass) {
        NotificationSourceClass.TRUSTED_PAYMENT -> TRUSTED_PAYMENT_BASE
        NotificationSourceClass.TRUSTED_BANK -> TRUSTED_BANK_BASE
        NotificationSourceClass.TRUSTED_FINANCE -> TRUSTED_FINANCE_BASE
        NotificationSourceClass.UNKNOWN -> UNKNOWN_SOURCE_BASE
    }

    /**
     * 用途说明：
     * - 计算金额证据分。
     *
     * 参数说明：
     * - amountKind：金额证据形态。
     */
    private fun amountScore(amountKind: AmountSignalKind): Float = when (amountKind) {
        AmountSignalKind.WITH_CURRENCY -> AMOUNT_WITH_CURRENCY_SCORE
        AmountSignalKind.WITH_UNIT -> AMOUNT_WITH_UNIT_SCORE
        AmountSignalKind.BARE_NUMBER -> BARE_AMOUNT_SCORE
    }

    /**
     * 用途说明：
     * - 对高风险语义做硬上限，避免普通规则把非交易通知自动入账。
     *
     * 参数说明：
     * - rawScore：规则加总后的分数。
     * - features：语义特征集合。
     * - reasons：原因列表，命中风险时会追加说明。
     */
    private fun applyRuleGuards(
        rawScore: Float,
        features: NotificationSemanticFeatures,
        reasons: MutableList<String>,
    ): Float {
        var guardedScore = rawScore
        if (features.hasUnsettledAssetValuationRisk) {
            guardedScore = guardedScore.coerceAtMost(ASSET_VALUATION_MAX_SCORE)
            reasons += "资产估值上限"
        }
        if (features.hasDirectionConflict) {
            guardedScore = guardedScore.coerceAtMost(DIRECTION_CONFLICT_MAX_SCORE)
            reasons += "方向冲突上限"
        }
        if (!features.hasReliableAmount) {
            guardedScore = guardedScore.coerceAtMost(UNRELIABLE_AMOUNT_MAX_SCORE)
            reasons += "金额弱证据上限"
        }
        if (features.hasMarketingKeyword && !features.hasTransactionSceneKeyword) {
            guardedScore = guardedScore.coerceAtMost(MARKETING_MAX_SCORE)
            reasons += "营销弱交易证据上限"
        }
        reasons += "最终=${formatScore(guardedScore)}"
        return guardedScore
    }
}

/**
 * 用途说明：
 * - 构造免费规则算法的特征快照，供训练样本和排查使用。
 *
 * 参数说明：
 * - sourceRule：来源规则命中结果。
 * - features：语义特征集合。
 * - finalScore：普通规则最终分数。
 */
private fun buildRuleSnapshotJson(
    sourceRule: ResolvedSourceRule,
    features: NotificationSemanticFeatures,
    finalScore: Float,
): String = buildString {
    append("{")
    appendJsonString("scorer", NotificationRuleConfidenceScorer.VERSION)
    append(",")
    appendJsonString("sourceClass", sourceRule.sourceClass.name)
    append(",")
    appendJsonString("amountKind", features.amountKind.name)
    append(",")
    appendJsonBoolean("hitIncome", features.directionDecision.hitIncome)
    append(",")
    appendJsonBoolean("hitExpense", features.directionDecision.hitExpense)
    append(",")
    appendJsonBoolean("hasReliableAmount", features.hasReliableAmount)
    append(",")
    appendJsonBoolean("hasStrongBankStructureSignal", features.hasStrongBankStructureSignal)
    append(",")
    appendJsonBoolean("hasTransactionSceneKeyword", features.hasTransactionSceneKeyword)
    append(",")
    appendJsonBoolean("hasFinanceSettledKeyword", features.hasFinanceSettledKeyword)
    append(",")
    appendJsonBoolean("hasMarketingKeyword", features.hasMarketingKeyword)
    append(",")
    appendJsonBoolean("hasUnsettledAssetValuationRisk", features.hasUnsettledAssetValuationRisk)
    append(",")
    appendJsonNumber("finalScore", finalScore)
    append("}")
}

/**
 * 用途说明：
 * - 追加 JSON 字符串字段。
 *
 * 参数说明：
 * - name：字段名。
 * - value：字段值。
 */
private fun StringBuilder.appendJsonString(
    name: String,
    value: String,
) {
    appendJsonValue(name)
    append(":")
    appendJsonValue(value)
}

/**
 * 用途说明：
 * - 追加 JSON 布尔字段。
 *
 * 参数说明：
 * - name：字段名。
 * - value：字段值。
 */
private fun StringBuilder.appendJsonBoolean(
    name: String,
    value: Boolean,
) {
    appendJsonValue(name)
    append(":")
    append(value)
}

/**
 * 用途说明：
 * - 追加 JSON 数值字段。
 *
 * 参数说明：
 * - name：字段名。
 * - value：字段值。
 */
private fun StringBuilder.appendJsonNumber(
    name: String,
    value: Float,
) {
    appendJsonValue(name)
    append(":")
    append(formatScore(value))
}

/**
 * 用途说明：
 * - 追加带引号和转义的 JSON 字符串值。
 *
 * 参数说明：
 * - value：待写入的原始字符串。
 */
private fun StringBuilder.appendJsonValue(value: String) {
    append('"')
    append(value.jsonEscaped())
    append('"')
}

/**
 * 用途说明：
 * - 对 JSON 字符串做最小安全转义。
 *
 * 参数说明：
 * - 接收者：待转义字符串。
 */
private fun String.jsonEscaped(): String = buildString {
    this@jsonEscaped.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}

/**
 * 用途说明：
 * - 格式化带符号的分数贡献。
 *
 * 参数说明：
 * - value：贡献值。
 */
private fun formatSigned(value: Float): String = String.format(Locale.ROOT, "%+.2f", value)

/**
 * 用途说明：
 * - 格式化普通规则最终分数。
 *
 * 参数说明：
 * - value：分数值。
 */
private fun formatScore(value: Float): String = String.format(Locale.ROOT, "%.4f", value)

private const val TRUSTED_PAYMENT_BASE = 0.53f
private const val TRUSTED_BANK_BASE = 0.44f
private const val TRUSTED_FINANCE_BASE = 0.32f
private const val UNKNOWN_SOURCE_BASE = 0.20f
private const val AMOUNT_WITH_CURRENCY_SCORE = 0.20f
private const val AMOUNT_WITH_UNIT_SCORE = 0.20f
private const val BARE_AMOUNT_SCORE = 0.06f
private const val DIRECTION_SCORE = 0.15f
private const val STRUCTURE_SCORE = 0.14f
private const val TRANSACTION_SCENE_SCORE = 0.08f
private const val FINANCE_SETTLED_SCORE = 0.16f
private const val MERCHANT_SCORE = 0.04f
private const val ASSET_VALUATION_MAX_SCORE = 0.40f
private const val DIRECTION_CONFLICT_MAX_SCORE = 0.42f
private const val UNRELIABLE_AMOUNT_MAX_SCORE = 0.80f
private const val MARKETING_MAX_SCORE = 0.50f
