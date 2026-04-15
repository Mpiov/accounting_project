package com.autobook.cn.app.capture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.autobook.cn.core.common.PermissionStatusChecker
import com.autobook.cn.core.common.SettingsIntents

/**
 * 通知栏快捷动作的透明跳板 Activity。
 *
 * 用途说明：
 * - 某些 ROM 对通知 action 直接拉起 Service 或 BroadcastReceiver 的兼容性并不稳定。
 * - 这里改为先拉起一个透明 Activity，再由 Activity 立即转发给前台服务，通常比广播/服务 action 更稳定。
 * - Activity 本身不承载 UI，只做动作分发，分发完成后立即结束。
 *
 * 参数说明：
 * - Activity 由系统通过通知动作启动，本类无显式构造参数。
 */
class PageCaptureActionActivity : Activity() {
    /**
     * 接住通知动作，并把动作转发给真正的前台服务。
     *
     * 参数说明：
     * - savedInstanceState：系统恢复状态；当前不使用，仅保留标准生命周期签名。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            ACTION_TRIGGER_CAPTURE -> {
                Log.d(LOG_TAG, "Notification action activity received: trigger capture")
                if (!hasAccessibilityCaptureAccess()) {
                    /**
                     * 当前截图链路依赖无障碍服务注册运行时。
                     *
                     * 用途说明：
                     * - 如果用户还没真正开启无障碍服务，继续把 action 派发给前台服务只会得到“截图能力不可用”。
                     * - 这里直接把用户带到系统无障碍设置页，避免通知入口进入无效死路。
                     */
                    Toast.makeText(this, "请先开启无障碍自动化，才能执行当前页面截图", Toast.LENGTH_LONG).show()
                    startActivity(
                        SettingsIntents.accessibilityAccess().apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                    finish()
                    overridePendingTransition(0, 0)
                    return
                }
                /**
                 * 当前截图走无障碍 `takeScreenshot()`，系统通常不会播放显式截图动画。
                 *
                 * 用途说明：
                 * - 给用户一个即时反馈，避免出现“通知栏收起了，但看起来什么都没发生”的错觉。
                 */
                Toast.makeText(this, "正在截图并分析当前页面", Toast.LENGTH_SHORT).show()
                dispatchTriggerCaptureToService()
            }

            ACTION_STOP_PERSISTENT -> {
                Log.d(LOG_TAG, "Notification action activity received: stop persistent entry")
                dispatchStopPersistentToService()
            }
        }

        finish()
        overridePendingTransition(0, 0)
    }

    /**
     * 把“立即截图记账”动作分发给常驻前台服务。
     *
     * 用途说明：
     * - 当前通知入口只会在常驻前台服务已经启动后展示，因此绝大多数情况下目标服务本来就存活。
     * - 这时继续调用 `startForegroundService()` 去“重新拉起”同一个前台服务，反而更容易触发部分 ROM 的限制，
     *   表现就是用户点了通知按钮，但界面和通知状态都没有任何变化。
     * - 因此这里优先对现有服务直接 `startService()` 派发新的 action；只有系统拒绝时，才回退到前台服务启动路径兜底。
     *
     * 参数说明：
     * - 无显式参数；内部直接使用当前 Activity 上下文构造服务 Intent。
     */
    private fun dispatchTriggerCaptureToService() {
        val triggerIntent = PageCaptureBookkeepingService.createTriggerCaptureIntent(this)
        runCatching {
            startService(triggerIntent)
        }.onSuccess { componentName ->
            Log.d(LOG_TAG, "Trigger capture dispatched via startService component=$componentName")
        }.onFailure { error ->
            Log.w(LOG_TAG, "startService trigger failed, fallback to startForegroundService", error)
            ContextCompat.startForegroundService(this, triggerIntent)
        }
    }

    /**
     * 把“关闭常驻入口”动作分发给前台服务。
     *
     * 用途说明：
     * - 关闭入口本质上只是给当前服务发送一个停止 action，不需要重新走前台服务拉起链路。
     * - 保留单独方法是为了让通知动作分发语义更清晰，也方便后续继续补充日志与兼容处理。
     *
     * 参数说明：
     * - 无显式参数；内部直接使用当前 Activity 上下文构造服务 Intent。
     */
    private fun dispatchStopPersistentToService() {
        val stopIntent = PageCaptureBookkeepingService.createStopPersistentIntent(this)
        runCatching {
            startService(stopIntent)
        }.onSuccess { componentName ->
            Log.d(LOG_TAG, "Stop persistent dispatched component=$componentName")
        }.onFailure { error ->
            Log.w(LOG_TAG, "startService stop failed", error)
        }
    }

    /**
     * 检查当前无障碍截图运行时是否已经具备可用前提。
     *
     * 用途说明：
     * - `CaptureCoordinator` 只有在无障碍服务真正连接成功后才会返回可用会话。
     * - 这里先做轻量预检，避免用户每次都先看到失败通知，再自己猜权限缺在哪。
     *
     * 参数说明：
     * - 无显式参数；内部直接基于当前 Activity 上下文查询系统设置。
     */
    private fun hasAccessibilityCaptureAccess(): Boolean {
        return PermissionStatusChecker.hasAccessibilityAccess(
            context = this,
            serviceClassName = ACCESSIBILITY_SERVICE_CLASS_NAME,
        )
    }

    companion object {
        /**
         * 通知栏“立即截图记账”动作的 Activity action。
         */
        const val ACTION_TRIGGER_CAPTURE = "com.autobook.cn.app.capture.ACTION_TRIGGER_CAPTURE_FROM_NOTIFICATION_ACTIVITY"

        /**
         * 通知栏“关闭入口”动作的 Activity action。
         */
        const val ACTION_STOP_PERSISTENT = "com.autobook.cn.app.capture.ACTION_STOP_PERSISTENT_FROM_NOTIFICATION_ACTIVITY"

        private const val LOG_TAG = "PageCaptureActionAct"
        private const val ACCESSIBILITY_SERVICE_CLASS_NAME =
            "com.autobook.cn.service.automation.AutobookAccessibilityService"
    }
}
