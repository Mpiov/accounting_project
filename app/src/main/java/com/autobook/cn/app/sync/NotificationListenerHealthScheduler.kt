package com.autobook.cn.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.autobook.cn.service.notification.NotificationListenerRebindHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用途说明：
 * - 统一调度通知监听健康检查，专门处理“通知使用权仍开启，但监听服务不在 Live listeners 中”的线上问题。
 * - 该调度不依赖网络和会员状态，只负责请求系统重新绑定 NotificationListenerService。
 *
 * 参数说明：
 * - context：应用级上下文，用于获取 WorkManager 实例并发送重绑请求。
 */
@Singleton
class NotificationListenerHealthScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    /**
     * 用途说明：
     * - 注册最小周期的通知监听健康检查，App 启动时可反复调用。
     */
    fun ensurePeriodicCheck() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_NOTIFICATION_LISTENER_HEALTH_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            buildPeriodicHealthWork(),
        )
    }

    /**
     * 用途说明：
     * - 立即排队一次通知监听重绑检查，用于 App 前台、开机广播和升级后补偿。
     *
     * 参数说明：
     * - reason：触发来源，会写入本地 trace，便于线上事故复盘。
     */
    fun enqueueImmediateCheck(reason: String) {
        workManager.enqueueUniqueWork(
            IMMEDIATE_NOTIFICATION_LISTENER_HEALTH_WORK,
            ExistingWorkPolicy.REPLACE,
            buildImmediateHealthWork(reason),
        )
    }
}

/**
 * 用途说明：
 * - WorkManager 后台任务，执行一次通知监听重绑请求。
 *
 * 参数说明：
 * - appContext：Worker 运行所需的应用上下文。
 * - workerParams：WorkManager 传入的任务参数。
 */
class NotificationListenerHealthWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    /**
     * 用途说明：
     * - 执行一次通知监听健康检查；即使当前没有通知权限，也直接成功结束，避免无意义重试。
     */
    override suspend fun doWork(): Result {
        val reason = inputData.getString(KEY_REBIND_REASON) ?: "periodic"
        NotificationListenerRebindHelper.requestRebindIfPermitted(
            context = applicationContext,
            reason = "health_worker_$reason",
            force = true,
        )
        return Result.success()
    }
}

/**
 * 用途说明：
 * - 接收开机完成和应用升级完成广播，尽快恢复通知监听绑定。
 */
class NotificationListenerRebindReceiver : BroadcastReceiver() {
    /**
     * 用途说明：
     * - 系统广播到达时，先直接请求一次重绑，再排队一次延迟检查，覆盖部分厂商系统启动早期服务尚未就绪的情况。
     *
     * 参数说明：
     * - context：广播上下文。
     * - intent：系统广播 Intent，当前主要处理 BOOT_COMPLETED 和 MY_PACKAGE_REPLACED。
     */
    override fun onReceive(context: Context, intent: Intent) {
        val actionLabel = intent.action.orEmpty().substringAfterLast(".").ifBlank { "unknown" }
        NotificationListenerRebindHelper.requestRebindIfPermitted(
            context = context,
            reason = "broadcast_$actionLabel",
            force = true,
        )
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            BROADCAST_DELAYED_NOTIFICATION_LISTENER_HEALTH_WORK,
            ExistingWorkPolicy.REPLACE,
            buildImmediateHealthWork(
                reason = "broadcast_delayed_$actionLabel",
                initialDelaySeconds = BROADCAST_DELAYED_CHECK_SECONDS,
            ),
        )
    }
}

/**
 * 用途说明：
 * - 创建周期健康检查任务。
 */
private fun buildPeriodicHealthWork(): PeriodicWorkRequest {
    return PeriodicWorkRequestBuilder<NotificationListenerHealthWorker>(
        NOTIFICATION_LISTENER_HEALTH_INTERVAL_MINUTES,
        TimeUnit.MINUTES,
    )
        .setInputData(workDataOf(KEY_REBIND_REASON to "periodic"))
        .addTag(NOTIFICATION_LISTENER_HEALTH_WORK_TAG)
        .build()
}

/**
 * 用途说明：
 * - 创建一次性健康检查任务。
 *
 * 参数说明：
 * - reason：触发来源。
 * - initialDelaySeconds：延迟执行秒数，用于广播后的二次补偿。
 */
private fun buildImmediateHealthWork(
    reason: String,
    initialDelaySeconds: Long = 0L,
): OneTimeWorkRequest {
    return OneTimeWorkRequestBuilder<NotificationListenerHealthWorker>()
        .setInputData(workDataOf(KEY_REBIND_REASON to reason))
        .setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
        .addTag(NOTIFICATION_LISTENER_HEALTH_WORK_TAG)
        .build()
}

private const val KEY_REBIND_REASON = "rebind_reason"
private const val NOTIFICATION_LISTENER_HEALTH_WORK_TAG = "notification_listener_health"

private const val PERIODIC_NOTIFICATION_LISTENER_HEALTH_WORK = "notification_listener_health_periodic"
private const val IMMEDIATE_NOTIFICATION_LISTENER_HEALTH_WORK = "notification_listener_health_immediate"
private const val BROADCAST_DELAYED_NOTIFICATION_LISTENER_HEALTH_WORK = "notification_listener_health_broadcast_delayed"

/** WorkManager 周期任务最短只能做到 15 分钟，这里使用系统允许的最短间隔尽快自愈。 */
private const val NOTIFICATION_LISTENER_HEALTH_INTERVAL_MINUTES = 15L

/** 开机或升级后延迟再查一次，避开系统刚启动时通知服务尚未完全 ready 的窗口。 */
private const val BROADCAST_DELAYED_CHECK_SECONDS = 20L
