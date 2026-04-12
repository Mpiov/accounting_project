package com.autobook.cn.core.network

import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.TransactionCandidate

data class CloudAnalysisRequest(
    val packageName: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
)

interface CloudAnalysisApi {
    suspend fun analyze(request: CloudAnalysisRequest): TransactionCandidate?
}

class FakeCloudAnalysisApi : CloudAnalysisApi {
    override suspend fun analyze(request: CloudAnalysisRequest): TransactionCandidate? = null
}

fun NotificationEvent.toCloudRequest(): CloudAnalysisRequest =
    CloudAnalysisRequest(
        packageName = packageName,
        title = title,
        text = text,
        timestamp = postedAt,
    )
