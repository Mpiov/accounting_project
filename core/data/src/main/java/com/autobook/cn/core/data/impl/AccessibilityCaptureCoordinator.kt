package com.autobook.cn.core.data.impl

import android.util.Log
import com.autobook.cn.core.data.CaptureCoordinator
import com.autobook.cn.core.data.automation.AutomationRuntimeRegistry
import com.autobook.cn.core.model.CaptureSession
import com.autobook.cn.core.model.CaptureSessionState
import com.autobook.cn.core.model.OperationResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * 基于无障碍运行时的截图协调器。
 *
 * 用途说明：
 * - 在当前阶段尚未接入 MediaProjection 的情况下，先复用已经授权的无障碍服务完成当前页面截图。
 * - 截图成功后把位图落盘成应用私有文件引用，供后续 OCR 与图片分析链路继续处理。
 *
 * 参数说明：
 * - screenshotStorage：负责把截图位图保存为稳定文件路径的存储组件。
 */
@Singleton
class AccessibilityCaptureCoordinator @Inject constructor(
    private val screenshotStorage: ScreenshotStorage,
) : CaptureCoordinator {
    /**
     * 检查当前自动化运行时是否在线，并创建一次截图会话。
     *
     * 用途说明：
     * - 当前实现不再额外申请 MediaProjection 级授权，只要无障碍运行时已连接，就认为截图能力可用。
     * - 返回会话对象是为了兼容后续可能扩展成多阶段截图授权的接口设计。
     *
     * 参数说明：
     * - reason：上层传入的触发原因，便于后续排查为什么触发了截图回退链路。
     */
    override suspend fun requestAuthorization(reason: String): OperationResult<CaptureSession> {
        val runtime = AutomationRuntimeRegistry.current()
            ?: return OperationResult.RetryableFailure("未检测到无障碍自动化服务，请先在系统设置中开启 Autobook 无障碍服务")
        Log.d(LOG_TAG, "requestAuthorization success reason=$reason")
        return OperationResult.Success(
            CaptureSession(
                sessionId = UUID.randomUUID().toString(),
                state = CaptureSessionState.AUTHORIZED,
                reason = reason,
            ),
        )
    }

    /**
     * 收起系统面板、等待界面稳定、执行截图，并把截图保存为本地引用。
     *
     * 用途说明：
     * - “立即截图记账”入口本身位于通知栏动作中，用户点击时系统通知面板通常仍然是展开态。
     * - 如果此时直接截图，往往截到的是通知栏本身，而不是用户刚才正在看的业务页面，表现出来就像“点击没有反应”。
     * - 因此这里会先尝试收起系统面板，再等待一个很短的稳定期，最后才真正截图。
     *
     * 参数说明：
     * - sessionId：由 `requestAuthorization` 返回的会话 ID，用于命名截图文件并串联本次流程。
     */
    override suspend fun capture(sessionId: String): OperationResult<String> {
        val runtime = AutomationRuntimeRegistry.current()
            ?: return OperationResult.RetryableFailure("未检测到无障碍自动化服务，请先在系统设置中开启 Autobook 无障碍服务")

        return try {
            /**
             * 先尝试收起通知面板。
             *
             * 用途说明：
             * - Android 12+ 会走专门的“收起通知栏”动作；
             * - 更低版本不再通过 BACK 强行关闭系统面板，避免把前台应用错误地返回上一页。
             * - 即使个别 ROM 上收起动作失败，也继续尝试截图，避免把整条能力完全卡死。
             */
            val collapseIssued = runtime.collapseSystemUi()
            Log.d(LOG_TAG, "collapseSystemUi issued=$collapseIssued for session=$sessionId")

            // 给系统面板收起动画和页面回稳留一点时间，减少截到过渡帧的概率。
            delay(CAPTURE_SETTLE_MS)

            when (val screenshot = runtime.captureScreenshot()) {
                is OperationResult.Success -> {
                    Log.d(LOG_TAG, "captureScreenshot success for session=$sessionId")
                    val screenshotRef = screenshotStorage.save(
                        sessionId = sessionId,
                        bitmap = screenshot.data,
                    )
                    Log.d(LOG_TAG, "screenshot saved ref=$screenshotRef")
                    OperationResult.Success(screenshotRef)
                }

                is OperationResult.RetryableFailure -> {
                    Log.w(LOG_TAG, "captureScreenshot retryable failure=${screenshot.message}")
                    screenshot
                }

                is OperationResult.TerminalFailure -> {
                    Log.w(LOG_TAG, "captureScreenshot terminal failure=${screenshot.message}")
                    screenshot
                }
            }
        } catch (error: Throwable) {
            Log.e(LOG_TAG, "capture crashed for session=$sessionId", error)
            OperationResult.RetryableFailure(
                message = "Failed to persist screenshot for OCR fallback",
                cause = error,
            )
        }
    }

    /**
     * 当前版本不维护跨进程会话状态，取消请求时保留空实现以兼容接口。
     *
     * 参数说明：
     * - sessionId：要取消的截图会话 ID；当前未实际使用。
     */
    override suspend fun cancel(sessionId: String) = Unit

    /**
     * 当前版本暂不单独上报截图失败，由上层结果对象统一承载错误信息。
     *
     * 参数说明：
     * - sessionId：发生失败的截图会话 ID。
     * - message：失败摘要。
     */
    override suspend fun reportFailure(sessionId: String, message: String) = Unit

    companion object {
        /**
         * 从通知动作回到目标页面后，等待系统界面回稳的时间。
         *
         * 用途说明：
         * - 相比之前略微放宽，优先保证截图拿到的是用户原始页面，而不是通知栏收起动画残帧。
         * - 仍保持在较短范围，避免给用户明显卡顿感。
         */
        private const val CAPTURE_SETTLE_MS = 500L
        private const val LOG_TAG = "AccessCaptureCoord"
    }
}
