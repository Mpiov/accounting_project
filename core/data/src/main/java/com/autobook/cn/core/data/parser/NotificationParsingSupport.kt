package com.autobook.cn.core.data.parser

import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationSourceApp
import java.security.MessageDigest
import java.util.Locale

internal fun NotificationEvent.enrichForProcessing(): NotificationEvent {
    val detectedSource = NotificationSourceClassifier.detect(packageName)
    return copy(
        sourceApp = detectedSource,
        dedupeKey = buildDedupeKey(
            packageName = packageName,
            title = title,
            text = text,
            postedAt = postedAt,
        ),
    )
}

internal object NotificationSourceClassifier {
    fun detect(packageName: String): NotificationSourceApp {
        val normalized = packageName.lowercase(Locale.ROOT)
        return when {
            normalized.contains("tencent.mm") -> NotificationSourceApp.WECHAT
            normalized.contains("alipay") -> NotificationSourceApp.ALIPAY
            normalized.contains("mms") ||
                normalized.contains("messaging") ||
                normalized.contains("sms") -> NotificationSourceApp.BANK_SMS
            else -> NotificationSourceApp.UNKNOWN
        }
    }
}

private fun buildDedupeKey(
    packageName: String,
    title: String?,
    text: String?,
    postedAt: Long,
): String {
    val payload = buildString {
        append(packageName.trim())
        append('|')
        append(title.orEmpty().trim())
        append('|')
        append(text.orEmpty().trim())
        append('|')
        append(postedAt)
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
