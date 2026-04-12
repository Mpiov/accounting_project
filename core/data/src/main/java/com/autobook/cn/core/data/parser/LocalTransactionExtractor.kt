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
    private val amountRegex = Regex("""(?:人民币|RMB|CNY|￥|¥)?\s*([0-9]+(?:\.[0-9]{1,2})?)""")
    private val orderIdRegex = Regex("""(?:订单号|交易单号|单号|订单)\s*[:：]?\s*([A-Za-z0-9-]{6,})""")

    override suspend fun extract(event: NotificationEvent): OperationResult<TransactionCandidate> {
        // 标题和正文在不同应用里承载的信息不一致，这里合并后统一解析。
        val body = listOfNotNull(event.title, event.text)
            .joinToString(separator = " ")
            .trim()
        if (body.isBlank()) {
            return OperationResult.RetryableFailure("Notification body is empty")
        }

        val amountMatch = amountRegex.find(body)
            ?: return OperationResult.RetryableFailure("No amount found in notification")
        val amount = amountMatch.groupValues[1].toDoubleOrNull()
            ?: return OperationResult.TerminalFailure("Amount was not parseable")

        val normalized = body.lowercase(Locale.ROOT)
        val direction = detectDirection(normalized)
        val sourceApp = if (event.sourceApp == NotificationSourceApp.UNKNOWN) {
            NotificationSourceClassifier.detect(event.packageName)
        } else {
            event.sourceApp
        }
        val orderId = orderIdRegex.find(body)?.groupValues?.getOrNull(1)
        val categoryHint = detectCategory(body)
        val merchant = detectMerchant(event = event, body = body, sourceApp = sourceApp)
        val confidence = computeConfidence(
            sourceApp = sourceApp,
            body = body,
            amount = amount,
            orderId = orderId,
            merchant = merchant,
        )

        return OperationResult.Success(
            TransactionCandidate(
                amount = amount,
                direction = direction,
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

    private fun detectDirection(normalizedBody: String): MoneyDirection {
        val incomeKeywords = listOf("收款", "收入", "入账", "到账", "存入", "转入", "退款")
        val expenseKeywords = listOf("支出", "付款", "支付", "扣款", "消费", "转出", "转账")
        return when {
            incomeKeywords.any(normalizedBody::contains) -> MoneyDirection.INCOME
            expenseKeywords.any(normalizedBody::contains) -> MoneyDirection.EXPENSE
            else -> MoneyDirection.EXPENSE
        }
    }

    private fun detectMerchant(
        event: NotificationEvent,
        body: String,
        sourceApp: NotificationSourceApp,
    ): String {
        val title = event.title
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
        return ConfidenceDecision(
            score = score.coerceAtMost(0.98f),
            reason = when {
                orderId != null -> "Matched amount, merchant, and order id"
                body.contains("成功") -> "Matched amount and success keyword"
                else -> "Matched package, amount, and direction keywords"
            },
        )
    }

    private fun channelLabel(sourceApp: NotificationSourceApp): String = when (sourceApp) {
        NotificationSourceApp.WECHAT -> "微信支付"
        NotificationSourceApp.ALIPAY -> "支付宝"
        NotificationSourceApp.BANK_SMS -> "银行卡短信"
        NotificationSourceApp.UNKNOWN -> "通知"
    }
}

/** 仅在解析器内部使用的置信度中间结果。 */
private data class ConfidenceDecision(
    val score: Float,
    val reason: String,
)
