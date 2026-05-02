package com.autobook.cn.core.data.parser

import com.autobook.cn.core.data.TransactionExtractor
import com.autobook.cn.core.data.TransactionScoringMode
import com.autobook.cn.core.data.CloudModelStore
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.TransactionCandidate
import javax.inject.Inject

/**
 * 基于本地规则从通知正文中提取交易候选。
 *
 * 用途说明：
 * - 当前实现已切换到“AC 自动机 + 来源规则表 + 语义特征 + 打分器 + 高置信度硬门槛”的新链路。
 * - 解析器自身只负责组织调用顺序，避免把来源判断、关键词扫描和分数公式再次揉回一个大函数。
 *
 * 参数说明：
 * - cloudModelStore：服务器全局模型的本地缓存；会员模式下优先读取它的权重。
 */
class LocalTransactionExtractor @Inject constructor(
    private val cloudModelStore: CloudModelStore,
) : TransactionExtractor {
    /**
     * 从单条通知里抽取标准化候选。
     *
     * 参数说明：
     * - event：原始通知事件，包含包名、标题、正文、时间戳与来源先验。
     * - scoringMode：免费用户使用普通规则，会员用户才允许机器学习评分。
     */
    override suspend fun extract(
        event: NotificationEvent,
        scoringMode: TransactionScoringMode,
    ): OperationResult<TransactionCandidate> {
        val textWindow = NotificationTextWindowBuilder.build(
            title = event.title,
            text = event.text,
        )
        if (textWindow.normalizedText.isBlank()) {
            return OperationResult.RetryableFailure("Notification body is empty")
        }

        val sourceApp = if (event.sourceApp == NotificationSourceApp.UNKNOWN) {
            NotificationSourceClassifier.detect(event.packageName)
        } else {
            event.sourceApp
        }
        val sourceRule = NotificationSourceRuleTable.resolve(
            packageName = event.packageName,
            sourceApp = sourceApp,
            textWindow = textWindow,
        )
        val amountResult = NotificationAmountParser.extract(textWindow.scoringText)
            ?: return OperationResult.TerminalFailure("Amount was not parseable")
        val directionDecision = NotificationDirectionDetector.detect(
            scoringText = textWindow.scoringText,
            sourceApp = sourceApp,
        )
        val merchant = NotificationMerchantExtractor.extract(
            text = textWindow.scoringText,
            fallbackTitle = event.title,
            channelLabel = sourceRule.channelLabel,
        )
        val features = NotificationSemanticAnalyzer.analyze(
            textWindow = textWindow,
            amountResult = amountResult,
            directionDecision = directionDecision,
            merchant = merchant,
            channelLabel = sourceRule.channelLabel,
        )
        val confidence = when (scoringMode) {
            TransactionScoringMode.RULES_ONLY -> NotificationRuleConfidenceScorer.score(
                sourceRule = sourceRule,
                features = features,
            )
            TransactionScoringMode.MEMBER_MACHINE_LEARNING -> NotificationConfidenceScorer.score(
                sourceRule = sourceRule,
                features = features,
                cloudModel = cloudModelStore.getActiveModel()?.takeIf { model -> model.isUsableAt() },
            )
        }
        val categoryHint = NotificationCategoryClassifier.detect(
            text = textWindow.scoringText,
            hasFinanceSettledKeyword = features.hasFinanceSettledKeyword,
        )

        return OperationResult.Success(
            TransactionCandidate(
                amount = amountResult.amount,
                direction = directionDecision.direction,
                merchant = merchant,
                channel = sourceRule.channelLabel,
                timestamp = event.postedAt,
                orderId = ORDER_ID_REGEX.find(textWindow.scoringText)?.groupValues?.getOrNull(1),
                confidence = ParseConfidence(confidence.score),
                confidenceReason = confidence.reason,
                categoryHint = categoryHint,
                rawSource = event.rawSource,
                parsePath = scoringMode.toParsePath(),
                // 版本号来自实际打分器，服务器模型落盘后会写入全局模型版本。
                modelVersion = confidence.modelVersion.ifBlank { scoringMode.toModelVersion() },
                modelFeatureSnapshotJson = confidence.featureSnapshotJson,
                sourceApp = normalizeSourceApp(
                    originalSourceApp = sourceApp,
                    sourceRule = sourceRule,
                ),
            ),
        )
    }

    /**
     * 当旧来源枚举还无法表达“金融收益来源”时，至少把银行来源补正回可用枚举。
     *
     * 参数说明：
     * - originalSourceApp：入参事件原本的来源枚举。
     * - sourceRule：新规则表命中的语义来源类别。
     */
    private fun normalizeSourceApp(
        originalSourceApp: NotificationSourceApp,
        sourceRule: ResolvedSourceRule,
    ): NotificationSourceApp {
        return when {
            originalSourceApp != NotificationSourceApp.UNKNOWN -> originalSourceApp
            sourceRule.sourceClass == NotificationSourceClass.TRUSTED_BANK -> NotificationSourceApp.BANK_SMS
            else -> originalSourceApp
        }
    }
}

/**
 * 用途说明：
 * - 把解析模式转换成可追踪的解析路径。
 *
 * 参数说明：
 * - 接收者：本次解析使用的置信度来源。
 */
private fun TransactionScoringMode.toParsePath(): String = when (this) {
    TransactionScoringMode.RULES_ONLY -> "local-parser/rules"
    TransactionScoringMode.MEMBER_MACHINE_LEARNING -> "local-parser/member-ml"
}

/**
 * 用途说明：
 * - 把解析模式转换成候选记录中的版本号。
 *
 * 参数说明：
 * - 接收者：本次解析使用的置信度来源。
 */
private fun TransactionScoringMode.toModelVersion(): String = when (this) {
    TransactionScoringMode.RULES_ONLY -> NotificationRuleConfidenceScorer.VERSION
    TransactionScoringMode.MEMBER_MACHINE_LEARNING -> NotificationConfidenceScorer.MODEL_VERSION
}

/** 订单号模式保留为结构化补充字段，不再参与重权重打分。 */
private val ORDER_ID_REGEX = Regex("""(?:订单号|交易单号|单号|订单)\s*[:：]?\s*([A-Za-z0-9-]{6,})""")
