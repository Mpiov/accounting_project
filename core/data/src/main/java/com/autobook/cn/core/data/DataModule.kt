package com.autobook.cn.core.data

import com.autobook.cn.core.data.impl.LedgerRepositoryImpl
import com.autobook.cn.core.data.impl.NoOpCaptureCoordinator
import com.autobook.cn.core.data.impl.NotificationIngestorImpl
import com.autobook.cn.core.data.impl.PendingRepositoryImpl
import com.autobook.cn.core.data.impl.SettingsRepositoryImpl
import com.autobook.cn.core.data.parser.LocalTransactionExtractor
import com.autobook.cn.core.data.pipeline.ModelRouterImpl
import com.autobook.cn.core.network.CloudAnalysisApi
import com.autobook.cn.core.network.FakeCloudAnalysisApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds
    abstract fun bindLedgerRepository(impl: LedgerRepositoryImpl): LedgerRepository

    @Binds
    abstract fun bindPendingRepository(impl: PendingRepositoryImpl): PendingRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindNotificationIngestor(impl: NotificationIngestorImpl): NotificationIngestor

    @Binds
    abstract fun bindCaptureCoordinator(impl: NoOpCaptureCoordinator): CaptureCoordinator

    @Binds
    abstract fun bindTransactionExtractor(impl: LocalTransactionExtractor): TransactionExtractor

    @Binds
    abstract fun bindModelRouter(impl: ModelRouterImpl): ModelRouter
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvisionModule {
    @Provides
    @Singleton
    fun provideCloudAnalysisApi(): CloudAnalysisApi = FakeCloudAnalysisApi()
}
