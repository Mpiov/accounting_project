package com.autobook.cn.core.data.parser

import com.autobook.cn.core.data.CloudConfidenceModel
import java.util.Locale
import kotlin.math.exp

/**
 * 轻量置信度模型入口。
 *
 * 用途说明：
 * - 使用纯 Kotlin 线性模型替代手写加减分表，只负责输出交易候选的置信度。
 * - 模型输入来自本地解析器已经抽取出的结构化特征，不引入大模型、推理引擎或网络调用。
 * - 当前权重为冷启动人工权重；后续有标注数据后，可用离线训练结果直接覆盖权重常量。
 */
internal object NotificationConfidenceScorer {
    /** 用途说明：当前置信度模型版本，用于写入候选记录的可追溯版本号。 */
    const val MODEL_VERSION: String = "local-linear-confidence-v1"

    /**
     * 用途说明：
     * - 根据来源规则和语义特征计算最终置信度。
     *
     * 参数说明：
     * - sourceRule：来源规则命中结果，提供支付、银行、金融、未知等来源先验。
     * - features：本地解析器提取出的金额、方向、账户、对手方和风险语义特征。
     */
    fun score(
        sourceRule: ResolvedSourceRule,
        features: NotificationSemanticFeatures,
        cloudModel: CloudConfidenceModel? = null,
    ): ConfidenceDecision {
        val modelConfig = MlModelConfig.from(cloudModel)
        val featureVector = MlConfidenceFeatureVector.from(
            sourceRule = sourceRule,
            features = features,
            modelConfig = modelConfig,
        )
        val rawScore = sigmoid(modelConfig.bias + featureVector.weightedSum())
        val guardedScore = applyRiskGuards(
            rawScore = rawScore,
            features = features,
            modelConfig = modelConfig,
        )

        return ConfidenceDecision(
            // 线性模型输出已经是概率形态，这里只做边界夹取，避免异常权重导致分数越界。
            score = guardedScore.coerceIn(0f, 1f),
            reason = buildReason(
                rawScore = rawScore,
                guardedScore = guardedScore,
                featureVector = featureVector,
                features = features,
                modelConfig = modelConfig,
            ),
            featureSnapshotJson = buildFeatureSnapshotJson(
                sourceRule = sourceRule,
                features = features,
                featureVector = featureVector,
                modelConfig = modelConfig,
            ),
            modelVersion = modelConfig.version,
        )
    }

    /**
     * 用途说明：
     * - 将线性模型的 logit 映射到 0 到 1 的概率区间。
     *
     * 参数说明：
     * - logit：模型偏置与全部特征贡献相加后的原始线性值。
     */
    private fun sigmoid(logit: Float): Float = (1.0 / (1.0 + exp(-logit.toDouble()))).toFloat()

    /**
     * 用途说明：
     * - 对高风险语义施加硬性上限，避免模型在金额、来源等强正向信号下误放行。
     *
     * 参数说明：
     * - rawScore：线性模型经过 sigmoid 后的原始置信度。
     * - features：语义特征集合，用来识别金额不可靠、方向冲突、营销和估值风险。
     */
    private fun applyRiskGuards(
        rawScore: Float,
        features: NotificationSemanticFeatures,
        modelConfig: MlModelConfig,
    ): Float {
        var guardedScore = rawScore
        if (features.hasUnsettledAssetValuationRisk) {
            guardedScore = guardedScore.coerceAtMost(
                modelConfig.riskThreshold(
                    key = THRESHOLD_ASSET_VALUATION_MAX_SCORE,
                    alias = "assetValuationMaxScore",
                    defaultValue = ASSET_VALUATION_MAX_SCORE,
                ),
            )
        }
        if (features.hasDirectionConflict) {
            guardedScore = guardedScore.coerceAtMost(
                modelConfig.riskThreshold(
                    key = THRESHOLD_DIRECTION_CONFLICT_MAX_SCORE,
                    alias = "directionConflictMaxScore",
                    defaultValue = DIRECTION_CONFLICT_MAX_SCORE,
                ),
            )
        }
        if (!features.hasReliableAmount) {
            guardedScore = guardedScore.coerceAtMost(
                modelConfig.riskThreshold(
                    key = THRESHOLD_UNRELIABLE_AMOUNT_MAX_SCORE,
                    alias = "unreliableAmountMaxScore",
                    defaultValue = UNRELIABLE_AMOUNT_MAX_SCORE,
                ),
            )
        }
        if (features.hasMarketingKeyword && !features.hasTransactionSceneKeyword) {
            guardedScore = guardedScore.coerceAtMost(
                modelConfig.riskThreshold(
                    key = THRESHOLD_WEAK_MARKETING_MAX_SCORE,
                    alias = "weakMarketingMaxScore",
                    defaultValue = WEAK_MARKETING_MAX_SCORE,
                ),
            )
        }
        return guardedScore
    }

