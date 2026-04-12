package com.autobook.cn.core.data.parser

import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationSourceApp
import java.security.MessageDigest
import java.util.Locale

/** 在通知进入处理链前补齐来源识别与去重键。 */
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

/** 基于包名的来源识别器。 */
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
    // 去重键混合包名、标题、正文和时间，避免不同时间的相似通知被错误折叠。
    val payload = buildString {
        append(packageName.trim())
        append('|')
        append(title.orEmpty().trim())
        append('|')
        append(text.orEmpty().trim())
        append('|')
        append(postedAt)
    }
    // 持久化哈希而不是原文，减少敏感内容在数据库中的暴露面。
    val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
