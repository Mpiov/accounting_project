package com.autobook.cn.core.data.pipeline

import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.MembershipRepository
import com.autobook.cn.core.data.ModelRouter
import com.autobook.cn.core.data.ModelTrainingSampleRepository
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.data.mapper.toLedgerEntry
import com.autobook.cn.core.data.mapper.toPendingEntry
import com.autobook.cn.core.model.ConfidenceLevel
import com.autobook.cn.core.model.HIGH_CONFIDENCE_THRESHOLD
import com.autobook.cn.core.model.MembershipStatus
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
    private val membershipRepository: MembershipRepository,
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
                val membership = membershipRepository.membershipStatus.first()
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

                val autoLedgerGate = resolveAutoLedgerGate(
                    settingsAutoAccountingEnabled = settings.autoAccountingEnabled,
                    membership = membership,
                )
                val isHighConfidenceCandidate = candidate.confidence.score >= HIGH_CONFIDENCE_THRESHOLD
                val shouldAutoLedger = isHighConfidenceCandidate && autoLedgerGate.allowed
                if (shouldAutoLedger) {
                    if (autoLedgerGate.shouldCountTrialSuccess) {
                        when (val trialResult = membershipRepository.recordAutoLedgerTrialSuccess()) {
                            is OperationResult.Success -> {
                                // 第 10 次免费自动入账会把剩余额度扣到 0，立即关闭开关，界面与后台行为保持一致。
                                if (!trialResult.data.isMember && trialResult.data.autoLedgerTrialRemaining <= 0) {
                                    settingsRepository.disableAutoAccountingForTrialExhaustion()
                                }
                            }
                            is OperationResult.RetryableFailure -> {
                                return routeCandidateToPending(
                                    notificationId = notificationId,
                                    event = event,
                                    candidate = candidate,
                                    reason = trialResult.message,
                                )
                            }
                            is OperationResult.TerminalFailure -> {
                                if (trialResult.code == AUTO_LEDGER_TRIAL_EXHAUSTED_CODE) {
                                    // 服务端确认体验耗尽后立即关闭本地开关，下一条通知不会继续尝试自动入账。
                                    settingsRepository.disableAutoAccountingForTrialExhaustion()
                                }
                                return routeCandidateToPending(
                                    notificationId = notificationId,
                                    event = event,
                                    candidate = candidate,
                                    reason = trialResult.message,
                                )
                            }
                        }
                    }
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
                            amount = candidate.amount,
                            direction = candidate.direction,
                            message = "Auto-posted with confidence ${candidate.confidence.score}",
                        ),
                    )
                } else {
                    val reason = if (isHighConfidenceCandidate) {
                        autoLedgerGate.blockedReason ?: "置信度不足：${candidate.confidenceReason}"
                    } else {
                        "置信度不足：${candidate.confidenceReason}"
                    }
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

    /**
     * 用途说明：
     * - 把候选交易写入待确认，并补齐训练样本分流记录。
     * - 免费自动记账扣次数接口失败时复用这条路径，保证不会在服务器未确认额度时直接落账。
     *
     * 参数说明：
     * - notificationId：来源通知主键。
     * - event：当前原始通知事件。
     * - candidate：已经解析出的交易候选。
     * - reason：进入待确认的用户可读原因。
     */
    private suspend fun routeCandidateToPending(
        notificationId: Long,
        event: NotificationEvent,
        candidate: TransactionCandidate,
        reason: String,
    ): OperationResult<NotificationProcessOutcome> {
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
        return OperationResult.Success(
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

/**
 * 自动入账门禁结果。
 *
 * 参数说明：
 * - allowed：当前高置信候选是否允许直接自动入账。
 * - blockedReason：不允许直接入账时写入待确认的解释。
 * - shouldCountTrialSuccess：本次成功自动入账后是否需要扣减免费体验次数。
 */
private data class AutoLedgerGate(
    val allowed: Boolean,
    val blockedReason: String?,
    val shouldCountTrialSuccess: Boolean,
)

/**
 * 解析高置信自动入账权益门槛。
 *
 * 参数说明：
 * - settingsAutoAccountingEnabled：用户是否打开自动记账开关。
 * - membership：服务器会员状态快照。
 */
private fun resolveAutoLedgerGate(
    settingsAutoAccountingEnabled: Boolean,
    membership: MembershipStatus,
): AutoLedgerGate {
    if (!settingsAutoAccountingEnabled) {
        return AutoLedgerGate(
            allowed = false,
            blockedReason = "自动记账已关闭，需要人工确认",
            shouldCountTrialSuccess = false,
        )
    }
    if (membership.isMember) {
        return AutoLedgerGate(
            allowed = true,
            blockedReason = null,
            shouldCountTrialSuccess = false,
        )
    }
    if (!membership.hasLoggedInAccount) {
        return AutoLedgerGate(
            allowed = false,
            blockedReason = "自动入账需要先登录邮箱账号，已转入待确认",
            shouldCountTrialSuccess = false,
        )
    }
    if (membership.autoLedgerTrialRemaining <= 0) {
        return AutoLedgerGate(
            allowed = false,
            blockedReason = "免费自动入账体验已用完，开通会员后可继续自动入账",
            shouldCountTrialSuccess = false,
        )
    }
    return AutoLedgerGate(
        allowed = true,
        blockedReason = null,
        shouldCountTrialSuccess = true,
    )
}

/** 用途说明：服务端表示免费自动记账体验已经耗尽的稳定错误码。 */
private const val AUTO_LEDGER_TRIAL_EXHAUSTED_CODE = "AUTO_LEDGER_TRIAL_EXHAUSTED"
