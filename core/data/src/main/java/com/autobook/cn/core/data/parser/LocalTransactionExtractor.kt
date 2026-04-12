package com.autobook.cn.core.data.parser

import com.autobook.cn.core.data.TransactionExtractor
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.TransactionCandidate
import java.util.Locale
import javax.inject.Inject

class LocalTransactionExtractor
    @Inject
    constructor() : TransactionExtractor {
        // 匹配常见金额表示：¥100, 100.00元, 支付100
        private val amountRegex = Regex("""(?:支付|收款|消费|入账)?\s*(?:¥|￥|RMB)?\s*(\d+(?:\.\d{1,2})?)\s*(?:元)?""")
        // 匹配商户名
        private val merchantAlipayRegex = Regex("""(?:付款给|在)(.*?)(?:成功|消费)""")
        private val merchantWechatRegex = Regex("""(?:你已在|向)(.*?)(?:成功付款|支付)""")

        override suspend fun extract(event: NotificationEvent): OperationResult<TransactionCandidate> {
            val title = event.title ?: ""
            val text = event.text ?: ""
            val body = "$title $text"
            val normalized = body.lowercase(Locale.ROOT)

            // 先判断是不是交易相关通知
            val isTransaction = normalized.contains("支付") || 
                                normalized.contains("收款") || 
                                normalized.contains("消费") || 
                                normalized.contains("入账") ||
                                normalized.contains("成功付款")
            
            if (!isTransaction) {
                return OperationResult.TerminalFailure("Not a transaction notification")
            }

            val amountMatch = amountRegex.find(body)
                ?: return OperationResult.RetryableFailure("No amount found in notification")

            val amount = amountMatch.groupValues[1].toDoubleOrNull()
                ?: return OperationResult.TerminalFailure("Amount was not parseable")

            val direction = if (
                normalized.contains("收款") ||
                normalized.contains("收入") ||
                normalized.contains("入账")
            ) {
                MoneyDirection.INCOME
            } else {
                MoneyDirection.EXPENSE
            }

            val isAlipay = event.packageName.contains("alipay", ignoreCase = true)
            val isWeChat = event.packageName.contains("mm", ignoreCase = true)

            var merchant = if (isAlipay) {
                merchantAlipayRegex.find(body)?.groupValues?.getOrNull(1)?.trim()
            } else if (isWeChat) {
                merchantWechatRegex.find(body)?.groupValues?.getOrNull(1)?.trim()
            } else null

            if (merchant.isNullOrBlank()) {
                merchant = title.takeIf { it.isNotBlank() } ?: "Unknown"
            }

            // 微信和支付宝如果成功匹配商户，置信度提高
            val confidence = when {
                (isAlipay || isWeChat) && merchant != "Unknown" && merchant != title -> 0.95f
                isAlipay || isWeChat -> 0.88f
                body.contains("成功") -> 0.85f
                else -> 0.72f
            }

            val channel = when {
                isAlipay -> "Alipay"
                isWeChat -> "WeChat Pay"
                else -> "Notification"
            }

            return OperationResult.Success(
                TransactionCandidate(
                    amount = amount,
                    direction = direction,
                    merchant = merchant,
                    channel = channel,
                    timestamp = event.postedAt,
                    confidence = ParseConfidence(confidence),
                    rawSource = event.rawSource,
                    parsePath = "local-parser",
                ),
            )
        }
    }