    /**
     * 用途说明：
     * - 生成面向日志和待确认页的可解释原因。
     *
     * 参数说明：
     * - rawScore：未经过风险上限处理的模型原始置信度。
     * - guardedScore：经过风险上限处理后的最终置信度。
     * - featureVector：本次参与线性模型计算的特征贡献明细。
     * - features：原始语义特征，用来补充风险说明。
     * - modelConfig：当前使用的模型配置，可能来自服务器全局模型或内置冷启动模型。
     */
    private fun buildReason(
        rawScore: Float,
        guardedScore: Float,
        featureVector: MlConfidenceFeatureVector,
        features: NotificationSemanticFeatures,
        modelConfig: MlModelConfig,
    ): String {
        val reasonParts = mutableListOf("模型=${modelConfig.version}")
        reasonParts += "来源=${modelConfig.source}"
        val positiveReasons = featureVector.topPositiveContributions()
        if (positiveReasons.isNotEmpty()) {
            reasonParts += "正向=${positiveReasons.joinToString(separator = "、")}"
        }
        val negativeReasons = featureVector.negativeContributions()
        if (negativeReasons.isNotEmpty()) {
            reasonParts += "风险=${negativeReasons.joinToString(separator = "、")}"
        }
        if (features.hasUnsettledAssetValuationRisk) {
            reasonParts += "命中资产估值风险词"
        }
        if (features.hasDirectionConflict) {
            reasonParts += "方向冲突"
        }
        if (!features.hasReliableAmount) {
            reasonParts += "金额不可靠"
        }
        if (features.hasMarketingKeyword && !features.hasTransactionSceneKeyword) {
            reasonParts += "营销弱交易证据"
        }
        reasonParts += "原始=${formatScore(rawScore)}"
        reasonParts += "最终=${formatScore(guardedScore)}"
        return reasonParts.joinToString(separator = "；")
    }

    /**
     * 用途说明：
     * - 格式化最终置信度分数，保证日志输出稳定。
     *
     * 参数说明：
     * - score：需要展示的置信度分数。
     */
    private fun formatScore(score: Float): String = String.format(Locale.ROOT, "%.2f", score)
}

/**
 * 用途说明：
 * - 构建可导出训练数据的特征快照。
 * - 快照只包含结构化特征和模型贡献，不写入通知标题、正文或商户原文。
 *
 * 参数说明：
 * - sourceRule：来源规则命中结果。
 * - features：语义特征集合。
 * - featureVector：线性模型实际使用的贡献项。
 * - modelConfig：当前用于评分的模型配置。
 */
