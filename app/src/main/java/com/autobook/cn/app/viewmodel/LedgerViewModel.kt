package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.common.CurrencyFormatter
import com.autobook.cn.core.common.TimeFormatter
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.feature.ledger.LedgerItemUiModel
import com.autobook.cn.feature.ledger.LedgerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LedgerViewModel @Inject constructor(
    ledgerRepository: LedgerRepository,
    pendingRepository: PendingRepository,
) : ViewModel() {
    val uiState: StateFlow<LedgerUiState> = combine(
        ledgerRepository.observeEntries(),
        pendingRepository.observeEntries(),
    ) { entries, pending ->
        val income = entries.filter { it.direction == MoneyDirection.INCOME }.sumOf { it.amount }
        val expense = entries.filter { it.direction == MoneyDirection.EXPENSE }.sumOf { it.amount }
        LedgerUiState(
            incomeToday = CurrencyFormatter.format(income),
            expenseToday = CurrencyFormatter.format(expense),
            pendingCount = pending.size,
            entries = entries.map { entry ->
                LedgerItemUiModel(
                    id = entry.id,
                    merchant = entry.merchant,
                    amount = CurrencyFormatter.format(entry.amount),
                    subtitle = "${entry.channel} · ${TimeFormatter.format(entry.timestamp)}",
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState(),
    )
}
