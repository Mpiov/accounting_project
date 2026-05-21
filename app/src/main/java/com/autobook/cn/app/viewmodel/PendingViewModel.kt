package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.common.CurrencyFormatter
import com.autobook.cn.core.common.TimeFormatter
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.NotificationTraceRepository
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.PendingEntry
import com.autobook.cn.feature.pending.model.PendingItemUiModel
import com.autobook.cn.feature.pending.model.PendingUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 待确认页 ViewModel，负责把待确认项映射成可展示状态。 */
@HiltViewModel
class PendingViewModel @Inject constructor(
    private val pendingRepository: PendingRepository,
    private val ledgerRepository: LedgerRepository,
    private val notificationTraceRepository: NotificationTraceRepository,
) : ViewModel() {
    /**
     * 把待确认仓库中的领域模型转换成 UI 模型。
     *
     * 用途说明：
     * - 首屏只等待待确认表自身查询，不能被通知历史原文全量映射拖住。
     * - 待确认项先展示出来，再按当前列表关联的通知 ID 补充原文。
     */
    val uiState: StateFlow<PendingUiState> = pendingRepository.observeEntries()
        .flatMapLatest { pendingEntries ->
            val notificationIds = pendingEntries.mapNotNull(PendingEntry::traceNotificationId).toSet()
            notificationTraceRepository.observeRawTextByIds(notificationIds)
                .mapLatest { notificationTextById ->
                    pendingEntries.toPendingUiState(
                        notificationTextById = notificationTextById,
                        isLoading = false,
                    )
                }
                .onStart {
                    // 原文查询回来前先展示待确认项，避免用户看到“当前无需处理”的假空状态。
                    emit(
                        pendingEntries.toPendingUiState(
                            notificationTextById = emptyMap(),
                            isLoading = false,
                        ),
                    )
                }
        }
        .onStart { emit(PendingUiState(isLoading = true)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PendingUiState(isLoading = true),
        )

    fun confirmPending(id: Long) {
        viewModelScope.launch {
            // 未识别出方向的待确认项不能直接确认，否则会落出一条方向 UNKNOWN 的账本记录，
            // 看起来像“已经入账”，但首页预算、收入和支出汇总都不会把它计进去。
            val current = pendingRepository.getById(id) ?: return@launch
            if (current.direction == MoneyDirection.UNKNOWN) {
                return@launch
            }

            // 确认后立即转成正式账本记录，保留原始追踪字段。
            val item = pendingRepository.confirm(id) ?: return@launch
            ledgerRepository.add(
                LedgerEntry(
                    sortId = item.sortId,
                    amount = item.amount,
                    direction = item.direction,
                    channel = item.channel,
                    timestamp = item.timestamp,
                    category = item.categoryHint,
                    note = item.note,
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

/**
 * 将待确认列表和通知原文映射成页面状态。
 *
 * 用途说明：
 * - 待确认列表和原文是两阶段加载：列表先出，原文后补。
 * - 这个函数统一做后台线程格式化，避免主线程承担金额和时间文本处理。
 *
 * 参数说明：
 * - notificationTextById：当前待确认项关联通知的原文映射。
 * - isLoading：是否仍处于首次加载。
 */
private suspend fun List<PendingEntry>.toPendingUiState(
    notificationTextById: Map<Long, String>,
    isLoading: Boolean,
): PendingUiState {
    return withContext(Dispatchers.Default) {
        PendingUiState(
            items = map { pending ->
                pending.toPendingItemUiModel(
                    rawNotificationText = pending.traceNotificationId?.let(notificationTextById::get),
                )
            },
            isLoading = isLoading,
        )
    }
}

/**
 * 将待确认领域对象映射成界面模型。
 *
 * 用途说明：
 * - 在这里统一决定某条待确认是否允许直接确认入账。
 * - 当方向仍未知时，主按钮会转成“先补方向”，把用户引到编辑页，而不是写出无效统计记录。
 *
 * 参数说明：
 * - rawNotificationText：按 `traceNotificationId` 关联查询到的通知标题/正文；为空表示当前没有可展示原文。
 */
internal fun PendingEntry.toPendingItemUiModel(
    rawNotificationText: String?,
): PendingItemUiModel {
    val canDirectConfirm = direction != MoneyDirection.UNKNOWN
    return PendingItemUiModel(
        id = id,
        title = buildPendingTitle(),
        amount = formatPendingSignedAmount(),
        isIncome = direction == MoneyDirection.INCOME,
        isDirectionKnown = canDirectConfirm,
        subtitle = "${channel} · ${TimeFormatter.format(timestamp)}",
        reason = reason,
        rawNotificationText = rawNotificationText,
        categoryHint = categoryHint,
        canDirectConfirm = canDirectConfirm,
        primaryActionLabel = if (canDirectConfirm) "确认入账" else "先补方向",
        directionHint = if (canDirectConfirm) {
            null
        } else {
            "当前尚未识别出收支方向，建议先编辑后入账，否则不会计入预算和收支汇总。"
        },
    )
}

/**
 * 用途说明：
 * - 给待确认金额补上方向符号，避免列表里只看到金额却看不出收入还是支出。
 * - 未识别方向时使用“±”而不是强行写成支出，提醒用户需要先补方向。
 */
private fun PendingEntry.formatPendingSignedAmount(): String {
    val formattedAmount = CurrencyFormatter.format(kotlin.math.abs(amount))
    return when (direction) {
        MoneyDirection.INCOME -> "+$formattedAmount"
        MoneyDirection.EXPENSE -> "-$formattedAmount"
        MoneyDirection.UNKNOWN -> "±$formattedAmount"
    }
}

/**
 * 用途说明：
 * - 生成待确认列表主标题，优先展示备注首行，备注为空时展示建议分类。
 */
private fun PendingEntry.buildPendingTitle(): String {
    return note
        ?.lineSequence()
        ?.map(String::trim)
        ?.firstOrNull(String::isNotBlank)
        ?: categoryHint.ifBlank { "待分类" }
}