private fun buildFeatureSnapshotJson(
    sourceRule: ResolvedSourceRule,
    features: NotificationSemanticFeatures,
    featureVector: MlConfidenceFeatureVector,
    modelConfig: MlModelConfig,
): String = buildString {
    append("{")
    appendJsonString("modelVersion", modelConfig.version)
    append(",")
    appendJsonString("modelSource", modelConfig.source)
    append(",")
    appendJsonString("sourceClass", sourceRule.sourceClass.name)
    append(",")
    appendJsonString("amountKind", features.amountKind.name)
    append(",")
    appendJsonBoolean("hitIncome", features.directionDecision.hitIncome)
    append(",")
    appendJsonBoolean("hitExpense", features.directionDecision.hitExpense)
    append(",")
    appendJsonBoolean("hasTailNumber", features.hasTailNumber)
    append(",")
    appendJsonBoolean("hasStrongAccountKeyword", features.hasStrongAccountKeyword)
    append(",")
    appendJsonBoolean("hasGenericAccountKeyword", features.hasGenericAccountKeyword)
    append(",")
    appendJsonBoolean("hasExplicitTime", features.hasExplicitTime)
    append(",")
    appendJsonBoolean("hasTransactionSceneKeyword", features.hasTransactionSceneKeyword)
    append(",")
    appendJsonBoolean("hasCounterpartyKeyword", features.hasCounterpartyKeyword)
    append(",")
    appendJsonBoolean("hasExtractedCounterparty", features.hasExtractedCounterparty)
    append(",")
    appendJsonBoolean("hasCurrencyKeyword", features.hasCurrencyKeyword)
    append(",")
    appendJsonBoolean("hasBankEntityKeyword", features.hasBankEntityKeyword)
    append(",")
    appendJsonBoolean("hasFinanceSettledKeyword", features.hasFinanceSettledKeyword)
    append(",")
    appendJsonBoolean("hasMarketingKeyword", features.hasMarketingKeyword)
    append(",")
    appendJsonBoolean("hasAssetValuationKeyword", features.hasAssetValuationKeyword)
    append(",")
    appendJsonBoolean("hasSettlementEvidenceKeyword", features.hasSettlementEvidenceKeyword)
    append(",")
    appendJsonBoolean("hasFinanceEntityKeyword", features.hasFinanceEntityKeyword)
    append(",")
    appendJsonBoolean("hasReliableAmount", features.hasReliableAmount)
    append(",")
    appendJsonBoolean("hasStrongBankStructureSignal", features.hasStrongBankStructureSignal)
    append(",")
    appendJsonBoolean("hasDirectionConflict", features.hasDirectionConflict)
    append(",")
    appendJsonBoolean("hasUnsettledAssetValuationRisk", features.hasUnsettledAssetValuationRisk)
    append(",\"matchedPatterns\":[")
    features.matchedPatterns.sorted().forEachIndexed { index, pattern ->
        if (index > 0) {
            append(",")
        }
        appendJsonValue(pattern)
    }
    append("],\"contributions\":[")
    featureVector.contributions.forEachIndexed { index, contribution ->
        if (index > 0) {
            append(",")
        }
        append("{")
        appendJsonString("key", contribution.key)
        append(",")
        appendJsonString("label", contribution.label)
        append(",")
        appendJsonNumber("value", contribution.value)
        append(",")
        appendJsonNumber("weight", contribution.weight)
        append(",")
        appendJsonNumber("contribution", contribution.contribution)
        append("}")
    }
    append("]}")
}

/**
 * 线性模型特征向量。
 *
 * 用途说明：
 * - 将解析器中的布尔/枚举信号整理成统一的模型贡献列表。
 * - 每个特征贡献为 `value * weight`，后续离线训练时只需要替换权重，不需要改调用链。
 *
 * 参数说明：
 * - contributions：本条通知实际参与模型计算的特征贡献项。
 */
