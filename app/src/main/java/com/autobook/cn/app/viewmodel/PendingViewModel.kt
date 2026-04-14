package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.common.CurrencyFormatter
import com.autobook.cn.core.common.TimeFormatter
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.feature.pending.PendingItemUiModel
import com.autobook.cn.feature.pending.PendingUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 待确认页 ViewModel，负责把待确认项映射成可展示状态。 */
@HiltViewModel
class PendingViewModel @Inject constructor(
    private val pendingRepository: PendingRepository,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {
    /** 把待确认仓库中的领域模型转换成 UI 模型。 */
    val uiState: StateFlow<PendingUiState> = pendingRepository.observeEntries()
        .map { entries ->
            PendingUiState(
                items = entries.map { entry ->
                    PendingItemUiModel(
                        id = entry.id,
                        merchant = entry.merchant,
                        amount = CurrencyFormatter.format(entry.amount),
                        subtitle = "${entry.channel} · ${TimeFormatter.format(entry.timestamp)}",
                        reason = entry.reason,
                        categoryHint = entry.categoryHint,
                        orderId = entry.orderId,
                    )
                },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PendingUiState(),
        )

    fun confirmPending(id: Long) {
        viewModelScope.launch {
            // 确认后立即转成正式账本记录，保留原始追踪字段。
            val item = pendingRepository.confirm(id) ?: return@launch
            ledgerRepository.add(
                LedgerEntry(
                    amount = item.amount,
                    direction = item.direction,
                    merchant = item.merchant,
                    channel = item.channel,
                    timestamp = item.timestamp,
                    category = item.categoryHint,
                    orderId = item.orderId,
                    traceSource = item.rawSource,
                    tracePath = item.parsePath,
                    traceModelVersion = item.modelVersion,
                    traceNotificationId = item.traceNotificationId,
                    editedManually = item.editedManually,
                ),
            )
        }
    }

    fun ignorePending(id: Long) {
        viewModelScope.launch {
            pendingRepository.ignore(id)
        }
    }

    fun markFalsePositive(id: Long) {
        viewModelScope.launch {
            pendingRepository.markFalsePositive(id)
        }
    }
}
