package com.autobook.cn.core.data

import com.autobook.cn.core.data.pipeline.ModelRouterImpl
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.CaptureSession
import com.autobook.cn.core.model.CaptureSessionState
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.TransactionCandidate
import com.autobook.cn.core.network.CloudAnalysisApi
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 验证路由器会优先采用本地解析成功结果。 */
class ModelRouterTest {
    @Test
    fun `uses local result before cloud`() = runTest {
        val extractor = object : TransactionExtractor {
            override suspend fun extract(event: NotificationEvent): OperationResult<TransactionCandidate> =
                OperationResult.Success(
                    TransactionCandidate(
                        amount = 12.5,
                        direction = MoneyDirection.EXPENSE,
                        merchant = "微信支付",
                        channel = "微信支付",
                        timestamp = 1L,
                        confidence = ParseConfidence(0.9f),
                        confidenceReason = "Matched local rules",
                        rawSource = "notification",
                        parsePath = "local-parser",
                        sourceApp = NotificationSourceApp.WECHAT,
                    ),
                )
        }
        val cloud = mockk<CloudAnalysisApi>(relaxed = true)
        val settings = object : SettingsRepository {
            override val settings: Flow<AppSettings> = flowOf(AppSettings(cloudAnalysisEnabled = true))
            override suspend fun setOnboardingCompleted(completed: Boolean) = Unit
            override suspend fun setAutoAccountingEnabled(enabled: Boolean) = Unit
            override suspend fun setCloudAnalysisEnabled(enabled: Boolean) = Unit
            override suspend fun setCaptureEnabled(enabled: Boolean) = Unit
            override suspend fun setOcrEnabled(enabled: Boolean) = Unit
            override suspend fun setNotificationRetentionDays(days: Int) = Unit
            override suspend fun setScreenshotRetentionDays(days: Int) = Unit
            override suspend fun setSourceEnabled(source: NotificationSourceApp, enabled: Boolean) = Unit
        }
        val captureCoordinator = object : CaptureCoordinator {
            override suspend fun requestAuthorization(reason: String): OperationResult<CaptureSession> =
                OperationResult.Success(CaptureSession("1", CaptureSessionState.REQUESTED, reason))

            override suspend fun capture(sessionId: String): OperationResult<String> =
                OperationResult.RetryableFailure("capture disabled")

            override suspend fun cancel(sessionId: String) = Unit
            override suspend fun reportFailure(sessionId: String, message: String) = Unit
        }
        val ocrEngine = object : OcrEngine {
            override suspend fun extractText(imageRef: String): OperationResult<String> =
                OperationResult.RetryableFailure("ocr disabled")
        }

        val result = ModelRouterImpl(
            localExtractor = extractor,
            cloudAnalysisApi = cloud,
            settingsRepository = settings,
            captureCoordinator = captureCoordinator,
            ocrEngine = ocrEngine,
        ).route(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "支付成功 12.50 元",
                postedAt = 1L,
            ),
        )

        assertTrue(result is OperationResult.Success)
    }

    @Test
    fun `uses ocr fallback when local parser needs more text`() = runTest {
        val extractor = object : TransactionExtractor {
            override suspend fun extract(event: NotificationEvent): OperationResult<TransactionCandidate> {
                return if (event.text?.contains("麦当劳", ignoreCase = true) == true) {
                    OperationResult.Success(
                        TransactionCandidate(
                            amount = 18.0,
                            direction = MoneyDirection.EXPENSE,
                            merchant = "麦当劳",
                            channel = "微信支付",
                            timestamp = event.postedAt,
                            confidence = ParseConfidence(0.9f),
                            confidenceReason = "Matched OCR enriched text",
                            screenshotRef = event.screenshotRef,
                            rawSource = "notification",
                            parsePath = "local-parser",
                            sourceApp = NotificationSourceApp.WECHAT,
                        ),
                    )
                } else {
                    OperationResult.RetryableFailure("Need OCR fallback")
                }
            }
        }
        val cloud = mockk<CloudAnalysisApi>(relaxed = true)
        val settings = object : SettingsRepository {
            override val settings: Flow<AppSettings> = flowOf(
                AppSettings(
                    cloudAnalysisEnabled = false,
                    captureEnabled = true,
                    ocrEnabled = true,
                ),
            )

            override suspend fun setOnboardingCompleted(completed: Boolean) = Unit
            override suspend fun setAutoAccountingEnabled(enabled: Boolean) = Unit
            override suspend fun setCloudAnalysisEnabled(enabled: Boolean) = Unit
            override suspend fun setCaptureEnabled(enabled: Boolean) = Unit
            override suspend fun setOcrEnabled(enabled: Boolean) = Unit
            override suspend fun setNotificationRetentionDays(days: Int) = Unit
            override suspend fun setScreenshotRetentionDays(days: Int) = Unit
            override suspend fun setSourceEnabled(source: NotificationSourceApp, enabled: Boolean) = Unit
        }
        val captureCoordinator = object : CaptureCoordinator {
            override suspend fun requestAuthorization(reason: String): OperationResult<CaptureSession> =
                OperationResult.Success(CaptureSession("ocr-session", CaptureSessionState.AUTHORIZED, reason))

            override suspend fun capture(sessionId: String): OperationResult<String> =
                OperationResult.Success("/tmp/ocr-session.png")

            override suspend fun cancel(sessionId: String) = Unit

            override suspend fun reportFailure(sessionId: String, message: String) = Unit
        }
        val ocrEngine = object : OcrEngine {
            override suspend fun extractText(imageRef: String): OperationResult<String> =
                OperationResult.Success("微信支付 麦当劳 支付成功 18.00 元")
        }

        val result = ModelRouterImpl(
            localExtractor = extractor,
            cloudAnalysisApi = cloud,
            settingsRepository = settings,
            captureCoordinator = captureCoordinator,
            ocrEngine = ocrEngine,
        ).route(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "支付成功 18.00 元",
                postedAt = 1L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals("/tmp/ocr-session.png", candidate.screenshotRef)
        assertEquals("notification+ocr", candidate.rawSource)
        assertEquals("local-parser+ocr-fallback", candidate.parsePath)
        assertEquals("local-ocr-v1", candidate.modelVersion)
    }
}
