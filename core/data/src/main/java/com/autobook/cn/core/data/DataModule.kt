package com.autobook.cn.core.data

import com.autobook.cn.core.data.impl.AccessibilityCaptureCoordinator
import com.autobook.cn.core.data.impl.LedgerRepositoryImpl
import com.autobook.cn.core.data.impl.LocalModelRepositoryImpl
import com.autobook.cn.core.data.impl.MlKitOcrEngine
import com.autobook.cn.core.data.impl.NotificationIngestorImpl
import com.autobook.cn.core.data.impl.OnDeviceLocalLlmAnalyzer
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

/** 为数据层抽象提供 Hilt 绑定。 */
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
    abstract fun bindLocalModelRepository(impl: LocalModelRepositoryImpl): LocalModelRepository

    @Binds
    abstract fun bindNotificationIngestor(impl: NotificationIngestorImpl): NotificationIngestor

    @Binds
    abstract fun bindCaptureCoordinator(impl: AccessibilityCaptureCoordinator): CaptureCoordinator

    @Binds
    abstract fun bindTransactionExtractor(impl: LocalTransactionExtractor): TransactionExtractor

    @Binds
    abstract fun bindModelRouter(impl: ModelRouterImpl): ModelRouter

    @Binds
    abstract fun bindOcrEngine(impl: MlKitOcrEngine): OcrEngine

    /**
     * 绑定本地 LLM 分析器。
     *
     * 用途说明：
     * - 当前版本默认先经过 on-device 适配层，再在适配层内部决定是否回退到启发式 baseline。
     * - 后续如果接入真正的端侧模型，只需要替换端侧引擎的实现绑定即可。
     */
    @Binds
    abstract fun bindLocalLlmAnalyzer(impl: OnDeviceLocalLlmAnalyzer): LocalLlmAnalyzer

}

/** 为暂时不需要接口绑定的具体类型提供实例。 */
@Module
@InstallIn(SingletonComponent::class)
object DataProvisionModule {
    @Provides
    @Singleton
    fun provideCloudAnalysisApi(): CloudAnalysisApi = FakeCloudAnalysisApi()
}
