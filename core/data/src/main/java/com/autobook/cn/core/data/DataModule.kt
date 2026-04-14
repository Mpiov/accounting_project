package com.autobook.cn.core.data

import android.content.Context
import com.autobook.cn.core.data.impl.AccessibilityCaptureCoordinator
import com.autobook.cn.core.data.impl.FakeImageImportLocalOcrEngine
import com.autobook.cn.core.data.impl.FakeImageImportLocalReasoner
import com.autobook.cn.core.data.impl.LedgerRepositoryImpl
import com.autobook.cn.core.data.impl.LlamaCppOnDeviceLlmEngine
import com.autobook.cn.core.data.impl.LocalModelRepositoryImpl
import com.autobook.cn.core.data.impl.MockImageImportAnalyzer
import com.autobook.cn.core.data.impl.MlKitOcrEngine
import com.autobook.cn.core.data.impl.NotificationIngestorImpl
import com.autobook.cn.core.data.impl.OnDeviceLocalLlmAnalyzer
import com.autobook.cn.core.data.impl.PendingRepositoryImpl
import com.autobook.cn.core.data.impl.SettingsRepositoryImpl
import com.autobook.cn.core.data.parser.LocalTransactionExtractor
import com.autobook.cn.core.data.pipeline.ModelRouterImpl
import com.autobook.cn.core.llm.LlamaCppRuntime
import com.autobook.cn.core.llm.LocalTextGenerationRuntime
import com.autobook.cn.core.network.CloudAnalysisApi
import com.autobook.cn.core.network.DirectGeminiImageImportApi
import com.autobook.cn.core.network.FakeCloudAnalysisApi
import com.autobook.cn.core.network.GeminiImageImportApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    @Binds
    abstract fun bindImageImportAnalyzer(impl: MockImageImportAnalyzer): ImageImportAnalyzer

    @Binds
    abstract fun bindImageImportLocalOcrEngine(impl: FakeImageImportLocalOcrEngine): ImageImportLocalOcrEngine

    @Binds
    abstract fun bindImageImportLocalReasoner(impl: FakeImageImportLocalReasoner): ImageImportLocalReasoner

    /**
     * 绑定本地 LLM 分析器。
     *
     * 用途说明：
     * - 当前版本默认先经过 on-device 适配层，再在适配层内部决定是否回退到启发式 baseline。
     * - 后续如果接入真正的端侧模型，只需要替换端侧引擎的实现绑定即可。
     */
    @Binds
    abstract fun bindLocalLlmAnalyzer(impl: OnDeviceLocalLlmAnalyzer): LocalLlmAnalyzer

    /**
     * 绑定端侧 LLM 引擎。
     *
     * 用途说明：
     * - 当前先使用占位引擎联调标准化输入输出协议。
     * - 后续可直接替换为真实 ONNX/GGUF 推理引擎。
     */
    @Binds
    abstract fun bindOnDeviceLlmEngine(impl: LlamaCppOnDeviceLlmEngine): OnDeviceLlmEngine
}

/** 为暂时不需要接口绑定的具体类型提供实例。 */
@Module
@InstallIn(SingletonComponent::class)
object DataProvisionModule {
    /**
     * 提供真本地文本生成运行时。
     *
     * 参数说明：
     * - context：应用上下文，用于定位 native so 所在目录。
     */
    @Provides
    @Singleton
    fun provideLocalTextGenerationRuntime(
        @ApplicationContext context: Context,
    ): LocalTextGenerationRuntime = LlamaCppRuntime(context)

    @Provides
    @Singleton
    fun provideCloudAnalysisApi(): CloudAnalysisApi = FakeCloudAnalysisApi()

    @Provides
    @Singleton
    fun provideGeminiImageImportApi(
        @ApplicationContext context: Context,
    ): GeminiImageImportApi = DirectGeminiImageImportApi(context)
}
