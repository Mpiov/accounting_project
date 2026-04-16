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
 * 通知栏截图记账动作的透明跳板 Activity。
 *
 * 用途说明：
 * - 仅承接应用自己创建的通知点击事件，再把“立即截图”动作转发给前台服务。
 * - 该 Activity 在 `AndroidManifest.xml` 中被声明为 `exported=false`，
 *   因此外部应用无法直接拉起，避免把截图链路暴露给应用外部。
 *
 * 参数解释：
 * - 无显式构造参数；实例由系统根据通知点击事件按需创建。
 */
class PageCaptureActionActivity : Activity() {

    /**
     * 处理通知点击，并把合法的截图动作转交给前台服务。
     *
     * 参数解释：
     * - `savedInstanceState`：系统恢复 Activity 状态时传入的 Bundle；当前仅保留标准签名。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            ACTION_TRIGGER_CAPTURE -> handleTriggerCaptureAction()
            else -> {
                // 只接受内部定义的截图动作，其他 action 一律忽略并立即结束。
                Log.w(LOG_TAG, "Ignoring unsupported action: ${intent?.action.orEmpty()}")
            }
        }

        finish()
        overridePendingTransition(0, 0)
    }

    /**
     * 执行截图通知的点击逻辑。
     *
     * 参数解释：
     * - 无显式参数；内部直接读取当前 Activity 的 `intent` 与系统权限状态。
     */
    private fun handleTriggerCaptureAction() {
        Log.d(LOG_TAG, "Notification action activity received: trigger capture")
        if (!hasAccessibilityCaptureAccess()) {
            // 截图能力依赖无障碍服务，权限缺失时直接引导用户去系统设置补齐前置条件。
            Toast.makeText(
                this,
                "请先开启无障碍自动化，才能执行当前页面截图",
                Toast.LENGTH_LONG,
            ).show()
            startActivity(
                SettingsIntents.accessibilityAccess().apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            return
        }

        // 无障碍截图通常不会出现系统截图动画，这里补一个轻提示让用户知道动作已开始执行。
        Toast.makeText(this, "正在截图并分析当前页面", Toast.LENGTH_SHORT).show()
        dispatchTriggerCaptureToService()
    }

    /**
     * 把“立即截图记账”动作投递给常驻前台服务。
     *
     * 参数解释：
     * - 无显式参数；内部基于当前 Activity 构造目标 Service 的显式 Intent。
     */
    private fun dispatchTriggerCaptureToService() {
        val triggerIntent = PageCaptureBookkeepingService.createTriggerCaptureIntent(this)
        runCatching {
            // 常驻通知已经出现时，目标服务通常处于存活状态，优先直接投递 action。
            startService(triggerIntent)
        }.onSuccess { componentName ->
            Log.d(LOG_TAG, "Trigger capture dispatched via startService component=$componentName")
        }.onFailure { error ->
            // 个别 ROM 可能拒绝普通 startService，这里再退回前台服务启动路径兜底。
            Log.w(LOG_TAG, "startService trigger failed, fallback to startForegroundService", error)
            ContextCompat.startForegroundService(this, triggerIntent)
        }
    }

    /**
     * 判断当前是否已经具备无障碍截图前置能力。
     *
     * 参数解释：
     * - 无显式参数；内部直接读取当前应用上下文与目标无障碍服务类名。
     *
     * 返回值说明：
     * - `true`：无障碍服务已就绪，可以继续触发截图。
     * - `false`：前置权限未满足，需要先引导用户去设置页开启。
     */
    private fun hasAccessibilityCaptureAccess(): Boolean {
        return PermissionStatusChecker.hasAccessibilityAccess(
            context = this,
            serviceClassName = ACCESSIBILITY_SERVICE_CLASS_NAME,
        )
    }

    companion object {
        /**
         * 应用内部通知点击时使用的截图 action。
         *
         * 用途说明：
         * - 只保留“点击整条通知触发截图”这一条主交互，不再承载 stop 动作。
         */
        const val ACTION_TRIGGER_CAPTURE =
            "com.autobook.cn.app.capture.ACTION_TRIGGER_CAPTURE_FROM_NOTIFICATION_ACTIVITY"

        private const val LOG_TAG = "PageCaptureActionAct"
        private const val ACCESSIBILITY_SERVICE_CLASS_NAME =
            "com.autobook.cn.service.automation.AutobookAccessibilityService"
    }
}
