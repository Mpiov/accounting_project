package com.autobook.cn.core.data.pipeline

import com.autobook.cn.core.data.ModelRouter
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.data.TransactionExtractor
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.TransactionCandidate
import com.autobook.cn.core.network.CloudAnalysisApi
import com.autobook.cn.core.network.toCloudRequest
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ModelRouterImpl
    @Inject
    constructor(
        private val localExtractor: TransactionExtractor,
        private val cloudAnalysisApi: CloudAnalysisApi,
        private val settingsRepository: SettingsRepository,
    ) : ModelRouter {
        override suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate> {
            val local = localExtractor.extract(event)
            if (local is OperationResult.Success) return local

            val settings = settingsRepository.settings.first()
            if (!settings.cloudAnalysisEnabled) {
                return local
            }

            val cloudCandidate =
                cloudAnalysisApi.analyze(event.toCloudRequest())
                    ?: return OperationResult.RetryableFailure("Cloud analysis unavailable")
            return OperationResult.Success(cloudCandidate.copy(parsePath = "cloud-placeholder"))
        }
    }