private data class MlConfidenceFeatureVector(
    val contributions: List<MlFeatureContribution>,
) {
    /**
     * 用途说明：
     * - 汇总全部特征贡献，供线性模型计算 logit。
     */
    fun weightedSum(): Float = contributions.sumOf { contribution ->
        (contribution.value * contribution.weight).toDouble()
    }.toFloat()

    /**
     * 用途说明：
     * - 取贡献最大的正向信号，避免原因文本过长。
     */
    fun topPositiveContributions(): List<String> = contributions
        .filter { contribution -> contribution.contribution > 0f }
        .sortedByDescending(MlFeatureContribution::contribution)
        .take(MAX_REASON_SIGNAL_COUNT)
        .map { contribution ->
            "${contribution.label}${formatContribution(contribution.contribution)}"
        }

    /**
     * 用途说明：
     * - 取全部负向风险信号，便于排查候选为什么被压低。
     */
    fun negativeContributions(): List<String> = contributions
        .filter { contribution -> contribution.contribution < 0f }
        .sortedBy(MlFeatureContribution::contribution)
        .map { contribution ->
            "${contribution.label}${formatContribution(contribution.contribution)}"
        }

    companion object {
        /**
         * 用途说明：
         * - 从来源规则和语义特征构造模型向量。
         *
         * 参数说明：
         * - sourceRule：来源规则命中结果，用来写入来源先验特征。
         * - features：本地语义分析结果，用来写入金额、方向、结构和风险特征。
         * - modelConfig：当前模型配置，用来按服务器下发权重覆盖内置权重。
         */
        fun from(
            sourceRule: ResolvedSourceRule,
            features: NotificationSemanticFeatures,
            modelConfig: MlModelConfig,
        ): MlConfidenceFeatureVector {
            val contributions = buildList {
                addSourcePrior(sourceRule.sourceClass, modelConfig)
                addAmountSignal(features.amountKind, modelConfig)
                addBinary(
                    key = KEY_DIRECTION_INCOME,
                    label = "收入方向",
                    enabled = features.directionDecision.hitIncome,
                    defaultWeight = DIRECTION_WEIGHT,
                    modelConfig = modelConfig,
                )
                addBinary(
                    key = KEY_DIRECTION_EXPENSE,
                    label = "支出方向",
                    enabled = features.directionDecision.hitExpense,
                    defaultWeight = DIRECTION_WEIGHT,
                    modelConfig = modelConfig,
                )
                addBinary(
                    key = KEY_TAIL_NUMBER,
                    label = "账户尾号",
                    enabled = features.hasTailNumber,
                    defaultWeight = TAIL_NUMBER_WEIGHT,
                    modelConfig = modelConfig,
                )
                addBinary(
                    key = KEY_STRONG_ACCOUNT,
                    label = "强账户词",
                    enabled = features.hasStrongAccountKeyword,
                    defaultWeight = STRONG_ACCOUNT_WEIGHT,
                    modelConfig = modelConfig,
                )
                addBinary(
                    key = KEY_GENERIC_ACCOUNT,
                    label = "账户词",
                    enabled = features.hasGenericAccountKeyword,
                    defaultWeight = GENERIC_ACCOUNT_WEIGHT,
                    modelConfig = modelConfig,
                )
                addBinary(
                    key = KEY_EXPLICIT_TIME,
                    label = "明确时间",
                    enabled = features.hasExplicitTime,
                    defaultWeight = EXPLICIT_TIME_WEIGHT,
                    modelConfig = modelConfig,
                )
                addBinary(
                    key = KEY_TRANSACTION_SCENE,
                    label = "动账语义",
                    enabled = features.hasTransactionSceneKeyword,
                    defaultWeight = TRANSACTION_SCENE_WEIGHT,
                    modelConfig = modelConfig,
                )
                addBinary(
                    key = KEY_COUNTERPARTY,
                    label = "对手方词",
                    enabled = features.hasCounterpartyKeyword,
                    defaultWeight = COUNTERPARTY_KEYWORD_WEIGHT,
                    modelConfig = modelConfig,
                )
                addBinary(
                    key = KEY_EXTRACTED_COUNTERPARTY,
                    label = "已提取备注线索",
                    enabled = features.hasExtractedCounterparty,
                    defaultWeight = EXTRACTED_COUNTERPARTY_WEIGHT,
                    modelConfig = modelConfig,
                )
                addBinary(
                    key = KEY_CURRENCY_KEYWORD,
                    label = "币种词",
                    enabled = features.hasCurrencyKeyword,
                    defaultWeight = CURRENCY_KEYWORD_WEIGHT,
                    modelConfig = modelConfig,
                )
                addBinary(
                    key = KEY_BANK_ENTITY,
                    label = "银行实体",
                    enabled = features.hasBankEntityKeyword,
                    defaultWeight = BANK_ENTITY_WEIGHT,
                    modelConfig = modelConfig,
                )
                addBinary(
                    key = KEY_FINANCE_ENTITY,
                    label = "金融实体",
                    enabled = features.hasFinanceEntityKeyword,
                    defaultWeight = FINANCE_ENTITY_WEIGHT,
                    modelConfig = modelConfig,
                )
                addBinary(
                    key = KEY_FINANCE_SETTLED,
                    label = "收益结算",
                    enabled = features.hasFinanceSettledKeyword,
                    defaultWeight = FINANCE_SETTLED_WEIGHT,
                    modelConfig = modelConfig,
                )
                addBinary(
                    key = KEY_DIRECTION_CONFLICT,
                    label = "方向冲突",
                    enabled = features.hasDirectionConflict,
                    defaultWeight = DIRECTION_CONFLICT_WEIGHT,
                    modelConfig = modelConfig,
                )
                addBinary(
                    key = KEY_UNRELIABLE_AMOUNT,
                    label = "金额不可靠",
                    enabled = !features.hasReliableAmount,
                    defaultWeight = UNRELIABLE_AMOUNT_WEIGHT,
                    modelConfig = modelConfig,
                )
                addBinary(
                    key = KEY_MARKETING,
                    label = "营销资讯",
                    enabled = features.hasMarketingKeyword,
                    defaultWeight = MARKETING_WEIGHT,
                    modelConfig = modelConfig,
                )
                addBinary(
                    key = KEY_ASSET_VALUATION,
                    label = "资产估值",
                    enabled = features.hasUnsettledAssetValuationRisk,
                    defaultWeight = ASSET_VALUATION_WEIGHT,
                    modelConfig = modelConfig,
                )
            }
            return MlConfidenceFeatureVector(contributions = contributions)
        }

        /**
         * 用途说明：
         * - 写入来源先验特征。
         *
         * 参数说明：
         * - sourceClass：当前通知命中的来源类别。
         * - modelConfig：当前模型配置。
         */
        private fun MutableList<MlFeatureContribution>.addSourcePrior(
            sourceClass: NotificationSourceClass,
            modelConfig: MlModelConfig,
        ) {
            val (key, defaultWeight) = when (sourceClass) {
                NotificationSourceClass.TRUSTED_PAYMENT -> KEY_SOURCE_TRUSTED_PAYMENT to TRUSTED_PAYMENT_WEIGHT
                NotificationSourceClass.TRUSTED_BANK -> KEY_SOURCE_TRUSTED_BANK to TRUSTED_BANK_WEIGHT
                NotificationSourceClass.TRUSTED_FINANCE -> KEY_SOURCE_TRUSTED_FINANCE to TRUSTED_FINANCE_WEIGHT
                NotificationSourceClass.UNKNOWN -> KEY_SOURCE_UNKNOWN to UNKNOWN_SOURCE_WEIGHT
            }
            val label = "来源${sourceClass.name}"
            add(
                MlFeatureContribution(
                    key = key,
                    label = label,
                    value = 1f,
                    weight = modelConfig.weight(key = key, label = label, defaultValue = defaultWeight),
                ),
            )
        }

        /**
         * 用途说明：
         * - 写入金额证据强度特征。
         *
         * 参数说明：
         * - amountKind：金额抽取器识别出的金额形态。
         * - modelConfig：当前模型配置。
         */
        private fun MutableList<MlFeatureContribution>.addAmountSignal(
            amountKind: AmountSignalKind,
            modelConfig: MlModelConfig,
        ) {
            val (key, defaultWeight) = when (amountKind) {
                AmountSignalKind.WITH_CURRENCY -> KEY_AMOUNT_WITH_CURRENCY to AMOUNT_WITH_CURRENCY_WEIGHT
                AmountSignalKind.WITH_UNIT -> KEY_AMOUNT_WITH_UNIT to AMOUNT_WITH_UNIT_WEIGHT
                AmountSignalKind.BARE_NUMBER -> KEY_AMOUNT_BARE_NUMBER to BARE_AMOUNT_WEIGHT
            }
            val label = "金额${amountKind.name}"
            add(
                MlFeatureContribution(
                    key = key,
                    label = label,
                    value = 1f,
                    weight = modelConfig.weight(key = key, label = label, defaultValue = defaultWeight),
                ),
            )
        }

        /**
         * 用途说明：
         * - 把布尔特征转换成线性模型贡献项。
         *
         * 参数说明：
         * - key：服务器训练和下发权重时使用的稳定特征键。
         * - label：原因文本中展示的特征名称。
         * - enabled：该特征是否命中。
         * - defaultWeight：内置冷启动模型权重。
         * - modelConfig：当前模型配置。
         */
        private fun MutableList<MlFeatureContribution>.addBinary(
            key: String,
            label: String,
            enabled: Boolean,
            defaultWeight: Float,
            modelConfig: MlModelConfig,
        ) {
            if (enabled) {
                add(
                    MlFeatureContribution(
                        key = key,
                        label = label,
                        value = 1f,
                        weight = modelConfig.weight(key = key, label = label, defaultValue = defaultWeight),
                    ),
                )
            }
        }
    }
}

