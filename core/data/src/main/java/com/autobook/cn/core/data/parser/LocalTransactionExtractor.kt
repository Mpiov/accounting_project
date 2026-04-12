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
        private val amountRegex = Regex("""(?:¥|￥|RMB)?\s*(\d+(?:\.\d{1,2})?)""")

        override suspend fun extract(event: NotificationEvent): OperationResult<TransactionCandidate> {
            val body = listOfNotNull(event.title, event.text).joinToString(" ")
            val match =
                amountRegex.find(body)
                    ?: return OperationResult.RetryableFailure("No amount found in notification")

            val amount =
                match.groupValues[1].toDoubleOrNull()
                    ?: return OperationResult.TerminalFailure("Amount was not parseable")
            val normalized = body.lowercase(Locale.ROOT)
            val direction =
                if (
                    normalized.contains("收款") ||
                    normalized.contains("收入") ||
                    normalized.contains("入账")
                ) {
                    MoneyDirection.INCOME
                } else {
                    MoneyDirection.EXPENSE
                }

            val confidence =
                when {
                    event.packageName.contains("mm", ignoreCase = true) -> 0.92f
                    body.contains("成功") -> 0.88f
                    else -> 0.72f
                }

            return OperationResult.Success(
                TransactionCandidate(
                    amount = amount,
                    direction = direction,
                    merchant = event.title?.takeIf { it.isNotBlank() } ?: event.packageName,
                    channel =
                        when {
                            event.packageName.contains("alipay", ignoreCase = true) -> "Alipay"
                            event.packageName.contains("mm", ignoreCase = true) -> "WeChat Pay"
                            else -> "Notification"
                        },
                    timestamp = event.postedAt,
                    confidence = ParseConfidence(confidence),
                    rawSource = event.rawSource,
                    parsePath = "local-parser",
                ),
            )
        }
    }
