package com.autobook.cn.core.common

import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessOutcome

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
        return message.replace(Regex("""\d{6,}"""), "******")
    }
}
