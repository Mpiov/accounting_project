package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.data.PendingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 底部导航待确认徽标 ViewModel。
 *
 * 用途说明：
 * - 只为主壳层提供待确认数量，不加载待确认详情和通知原文。
 * - 这样切到设置页等无关页面时，也不会因为底部徽标触发完整列表映射。
 *
 * 参数说明：
 * - pendingRepository：待确认仓储，用于订阅轻量数量流。
 */
@HiltViewModel
class PendingBadgeViewModel @Inject constructor(
    pendingRepository: PendingRepository,
) : ViewModel() {
    /**
     * 当前待确认数量。
     *
     * 用途说明：
 * - 使用待确认数量缓存驱动徽标展示，避免主壳层常驻完整 `PendingViewModel` 或频繁扫表。
     */
    val pendingCount: StateFlow<Int> = pendingRepository.observeActiveCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )
}
