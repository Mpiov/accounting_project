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
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
     * 用途说明：
     * - 串行化系统截图请求，避免用户连续点击截图入口时并发调用 `takeScreenshot`。
     * - 部分 ROM 对连续截图很敏感，并发请求可能让无障碍服务被系统判定异常。
     */
    private val screenshotMutex = Mutex()

    /**
     * 用途说明：
     * - 标记无障碍服务是否已经完成系统连接。
     * - 截图前先检查该状态，避免服务断开后继续调用系统能力造成异常。
     */
    @Volatile
    private var serviceConnected = false

    /**
     * 对外暴露给数据层的自动化运行时实现。
     *
     * 说明：
     * - 保持在 service 内部定义，便于直接复用当前 service 的系统能力。
     * - 数据层只依赖接口，不感知具体 service 类型。
     */
    private val runtime = object : AutomationRuntime {
        override fun openNotificationShade(): Boolean {
            return safePerformGlobalAction(
                action = GLOBAL_ACTION_NOTIFICATIONS,
                actionName = "openNotificationShade",
            )
        }

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
                safePerformGlobalAction(
                    action = GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE,
                    actionName = "collapseNotificationShade",
                )
            } else {
                true
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceConnected = true
        AutomationRuntimeRegistry.register(runtime)
        Log.d(LOG_TAG, "Accessibility automation service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 当前服务只使用截图和全局动作能力，不消费窗口事件；空实现可保持系统回调稳定。
    }

    override fun onInterrupt() {
        // 中断回调不等于服务解绑，这里只记录日志，避免误注销仍可用的无障碍运行时。
        Log.w(LOG_TAG, "Accessibility automation service interrupted")
    }

    override fun onDestroy() {
        serviceConnected = false
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
        if (!serviceConnected) {
            return OperationResult.RetryableFailure("Accessibility service is not connected")
        }

        return screenshotMutex.withLock {
            suspendCancellableCoroutine { continuation ->
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
                                    continuation.resumeIfActive(
                                        OperationResult.TerminalFailure("Accessibility screenshot bitmap was null"),
                                    )
                                    return
                                }

                                // 复制为普通软件位图，避免后续图片保存或分析读取硬件位图时出现兼容性问题。
                                val copiedBitmap = wrappedBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                wrappedBitmap.recycle()
                                if (copiedBitmap == null) {
                                    continuation.resumeIfActive(
                                        OperationResult.TerminalFailure("Accessibility screenshot copy failed"),
                                    )
                                    return
                                }
                                continuation.resumeIfActive(OperationResult.Success(copiedBitmap))
                            } catch (error: Throwable) {
                                continuation.resumeIfActive(
                                    OperationResult.RetryableFailure(
                                        message = "Accessibility screenshot crashed",
                                        cause = error,
                                    ),
                                )
                            } finally {
                                // 截图结果底层持有硬件缓冲区，这里及时关闭以避免资源泄漏。
                                runCatching { hardwareBuffer.close() }
                                    .onFailure { Log.w(LOG_TAG, "close screenshot hardware buffer failed", it) }
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            continuation.resumeIfActive(
                                OperationResult.RetryableFailure(
                                    message = "Accessibility screenshot failed: ${screenshotFailureMessage(errorCode)}",
                                ),
                            )
                        }
                    },
                )
            }
        }
    }

    /**
     * 安全恢复截图协程结果。
     *
     * 用途说明：
     * - 系统截图回调可能晚于调用方取消动作到达，直接 `resume` 容易抛出重复恢复异常。
     * - 如果成功位图已经生成但调用方不再等待，需要主动回收，避免短时间多次截图造成内存压力。
     *
     * 参数说明：
     * - result：准备返回给截图调用方的处理结果。
     */
    private fun CancellableContinuation<OperationResult<Bitmap>>.resumeIfActive(result: OperationResult<Bitmap>) {
        if (!isActive) {
            // 调用方已经取消时，成功位图不会再被消费，需要在服务侧立即释放。
            (result as? OperationResult.Success<Bitmap>)?.data?.recycle()
            return
        }
        runCatching { resume(result) }
            .onFailure { error ->
                // 极端竞态下恢复失败时也不能让回调异常冒泡到系统无障碍服务。
                (result as? OperationResult.Success<Bitmap>)?.data?.recycle()
                Log.w(LOG_TAG, "resume screenshot result failed", error)
            }
    }

    /**
     * 安全执行无障碍全局动作。
     *
     * 用途说明：
     * - 部分系统在服务刚断开或权限被回收时，执行全局动作可能抛出运行时异常。
     * - 这里统一吞掉异常并返回 `false`，避免一次动作失败把无障碍服务进程打崩。
     *
     * 参数说明：
     * - action：Android 无障碍全局动作常量。
     * - actionName：日志中使用的动作名称，便于定位失败动作。
     */
    private fun safePerformGlobalAction(action: Int, actionName: String): Boolean {
        if (!serviceConnected) {
            Log.w(LOG_TAG, "skip $actionName because accessibility service is not connected")
            return false
        }
        return runCatching { performGlobalAction(action) }
            .onFailure { error -> Log.w(LOG_TAG, "$actionName failed", error) }
            .getOrDefault(false)
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
