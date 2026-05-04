package com.autobook.cn.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用途说明：
 * - 统一管理会员状态、训练样本上传和全局模型拉取的后台同步排队。
 * - 这里只负责 WorkManager 调度，不直接承载业务判断；用户改善计划开关和模型权限仍由 data 层仓储校验。
 *
 * 参数说明：
 * - context：应用级上下文，用于获取进程内唯一的 WorkManager 实例。
 */
@Singleton
class CloudLearningSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    /**
     * 用途说明：
     * - 确保云端学习相关的周期任务已经注册。
     * - 该方法可在应用启动时反复调用，`KEEP` 会避免重复创建同名任务。
     */
    fun ensurePeriodicSync() {
        // 旧版本曾在用户处理待确认后创建一次性上传任务；现在统一只保留周期上传，启动时清理遗留队列。
        workManager.cancelUniqueWork(LEGACY_DELAYED_TRAINING_SAMPLE_UPLOAD_WORK)
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_MEMBERSHIP_REFRESH_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            buildPeriodicNetworkWork<MembershipRefreshWorker>(
                intervalHours = MEMBERSHIP_REFRESH_INTERVAL_HOURS,
                initialDelayMinutes = MEMBERSHIP_REFRESH_INITIAL_DELAY_MINUTES,
            ),
        )
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_TRAINING_SAMPLE_UPLOAD_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            buildPeriodicNetworkWork<TrainingSampleUploadWorker>(
                intervalHours = TRAINING_SAMPLE_UPLOAD_INTERVAL_HOURS,
                initialDelayMinutes = TRAINING_SAMPLE_UPLOAD_INITIAL_DELAY_MINUTES,
            ),
        )
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_CLOUD_MODEL_PULL_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            buildPeriodicNetworkWork<CloudModelPullWorker>(
                intervalHours = CLOUD_MODEL_PULL_INTERVAL_HOURS,
                initialDelayMinutes = CLOUD_MODEL_PULL_INITIAL_DELAY_MINUTES,
            ),
        )
    }

    /**
     * 用途说明：
     * - 在 App 启动或重新回到前台时补一次会员状态刷新。
     * - 会员状态是服务器权威数据，前台刷新可以避免用户开通或到期后还长时间看到旧状态。
     */
    fun enqueueForegroundMembershipRefresh() {
        workManager.enqueueUniqueWork(
            FOREGROUND_MEMBERSHIP_REFRESH_WORK,
            ExistingWorkPolicy.KEEP,
            buildOneTimeNetworkWork<MembershipRefreshWorker>(),
        )
    }

}

/**
 * 用途说明：
 * - 创建带联网约束和指数退避的周期任务。
 *
 * 参数说明：
 * - intervalHours：任务重复间隔，单位小时。
 * - initialDelayMinutes：首次执行前的延迟，单位分钟。
 */
private inline fun <reified Worker : ListenableWorker> buildPeriodicNetworkWork(
    intervalHours: Long,
    initialDelayMinutes: Long,
): PeriodicWorkRequest {
    return PeriodicWorkRequestBuilder<Worker>(intervalHours, TimeUnit.HOURS)
        .setConstraints(cloudSyncNetworkConstraints())
        .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, CLOUD_SYNC_BACKOFF_MINUTES, TimeUnit.MINUTES)
        .addTag(CLOUD_SYNC_WORK_TAG)
        .build()
}

/**
 * 用途说明：
 * - 创建带联网约束和指数退避的一次性任务。
 *
 * 参数说明：
 * - initialDelayMinutes：首次执行前的延迟，单位分钟；默认为 0 表示尽快执行。
 */
private inline fun <reified Worker : ListenableWorker> buildOneTimeNetworkWork(
    initialDelayMinutes: Long = 0L,
): OneTimeWorkRequest {
    return OneTimeWorkRequestBuilder<Worker>()
        .setConstraints(cloudSyncNetworkConstraints())
        .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, CLOUD_SYNC_BACKOFF_MINUTES, TimeUnit.MINUTES)
        .addTag(CLOUD_SYNC_WORK_TAG)
        .build()
}

/**
 * 用途说明：
 * - 生成所有云端同步任务共用的联网约束。
 */
private fun cloudSyncNetworkConstraints(): Constraints {
    return Constraints.Builder()
        // 云端学习任务都依赖服务器接口，离线时交给 WorkManager 等待网络恢复。
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}

private const val CLOUD_SYNC_WORK_TAG = "cloud_learning_sync"

private const val PERIODIC_MEMBERSHIP_REFRESH_WORK = "cloud_learning_membership_refresh_periodic"
private const val PERIODIC_TRAINING_SAMPLE_UPLOAD_WORK = "cloud_learning_training_sample_upload_periodic"
private const val PERIODIC_CLOUD_MODEL_PULL_WORK = "cloud_learning_model_pull_periodic"
private const val FOREGROUND_MEMBERSHIP_REFRESH_WORK = "cloud_learning_membership_refresh_foreground"
private const val LEGACY_DELAYED_TRAINING_SAMPLE_UPLOAD_WORK = "cloud_learning_training_sample_upload_feedback"

private const val MEMBERSHIP_REFRESH_INTERVAL_HOURS = 12L
private const val TRAINING_SAMPLE_UPLOAD_INTERVAL_HOURS = 1L
private const val CLOUD_MODEL_PULL_INTERVAL_HOURS = 24L

private const val MEMBERSHIP_REFRESH_INITIAL_DELAY_MINUTES = 12L * 60L
private const val TRAINING_SAMPLE_UPLOAD_INITIAL_DELAY_MINUTES = 15L
private const val CLOUD_MODEL_PULL_INITIAL_DELAY_MINUTES = 60L
private const val CLOUD_SYNC_BACKOFF_MINUTES = 30L
