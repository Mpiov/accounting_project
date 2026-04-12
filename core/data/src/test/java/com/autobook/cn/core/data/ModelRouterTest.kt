package com.autobook.cn.core.data

import com.autobook.cn.core.data.pipeline.ModelRouterImpl
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.TransactionCandidate
import com.autobook.cn.core.network.CloudAnalysisApi
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
                        channel = "WeChat Pay",
                        timestamp = 1L,
                        confidence = ParseConfidence(0.9f),
                        rawSource = "notification",
                        parsePath = "local-parser",
                    ),
                )
        }
        val cloud = mockk<CloudAnalysisApi>(relaxed = true)
        val settings = object : SettingsRepository {
            override val settings: Flow<AppSettings> = flowOf(AppSettings(cloudAnalysisEnabled = true))
            override suspend fun setOnboardingCompleted(completed: Boolean) = Unit
            override suspend fun setAutoAccountingEnabled(enabled: Boolean) = Unit
            override suspend fun setCloudAnalysisEnabled(enabled: Boolean) = Unit
        }

        val result = ModelRouterImpl(
            localExtractor = extractor,
            cloudAnalysisApi = cloud,
            settingsRepository = settings,
        ).route(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "支出 12.50 元",
                postedAt = 1L,
            ),
        )

        assertTrue(result is OperationResult.Success)
    }
}
