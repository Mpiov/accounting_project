package com.autobook.cn.core.data.pipeline

import com.autobook.cn.core.data.CaptureCoordinator
import com.autobook.cn.core.data.ModelRouter
import com.autobook.cn.core.data.OcrEngine
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.data.TransactionExtractor
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.TransactionCandidate
import com.autobook.cn.core.network.CloudAnalysisApi
import com.autobook.cn.core.network.toCloudRequest
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 在本地规则、OCR 回退与云端分析之间做逐级兜底。
 *
 * 用途说明：
 * - 优先尝试本地解析，保证主链路离线可用且时延最低。
 * - 当文本不足时再退到“截图 + OCR + 本地重试解析”。
 * - 只有用户显式开启云端分析后，才继续升级到网络回退。
 *
 * 参数说明：
 * - localExtractor：本地规则解析器。
 * - cloudAnalysisApi：云端解析占位接口。
 * - settingsRepository：运行时开关与留存配置来源。
 * - captureCoordinator：截图协调器，负责拉起通知栏并产出截图引用。
 * - ocrEngine：端内 OCR 引擎，负责从截图中提取文本。
 */
class ModelRouterImpl @Inject constructor(
    private val localExtractor: TransactionExtractor,
    private val cloudAnalysisApi: CloudAnalysisApi,
    private val settingsRepository: SettingsRepository,
    private val captureCoordinator: CaptureCoordinator,
    private val ocrEngine: OcrEngine,
) : ModelRouter {
    override suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate> {
        // 第一优先级始终是本地规则解析，避免为每条通知都拉起额外能力。
        val local = localExtractor.extract(event)
        if (local is OperationResult.Success) {
            return local
        }

        val settings = settingsRepository.settings.first()
        val ocrRecovered = tryLocalCaptureAndOcr(event = event, settings = settings)
        if (ocrRecovered is OperationResult.Success) {
            return ocrRecovered
        }

        // 只有用户明确开启云端分析时，才允许继续升级到网络兜底。
        if (!settings.cloudAnalysisEnabled) {
            return when {
                ocrRecovered is OperationResult.RetryableFailure -> ocrRecovered
                local is OperationResult.RetryableFailure -> local
                else -> OperationResult.RetryableFailure("Cloud analysis disabled and local parsing failed")
            }
        }

        val cloudCandidate = cloudAnalysisApi.analyze(event.toCloudRequest())
            ?: return OperationResult.RetryableFailure("Cloud analysis unavailable")
        return OperationResult.Success(cloudCandidate.copy(parsePath = "cloud-placeholder"))
    }

    /**
     * 当原始通知正文不足以完成结构化解析时，尝试走一次截图 OCR 回退链路。
     *
     * 参数说明：
     * - event：当前正在处理的通知事件。
     * - settings：当前运行时设置，用于决定是否允许截图与 OCR。
     */
    private suspend fun tryLocalCaptureAndOcr(
        event: NotificationEvent,
        settings: AppSettings,
    ): OperationResult<TransactionCandidate> {
        if (!settings.captureEnabled || !settings.ocrEnabled) {
            return OperationResult.RetryableFailure("OCR fallback disabled")
        }

        // 先申请截图会话，再执行截图与 OCR；只有两步都成功时才重试本地解析。
        val session = when (val sessionResult = captureCoordinator.requestAuthorization("notification-ocr-fallback")) {
            is OperationResult.Success -> sessionResult.data
            is OperationResult.RetryableFailure -> return sessionResult
            is OperationResult.TerminalFailure -> return sessionResult
        }

        return try {
            when (val captureResult = captureCoordinator.capture(session.sessionId)) {
                is OperationResult.Success -> {
                    when (val ocrText = ocrEngine.extractText(captureResult.data)) {
                        is OperationResult.Success -> retryLocalParseWithOcr(
                            event = event,
                            screenshotRef = captureResult.data,
                            ocrText = ocrText.data,
                        )

                        is OperationResult.RetryableFailure -> ocrText
                        is OperationResult.TerminalFailure -> ocrText
                    }
                }

                is OperationResult.RetryableFailure -> captureResult
                is OperationResult.TerminalFailure -> captureResult
            }
        } finally {
            // 当前截图会话是一次性的；无论成功失败都显式结束，避免后续真实会话实现时遗漏清理点。
            captureCoordinator.cancel(session.sessionId)
        }
    }

    /**
     * 把 OCR 文本拼接回原通知正文后，再交给本地解析器重试一次。
     *
     * 参数说明：
     * - event：原始通知事件。
     * - screenshotRef：本次截图保存后的本地引用。
     * - ocrText：OCR 输出的纯文本。
     */
    private suspend fun retryLocalParseWithOcr(
        event: NotificationEvent,
        screenshotRef: String,
        ocrText: String,
    ): OperationResult<TransactionCandidate> {
        val mergedEvent = event.copy(
            text = listOfNotNull(event.text, ocrText).joinToString(separator = " "),
            screenshotRef = screenshotRef,
        )
        return when (val retriedResult = localExtractor.extract(mergedEvent)) {
            is OperationResult.Success -> {
                // 明确标记这条结果经过了 OCR 回退，方便设置页和后续审计追溯来源。
                OperationResult.Success(
                    retriedResult.data.copy(
                        screenshotRef = screenshotRef,
                        rawSource = "notification+ocr",
                        parsePath = "${retriedResult.data.parsePath}+ocr-fallback",
                        modelVersion = "local-ocr-v1",
                    ),
                )
            }

            is OperationResult.RetryableFailure -> retriedResult
            is OperationResult.TerminalFailure -> retriedResult
        }
    }
}
