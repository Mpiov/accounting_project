package com.autobook.cn.app.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
/**
 * 承接通知栏快捷入口动作的广播接收器。
 *
 * 用途说明：
 * - 一些 ROM 对“通知 action 直接拉起 Service”的兼容性并不稳定，表现就是按钮可见但点击无响应。
 * - 这里先用广播接收器承接通知动作，再由接收器统一转发给前台服务，能显著提升通知动作在 OEM 系统上的触发稳定性。
 *
 * 参数说明：
 * - BroadcastReceiver 由系统按需创建，本类无显式构造参数。
 */
class PageCaptureActionReceiver : BroadcastReceiver() {
    /**
     * 处理通知栏动作，并把动作转发给真正的前台服务。
     *
     * 参数说明：
     * - context：系统传入的接收器上下文，用于继续启动或关闭前台服务。
     * - intent：通知动作对应的广播 Intent，内部只识别“立即截图记账”和“关闭入口”两种动作。
     */
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_TRIGGER_CAPTURE -> {
                Log.d(LOG_TAG, "Notification action received: trigger capture")
                /**
                 * 这里直接向已经常驻运行的前台服务派发新 Intent。
                 *
                 * 说明：
                 * - “立即截图记账”按钮只会出现在常驻入口已经启动成功后，因此目标服务此时应当已经存活。
                 * - 对已运行的前台服务继续使用 `startForegroundService` 再次拉起，反而更容易触发 ROM/系统限制；
                 *   直接 `startService` 交付新的 action 会更稳定。
                 */
                context.startService(PageCaptureBookkeepingService.createTriggerCaptureIntent(context))
            }

            ACTION_STOP_PERSISTENT -> {
                Log.d(LOG_TAG, "Notification action received: stop persistent entry")
                context.startService(PageCaptureBookkeepingService.createStopPersistentIntent(context))
            }
        }
    }

    companion object {
        /**
         * 通知栏“立即截图记账”动作的广播 action。
         *
         * 用途说明：
         * - 与前台服务内部 action 分离，避免通知动作直接命中 Service 时被 OEM 兼容层吞掉。
         */
        const val ACTION_TRIGGER_CAPTURE = "com.autobook.cn.app.capture.ACTION_TRIGGER_CAPTURE_FROM_NOTIFICATION"

        /**
         * 通知栏“关闭入口”动作的广播 action。
         */
        const val ACTION_STOP_PERSISTENT = "com.autobook.cn.app.capture.ACTION_STOP_PERSISTENT_FROM_NOTIFICATION"

        private const val LOG_TAG = "PageCaptureAction"
    }
}
