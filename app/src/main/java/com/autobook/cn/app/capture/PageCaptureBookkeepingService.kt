package com.autobook.cn.app.capture

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
import com.autobook.cn.core.model.ImageImportAnalysisPath
import com.autobook.cn.core.model.ImageImportCandidate
import com.autobook.cn.core.model.LedgerEntry
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
 * 当前页面截图记账后台服务。
 *
 * 用途说明：
 * - 通过常驻前台通知给用户提供“当前页面一键截图记账”入口。
 * - 点击通知动作后会直接抓取当前页面截图，交给图片分析链路，再按置信度对多条有效候选执行批量自动入账或待确认。
 *
 * 参数说明：
 * - Service 由系统创建，本类无显式构造参数。
 */
class PageCaptureBookkeepingService : Service() {
    /** 说明：截图、OCR、模型推理和数据库写入都属于后台 I/O 任务，统一放到服务协程作用域里执行。 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 前台服务不提供绑定能力。
     *
     * 参数说明：
     * - intent：绑定请求；当前不会使用。
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 处理常驻通知的启动、截图和关闭动作。
     *
     * 参数说明：
     * - intent：包含本次服务动作的启动参数。
     * - flags：系统传入的启动标记。
     * - startId：本次启动序号。
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
                    NOTIFICATION_ID,
                    buildPersistentNotification(statusText = "点击这条通知即可截图记账"),
                )
            }

            ACTION_TRIGGER_CAPTURE -> {
                Log.d(LOG_TAG, "Handling trigger capture action")
                startPersistentForeground(
                    NOTIFICATION_ID,
                    buildPersistentNotification(statusText = "正在截屏并准备分析，请稍候..."),
                )
                serviceScope.launch {
                    Log.d(LOG_TAG, "Starting capture coroutine")
                    runCatching {
                        runCaptureAndBookkeeping()
                    }.onFailure { error ->
                        Log.e(LOG_TAG, "runCaptureAndBookkeeping crashed", error)
                        updateForegroundStatus("截图链路异常：${error.message ?: error.javaClass.simpleName}")
                    }
                }
            }

            ACTION_STOP_PERSISTENT -> {
                Log.d(LOG_TAG, "Handling stop persistent action")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }

            else -> {
                Log.d(LOG_TAG, "Handling fallback start action")
                startPersistentForeground(
                    NOTIFICATION_ID,
                    buildPersistentNotification(statusText = "点击这条通知即可截图记账"),
                )
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * 执行一次完整的“当前页面截图 -> 分析 -> 入账/待确认”流程。
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
        /**
         * 截图成功后立即给用户一个“已进入异步分析”的反馈。
         *
         * 用途说明：
         * - 让用户在点击通知后的第一时间获得正反馈，而不是一直盯着“分析中”等待。
         * - 后续真正的分析结果会再通过同一条常驻通知或结果通知同步回来。
         */
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
        var autoLedgerCount = 0
        var pendingCount = 0

        validCandidates.forEach { candidate ->
            val shouldAutoLedger = settings.autoAccountingEnabled &&
                candidate.merchant.isNotBlank() &&
                candidate.confidence.score >= AUTO_LEDGER_THRESHOLD &&
                candidate.confidence.level == ConfidenceLevel.HIGH
            if (shouldAutoLedger) {
                entryPoint.ledgerRepository().add(candidate.toLedgerEntry(suggestion.tracePath))
                autoLedgerCount += 1
            } else if (candidate.confidence.level != ConfidenceLevel.LOW) {
                entryPoint.pendingRepository().add(
                    candidate.toPendingEntry(
                        analysisPath = suggestion.tracePath,
                        reason = buildPendingReason(
                            suggestionMessage = suggestion.message,
                            autoAccountingEnabled = settings.autoAccountingEnabled,
                        ),
                    ),
                )
                pendingCount += 1
            }
        }

