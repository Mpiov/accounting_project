package com.autobook.cn.core.data.parser

import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationSourceApp
import java.util.Locale

/**
 * 在通知进入处理链前补齐来源识别信息。
 *
 * 用途说明：
 * - 当前通知链路不再做“完全相同原文”的精确哈希去重，这里只负责补齐来源枚举。
 * - 后续是否判重，统一交给账本层基于金额、方向和时间窗口判断。
 */
internal fun NotificationEvent.enrichForProcessing(): NotificationEvent {
    val detectedSource = NotificationSourceClassifier.detect(packageName)
    return copy(sourceApp = detectedSource)
}

/**
 * 基于包名的通知来源识别器。
 *
 * 用途说明：
 * - 根据通知所属应用的包名，把通知先映射到微信、支付宝、银行卡或未知来源。
 * - 这一步只做来源粗分类，不负责判定两条通知是不是同一笔交易。
 *
 * 参数说明：
 * - packageName：通知所属应用的包名。
 */
internal object NotificationSourceClassifier {
    /**
     * 按包名识别通知来源。
     *
     * 参数说明：
     * - packageName：系统通知提供的原始包名。
     */
    fun detect(packageName: String): NotificationSourceApp {
        val normalized = packageName.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) {
            return NotificationSourceApp.UNKNOWN
        }

        EXACT_PACKAGE_SOURCE[normalized]?.let { source -> return source }
        PACKAGE_PREFIX_RULES.firstOrNull { rule -> rule.matches(normalized) }?.let { rule ->
            return rule.source
        }

        return when {
            SMS_PACKAGE_HINTS.any(normalized::contains) -> NotificationSourceApp.BANK_SMS
            else -> NotificationSourceApp.UNKNOWN
        }
    }
}

/**
 * 包名前缀来源规则。
 *
 * 参数说明：
 * - prefix：包名前缀，小写。
 * - source：命中后的通知来源。
 */
private data class PackagePrefixRule(
    val prefix: String,
    val source: NotificationSourceApp,
) {
    /**
     * 判断当前包名是否命中前缀。
     *
     * 参数说明：
     * - packageName：已经小写化的包名。
     */
    fun matches(packageName: String): Boolean {
        return packageName == prefix || packageName.startsWith("$prefix.")
    }
}

/**
 * 精确包名表。
 *
 * 用途说明：
 * - 对微信、支付宝和主流短信 App 使用精确匹配，避免把名称相似但无关的 App 误归类。
 */
private val EXACT_PACKAGE_SOURCE = mapOf(
    "com.tencent.mm" to NotificationSourceApp.WECHAT,
    "com.eg.android.alipaygphone" to NotificationSourceApp.ALIPAY,
    "com.android.mms" to NotificationSourceApp.BANK_SMS,
    "com.google.android.apps.messaging" to NotificationSourceApp.BANK_SMS,
    "com.samsung.android.messaging" to NotificationSourceApp.BANK_SMS,
    "com.xiaomi.mms" to NotificationSourceApp.BANK_SMS,
    "com.miui.mms" to NotificationSourceApp.BANK_SMS,
    "com.huawei.mms" to NotificationSourceApp.BANK_SMS,
    "com.hihonor.mms" to NotificationSourceApp.BANK_SMS,
)

/**
 * 包名前缀表。
 *
 * 用途说明：
 * - 银行 App 包名分散，维护明确前缀比 `contains("bank")` 更稳。
 * - 这里先覆盖常见银行，后续真机遇到新包名直接追加即可。
 */
private val PACKAGE_PREFIX_RULES = listOf(
    PackagePrefixRule("com.tencent.mm", NotificationSourceApp.WECHAT),
    PackagePrefixRule("com.eg.android.alipaygphone", NotificationSourceApp.ALIPAY),
    PackagePrefixRule("com.chinamworld.main", NotificationSourceApp.BANK_SMS),
    PackagePrefixRule("com.icbc", NotificationSourceApp.BANK_SMS),
    PackagePrefixRule("com.android.bankabc", NotificationSourceApp.BANK_SMS),
    PackagePrefixRule("com.bankcomm", NotificationSourceApp.BANK_SMS),
    PackagePrefixRule("com.cmbchina", NotificationSourceApp.BANK_SMS),
    PackagePrefixRule("com.yitong.mbank.psbc", NotificationSourceApp.BANK_SMS),
    PackagePrefixRule("com.pingan.paces.ccms", NotificationSourceApp.BANK_SMS),
    PackagePrefixRule("com.cib.cibmb", NotificationSourceApp.BANK_SMS),
    PackagePrefixRule("cn.com.citicbank", NotificationSourceApp.BANK_SMS),
    PackagePrefixRule("cn.com.cmbc", NotificationSourceApp.BANK_SMS),
    PackagePrefixRule("com.cebbank", NotificationSourceApp.BANK_SMS),
    PackagePrefixRule("cn.com.spdb", NotificationSourceApp.BANK_SMS),
)

/**
 * 短信包名兜底关键词。
 *
 * 用途说明：
 * - 厂商短信 App 包名差异大，短信来源可保留保守关键词兜底。
 * - 银行 App 不再使用 `bank` 或 `wallet` 这类宽关键词，降低误判。
 */
private val SMS_PACKAGE_HINTS = listOf(
    ".mms",
    ".sms",
    ".messaging",
    "message",
)
