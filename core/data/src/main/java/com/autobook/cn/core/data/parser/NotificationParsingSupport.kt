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
    fun detect(packageName: String): NotificationSourceApp {
        val normalized = packageName.lowercase(Locale.ROOT)
        return when {
            normalized.contains("tencent.mm") -> NotificationSourceApp.WECHAT
            normalized.contains("alipay") -> NotificationSourceApp.ALIPAY
            // 建行这类银行 App 会直接发“动账提醒”通知；虽然不是短信包，但业务上应归入银行卡来源。
            normalized.contains("chinamworld") ||
                normalized.contains("ccb") ||
                normalized.contains("bank") ||
                normalized.contains("wallet") -> NotificationSourceApp.BANK_SMS
            normalized.contains("mms") ||
                normalized.contains("messaging") ||
                normalized.contains("sms") -> NotificationSourceApp.BANK_SMS
            else -> NotificationSourceApp.UNKNOWN
        }
    }
}
