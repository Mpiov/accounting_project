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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PendingViewModel
    @Inject
    constructor(
        private val pendingRepository: PendingRepository,
        private val ledgerRepository: LedgerRepository,
    ) : ViewModel() {
        val uiState: StateFlow<PendingUiState> =
            pendingRepository.observeEntries()
                .map { entries ->
                    PendingUiState(
                        items =
                            entries.map { entry ->
                                PendingItemUiModel(
                                    id = entry.id,
                                    merchant = entry.merchant,
                                    amount = CurrencyFormatter.format(entry.amount),
                                    subtitle = "${entry.channel} · ${TimeFormatter.format(entry.timestamp)}",
                                    reason = entry.reason,
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
                val item = pendingRepository.confirm(id) ?: return@launch
                ledgerRepository.add(
                    LedgerEntry(
                        amount = item.amount,
                        direction = item.direction,
                        merchant = item.merchant,
                        channel = item.channel,
                        timestamp = item.timestamp,
                        category = "待分类",
                        traceSource = item.rawSource,
                        tracePath = item.parsePath,
                        traceModelVersion = item.modelVersion,
                    ),
                )
            }
        }

        fun ignorePending(id: Long) {
            viewModelScope.launch {
                pendingRepository.ignore(id)
            }
        }
    }
