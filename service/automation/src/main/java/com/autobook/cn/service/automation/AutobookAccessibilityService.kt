package com.autobook.cn.service.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.autobook.cn.core.data.automation.AutomationRuntime
import com.autobook.cn.core.data.automation.AutomationRuntimeRegistry
import com.autobook.cn.core.model.OperationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 无障碍自动化服务。
 *
 * 用途说明：
 * - 提供打开通知栏、执行系统截图、收起系统面板等主动截图所需能力。
 * - 服务连接成功后会把能力注册进运行时注册表，供数据层的截图协调器调用。
 *
 * 参数说明：
 * - AccessibilityService 由系统创建，本类无显式构造参数。
 */
class AutobookAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 对外暴露给数据层的自动化运行时实现。
     *
     * 说明：
     * - 保持在 service 内部定义，便于直接复用当前 service 的系统能力。
     * - 数据层只依赖接口，不感知具体 service 类型。
     */
    private val runtime = object : AutomationRuntime {
        override fun openNotificationShade(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

        override suspend fun captureScreenshot(): OperationResult<Bitmap> = captureCurrentDisplay()

        override fun collapseSystemUi(): Boolean {
            /**
             * 这里不能再用 `GLOBAL_ACTION_BACK` 收起通知栏。
             *
             * 用途说明：
             * - `GLOBAL_ACTION_BACK` 本质上就是一次真实的系统返回键事件。
             * - 用户从通知点进截图入口时，如果我们为了收起通知栏去执行 BACK，
             *   前台应用就很可能直接回退到上一页，这正是当前线上现象。
             * - Android 12 / API 31 开始官方提供了 `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE`，
             *   它只负责收起通知栏，不会额外触发页面返回。
             * - 对更低版本，这里宁可不主动发“返回键”，也不要再制造页面回退副作用。
             */
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            } else {
                true
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutomationRuntimeRegistry.register(runtime)
        Log.d(LOG_TAG, "Accessibility automation service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        AutomationRuntimeRegistry.unregister(runtime)
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * 通过系统截图 API 抓取当前显示内容。
     *
     * 说明：
     * - AccessibilityService 返回的是 HardwareBuffer，必须复制成普通 ARGB_8888 位图后再交给上层保存。
     * - 这样既能避免硬件缓冲区生命周期问题，也便于后续 PNG 落盘与图片分析。
     */
    private suspend fun captureCurrentDisplay(): OperationResult<Bitmap> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return OperationResult.TerminalFailure("Accessibility screenshot requires Android 11 or above")
        }

        return suspendCancellableCoroutine { continuation ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        try {
                            val wrappedBitmap = Bitmap.wrapHardwareBuffer(
                                hardwareBuffer,
                                screenshot.colorSpace,
                            )
                            if (wrappedBitmap == null) {
                                continuation.resume(
                                    OperationResult.TerminalFailure("Accessibility screenshot bitmap was null"),
                                )
                                return
                            }

                            // 复制为普通软件位图，避免后续图片保存或分析读取硬件位图时出现兼容性问题。
                            val copiedBitmap = wrappedBitmap.copy(Bitmap.Config.ARGB_8888, false)
                            wrappedBitmap.recycle()
                            if (copiedBitmap == null) {
                                continuation.resume(
                                    OperationResult.TerminalFailure("Accessibility screenshot copy failed"),
                                )
                                return
                            }
                            continuation.resume(OperationResult.Success(copiedBitmap))
                        } catch (error: Throwable) {
                            continuation.resume(
                                OperationResult.RetryableFailure(
                                    message = "Accessibility screenshot crashed",
                                    cause = error,
                                ),
                            )
                        } finally {
                            // 截图结果底层持有硬件缓冲区，这里及时关闭以避免资源泄漏。
                            hardwareBuffer.close()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        continuation.resume(
                            OperationResult.RetryableFailure(
                                message = "Accessibility screenshot failed: ${screenshotFailureMessage(errorCode)}",
                            ),
                        )
                    }
                },
            )
        }
    }

    /**
     * 把系统错误码转换成更可读的错误摘要，便于后续 logcat 排查。
     *
     * 参数说明：
     * - errorCode：系统截图 API 返回的失败码。
     */
    private fun screenshotFailureMessage(errorCode: Int): String {
        return when (errorCode) {
            ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "internal_error"
            ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "interval_too_short"
            ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "invalid_display"
            ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "accessibility_access_lost"
            else -> "unknown_error_code_$errorCode"
        }
    }

    companion object {
        private const val LOG_TAG = "AutobookAccessService"
    }
}
