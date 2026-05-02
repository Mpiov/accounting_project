package com.autobook.cn.core.data.pipeline

import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.ModelRouter
import com.autobook.cn.core.data.ModelTrainingSampleRepository
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.data.mapper.toLedgerEntry
import com.autobook.cn.core.data.mapper.toPendingEntry
import com.autobook.cn.core.model.ConfidenceLevel
import com.autobook.cn.core.model.HIGH_CONFIDENCE_THRESHOLD
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessOutcome
import com.autobook.cn.core.model.NotificationProcessStatus
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ProcessDestination
import com.autobook.cn.core.model.TransactionCandidate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** 决定候选交易是自动入账还是进入待确认队列。 */
class TransactionProcessingPipeline @Inject constructor(
    private val modelRouter: ModelRouter,
    private val ledgerRepository: LedgerRepository,
    private val pendingRepository: PendingRepository,
    private val settingsRepository: SettingsRepository,
    private val modelTrainingSampleRepository: ModelTrainingSampleRepository,
) {
    /**
     * 执行通知候选的最终分流。
     *
     * 用途说明：
     * - 接收路由层返回的标准化交易候选，并在这里统一决定是直接过滤、进入待确认，还是正式写入账本。
     * - 当前策略调整为“低置信度直接视为与交易无关”，只保留中高置信度候选参与后续人工确认或自动入账。
     *
     * 参数说明：
     * - notificationId：当前通知事件在本地数据库中的主键，用于把后续账本/待确认记录与原始通知关联起来。
     * - event：当前待处理的原始通知事件；这里主要用于透传给下游，便于保持统一处理入口。
     */
    suspend fun process(
        notificationId: Long,
        event: NotificationEvent,
    ): OperationResult<NotificationProcessOutcome> {
        return when (val candidateResult = modelRouter.route(event)) {
            is OperationResult.Success -> {
                val candidate = candidateResult.data
                val settings = settingsRepository.settings.first()
                // 低置信度说明当前通知更像“非交易消息”或“结构信息严重不足”，直接过滤，不再打扰用户做待确认。
                if (candidate.confidence.level == ConfidenceLevel.LOW) {
                    recordTrainingSampleSafely(
                        notificationId = notificationId,
                        event = event,
                        candidate = candidate,
                        destination = ProcessDestination.FILTERED,
                        status = NotificationProcessStatus.FILTERED_OUT,
                    )
                    return OperationResult.Success(
                        NotificationProcessOutcome(
                            notificationId = notificationId,
                            destination = ProcessDestination.FILTERED,
                            status = NotificationProcessStatus.FILTERED_OUT,
                            screenshotRef = candidate.screenshotRef,
                            message = "低置信度候选已过滤：${candidate.confidenceReason}",
                        ),
                    )
                }

                // 只要命中疑似重复，就优先交给人工确认而不是直接入账。
                val duplicate = ledgerRepository.findPotentialDuplicate(candidate) != null ||
                    candidate.isDuplicateCandidate
                if (duplicate) {
                    val pendingEntryId = pendingRepository.add(
                        candidate.toPendingEntry(
                            reason = "可能是重复交易，请人工确认",
                            notificationId = notificationId,
                        ),
                    )
                    recordTrainingSampleSafely(
                        notificationId = notificationId,
                        event = event,
                        candidate = candidate,
                        destination = ProcessDestination.PENDING,
                        status = NotificationProcessStatus.PENDING_REVIEW,
                        pendingEntryId = pendingEntryId,
                    )
                    return OperationResult.Success(
                        NotificationProcessOutcome(
                            notificationId = notificationId,
                            destination = ProcessDestination.PENDING,
                            status = NotificationProcessStatus.PENDING_REVIEW,
                            screenshotRef = candidate.screenshotRef,
                            message = "Routed to pending review because it looks duplicated",
                        ),
                    )
                }

                val shouldAutoLedger = settings.autoAccountingEnabled &&
                    candidate.confidence.score >= HIGH_CONFIDENCE_THRESHOLD
                if (shouldAutoLedger) {
                    val ledgerEntryId = ledgerRepository.add(candidate.toLedgerEntry(notificationId = notificationId))
                    recordTrainingSampleSafely(
                        notificationId = notificationId,
                        event = event,
                        candidate = candidate,
                        destination = ProcessDestination.LEDGER,
                        status = NotificationProcessStatus.LEDGER_POSTED,
                        ledgerEntryId = ledgerEntryId,
                    )
                    OperationResult.Success(
                        NotificationProcessOutcome(
                            notificationId = notificationId,
                            destination = ProcessDestination.LEDGER,
                            status = NotificationProcessStatus.LEDGER_POSTED,
                            screenshotRef = candidate.screenshotRef,
                            message = "Auto-posted with confidence ${candidate.confidence.score}",
                        ),
                    )
                } else {
                    val reason = "置信度不足：${candidate.confidenceReason}"
                    val pendingEntryId = pendingRepository.add(
                        candidate.toPendingEntry(
                            reason = reason,
                            notificationId = notificationId,
                        ),
                    )
                    recordTrainingSampleSafely(
                        notificationId = notificationId,
                        event = event,
                        candidate = candidate,
                        destination = ProcessDestination.PENDING,
                        status = NotificationProcessStatus.PENDING_REVIEW,
                        pendingEntryId = pendingEntryId,
                    )
                    OperationResult.Success(
                        NotificationProcessOutcome(
                            notificationId = notificationId,
                            destination = ProcessDestination.PENDING,
                            status = NotificationProcessStatus.PENDING_REVIEW,
                            screenshotRef = candidate.screenshotRef,
                            message = reason,
                        ),
                    )
                }
            }

            is OperationResult.RetryableFailure -> candidateResult
            is OperationResult.TerminalFailure -> candidateResult
        }
    }

    /**
     * 用途说明：
     * - 记录模型训练样本，但不让样本写入失败影响通知主处理链路。
     *
     * 参数说明：
     * - notificationId：来源通知主键。
     * - event：当前通知事件。
     * - candidate：解析器产出的交易候选。
     * - destination：流水线分流落点。
     * - status：通知最终状态。
     * - pendingEntryId：待确认记录主键，可为空。
     * - ledgerEntryId：账本记录主键，可为空。
     */
    private suspend fun recordTrainingSampleSafely(
        notificationId: Long,
        event: NotificationEvent,
        candidate: TransactionCandidate,
        destination: ProcessDestination,
        status: NotificationProcessStatus,
        pendingEntryId: Long? = null,
        ledgerEntryId: Long? = null,
    ) {
        runCatching {
            modelTrainingSampleRepository.recordPipelineDecision(
                notificationId = notificationId,
                event = event,
                candidate = candidate,
                destination = destination,
                status = status,
                pendingEntryId = pendingEntryId,
                ledgerEntryId = ledgerEntryId,
            )
        }
    }
}
