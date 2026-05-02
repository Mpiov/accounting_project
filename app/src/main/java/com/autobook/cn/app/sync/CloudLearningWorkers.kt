package com.autobook.cn.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.autobook.cn.core.data.CloudLearningRepository
import com.autobook.cn.core.data.MembershipRepository
import com.autobook.cn.core.model.OperationResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 用途说明：
 * - WorkManager Worker 无法直接使用普通构造函数注入时，通过 Hilt EntryPoint 读取单例仓储。
 * - 这样可以避免额外接入 Hilt Worker 工厂，同时保持 Worker 内没有业务状态。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CloudSyncWorkerEntryPoint {
    /** 用途说明：提供服务器权威会员状态仓储。 */
    fun membershipRepository(): MembershipRepository

    /** 用途说明：提供训练样本上传和全局模型拉取仓储。 */
    fun cloudLearningRepository(): CloudLearningRepository
}

/**
 * 用途说明：
 * - 刷新服务器权威会员状态。
 *
 * 参数说明：
 * - appContext：Worker 运行所需的应用上下文。
 * - workerParams：WorkManager 传入的任务参数。
 */
class MembershipRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    /**
     * 用途说明：
     * - 执行一次会员状态刷新，并把临时服务器错误交给 WorkManager 重试。
     */
    override suspend fun doWork(): Result {
        return runCatching {
            val status = cloudSyncEntryPoint().membershipRepository().refresh()
            if (status.source == MEMBERSHIP_SERVER_ERROR_SOURCE) {
                Result.retry()
            } else {
                Result.success()
            }
        }.getOrElse {
            Result.retry()
        }
    }
}

/**
 * 用途说明：
 * - 上传本地累计的会员全局训练样本。
 *
 * 参数说明：
 * - appContext：Worker 运行所需的应用上下文。
 * - workerParams：WorkManager 传入的任务参数。
 */
class TrainingSampleUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    /**
     * 用途说明：
     * - 执行一次训练样本上传；未登录、非会员或关闭云学习属于业务门禁，不视为 Worker 失败。
     */
    override suspend fun doWork(): Result {
        return runCatching {
            cloudSyncEntryPoint()
                .cloudLearningRepository()
                .uploadTrainingSamples()
                .toWorkerResult()
        }.getOrElse {
            Result.retry()
        }
    }
}

/**
 * 用途说明：
 * - 拉取服务器发布给会员客户端的最新全局模型。
 *
 * 参数说明：
 * - appContext：Worker 运行所需的应用上下文。
 * - workerParams：WorkManager 传入的任务参数。
 */
class CloudModelPullWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    /**
     * 用途说明：
     * - 执行一次全局模型拉取；非会员没有模型权限，直接成功结束并继续使用普通规则算法。
     */
    override suspend fun doWork(): Result {
        return runCatching {
            cloudSyncEntryPoint()
                .cloudLearningRepository()
                .pullCloudModel()
                .toWorkerResult()
        }.getOrElse {
            Result.retry()
        }
    }
}

/**
 * 用途说明：
 * - 从 Application 级 Hilt 容器中取出 Worker 需要的仓储入口。
 *
 * 参数说明：
 * - 接收者：当前 Worker 实例。
 */
private fun CoroutineWorker.cloudSyncEntryPoint(): CloudSyncWorkerEntryPoint {
    return EntryPointAccessors.fromApplication(
        applicationContext,
        CloudSyncWorkerEntryPoint::class.java,
    )
}

/**
 * 用途说明：
 * - 把业务操作结果映射为 WorkManager 可理解的执行结果。
 *
 * 参数说明：
 * - 接收者：仓储返回的业务结果。
 */
private fun OperationResult<*>.toWorkerResult(): Result {
    return when (this) {
        is OperationResult.Success -> Result.success()
        is OperationResult.RetryableFailure -> Result.retry()
        is OperationResult.TerminalFailure -> Result.success()
    }
}

private const val MEMBERSHIP_SERVER_ERROR_SOURCE = "server-error"
