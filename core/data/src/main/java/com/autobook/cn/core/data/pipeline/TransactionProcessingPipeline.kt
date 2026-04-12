package com.autobook.cn.core.data.pipeline

import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.ModelRouter
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.data.mapper.toLedgerEntry
import com.autobook.cn.core.data.mapper.toPendingEntry
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessOutcome
import com.autobook.cn.core.model.NotificationProcessStatus
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ProcessDestination
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** 决定候选交易是自动入账还是进入待确认队列。 */
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
                // 只要命中疑似重复，就优先交给人工确认而不是直接入账。
                val duplicate = ledgerRepository.findPotentialDuplicate(candidate) != null ||
                    candidate.isDuplicateCandidate
                if (duplicate) {
                    pendingRepository.add(
                        candidate.toPendingEntry(
                            reason = "可能是重复交易，请人工确认",
                            notificationId = notificationId,
                        ),
                    )
                    return OperationResult.Success(
                        NotificationProcessOutcome(
                            notificationId = notificationId,
                            destination = ProcessDestination.PENDING,
                            status = NotificationProcessStatus.PENDING_REVIEW,
                            message = "Routed to pending review because it looks duplicated",
                        ),
                    )
                }

                val shouldAutoLedger = settings.autoAccountingEnabled &&
                    candidate.confidence.score >= AUTO_LEDGER_THRESHOLD
                if (shouldAutoLedger) {
                    ledgerRepository.add(candidate.toLedgerEntry(notificationId = notificationId))
                    OperationResult.Success(
                        NotificationProcessOutcome(
                            notificationId = notificationId,
                            destination = ProcessDestination.LEDGER,
                            status = NotificationProcessStatus.LEDGER_POSTED,
                            message = "Auto-posted with confidence ${candidate.confidence.score}",
                        ),
                    )
                } else {
                    val reason = if (!settings.autoAccountingEnabled) {
                        "自动记账已关闭"
                    } else {
                        "置信度不足：${candidate.confidenceReason}"
                    }
                    pendingRepository.add(
                        candidate.toPendingEntry(
                            reason = reason,
                            notificationId = notificationId,
                        ),
                    )
                    OperationResult.Success(
                        NotificationProcessOutcome(
                            notificationId = notificationId,
                            destination = ProcessDestination.PENDING,
                            status = NotificationProcessStatus.PENDING_REVIEW,
                            message = reason,
                        ),
                    )
                }
            }

            is OperationResult.RetryableFailure -> candidateResult
            is OperationResult.TerminalFailure -> candidateResult
        }
    }
}

/** 超过该阈值的候选记录允许直接自动入账。 */
private const val AUTO_LEDGER_THRESHOLD = 0.85f
