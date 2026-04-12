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
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ModelRouterImpl @Inject constructor(
    private val localExtractor: TransactionExtractor,
    private val cloudAnalysisApi: CloudAnalysisApi,
    private val settingsRepository: SettingsRepository,
    private val captureCoordinator: CaptureCoordinator,
    private val ocrEngine: OcrEngine,
) : ModelRouter {
    override suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate> {
        val local = localExtractor.extract(event)
        if (local is OperationResult.Success) {
            return local
        }

        val settings = settingsRepository.settings.first()
        val ocrRecovered = tryLocalCaptureAndOcr(event, settings)
        if (ocrRecovered is OperationResult.Success) {
            return ocrRecovered
        }

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

    private suspend fun tryLocalCaptureAndOcr(
        event: NotificationEvent,
        settings: com.autobook.cn.core.model.AppSettings,
    ): OperationResult<TransactionCandidate> {
        if (!settings.captureEnabled || !settings.ocrEnabled) {
            return OperationResult.RetryableFailure("OCR fallback disabled")
        }

        val session = when (val sessionResult = captureCoordinator.requestAuthorization("notification-ocr-fallback")) {
            is OperationResult.Success -> sessionResult.data
            is OperationResult.RetryableFailure -> return sessionResult
            is OperationResult.TerminalFailure -> return sessionResult
        }
        val captureResult = captureCoordinator.capture(session.sessionId)
        if (captureResult is OperationResult.Success) {
            return when (val ocrText = ocrEngine.extractText(captureResult.data)) {
                is OperationResult.Success -> {
                    localExtractor.extract(
                        event.copy(
                            text = listOfNotNull(event.text, ocrText.data).joinToString(separator = " "),
                            screenshotRef = captureResult.data,
                        ),
                    )
                }

                is OperationResult.RetryableFailure -> {
                    OperationResult.RetryableFailure(ocrText.message, ocrText.cause)
                }
                is OperationResult.TerminalFailure -> {
                    OperationResult.TerminalFailure(ocrText.message, ocrText.cause)
                }
            }
        }
        return when (captureResult) {
            is OperationResult.RetryableFailure -> {
                OperationResult.RetryableFailure(captureResult.message, captureResult.cause)
            }
            is OperationResult.TerminalFailure -> {
                OperationResult.TerminalFailure(captureResult.message, captureResult.cause)
            }
            is OperationResult.Success -> error("captureResult success branch should have already returned")
        }
    }
}
