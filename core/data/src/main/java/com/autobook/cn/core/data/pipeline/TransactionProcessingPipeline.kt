package com.autobook.cn.core.data.pipeline

import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.ModelRouter
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.data.mapper.toLedgerEntry
import com.autobook.cn.core.data.mapper.toPendingEntry
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessOutcome
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ProcessDestination
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class TransactionProcessingPipeline @Inject constructor(
    private val modelRouter: ModelRouter,
    private val ledgerRepository: LedgerRepository,
    private val pendingRepository: PendingRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun process(
        notificationId: Long,
        event: NotificationEvent,
    ): OperationResult<NotificationProcessOutcome> {
        return when (val candidateResult = modelRouter.route(event)) {
            is OperationResult.Success -> {
                val candidate = candidateResult.data
                val settings = settingsRepository.settings.first()
                val shouldAutoLedger = settings.autoAccountingEnabled &&
                    candidate.confidence.score >= 0.85f
                if (shouldAutoLedger) {
                    ledgerRepository.add(candidate.toLedgerEntry())
                    OperationResult.Success(
                        NotificationProcessOutcome(notificationId, ProcessDestination.LEDGER),
                    )
                } else {
                    val reason = if (!settings.autoAccountingEnabled) {
                        "Auto accounting disabled"
                    } else {
                        "Low confidence"
                    }
                    pendingRepository.add(candidate.toPendingEntry(reason = reason))
                    OperationResult.Success(
                        NotificationProcessOutcome(notificationId, ProcessDestination.PENDING),
                    )
                }
            }

            is OperationResult.RetryableFailure -> candidateResult
            is OperationResult.TerminalFailure -> candidateResult
        }
    }
}
