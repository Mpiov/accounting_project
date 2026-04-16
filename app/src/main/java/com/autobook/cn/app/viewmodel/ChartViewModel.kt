package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.model.MoneyDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * 图表页的原始数据状态。
 *
 * 参数说明：
 * - entries：供图表页本地计算趋势与分类的源数据。
 */
data class ChartUiState(
    val entries: List<ChartEntryUiModel> = emptyList(),
)

/**
 * 图表页单条记录源模型。
 *
 * 参数说明：
 * - id：记录标识。
 * - date：发生日期。
 * - amount：金额。
 * - direction：收支方向。
 * - category：分类。
 */
data class ChartEntryUiModel(
    val id: Long,
    val date: LocalDate,
    val amount: Double,
    val direction: MoneyDirection,
    val category: String,
)

/**
 * 图表页 ViewModel。
 *
 * 用途说明：
 * - 只负责把账本记录转成图表页可计算的轻量源数据。
 * - 周/月/年和收入/支出切换由图表页本地状态控制，减少 ViewModel 状态膨胀。
 */
@HiltViewModel
class ChartViewModel @Inject constructor(
    ledgerRepository: LedgerRepository,
) : ViewModel() {
    val uiState: StateFlow<ChartUiState> = ledgerRepository.observeEntries()
        .mapLatest { entries ->
            // 图表页切入时会把全量账本映射成图表源数据，这里放到后台线程，减少底部切换的首帧停顿。
            withContext(Dispatchers.Default) {
                ChartUiState(
                    entries = entries.map { entry ->
                        ChartEntryUiModel(
                            id = entry.id,
                            date = Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).toLocalDate(),
                            amount = entry.amount,
                            direction = entry.direction,
                            category = entry.category,
                        )
                    },
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChartUiState(),
        )
}
