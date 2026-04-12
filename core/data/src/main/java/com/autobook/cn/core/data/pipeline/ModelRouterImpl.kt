package com.autobook.cn.core.data.pipeline

import com.autobook.cn.core.data.CaptureCoordinator
import com.autobook.cn.core.data.ModelRouter
import com.autobook.cn.core.data.OcrEngine
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.data.TransactionExtractor
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.TransactionCandidate
import com.autobook.cn.core.network.CloudAnalysisApi
import com.autobook.cn.core.network.toCloudRequest
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** 在本地规则、OCR 回退与云端分析之间做逐级兜底。 */
class ModelRouterImpl @Inject constructor(
    private val localExtractor: TransactionExtractor,
    private val cloudAnalysisApi: CloudAnalysisApi,
    private val settingsRepository: SettingsRepository,
    private val captureCoordinator: CaptureCoordinator,
    private val ocrEngine: OcrEngine,
) : ModelRouter {
    override suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate> {
        // 优先走本地解析，保持主链路离线且低延迟。
        val local = localExtractor.extract(event)
        if (local is OperationResult.Success) {
            return local
        }

        val settings = settingsRepository.settings.first()
        val ocrRecovered = tryLocalCaptureAndOcr(event, settings)
        if (ocrRecovered is OperationResult.Success) {
            return ocrRecovered
        }

        // 只有用户显式开启云端分析时，才允许继续升级到网络兜底。
        if (!settings.cloudAnalysisEnabled) {
            return when {
                ocrRecovered is OperationResult.RetryableFailure -> ocrRecovered
                local is OperationResult.RetryableFailure -> local
                else -> OperationResult.RetryableFailure("Cloud analysis disabled and local parsing failed")
            }
        }
    }

    private suspend fun tryLocalCaptureAndOcr(
        event: NotificationEvent,
        settings: com.autobook.cn.core.model.AppSettings,
    ): OperationResult<TransactionCandidate> {
        if (!settings.captureEnabled || !settings.ocrEnabled) {
            return OperationResult.RetryableFailure("OCR fallback disabled")
        }

        // 先申请截图会话，再走截图与 OCR，两步都成功后才重试本地解析。
        val session = when (val sessionResult = captureCoordinator.requestAuthorization("notification-ocr-fallback")) {
            is OperationResult.Success -> sessionResult.data
            is OperationResult.RetryableFailure -> return sessionResult
            is OperationResult.TerminalFailure -> return sessionResult
        }
        return when (val captureResult = captureCoordinator.capture(session.sessionId)) {
            is OperationResult.Success -> {
                when (val ocrText = ocrEngine.extractText(captureResult.data)) {
                    is OperationResult.Success -> {
                        // 把 OCR 文本拼回原通知正文，提高第二次本地解析命中率。
                        localExtractor.extract(
                            event.copy(
                                text = listOfNotNull(event.text, ocrText.data).joinToString(separator = " "),
                                screenshotRef = captureResult.data,
                            ),
                        )
                    }

                    is OperationResult.RetryableFailure -> ocrText
                    is OperationResult.TerminalFailure -> ocrText
                }
            }

            is OperationResult.RetryableFailure -> captureResult
            is OperationResult.TerminalFailure -> captureResult
        }
    }
}
