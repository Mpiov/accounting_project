package com.autobook.cn.core.data.impl

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
 * - 在现阶段尚未接入 MediaProjection 的情况下，先复用已授权的无障碍服务完成通知栏拉起与截图。
 * - 识别成功后把截图保存为本地引用，供 OCR 引擎继续处理。
 *
 * 参数说明：
 * - screenshotStorage：负责把截图位图落盘并返回稳定路径。
 */
@Singleton
class AccessibilityCaptureCoordinator @Inject constructor(
    private val screenshotStorage: ScreenshotStorage,
) : CaptureCoordinator {
    /**
     * 检查当前自动化服务是否已就绪，并创建本次截图会话。
     *
     * 参数说明：
     * - reason：上层传入的触发原因，便于后续排查链路时理解为何发生截图回退。
     */
    override suspend fun requestAuthorization(reason: String): OperationResult<CaptureSession> {
        val runtime = AutomationRuntimeRegistry.current()
            ?: return OperationResult.RetryableFailure("Accessibility automation service is unavailable")
        return OperationResult.Success(
            CaptureSession(
                sessionId = UUID.randomUUID().toString(),
                state = if (runtime.openNotificationShade()) {
                    CaptureSessionState.AUTHORIZED
                } else {
                    CaptureSessionState.REQUESTED
                },
                reason = reason,
            ),
        )
    }

    /**
     * 拉起通知栏、等待界面稳定、执行截图并保存为本地文件路径。
     *
     * 参数说明：
     * - sessionId：由 requestAuthorization 返回的会话 id，用于命名截图文件。
     */
    override suspend fun capture(sessionId: String): OperationResult<String> {
        val runtime = AutomationRuntimeRegistry.current()
            ?: return OperationResult.RetryableFailure("Accessibility automation service is unavailable")
        val opened = runtime.openNotificationShade()
        if (!opened) {
            return OperationResult.RetryableFailure("Unable to open notification shade for OCR fallback")
        }

        return try {
            // 给通知栏下拉动画和系统重绘留出一点时间，避免截到半展开状态。
            delay(NOTIFICATION_SHADE_SETTLE_MS)
            when (val screenshot = runtime.captureScreenshot()) {
                is OperationResult.Success -> {
                    val screenshotRef = screenshotStorage.save(
                        sessionId = sessionId,
                        bitmap = screenshot.data,
                    )
                    OperationResult.Success(screenshotRef)
                }

                is OperationResult.RetryableFailure -> screenshot
                is OperationResult.TerminalFailure -> screenshot
            }
        } catch (error: Throwable) {
            OperationResult.RetryableFailure(
                message = "Failed to persist screenshot for OCR fallback",
                cause = error,
            )
        } finally {
            // 即使 OCR 最终失败，也尽量收起通知栏，减少对用户当前界面的打扰。
            runtime.collapseSystemUi()
        }
    }

    /**
     * 当前版本未维护跨进程会话状态，取消请求时保留空实现以兼容接口。
     */
    override suspend fun cancel(sessionId: String) = Unit

    /**
     * 当前版本暂不额外上报截图失败，会由上层结果对象统一承载错误信息。
     *
     * 参数说明：
     * - sessionId：发生失败的截图会话 id。
     * - message：失败摘要。
     */
    override suspend fun reportFailure(sessionId: String, message: String) = Unit

    companion object {
        private const val NOTIFICATION_SHADE_SETTLE_MS = 350L
    }
}