        if (pendingCount > 0) {
            notifyPendingReviewResult(pendingCount = pendingCount)
            updateForegroundStatus("$analysisPathLabel：已生成待确认结果，点击这条通知即可再次截图记账")
        } else {
            updateForegroundStatus(
                "$analysisPathLabel：" + buildCaptureSummary(
                    autoLedgerCount = autoLedgerCount,
                    pendingCount = pendingCount,
                    filteredCount = suggestion.filteredOutCandidateCount,
                    totalValidCount = validCandidates.size,
                ),
            )
        }
    }

    /**
     * 更新常驻前台通知中的状态文案。
     *
     * 参数说明：
     * - statusText：要展示在通知里的状态摘要。
     */
    private fun updateForegroundStatus(statusText: String) {
        Log.d(LOG_TAG, "updateForegroundStatus status=$statusText")
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ID,
            buildPersistentNotification(statusText = statusText),
        )
    }

    /**
     * 以显式的 `dataSync` 类型启动前台服务。
     *
     * 用途说明：
     * - Android 14+ 会同时校验 Manifest 中声明的前台服务类型和运行时传入的服务类型。
     * - 这里统一走 `ServiceCompat.startForeground`，避免目标 SDK 35 下因为类型不明确导致系统行为不一致。
     *
     * 参数说明：
     * - notificationId：当前前台通知 ID。
     * - notification：当前要展示的前台通知实例。
     */
    private fun startPersistentForeground(
        notificationId: Int,
        notification: android.app.Notification,
    ) {
        ServiceCompat.startForeground(
            this,
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        /**
         * 某些系统第一次只完成前台服务注册，但不会立刻把通知刷新到通知栏。
         *
         * 用途说明：
         * - 在 `startForeground()` 之后再显式 `notify(...)` 一次，能让通知管理器立刻重绘当前常驻通知，
         *   减少“第一次点启动常驻入口不显示，必须再开关一次”的问题。
         */
        getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }

    /**
     * 当本次截图分析产生待确认记录时，额外发送一条结果通知。
     *
     * 用途说明：
     * - 只在确实需要用户介入时发明确通知，避免自动入账或全过滤场景打扰用户。
     * - 通知文案保持保守抽象，不直接暴露“重复/退还过滤了几条”这类内部推断细节。
     *
     * 参数说明：
     * - pendingCount：本次截图分析后新增的待确认记录数。
     */
    private fun notifyPendingReviewResult(pendingCount: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_RESULT,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
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
     * 构造常驻通知。
     *
     * 参数说明：
     * - statusText：当前通知中展示的状态摘要。
     */
    private fun buildPersistentNotification(statusText: String): android.app.Notification {
        val capturePendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_CAPTURE,
            Intent(this, PageCaptureActionActivity::class.java).apply {
                action = PageCaptureActionActivity.ACTION_TRIGGER_CAPTURE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_STOP,
            Intent(this, PageCaptureActionActivity::class.java).apply {
                action = PageCaptureActionActivity.ACTION_STOP_PERSISTENT
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("截图快捷记账")
            .setContentText(statusText)
            .setContentIntent(capturePendingIntent)
            /**
             * 改回系统原生前台服务通知样式，只保留“点击整条通知即可截图”。
             *
             * 用途说明：
             * - 不再依赖 ROM 是否渲染 action 按钮或自定义通知布局。
             * - 只要通知本身能显示，用户就始终可以通过点击整条通知触发截图链路。
             */
            .setStyle(NotificationCompat.BigTextStyle().bigText("$statusText\n点击这条通知即可截图记账"))
            // 说明：提高兼容层优先级与可见性，尽量让系统保留可点击入口与 action 按钮。
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * 创建通知渠道。
     */
    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        // 说明：旧渠道曾使用 LOW 等级，这会导致部分 ROM 不展示 action 按钮。
        // 这里先清理旧渠道，再确保当前渠道至少以 DEFAULT 等级创建。
        manager.deleteNotificationChannel(LEGACY_NOTIFICATION_CHANNEL_ID)
        val existing = manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
        if (existing != null &&
            existing.importance >= NotificationManager.IMPORTANCE_DEFAULT
        ) {
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
        private const val REQUEST_CODE_STOP = 3002
        private const val REQUEST_CODE_OPEN_RESULT = 3003
        private const val AUTO_LEDGER_THRESHOLD = 0.85f
        private const val LOG_TAG = "PageCaptureService"

        /**
         * 创建“启动常驻入口”的 Intent。
         */
        fun createStartPersistentIntent(context: Context): Intent {
            return Intent(context, PageCaptureBookkeepingService::class.java).apply {
                action = ACTION_START_PERSISTENT
            }
        }

        /**
         * 创建“立即截图记账”的 Intent。
         */
        fun createTriggerCaptureIntent(context: Context): Intent {
            return Intent(context, PageCaptureBookkeepingService::class.java).apply {
                action = ACTION_TRIGGER_CAPTURE
            }
        }

        /**
         * 创建“关闭常驻入口”的 Intent。
         */
        fun createStopPersistentIntent(context: Context): Intent {
            return Intent(context, PageCaptureBookkeepingService::class.java).apply {
                action = ACTION_STOP_PERSISTENT
            }
        }
    }
}

/**
 * 把图片候选转成账本记录。
 *
 * 参数说明：
 * - 接收者：当前已筛选出的最佳图片候选。
 */
private fun ImageImportCandidate.toLedgerEntry(
    analysisPath: ImageImportAnalysisPath,
): LedgerEntry {
    return LedgerEntry(
        amount = amount ?: 0.0,
        direction = direction,
        merchant = merchant.ifBlank { "待确认对象" },
        channel = "当前页面截图",
        timestamp = System.currentTimeMillis(),
        category = category.ifBlank { "待分类" },
        orderId = orderId,
        note = buildCaptureCandidateNote(note = note, evidence = evidence),
        traceSource = "page-capture",
        tracePath = "page-capture:${analysisPath.name.lowercase()}",
        traceModelVersion = "page-capture-v1",
        editedManually = false,
    )
}

/**
 * 把图片候选转成待确认记录。
 *
 * 参数说明：
 * - 接收者：当前已筛选出的最佳图片候选。
 * - reason：进入待确认的原因。
 */
private fun ImageImportCandidate.toPendingEntry(
    analysisPath: ImageImportAnalysisPath,
    reason: String,
): PendingEntry {
    return PendingEntry(
        amount = amount ?: 0.0,
        direction = direction,
        merchant = merchant.ifBlank { "待确认对象" },
        channel = "当前页面截图",
        timestamp = System.currentTimeMillis(),
        confidence = confidence,
        orderId = orderId,
        categoryHint = category.ifBlank { "待分类" },
        rawSource = "page-capture",
        parsePath = "page-capture:${analysisPath.name.lowercase()}",
        modelVersion = "page-capture-v1",
        reason = reason,
    )
}

/**
 * 统一拼接待确认原因。
 *
 * 参数说明：
 * - suggestionMessage：图片分析结果摘要。
 * - multipleCandidatesDetected：本次是否保留了多条候选。
 * - autoAccountingEnabled：当前是否开启自动记账。
 */
private fun buildPendingReason(
    suggestionMessage: String,
    autoAccountingEnabled: Boolean,
): String {
    return when {
        !autoAccountingEnabled -> "自动记账已关闭；$suggestionMessage"
        else -> "置信度不足，需要人工确认；$suggestionMessage"
    }
}

/**
 * 从统一结果对象中提取失败摘要。
 *
 * 参数说明：
 * - result：失败结果。
 */
private fun extractFailureMessage(result: OperationResult<*>): String {
    return when (result) {
        is OperationResult.RetryableFailure -> result.message
        is OperationResult.TerminalFailure -> result.message
        is OperationResult.Success -> ""
    }
}

/**
 * 格式化金额显示。
 *
 * 参数说明：
 * - amount：待展示金额。
 */
private fun formatAmount(amount: Double): String = "¥${"%.2f".format(amount)}"

/**
 * 生成截图记账摘要。
 *
 * 参数说明：
 * - autoLedgerCount：自动入账条数。
 * - pendingCount：待确认条数。
 * - filteredCount：过滤条数。
 * - totalValidCount：有效候选条数。
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
    }.joinToString(separator = "，")
}

/**
 * 生成待确认结果通知标题。
 *
 * 参数说明：
 * - pendingCount：待确认记录数。
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
 * 参数说明：
 * - pendingCount：待确认记录数。
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
 * 参数说明：
 * - note：模型原始备注。
 * - evidence：模型给出的证据片段。
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
 * 把图片分析路径转成通知里更容易理解的中文标签。
 *
 * 参数说明：
 * - 接收者：当前截图分析实际走到的图片分析路径。
 */
private fun ImageImportAnalysisPath.toStatusLabel(): String {
    return when (this) {
        ImageImportAnalysisPath.GEMINI_3_FLASH_PROXY -> "云端 Gemini"
        ImageImportAnalysisPath.OCR_PLUS_LOCAL_LLM -> "本地 OCR + 模型"
        ImageImportAnalysisPath.MANUAL_CONFIRMATION_REQUIRED -> "手动确认草稿"
    }
}