/**
 * 当前参与评分的模型配置。
 *
 * 用途说明：
 * - 把服务器全局模型和内置冷启动模型统一成同一个读取接口。
 * - 服务器模型只覆盖它明确下发的权重或阈值，缺失字段继续使用内置安全默认值。
 *
 * 参数说明：
 * - version：当前模型版本。
 * - source：模型来源，`cloud` 表示服务器全局模型，`embedded` 表示内置冷启动模型。
 * - weights：服务器下发的权重表。
 * - thresholds：服务器下发的风险阈值表。
 */
private data class MlModelConfig(
    val version: String,
    val source: String,
    private val weights: Map<String, Double>,
    private val thresholds: Map<String, Double>,
) {
    /** 用途说明：模型偏置项，缺失时使用内置冷启动偏置。 */
    val bias: Float
        get() = weight(key = KEY_BIAS, label = "bias", defaultValue = MODEL_BIAS)

    /**
     * 用途说明：
     * - 读取单个特征权重，优先按稳定 key 命中，再兼容旧样本里的中文 label。
     *
     * 参数说明：
     * - key：服务器训练产物推荐使用的稳定特征键。
     * - label：历史特征快照中的展示名，作为兼容兜底。
     * - defaultValue：内置冷启动权重。
     */
    fun weight(
        key: String,
        label: String,
        defaultValue: Float,
    ): Float {
        return weights.resolveFloat(key, label, *legacyWeightAliases(key)) ?: defaultValue
    }

    /**
     * 用途说明：
     * - 读取风险上限阈值，并限制服务器只能收紧、不能放宽客户端安全边界。
     *
     * 参数说明：
     * - key：推荐的稳定阈值键。
     * - alias：兼容驼峰命名阈值键。
     * - defaultValue：客户端内置安全上限。
     */
    fun riskThreshold(
        key: String,
        alias: String,
        defaultValue: Float,
    ): Float {
        val serverValue = thresholds.resolveFloat(key, alias) ?: return defaultValue
        return serverValue.coerceIn(0f, defaultValue)
    }

    companion object {
        /**
         * 用途说明：
         * - 根据本地缓存模型生成评分配置；不可用时回退内置模型。
         *
         * 参数说明：
         * - cloudModel：本地持久化的服务器全局模型。
         */
        fun from(cloudModel: CloudConfidenceModel?): MlModelConfig {
            if (cloudModel != null && cloudModel.isUsableAt()) {
                return MlModelConfig(
                    version = cloudModel.modelVersion,
                    source = MODEL_SOURCE_CLOUD,
                    weights = cloudModel.weights,
                    thresholds = cloudModel.thresholds,
                )
            }
            return MlModelConfig(
                version = NotificationConfidenceScorer.MODEL_VERSION,
                source = MODEL_SOURCE_EMBEDDED,
                weights = emptyMap(),
                thresholds = emptyMap(),
            )
        }
    }
}

