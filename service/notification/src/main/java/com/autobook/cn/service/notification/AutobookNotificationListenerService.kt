package com.autobook.cn.service.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.autobook.cn.core.common.SafeLogFormatter
import com.autobook.cn.core.data.ServiceDependenciesEntryPoint
import com.autobook.cn.core.model.NotificationEvent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 监听系统通知并把可疑交易通知送入记账流水线。 */
class AutobookNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        // 这里只提取标题和正文，避免把不必要字段带入后续链路。
        val event = NotificationEvent(
            packageName = sbn.packageName,
            title = extras?.getCharSequence("android.title")?.toString(),
            text = extras?.getCharSequence("android.text")?.toString(),
            postedAt = sbn.postTime,
        )

        serviceScope.launch {
            val entryPoint =
                EntryPointAccessors.fromApplication(
                    applicationContext,
                    ServiceDependenciesEntryPoint::class.java,
                )
            val result = entryPoint.notificationIngestor().ingest(event)
            // 所有日志都走脱敏摘要，避免原始通知内容直接进入 logcat。
            when (result) {
                is com.autobook.cn.core.model.OperationResult.Success -> {
                    Log.d(
                        "AutobookNotifService",
                        "Notification processed ${SafeLogFormatter.forOutcome(result.data)}",
                    )
                }

                is com.autobook.cn.core.model.OperationResult.RetryableFailure -> {
                    val sanitizedMessage = SafeLogFormatter.forFailure(result.message)
                    val notificationSummary = SafeLogFormatter.forNotification(event)
                    Log.w(
                        "AutobookNotifService",
                        "Retryable failure $sanitizedMessage for $notificationSummary",
                    )
                }

                is com.autobook.cn.core.model.OperationResult.TerminalFailure -> {
                    val sanitizedMessage = SafeLogFormatter.forFailure(result.message)
                    val notificationSummary = SafeLogFormatter.forNotification(event)
                    Log.e(
                        "AutobookNotifService",
                        "Terminal failure $sanitizedMessage for $notificationSummary",
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
