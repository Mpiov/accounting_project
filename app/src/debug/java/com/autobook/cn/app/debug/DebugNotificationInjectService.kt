package com.autobook.cn.app.debug

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.autobook.cn.core.common.SafeLogFormatter
import com.autobook.cn.core.data.ServiceDependenciesEntryPoint
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessOutcome
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.PendingEntry
import com.autobook.cn.core.model.ProcessDestination
import dagger.hilt.android.EntryPointAccessors
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * debug 通知注入后台服务。
 *
 * 用途说明：
 * - 承接 `BroadcastReceiver` 转交过来的注入任务，在服务线程里执行完整的通知入账链路。
 * - 避免把本地 LLM 推理挂在广播生命周期内，导致系统把调试注入判定为 Broadcast ANR。
 */
class DebugNotificationInjectService : Service() {
    /** 说明：调试注入本质是 I/O 与模型推理混合任务，这里统一放到后台协程里执行。 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 服务不提供绑定能力。
     *
     * 参数说明：
     * - intent：绑定请求；当前不会使用。
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 接收 Receiver 转交的 debug 注入请求。
     *
     * 参数说明：
     * - intent：包含通知内容、开关状态与模型选择信息的启动参数。
     * - flags：系统传入的启动标记。
     * - startId：本次启动序号，用于任务完成后精准停止当前服务实例。
     */
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action != DebugNotificationInjectContract.ACTION_PROCESS_INJECTION) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        serviceScope.launch {
            try {
                processInjection(intent)
            } catch (error: Throwable) {
                val message = error.message ?: error.javaClass.simpleName
                recordTrace(applicationContext, "injectException message=$message")
                Log.e(DebugNotificationInjectContract.TAG, "Debug inject crashed: $message", error)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 执行一次完整的 debug 通知注入。
     *
     * 参数说明：
     * - intent：承载调试通知与开关配置的启动参数。
     */
    private suspend fun processInjection(intent: Intent) {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            ServiceDependenciesEntryPoint::class.java,
        )
        val settingsRepository = entryPoint.settingsRepository()
        val localModelRepository = entryPoint.localModelRepository()
        val packageName = intent.stringExtraOrDefault(
            DebugNotificationInjectContract.EXTRA_PACKAGE_NAME,
            DebugNotificationInjectContract.DEFAULT_PACKAGE_NAME,
        )
        val title = intent.stringExtraOrDefault(
            DebugNotificationInjectContract.EXTRA_TITLE,
            DebugNotificationInjectContract.DEFAULT_TITLE,
        )
        val text = intent.stringExtraOrDefault(
            DebugNotificationInjectContract.EXTRA_TEXT,
            DebugNotificationInjectContract.DEFAULT_TEXT,
        )
        val postedAt = intent.getLongExtra(
            DebugNotificationInjectContract.EXTRA_POSTED_AT,
            System.currentTimeMillis(),
        )
        val enableLocalLlm = intent.getBooleanExtra(
            DebugNotificationInjectContract.EXTRA_ENABLE_LOCAL_LLM,
            true,
        )
        val enableAutoAccounting = intent.getBooleanExtra(
            DebugNotificationInjectContract.EXTRA_ENABLE_AUTO_ACCOUNTING,
            true,
        )
        val modelId = intent.stringExtraOrDefault(
            DebugNotificationInjectContract.EXTRA_MODEL_ID,
            DebugNotificationInjectContract.DEFAULT_MODEL_ID,
        )
        val event = NotificationEvent(
            packageName = packageName,
            title = title,
            text = text,
            postedAt = postedAt,
        )

        // 真机联调前先把必要开关和来源过滤配置好，保证这条测试通知不会被设置页短路掉。
        settingsRepository.setOnboardingCompleted(true)
        settingsRepository.setAutoAccountingEnabled(enableAutoAccounting)
        settingsRepository.setLocalLlmConfidenceBoostEnabled(enableLocalLlm)
        settingsRepository.setSourceEnabled(NotificationSourceApp.BANK_SMS, true)
        localModelRepository.selectModel(modelId)
        localModelRepository.refresh()

        val notificationSummary = SafeLogFormatter.forNotification(event)
        recordTrace(
            context = applicationContext,
            message = "injectStart summary=$notificationSummary enableLocalLlm=$enableLocalLlm modelId=$modelId",
        )
        Log.d(DebugNotificationInjectContract.TAG, "Injecting debug notification $notificationSummary")

        when (val result = entryPoint.notificationIngestor().ingest(event)) {
            is OperationResult.Success -> {
                val outcome = SafeLogFormatter.forOutcome(result.data)
                recordTrace(applicationContext, "injectSuccess outcome=$outcome")
                recordPersistedTrace(
                    context = applicationContext,
                    entryPoint = entryPoint,
                    outcome = result.data,
                )
                Log.d(DebugNotificationInjectContract.TAG, "Debug inject success $outcome")
            }

            is OperationResult.RetryableFailure -> {
                val sanitizedMessage = SafeLogFormatter.forFailure(result.message)
                recordTrace(applicationContext, "injectRetryableFailure message=$sanitizedMessage")
                Log.w(DebugNotificationInjectContract.TAG, "Debug inject retryable failure $sanitizedMessage")
            }

            is OperationResult.TerminalFailure -> {
                val sanitizedMessage = SafeLogFormatter.forFailure(result.message)
                recordTrace(applicationContext, "injectTerminalFailure message=$sanitizedMessage")
                Log.e(DebugNotificationInjectContract.TAG, "Debug inject terminal failure $sanitizedMessage")
            }
        }
    }
}

