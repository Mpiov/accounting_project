package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.common.CurrencyFormatter
import com.autobook.cn.core.common.TimeFormatter
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.feature.ledger.LedgerItemUiModel
import com.autobook.cn.feature.ledger.LedgerRangeFilter
import com.autobook.cn.feature.ledger.LedgerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LedgerViewModel @Inject constructor(
    ledgerRepository: LedgerRepository,
    pendingRepository: PendingRepository,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val selectedRange = MutableStateFlow(LedgerRangeFilter.TODAY)

    val uiState: StateFlow<LedgerUiState> = combine(
        ledgerRepository.observeEntries(),
        pendingRepository.observeEntries(),
        searchQuery,
        selectedRange,
    ) { entries, pending, query, range ->
        val filtered = entries
            .filterByRange(range)
            .filterByQuery(query)
        val income = filtered.filter { it.direction == MoneyDirection.INCOME }.sumOf { it.amount }
        val expense = filtered.filter { it.direction == MoneyDirection.EXPENSE }.sumOf { it.amount }
        LedgerUiState(
            incomeSummary = CurrencyFormatter.format(income),
            expenseSummary = CurrencyFormatter.format(expense),
            pendingCount = pending.size,
            searchQuery = query,
            selectedRange = range,
            entries = filtered.map { entry ->
                LedgerItemUiModel(
                    id = entry.id,
                    merchant = entry.merchant,
                    amount = CurrencyFormatter.format(entry.amount),
                    subtitle = "${entry.channel} · ${TimeFormatter.format(entry.timestamp)}",
                    category = entry.category,
                    note = entry.note,
                    isManual = entry.editedManually || entry.traceSource == "manual",
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState(),
    )

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setRange(range: LedgerRangeFilter) {
        selectedRange.value = range
    }
}

private fun List<LedgerEntry>.filterByQuery(query: String): List<LedgerEntry> {
    if (query.isBlank()) return this
    val normalized = query.trim()
    return filter { entry ->
        entry.merchant.contains(normalized, ignoreCase = true) ||
            entry.category.contains(normalized, ignoreCase = true) ||
            entry.note?.contains(normalized, ignoreCase = true) == true
    }
}

private fun List<LedgerEntry>.filterByRange(range: LedgerRangeFilter): List<LedgerEntry> {
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance()
    return when (range) {
        LedgerRangeFilter.TODAY -> {
            calendar.timeInMillis = now
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val start = calendar.timeInMillis
            filter { it.timestamp >= start }
        }

        LedgerRangeFilter.MONTH -> {
            calendar.timeInMillis = now
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val start = calendar.timeInMillis
            filter { it.timestamp >= start }
        }

        LedgerRangeFilter.ALL -> this
    }
}
