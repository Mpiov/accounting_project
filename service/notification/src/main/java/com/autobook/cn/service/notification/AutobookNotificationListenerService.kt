package com.autobook.cn.service.notification

import android.app.Notification
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

/** 监听系统通知，并把可疑交易通知送入记账处理链路。 */
class AutobookNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 记录通知监听服务创建时机，便于确认系统是否真的拉起了该服务。
     */
    override fun onCreate() {
        super.onCreate()
        Log.d("AutobookNotifService", "Notification listener created")
        NotificationListenerTraceRecorder.record(applicationContext, "onCreate")
    }

    /**
     * 记录通知监听连接建立时机，便于排查权限已开但回调链路未真正连通的问题。
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("AutobookNotifService", "Notification listener connected")
        NotificationListenerTraceRecorder.clear(applicationContext)
        NotificationListenerTraceRecorder.record(applicationContext, "onListenerConnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val payloads = NotificationPayloadExtractor.extractAll(
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            bigTitle = extras?.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            textLines = extras
                ?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.map(CharSequence::toString)
                .orEmpty(),
            tickerText = sbn.notification.tickerText?.toString(),
        )
        // `textLines` 聚合通知在最新方案下可能拆成两条独立 payload，这里需要逐条入库和处理。
        payloads.forEachIndexed { index, payload ->
            // 通知样式会因厂商、Android 版本、折叠/展开状态而变化，这里先做一层标准化。
            val event = NotificationEvent(
                packageName = sbn.packageName,
                title = payload.title,
                text = payload.text,
                postedAt = sbn.postTime,
            )
            val payloadIndexLabel = if (payloads.size > 1) "[${index + 1}/${payloads.size}] " else ""
            // 先打印一条轻量摘要，确认系统通知是否已经真正进入应用侧监听服务。
            Log.d(
                "AutobookNotifService",
                "${payloadIndexLabel}Notification posted package=${sbn.packageName} title=${payload.title.orEmpty()} text=${payload.text.orEmpty()}",
            )
            NotificationListenerTraceRecorder.record(
                applicationContext,
                "${payloadIndexLabel}onNotificationPosted package=${sbn.packageName} title=${payload.title.orEmpty()} text=${payload.text.orEmpty()}",
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
                        NotificationListenerTraceRecorder.record(
                            applicationContext,
                            "${payloadIndexLabel}processSuccess outcome=${SafeLogFormatter.forOutcome(result.data)}",
                        )
                        Log.d(
                            "AutobookNotifService",
                            "${payloadIndexLabel}Notification processed ${SafeLogFormatter.forOutcome(result.data)}",
                        )
                    }

                    is com.autobook.cn.core.model.OperationResult.RetryableFailure -> {
                        val sanitizedMessage = SafeLogFormatter.forFailure(result.message)
                        val notificationSummary = SafeLogFormatter.forNotification(event)
                        NotificationListenerTraceRecorder.record(
                            applicationContext,
                            "${payloadIndexLabel}processRetryableFailure message=$sanitizedMessage summary=$notificationSummary",
                        )
                        Log.w(
                            "AutobookNotifService",
                            "${payloadIndexLabel}Retryable failure $sanitizedMessage for $notificationSummary",
                        )
                    }

                    is com.autobook.cn.core.model.OperationResult.TerminalFailure -> {
                        val sanitizedMessage = SafeLogFormatter.forFailure(result.message)
                        val notificationSummary = SafeLogFormatter.forNotification(event)
                        NotificationListenerTraceRecorder.record(
                            applicationContext,
                            "${payloadIndexLabel}processTerminalFailure message=$sanitizedMessage summary=$notificationSummary",
                        )
                        Log.e(
                            "AutobookNotifService",
                            "${payloadIndexLabel}Terminal failure $sanitizedMessage for $notificationSummary",
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
