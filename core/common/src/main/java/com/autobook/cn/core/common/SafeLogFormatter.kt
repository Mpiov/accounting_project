package com.autobook.cn.core.common

import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessOutcome

/** 生成适合日志输出的摘要，避免直接打印完整通知内容。 */
object SafeLogFormatter {
    fun forNotification(event: NotificationEvent): String {
        return "source=${event.sourceApp.name} package=${event.packageName} postedAt=${event.postedAt}"
    }

    fun forOutcome(outcome: NotificationProcessOutcome): String {
        return buildString {
            append("notificationId=${outcome.notificationId} ")
            append("destination=${outcome.destination.name} ")
            append("status=${outcome.status.name}")
        }
    }

    fun forFailure(message: String): String {
        // 屏蔽较长数字串，避免订单号、账号等敏感标识直接落日志。
        return message.replace(Regex("""\d{6,}"""), "******")
    }
}
