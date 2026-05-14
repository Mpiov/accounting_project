package com.autobook.cn.core.data

import android.content.Context
import com.autobook.cn.core.data.impl.CloudModelStoreImpl
import com.autobook.cn.core.data.impl.AccessibilityCaptureCoordinator
import com.autobook.cn.core.data.impl.AppUpdateRepositoryImpl
import com.autobook.cn.core.data.impl.AuthRepositoryImpl
import com.autobook.cn.core.data.impl.CloudLearningRepositoryImpl
import com.autobook.cn.core.data.impl.LedgerRepositoryImpl
import com.autobook.cn.core.data.impl.MembershipRepositoryImpl
import com.autobook.cn.core.data.impl.ModelTrainingSampleRepositoryImpl
import com.autobook.cn.core.data.impl.MockImageImportAnalyzer
import com.autobook.cn.core.data.impl.NotificationIngestorImpl
import com.autobook.cn.core.data.impl.PaymentRepositoryImpl
import com.autobook.cn.core.data.impl.NotificationTraceRepositoryImpl
import com.autobook.cn.core.data.impl.PendingRepositoryImpl
import com.autobook.cn.core.data.impl.SettingsRepositoryImpl
import com.autobook.cn.core.data.parser.LocalTransactionExtractor
import com.autobook.cn.core.data.pipeline.ModelRouterImpl
import com.autobook.cn.core.network.CloudImageImportApi
import com.autobook.cn.core.network.DirectCloudImageImportApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 用途说明：
 * - 为数据层抽象提供 Hilt 绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds
    abstract fun bindLedgerRepository(impl: LedgerRepositoryImpl): LedgerRepository

    @Binds
    abstract fun bindPendingRepository(impl: PendingRepositoryImpl): PendingRepository

    @Binds
    abstract fun bindNotificationTraceRepository(impl: NotificationTraceRepositoryImpl): NotificationTraceRepository

    @Binds
    abstract fun bindModelTrainingSampleRepository(
        impl: ModelTrainingSampleRepositoryImpl,
    ): ModelTrainingSampleRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindNotificationIngestor(impl: NotificationIngestorImpl): NotificationIngestor

    @Binds
    abstract fun bindCaptureCoordinator(impl: AccessibilityCaptureCoordinator): CaptureCoordinator

    @Binds
    abstract fun bindTransactionExtractor(impl: LocalTransactionExtractor): TransactionExtractor

    @Binds
    abstract fun bindModelRouter(impl: ModelRouterImpl): ModelRouter

    @Binds
    abstract fun bindMembershipRepository(impl: MembershipRepositoryImpl): MembershipRepository

    @Binds
    abstract fun bindPaymentRepository(impl: PaymentRepositoryImpl): PaymentRepository

    @Binds
    abstract fun bindAppUpdateRepository(impl: AppUpdateRepositoryImpl): AppUpdateRepository

    @Binds
    abstract fun bindCloudLearningRepository(impl: CloudLearningRepositoryImpl): CloudLearningRepository

    @Binds
    abstract fun bindCloudModelStore(impl: CloudModelStoreImpl): CloudModelStore

    @Binds
    abstract fun bindImageImportAnalyzer(impl: MockImageImportAnalyzer): ImageImportAnalyzer
}

/**
 * 用途说明：
 * - 提供无需接口绑定的具体实例。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataProvisionModule {
    @Provides
    @Singleton
    fun provideCloudImageImportApi(
        @ApplicationContext context: Context,
    ): CloudImageImportApi = DirectCloudImageImportApi(context)
}