/**
 * 单个线性模型特征贡献。
 *
 * 用途说明：
 * - 表示一个特征在本次置信度计算中的实际影响。
 *
 * 参数说明：
 * - key：稳定特征键，用于对齐服务器训练产物。
 * - label：便于解释和日志查看的特征名。
 * - value：特征值，当前布尔特征固定为 1。
 * - weight：模型权重，正数提升置信度，负数压低置信度。
 */
private data class MlFeatureContribution(
    val key: String,
    val label: String,
    val value: Float,
    val weight: Float,
) {
    /** 用途说明：该特征对 logit 的实际贡献。 */
    val contribution: Float
        get() = value * weight
}

/**
 * 用途说明：
 * - 格式化模型贡献值，保证日志原因里正负号和小数位稳定。
 *
 * 参数说明：
 * - value：单个特征对 logit 的贡献值。
 */
private fun formatContribution(value: Float): String = String.format(Locale.ROOT, "%+.2f", value)

/**
 * 用途说明：
 * - 从 Double 权重表中读取安全 Float 值。
 *
 * 参数说明：
 * - keys：按优先级尝试的权重键。
 */
private fun Map<String, Double>.resolveFloat(vararg keys: String): Float? {
    keys.forEach { key ->
        val value = this[key]?.takeIf { weight -> weight.isFinite() } ?: return@forEach
        return value.toFloat()
    }
    return null
}

