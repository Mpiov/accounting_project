package com.autobook.cn.service.notification

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import com.autobook.cn.core.common.PermissionStatusChecker

/**
 * 通知监听服务主动重绑辅助器。
 *
 * 用途说明：
 * - 处理“系统权限仍然开启，但 NotificationListenerService 不在 Live listeners 中”的真机异常。
 * - 当 App 启动、回到前台、后台任务被唤醒或监听服务断开时，主动请求系统重新绑定监听服务。
 */
object NotificationListenerRebindHelper {
    @Volatile
    private var lastRequestAtElapsed: Long = 0L

    /**
     * 在通知监听权限已开启时请求系统重新绑定监听服务。
     *
     * 参数说明：
     * - context：任意 Context，内部会统一转成应用级 Context。
     * - reason：本次触发重绑的来源，写入 trace 便于真机排查。
     * - force：是否忽略短时间节流；服务断开回调应强制请求，其它入口保持节流。
     */
    fun requestRebindIfPermitted(
        context: Context,
        reason: String,
        force: Boolean = false,
    ) {
        val appContext = context.applicationContext
        if (!PermissionStatusChecker.hasNotificationAccess(appContext)) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastRequestAtElapsed < REBIND_MIN_INTERVAL_MS) {
            return
        }
        lastRequestAtElapsed = now

        val component = ComponentName(
            appContext,
            AutobookNotificationListenerService::class.java,
        )
        runCatching {
            // requestRebind 只会对已授权的监听服务生效，不会绕过用户的通知使用权开关。
            NotificationListenerService.requestRebind(component)
            NotificationListenerTraceRecorder.record(
                appContext,
                "requestRebind reason=$reason component=${component.flattenToShortString()}",
            )
            Log.d(LOG_TAG, "Requested notification listener rebind reason=$reason component=$component")
        }.onFailure { error ->
            NotificationListenerTraceRecorder.record(
                appContext,
                "requestRebindFailed reason=$reason error=${error.javaClass.simpleName}",
            )
            Log.w(LOG_TAG, "Notification listener rebind failed reason=$reason", error)
        }
    }
}

private const val LOG_TAG = "AutobookNotifRebind"

/** 前台恢复和后台任务可能短时间连续触发，节流可以避免反复打系统 binder。 */
private const val REBIND_MIN_INTERVAL_MS = 30_000L
