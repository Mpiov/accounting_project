package com.autobook.cn.core.data

import com.autobook.cn.core.data.pipeline.TransactionProcessingPipeline
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.PendingEntry
import com.autobook.cn.core.model.ProcessDestination
import com.autobook.cn.core.model.TransactionCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TransactionPipelineTest {
    @Test
    fun `routes low confidence candidates to pending`() =
        runTest {
            val modelRouter =
                object : ModelRouter {
                    override suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate> =
                        OperationResult.Success(
                            TransactionCandidate(
                                amount = 20.0,
                                direction = MoneyDirection.EXPENSE,
                                merchant = "Mock Merchant",
                                channel = "Notification",
                                timestamp = event.postedAt,
                                confidence = ParseConfidence(0.7f),
                                rawSource = "notification",
                                parsePath = "local-parser",
                            ),
                        )
                }
            var pendingCount = 0
            val ledgerRepository =
                object : LedgerRepository {
                    override fun observeEntries(): Flow<List<LedgerEntry>> = emptyFlow()

                    override suspend fun add(entry: LedgerEntry): Long = 1L
                }
            val pendingRepository =
                object : PendingRepository {
                    override fun observeEntries(): Flow<List<PendingEntry>> = emptyFlow()

                    override suspend fun add(entry: PendingEntry): Long {
                        pendingCount += 1
                        return pendingCount.toLong()
                    }

                    override suspend fun confirm(id: Long): PendingEntry? = null

                    override suspend fun ignore(id: Long) = Unit
                }
            val settingsRepository =
                object : SettingsRepository {
                    override val settings: Flow<com.autobook.cn.core.model.AppSettings> =
                        flowOf(com.autobook.cn.core.model.AppSettings(autoAccountingEnabled = true))

                    override suspend fun setOnboardingCompleted(completed: Boolean) = Unit

                    override suspend fun setAutoAccountingEnabled(enabled: Boolean) = Unit

                    override suspend fun setCloudAnalysisEnabled(enabled: Boolean) = Unit
                }

            val outcome =
                TransactionProcessingPipeline(
                    modelRouter,
                    ledgerRepository,
                    pendingRepository,
                    settingsRepository,
                )
                    .process(
                        notificationId = 10L,
                        event =
                            NotificationEvent(
                                packageName = "com.eg.android.AlipayGphone",
                                title = "支付宝",
                                text = "付款 20 元",
                                postedAt = 1L,
                            ),
                    )

            assertEquals(ProcessDestination.PENDING, (outcome as OperationResult.Success).data.destination)
            assertEquals(1, pendingCount)
        }
}