/**
 * 用途说明：
 * - 兼容早期后端训练产物里的旧特征名。
 * - 新模型应使用稳定点号命名；这里仅用于平滑过渡已经发布的 bootstrap 模型。
 *
 * 参数说明：
 * - key：客户端当前稳定特征键。
 */
private fun legacyWeightAliases(key: String): Array<String> = when (key) {
    KEY_SOURCE_TRUSTED_PAYMENT -> arrayOf("trustedPayment", "sourceTrustedPayment")
    KEY_SOURCE_TRUSTED_BANK -> arrayOf("trustedBank", "sourceTrustedBank")
    KEY_SOURCE_TRUSTED_FINANCE -> arrayOf("sourceTrustedFinance")
    KEY_SOURCE_UNKNOWN -> arrayOf("sourceUnknown")
    KEY_AMOUNT_WITH_CURRENCY -> arrayOf("amountKeywordNearNumber", "amountWithCurrency")
    KEY_AMOUNT_WITH_UNIT -> arrayOf("amountWithUnit")
    KEY_AMOUNT_BARE_NUMBER -> arrayOf("amountBareNumber")
    KEY_DIRECTION_INCOME -> arrayOf("hitIncome")
    KEY_DIRECTION_EXPENSE -> arrayOf("hitExpense")
    KEY_TAIL_NUMBER -> arrayOf("hasTailNumber")
    KEY_STRONG_ACCOUNT -> arrayOf("hasStrongAccountKeyword")
    KEY_GENERIC_ACCOUNT -> arrayOf("hasGenericAccountKeyword")
    KEY_EXPLICIT_TIME -> arrayOf("hasExplicitTime")
    KEY_TRANSACTION_SCENE -> arrayOf("hasTransactionSceneKeyword")
    KEY_COUNTERPARTY -> arrayOf("hasCounterpartyKeyword")
    KEY_EXTRACTED_COUNTERPARTY -> arrayOf("hasExtractedCounterparty")
    KEY_CURRENCY_KEYWORD -> arrayOf("hasCurrencyKeyword")
    KEY_BANK_ENTITY -> arrayOf("hasBankEntityKeyword")
    KEY_FINANCE_ENTITY -> arrayOf("hasFinanceEntityKeyword")
    KEY_FINANCE_SETTLED -> arrayOf("hasFinanceSettledKeyword")
    KEY_DIRECTION_CONFLICT -> arrayOf("hasDirectionConflict")
    KEY_UNRELIABLE_AMOUNT -> emptyArray()
    KEY_MARKETING -> arrayOf("hasMarketingKeyword")
    KEY_ASSET_VALUATION -> arrayOf("hasUnsettledAssetValuationRisk")
    else -> emptyArray()
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
 * - value：布尔字段值。
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
 * - value：数值字段值。
 */
private fun StringBuilder.appendJsonNumber(
    name: String,
    value: Float,
) {
    appendJsonValue(name)
    append(":")
    append(String.format(Locale.ROOT, "%.6f", value))
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
 * 轻量置信度模型权重。
 *
 * 用途说明：
 * - 当前权重按冷启动样本和业务直觉初始化，形态与逻辑回归一致。
 * - 后续离线训练可以直接覆盖这些常量，不需要改解析器主链路。
 */
private const val MODEL_SOURCE_CLOUD = "cloud"
private const val MODEL_SOURCE_EMBEDDED = "embedded"

private const val KEY_BIAS = "bias"
private const val KEY_SOURCE_TRUSTED_PAYMENT = "source.trusted_payment"
private const val KEY_SOURCE_TRUSTED_BANK = "source.trusted_bank"
private const val KEY_SOURCE_TRUSTED_FINANCE = "source.trusted_finance"
private const val KEY_SOURCE_UNKNOWN = "source.unknown"
private const val KEY_AMOUNT_WITH_CURRENCY = "amount.with_currency"
private const val KEY_AMOUNT_WITH_UNIT = "amount.with_unit"
private const val KEY_AMOUNT_BARE_NUMBER = "amount.bare_number"
private const val KEY_DIRECTION_INCOME = "direction.income"
private const val KEY_DIRECTION_EXPENSE = "direction.expense"
private const val KEY_TAIL_NUMBER = "structure.tail_number"
private const val KEY_STRONG_ACCOUNT = "structure.strong_account_keyword"
private const val KEY_GENERIC_ACCOUNT = "structure.generic_account_keyword"
private const val KEY_EXPLICIT_TIME = "structure.explicit_time"
private const val KEY_TRANSACTION_SCENE = "semantic.transaction_scene"
private const val KEY_COUNTERPARTY = "semantic.counterparty"
private const val KEY_EXTRACTED_COUNTERPARTY = "semantic.extracted_counterparty"
private const val KEY_CURRENCY_KEYWORD = "semantic.currency_keyword"
private const val KEY_BANK_ENTITY = "semantic.bank_entity"
private const val KEY_FINANCE_ENTITY = "semantic.finance_entity"
private const val KEY_FINANCE_SETTLED = "semantic.finance_settled"
private const val KEY_DIRECTION_CONFLICT = "risk.direction_conflict"
private const val KEY_UNRELIABLE_AMOUNT = "risk.unreliable_amount"
private const val KEY_MARKETING = "risk.marketing"
private const val KEY_ASSET_VALUATION = "risk.asset_valuation"

private const val THRESHOLD_ASSET_VALUATION_MAX_SCORE = "threshold.asset_valuation_max_score"
private const val THRESHOLD_DIRECTION_CONFLICT_MAX_SCORE = "threshold.direction_conflict_max_score"
private const val THRESHOLD_UNRELIABLE_AMOUNT_MAX_SCORE = "threshold.unreliable_amount_max_score"
private const val THRESHOLD_WEAK_MARKETING_MAX_SCORE = "threshold.weak_marketing_max_score"

private const val MODEL_BIAS = -2.00f
private const val TRUSTED_PAYMENT_WEIGHT = 1.55f
private const val TRUSTED_BANK_WEIGHT = 1.40f
private const val TRUSTED_FINANCE_WEIGHT = 0.55f
private const val UNKNOWN_SOURCE_WEIGHT = 0.00f
private const val AMOUNT_WITH_CURRENCY_WEIGHT = 1.20f
private const val AMOUNT_WITH_UNIT_WEIGHT = 1.00f
private const val BARE_AMOUNT_WEIGHT = 0.20f
private const val DIRECTION_WEIGHT = 1.05f
private const val TAIL_NUMBER_WEIGHT = 0.75f
private const val STRONG_ACCOUNT_WEIGHT = 0.55f
private const val GENERIC_ACCOUNT_WEIGHT = 0.25f
private const val EXPLICIT_TIME_WEIGHT = 0.25f
private const val TRANSACTION_SCENE_WEIGHT = 0.45f
private const val COUNTERPARTY_KEYWORD_WEIGHT = 0.20f
private const val EXTRACTED_COUNTERPARTY_WEIGHT = 0.10f
private const val CURRENCY_KEYWORD_WEIGHT = 0.25f
private const val BANK_ENTITY_WEIGHT = 0.35f
private const val FINANCE_ENTITY_WEIGHT = 0.25f
private const val FINANCE_SETTLED_WEIGHT = 0.55f
private const val DIRECTION_CONFLICT_WEIGHT = -1.25f
private const val UNRELIABLE_AMOUNT_WEIGHT = -0.90f
private const val MARKETING_WEIGHT = -1.15f
private const val ASSET_VALUATION_WEIGHT = -1.60f
private const val MAX_REASON_SIGNAL_COUNT = 5

/** 用途说明：资产估值类消息即使有金额，也不能进入中高置信度自动处理。 */
private const val ASSET_VALUATION_MAX_SCORE = 0.45f

/** 用途说明：方向冲突消息最多进入低置信度，避免收支方向写反。 */
private const val DIRECTION_CONFLICT_MAX_SCORE = 0.61f

/** 用途说明：裸数字金额缺少货币或单位证据时，最多停留在待确认边界以下。 */
private const val UNRELIABLE_AMOUNT_MAX_SCORE = 0.85f

/** 用途说明：营销类消息且缺少交易场景证据时，直接压到低置信度。 */
private const val WEAK_MARKETING_MAX_SCORE = 0.55f