/**
 * 把每次 debug 注入的关键阶段写入缓存 trace。
 *
 * 参数说明：
 * - context：应用上下文，用于定位 trace 文件。
 * - message：要写入的调试消息。
 */
private fun recordTrace(
    context: Context,
    message: String,
) {
    runCatching {
        val traceFile = context.cacheDir.resolve(DebugNotificationInjectContract.TRACE_FILE_NAME)
        val timestamp = DebugNotificationInjectContract.TRACE_TIME_FORMAT.format(Date())
        // 每条记录都带时间戳，方便和 logcat、数据库结果对齐。
        traceFile.appendText("$timestamp $message\n")
    }
}

/**
 * 从仓库里回读本次通知最终落库的记录，并把 trace 字段补写到调试文件中。
 *
 * 参数说明：
 * - context：应用上下文，用于写 trace。
 * - entryPoint：当前应用的依赖入口，用于读取账本与待确认仓库。
 * - outcome：通知处理结果，包含目标去向与通知主键。
 */
private suspend fun recordPersistedTrace(
    context: Context,
    entryPoint: ServiceDependenciesEntryPoint,
    outcome: NotificationProcessOutcome,
) {
    when (outcome.destination) {
        ProcessDestination.PENDING -> {
            val pendingEntry = entryPoint.pendingRepository()
                .observeEntries()
                .first()
                .firstOrNull { it.traceNotificationId == outcome.notificationId }
            if (pendingEntry != null) {
                recordPendingTrace(
                    context = context,
                    pendingEntry = pendingEntry,
                )
            }
        }

        ProcessDestination.LEDGER -> {
            val ledgerEntry = entryPoint.ledgerRepository()
                .observeEntries()
                .first()
                .firstOrNull { it.traceNotificationId == outcome.notificationId }
            if (ledgerEntry != null) {
                recordLedgerTrace(
                    context = context,
                    ledgerEntry = ledgerEntry,
                )
            }
        }

        else -> {
            // 过滤或去重分支没有后续落库记录，这里只保留 outcome 即可。
        }
    }
}

/**
 * 记录待确认项的关键信息。
 *
 * 参数说明：
 * - context：应用上下文，用于写 trace。
 * - pendingEntry：刚刚写入待确认列表的领域对象。
 */
private fun recordPendingTrace(
    context: Context,
    pendingEntry: PendingEntry,
) {
    recordTrace(
        context = context,
        message = "pendingTrace parsePath=${pendingEntry.parsePath} modelVersion=${pendingEntry.modelVersion} reason=${pendingEntry.reason}",
    )
}

/**
 * 记录账本项的关键信息。
 *
 * 参数说明：
 * - context：应用上下文，用于写 trace。
 * - ledgerEntry：刚刚写入账本的领域对象。
 */
private fun recordLedgerTrace(
    context: Context,
    ledgerEntry: LedgerEntry,
) {
    recordTrace(
        context = context,
        message = "ledgerTrace tracePath=${ledgerEntry.tracePath} modelVersion=${ledgerEntry.traceModelVersion} merchant=${ledgerEntry.merchant}",
    )
}
