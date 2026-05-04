package com.autobook.cn.app.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.autobook.cn.app.MainActivity
import com.autobook.cn.core.data.ServiceDependenciesEntryPoint
import com.autobook.cn.core.model.ConfidenceLevel
import com.autobook.cn.core.model.HIGH_CONFIDENCE_THRESHOLD
import com.autobook.cn.core.model.ImageImportAnalysisPath
import com.autobook.cn.core.model.ImageImportCandidate
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MembershipStatus
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.PendingEntry
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 当前页面截图记账的前台服务。
 *
 * 用途说明：
 * - 负责维持通知栏中的“截图快捷记账”常驻入口。
 * - 负责串起“截图 -> 图片分析 -> 自动入账或进入待确认”的完整链路。
 * - 负责把结果通过前台通知或结果通知反馈给用户。
 *
 * 参数解释：
 * - Service 由系统按需创建，本类无显式构造参数。
 */
class PageCaptureBookkeepingService : Service() {

    /**
     * 服务级协程作用域。
     *
     * 用途说明：
     * - 截图、图片分析和仓库写入都属于后台 I/O 工作，统一放到该作用域执行。
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 当前前台服务不提供绑定能力。
     *
     * 参数解释：
     * - `intent`：绑定请求参数；当前实现不会使用。
     *
     * 返回值说明：
     * - 始终返回 `null`，表示不支持 `bindService`。
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 处理服务启动、截图触发以及内部停止动作。
     *
     * 参数解释：
     * - `intent`：本次启动携带的动作参数。
     * - `flags`：系统传入的启动标记。
     * - `startId`：本次启动序号，用于在停止服务时对应当前请求。
     *
     * 返回值说明：
     * - 返回 `START_STICKY`，表示系统回收后可以按需要重新拉起服务。
     */
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Log.d(LOG_TAG, "onStartCommand action=${intent?.action.orEmpty()} startId=$startId")
        ensureNotificationChannel()
        when (intent?.action) {
            ACTION_START_PERSISTENT -> {
                Log.d(LOG_TAG, "Handling start persistent action")
                startPersistentForeground(
                    notificationId = NOTIFICATION_ID,
                    notification = buildPersistentNotification(
                        statusText = DEFAULT_IDLE_STATUS_TEXT,
                    ),
                )
            }

            ACTION_TRIGGER_CAPTURE -> {
                Log.d(LOG_TAG, "Handling trigger capture action")
                startPersistentForeground(
                    notificationId = NOTIFICATION_ID,
                    notification = buildPersistentNotification(
                        statusText = CAPTURING_STATUS_TEXT,
                    ),
                )
                serviceScope.launch {
                    Log.d(LOG_TAG, "Starting capture coroutine")
                    runCatching {
                        runCaptureAndBookkeeping()
                    }.onFailure { error ->
                        Log.e(LOG_TAG, "runCaptureAndBookkeeping crashed", error)
                        updateForegroundStatus(
                            "截图链路异常：${error.message ?: error.javaClass.simpleName}",
                        )
                    }
                }
            }

            ACTION_STOP_PERSISTENT -> {
                Log.d(LOG_TAG, "Handling stop persistent action")
                // 停止动作仅保留给设置页内部调用，通知侧不再暴露这条路径。
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }

            else -> {
                Log.d(LOG_TAG, "Handling fallback start action")
                startPersistentForeground(
                    notificationId = NOTIFICATION_ID,
                    notification = buildPersistentNotification(
                        statusText = DEFAULT_IDLE_STATUS_TEXT,
                    ),
                )
            }
        }
        return START_STICKY
    }

    /**
     * 在服务销毁时回收协程资源。
     */
    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * 执行完整的截图记账流程。
     *
     * 参数解释：
     * - 无显式参数；内部直接从应用容器获取依赖，并串行执行截图与分析。
     */
    private suspend fun runCaptureAndBookkeeping() {
        Log.d(LOG_TAG, "runCaptureAndBookkeeping started")
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            ServiceDependenciesEntryPoint::class.java,
        )
        val captureCoordinator = entryPoint.captureCoordinator()
        val authorization = captureCoordinator.requestAuthorization("current-page-capture")
        Log.d(LOG_TAG, "Capture authorization result=${authorization::class.simpleName}")
        if (authorization !is OperationResult.Success) {
            updateForegroundStatus("截图能力不可用：${extractFailureMessage(authorization)}")
            return
        }

        val screenshotResult = captureCoordinator.capture(authorization.data.sessionId)
        Log.d(LOG_TAG, "Screenshot result=${screenshotResult::class.simpleName}")
        if (screenshotResult !is OperationResult.Success) {
            updateForegroundStatus("截图失败：${extractFailureMessage(screenshotResult)}")
            return
        }

        val screenshotRef = screenshotResult.data
        // 截图已经拿到后，先用前台通知告诉用户系统正在异步分析，避免误以为点击无效。
        updateForegroundStatus("已截屏分析，稍后结果会同步")

        val analyzeResult = entryPoint.imageImportAnalyzer().analyze(screenshotRef)
        Log.d(LOG_TAG, "Image analyze result=${analyzeResult::class.simpleName}")
        if (analyzeResult !is OperationResult.Success) {
            updateForegroundStatus("图片分析失败：${extractFailureMessage(analyzeResult)}")
            return
        }

        val suggestion = analyzeResult.data
        val analysisPathLabel = suggestion.tracePath.toStatusLabel()
        val validCandidates = suggestion.candidates.filter { candidate ->
            val amount = candidate.amount
            amount != null &&
                amount > 0.0 &&
                candidate.direction != MoneyDirection.UNKNOWN
        }
        if (validCandidates.isEmpty()) {
            updateForegroundStatus("未识别到可用交易（$analysisPathLabel）：${suggestion.message}")
            return
        }

        val settings = entryPoint.settingsRepository().settings.first()
        var membership = entryPoint.membershipRepository().membershipStatus.first()
        var autoLedgerCount = 0
        var pendingCount = 0

        for (candidate in validCandidates) {
            val autoLedgerGate = resolveCaptureAutoLedgerGate(
                settingsAutoAccountingEnabled = settings.autoAccountingEnabled,
                membership = membership,
            )
            val isHighConfidenceCandidate = candidate.confidence.score >= HIGH_CONFIDENCE_THRESHOLD &&
                candidate.confidence.level == ConfidenceLevel.HIGH
            val shouldAutoLedger = isHighConfidenceCandidate && autoLedgerGate.allowed
            if (shouldAutoLedger) {
                if (autoLedgerGate.shouldCountTrialSuccess) {
                    when (val trialResult = entryPoint.membershipRepository().recordAutoLedgerTrialSuccess()) {
                        is OperationResult.Success -> {
                            membership = trialResult.data
                            // 截图记账同样消耗自动入账体验，第 10 次成功后立即关闭总开关。
                            if (!trialResult.data.isMember && trialResult.data.autoLedgerTrialRemaining <= 0) {
                                entryPoint.settingsRepository().setAutoAccountingEnabled(false)
                            }
                        }
                        is OperationResult.RetryableFailure -> {
                            entryPoint.pendingRepository().add(
                                candidate.toPendingEntry(
                                    analysisPath = suggestion.tracePath,
                                    reason = buildPendingReason(
                                        suggestionMessage = suggestion.message,
                                        autoAccountingEnabled = settings.autoAccountingEnabled,
                                        autoLedgerBlockedReason = trialResult.message,
                                    ),
                                ),
                            )
                            pendingCount += 1
                            continue
                        }
                        is OperationResult.TerminalFailure -> {
                            if (trialResult.code == AUTO_LEDGER_TRIAL_EXHAUSTED_CODE) {
                                // 服务端确认免费体验耗尽后关闭本地总开关，避免后续截图继续尝试自动落账。
                                entryPoint.settingsRepository().setAutoAccountingEnabled(false)
                            }
                            entryPoint.pendingRepository().add(
                                candidate.toPendingEntry(
                                    analysisPath = suggestion.tracePath,
                                    reason = buildPendingReason(
                                        suggestionMessage = suggestion.message,
                                        autoAccountingEnabled = settings.autoAccountingEnabled,
                                        autoLedgerBlockedReason = trialResult.message,
                                    ),
                                ),
                            )
                            pendingCount += 1
                            continue
                        }
                    }
                }
                entryPoint.ledgerRepository().add(candidate.toLedgerEntry(suggestion.tracePath))
                autoLedgerCount += 1
            } else if (candidate.confidence.level != ConfidenceLevel.LOW) {
                entryPoint.pendingRepository().add(
                    candidate.toPendingEntry(
                        analysisPath = suggestion.tracePath,
                        reason = buildPendingReason(
                            suggestionMessage = suggestion.message,
                            autoAccountingEnabled = settings.autoAccountingEnabled,
                            autoLedgerBlockedReason = autoLedgerGate.blockedReason.takeIf {
                                isHighConfidenceCandidate
                            },
                        ),
                    ),
                )
                pendingCount += 1
            }
        }

        if (pendingCount > 0) {
            notifyPendingReviewResult(pendingCount = pendingCount)
            updateForegroundStatus("$analysisPathLabel：已生成待确认结果，点击这条通知即可截图记账")
        } else {
            updateForegroundStatus(
                "$analysisPathLabel：${
                    buildCaptureSummary(
                        autoLedgerCount = autoLedgerCount,
                        pendingCount = pendingCount,
                        filteredCount = suggestion.filteredOutCandidateCount,
                        totalValidCount = validCandidates.size,
                    )
                }",
            )
        }
    }

    /**
     * 更新常驻前台通知中的状态文案。
     *
     * 参数解释：
     * - `statusText`：要展示在通知里的最新状态摘要。
     */
    private fun updateForegroundStatus(statusText: String) {
        Log.d(LOG_TAG, "updateForegroundStatus status=$statusText")
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildPersistentNotification(statusText = statusText),
        )
    }

    /**
     * 以显式 `dataSync` 类型启动前台服务。
     *
     * 参数解释：
     * - `notificationId`：本次前台通知的通知 ID。
     * - `notification`：本次需要展示的通知实例。
     */
    private fun startPersistentForeground(
        notificationId: Int,
        notification: Notification,
    ) {
        ServiceCompat.startForeground(
            this,
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        // 某些系统首次前台化后不会立刻刷新通知栏，这里主动补一次 notify 保证入口可见。
        getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    /**
     * 当本次截图分析产出待确认记录时，额外发送一条结果通知。
     *
     * 参数解释：
     * - `pendingCount`：本次新增的待确认记录数量。
     */
    private fun notifyPendingReviewResult(pendingCount: Int) {
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_RESULT,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        getSystemService(NotificationManager::class.java).notify(
            RESULT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, RESULT_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(buildPendingResultTitle(pendingCount))
                .setContentText(buildPendingResultText(pendingCount))
                .setAutoCancel(true)
                .setContentIntent(openAppPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build(),
        )
    }

    /**
     * 构造截图记账常驻通知。
     *
     * 参数解释：
     * - `statusText`：通知中需要展示给用户的当前状态摘要。
     *
     * 返回值说明：
     * - 返回仅保留“点击整条通知即可截图”交互的前台通知实例。
     */
    private fun buildPersistentNotification(statusText: String): Notification {
        val capturePendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_CAPTURE,
            Intent(this, PageCaptureActionActivity::class.java).apply {
                action = PageCaptureActionActivity.ACTION_TRIGGER_CAPTURE
                // 显式回到应用内未导出的透明 Activity，避免外部应用伪造入口。
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("截图快捷记账")
            .setContentText(statusText)
            .setContentIntent(capturePendingIntent)
            // 只保留整条通知点击交互，通知内不再挂 stop action 或额外按钮。
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$statusText\n点击这条通知即可截图记账",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * 确保通知渠道存在且等级满足当前通知策略。
     */
    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        // 老渠道可能残留较低 importance，这里先清理遗留渠道，再保证当前渠道以默认等级存在。
        manager.deleteNotificationChannel(LEGACY_NOTIFICATION_CHANNEL_ID)
        val existing = manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
        if (existing != null && existing.importance >= NotificationManager.IMPORTANCE_DEFAULT) {
            return
        }
        if (existing != null) {
            manager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID)
        }

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "截图快捷记账",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "用于常驻显示当前页面截图记账入口"
        }
        manager.createNotificationChannel(channel)

        val resultChannel = NotificationChannel(
            RESULT_NOTIFICATION_CHANNEL_ID,
            "截图记账结果",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "用于提示当前页面截图记账后仍需人工确认的记录"
        }
        manager.createNotificationChannel(resultChannel)
    }

    companion object {
        private const val ACTION_START_PERSISTENT = "com.autobook.cn.app.capture.START_PERSISTENT"
        private const val ACTION_TRIGGER_CAPTURE = "com.autobook.cn.app.capture.TRIGGER_CAPTURE"
        private const val ACTION_STOP_PERSISTENT = "com.autobook.cn.app.capture.STOP_PERSISTENT"

        private const val LEGACY_NOTIFICATION_CHANNEL_ID = "page_capture_bookkeeping"
        private const val NOTIFICATION_CHANNEL_ID = "page_capture_bookkeeping_v2"
        private const val RESULT_NOTIFICATION_CHANNEL_ID = "page_capture_bookkeeping_result"
        private const val NOTIFICATION_ID = 2001
        private const val RESULT_NOTIFICATION_ID = 2002
        private const val REQUEST_CODE_CAPTURE = 3001
        private const val REQUEST_CODE_OPEN_RESULT = 3003
        private const val LOG_TAG = "PageCaptureService"
        private const val DEFAULT_IDLE_STATUS_TEXT = "点击这条通知即可截图记账"
        private const val CAPTURING_STATUS_TEXT = "正在截屏并准备分析，请稍候..."

        /**
         * 创建“启动截图常驻入口”的显式 Intent。
         *
         * 参数解释：
         * - `context`：用于构造目标 Service 显式 Intent 的上下文。
         */
        fun createStartPersistentIntent(context: Context): Intent {
            return Intent(context, PageCaptureBookkeepingService::class.java).apply {
                action = ACTION_START_PERSISTENT
            }
        }

        /**
         * 创建“立即截图记账”的显式 Intent。
         *
         * 参数解释：
         * - `context`：用于构造目标 Service 显式 Intent 的上下文。
         */
        fun createTriggerCaptureIntent(context: Context): Intent {
            return Intent(context, PageCaptureBookkeepingService::class.java).apply {
                action = ACTION_TRIGGER_CAPTURE
            }
        }

        /**
         * 创建“关闭截图常驻入口”的显式 Intent。
         *
         * 参数解释：
         * - `context`：用于构造目标 Service 显式 Intent 的上下文。
         *
         * 说明：
         * - 该动作仅供设置页内部使用，不再从通知栏直接暴露。
         */
        fun createStopPersistentIntent(context: Context): Intent {
            return Intent(context, PageCaptureBookkeepingService::class.java).apply {
                action = ACTION_STOP_PERSISTENT
            }
        }
    }
}

/**
 * 把图片候选转换成正式账本记录。
 *
 * 参数解释：
 * - `analysisPath`：当前图片分析最终走到的识别路径。
 */
private fun ImageImportCandidate.toLedgerEntry(
    analysisPath: ImageImportAnalysisPath,
): LedgerEntry {
    return LedgerEntry(
        amount = amount ?: 0.0,
        direction = direction,
        channel = "当前页面截图",
        // 优先落识别出的交易时间，只有当前候选没有稳定时间时才回退到创建时刻。
        timestamp = timestamp ?: System.currentTimeMillis(),
        category = category.ifBlank { "待分类" },
        note = buildCaptureCandidateNote(note = note, evidence = evidence),
        traceSource = "page-capture",
        tracePath = "page-capture:${analysisPath.name.lowercase()}",
        traceModelVersion = "page-capture-v1",
        editedManually = false,
    )
}

/**
 * 把图片候选转换成待确认记录。
 *
 * 参数解释：
 * - `analysisPath`：当前图片分析最终走到的识别路径。
 * - `reason`：进入待确认列表的原因说明。
 */
private fun ImageImportCandidate.toPendingEntry(
    analysisPath: ImageImportAnalysisPath,
    reason: String,
): PendingEntry {
    return PendingEntry(
        amount = amount ?: 0.0,
        direction = direction,
        channel = "当前页面截图",
        // 待确认也要保留截图里识别出的交易时间，避免用户后续确认时丢失账单原始时间。
        timestamp = timestamp ?: System.currentTimeMillis(),
        confidence = confidence,
        categoryHint = category.ifBlank { "待分类" },
        note = buildCaptureCandidateNote(note = note, evidence = evidence),
        rawSource = "page-capture",
        parsePath = "page-capture:${analysisPath.name.lowercase()}",
        modelVersion = "page-capture-v1",
        reason = reason,
    )
}

/**
 * 统一生成待确认原因文案。
 *
 * 参数解释：
 * - `suggestionMessage`：图片分析结果的摘要说明。
 * - `autoAccountingEnabled`：当前是否开启了自动记账。
 */
private fun buildPendingReason(
    suggestionMessage: String,
    autoAccountingEnabled: Boolean,
    autoLedgerBlockedReason: String? = null,
): String {
    return when {
        !autoAccountingEnabled -> "自动记账已关闭；$suggestionMessage"
        !autoLedgerBlockedReason.isNullOrBlank() -> "$autoLedgerBlockedReason；$suggestionMessage"
        else -> "置信度不足，需要人工确认；$suggestionMessage"
    }
}

/**
 * 截图自动入账门禁结果。
 *
 * 参数解释：
 * - `allowed`：当前高置信截图候选是否允许直接入账。
 * - `blockedReason`：不能直接入账时转入待确认的解释。
 * - `shouldCountTrialSuccess`：本次成功自动入账后是否需要计入免费体验次数。
 */
private data class CaptureAutoLedgerGate(
    val allowed: Boolean,
    val blockedReason: String?,
    val shouldCountTrialSuccess: Boolean,
)

/**
 * 解析截图候选的自动入账权益门槛。
 *
 * 参数解释：
 * - `settingsAutoAccountingEnabled`：用户是否打开自动记账开关。
 * - `membership`：服务器会员状态快照。
 */
private fun resolveCaptureAutoLedgerGate(
    settingsAutoAccountingEnabled: Boolean,
    membership: MembershipStatus,
): CaptureAutoLedgerGate {
    if (!settingsAutoAccountingEnabled) {
        return CaptureAutoLedgerGate(
            allowed = false,
            blockedReason = "自动记账已关闭",
            shouldCountTrialSuccess = false,
        )
    }
    if (membership.isMember) {
        return CaptureAutoLedgerGate(
            allowed = true,
            blockedReason = null,
            shouldCountTrialSuccess = false,
        )
    }
    if (!membership.hasLoggedInAccount) {
        return CaptureAutoLedgerGate(
            allowed = false,
            blockedReason = "自动入账需要先登录邮箱账号",
            shouldCountTrialSuccess = false,
        )
    }
    if (membership.autoLedgerTrialRemaining <= 0) {
        return CaptureAutoLedgerGate(
            allowed = false,
            blockedReason = "免费自动入账体验已用完，开通会员后可继续自动入账",
            shouldCountTrialSuccess = false,
        )
    }
    return CaptureAutoLedgerGate(
        allowed = true,
        blockedReason = null,
        shouldCountTrialSuccess = true,
    )
}

/**
 * 从统一结果对象中提取失败摘要。
 *
 * 参数解释：
 * - `result`：统一操作结果对象。
 */
private fun extractFailureMessage(result: OperationResult<*>): String {
    return when (result) {
        is OperationResult.RetryableFailure -> result.message
        is OperationResult.TerminalFailure -> result.message
        is OperationResult.Success -> ""
    }
}

/**
 * 生成截图记账结果摘要。
 *
 * 参数解释：
 * - `autoLedgerCount`：自动入账的条数。
 * - `pendingCount`：进入待确认的条数。
 * - `filteredCount`：被过滤的条数。
 * - `totalValidCount`：保留下来的有效候选数。
 */
private fun buildCaptureSummary(
    autoLedgerCount: Int,
    pendingCount: Int,
    filteredCount: Int,
    totalValidCount: Int,
): String {
    return buildList {
        if (autoLedgerCount > 0) {
            add("已入账 $autoLedgerCount 条")
        }
        if (pendingCount > 0) {
            add("待确认 $pendingCount 条")
        }
        if (filteredCount > 0) {
            add("已过滤 $filteredCount 条")
        }
        if (isEmpty()) {
            add("未处理任何记录")
        } else if (totalValidCount > 1) {
            add("共保留 $totalValidCount 条有效记录")
        }
    }.joinToString(separator = "；")
}

/**
 * 生成待确认结果通知标题。
 *
 * 参数解释：
 * - `pendingCount`：待确认记录数量。
 */
private fun buildPendingResultTitle(pendingCount: Int): String {
    return if (pendingCount == 1) {
        "发现 1 条待确认记录"
    } else {
        "发现 $pendingCount 条待确认记录"
    }
}

/**
 * 生成待确认结果通知正文。
 *
 * 参数解释：
 * - `pendingCount`：待确认记录数量。
 */
private fun buildPendingResultText(pendingCount: Int): String {
    return if (pendingCount == 1) {
        "请确认本次截图中的交易信息"
    } else {
        "请查看并确认本次截图中的交易信息"
    }
}

/**
 * 统一拼接截图候选的备注信息。
 *
 * 参数解释：
 * - `note`：模型给出的备注说明。
 * - `evidence`：模型给出的证据片段。
 */
private fun buildCaptureCandidateNote(
    note: String,
    evidence: String,
): String? {
    return buildList {
        if (note.isNotBlank()) {
            add(note)
        }
        if (evidence.isNotBlank()) {
            add("证据：$evidence")
        }
    }.joinToString(separator = "；").takeIf { it.isNotBlank() }
}

/**
 * 把分析路径转换成通知里更易理解的中文标签。
 *
 * 参数解释：
 * - 接收者：当前截图分析实际走到的识别路径。
 */
private fun ImageImportAnalysisPath.toStatusLabel(): String {
    return when (this) {
        ImageImportAnalysisPath.CLOUD_LLM_PROXY -> "云端大模型"
        ImageImportAnalysisPath.MANUAL_CONFIRMATION_REQUIRED -> "手动确认草稿"
    }
}

/** 用途说明：服务端表示免费自动记账体验已经耗尽的稳定错误码。 */
private const val AUTO_LEDGER_TRIAL_EXHAUSTED_CODE = "AUTO_LEDGER_TRIAL_EXHAUSTED"
