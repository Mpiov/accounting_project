package com.autobook.cn.service.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.autobook.cn.core.common.SafeLogFormatter
import com.autobook.cn.core.data.ServiceDependenciesEntryPoint
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.ProcessDestination
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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

    /**
     * 监听服务被系统断开时主动请求重新绑定。
     *
     * 用途说明：
     * - 真机上出现过“通知使用权仍开启，但服务不在 Live listeners 中”的情况。
     * - 系统回调断开时立即 requestRebind，可以让微信/支付宝支付通知尽快重新进入处理链路。
     */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w("AutobookNotifService", "Notification listener disconnected")
        NotificationListenerTraceRecorder.record(applicationContext, "onListenerDisconnected")
        NotificationListenerRebindHelper.requestRebindIfPermitted(
            context = applicationContext,
            reason = "listener_disconnected",
            force = true,
        )
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
        if (payloads.isEmpty()) {
            // 部分厂商样式可能不给标题和正文；此时至少留下包名 trace，避免后续排查时误以为系统没有回调。
            NotificationListenerTraceRecorder.record(
                applicationContext,
                "payloadEmpty package=${sbn.packageName} postedAt=${sbn.postTime}",
            )
            Log.d("AutobookNotifService", "Notification payload empty package=${sbn.packageName}")
            return
        }
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
                        val settings = entryPoint.settingsRepository().settings.first()
                        if (
                            result.data.destination == ProcessDestination.LEDGER &&
                            settings.autoLedgerSuccessNotificationEnabled
                        ) {
                            AutoLedgerSuccessNotifier(applicationContext).notifySuccess(
                                notificationId = result.data.notificationId,
                                message = result.data.message,
                            )
                        }
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
