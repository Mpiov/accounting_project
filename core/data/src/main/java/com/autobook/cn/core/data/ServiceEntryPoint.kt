package com.autobook.cn.core.data

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceDependenciesEntryPoint {
    fun notificationIngestor(): NotificationIngestor
    fun captureCoordinator(): CaptureCoordinator
}
