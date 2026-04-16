package com.autobook.cn.core.data

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 向无法走构造注入的 Android Service 暴露数据层依赖。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceDependenciesEntryPoint {
    fun notificationIngestor(): NotificationIngestor

    fun captureCoordinator(): CaptureCoordinator
    fun imageImportAnalyzer(): ImageImportAnalyzer
    fun settingsRepository(): SettingsRepository
    fun pendingRepository(): PendingRepository
    fun ledgerRepository(): LedgerRepository
}
