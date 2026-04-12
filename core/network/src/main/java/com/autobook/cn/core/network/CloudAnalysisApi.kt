package com.autobook.cn.core.network

import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.TransactionCandidate

/** 发送到未来云端解析服务的最小请求载荷。 */
data class CloudAnalysisRequest(
    val packageName: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
)

/** 云端补充解析能力的网络边界接口。 */
interface CloudAnalysisApi {
    suspend fun analyze(request: CloudAnalysisRequest): TransactionCandidate?
}

/** 云端能力尚未接入前的占位实现。 */
class FakeCloudAnalysisApi : CloudAnalysisApi {
    override suspend fun analyze(request: CloudAnalysisRequest): TransactionCandidate? = null
}

/** 将本地通知模型转换为云端接口需要的请求格式。 */
fun NotificationEvent.toCloudRequest(): CloudAnalysisRequest = CloudAnalysisRequest(
    packageName = packageName,
    title = title,
    text = text,
    timestamp = postedAt,
)
