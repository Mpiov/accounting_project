package com.autobook.cn.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.PendingEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 编辑页的工作模式：新建、编辑账本、处理待确认。 */
enum class EntryEditorMode {
    CREATE,
    EDIT_LEDGER,
    RESOLVE_PENDING,
}

/** 编辑页 UI 状态，覆盖三种模式的公共表单字段。 */
data class EntryEditorUiState(
    val mode: EntryEditorMode = EntryEditorMode.CREATE,
    val title: String = "新建手动记账",
    val amount: String = "",
    val merchant: String = "",
    val category: String = "待分类",
    val note: String = "",
    val orderId: String = "",
    val direction: MoneyDirection = MoneyDirection.EXPENSE,
    val canDelete: Boolean = false,
    val errorMessage: String? = null,
    val finished: Boolean = false,
    val templates: List<EntryTemplateUiModel> = ENTRY_TEMPLATE_MODELS,
)

/** 编辑页快捷模板的展示模型。 */
data class EntryTemplateUiModel(
    val id: String,
    val label: String,
)

/** 模板应用到表单前的内部定义。 */
internal data class EntryTemplate(
    val id: String,
    val label: String,
    val direction: MoneyDirection,
    val category: String,
    val defaultMerchant: String = "",
    val defaultNote: String = "",
)

/** 手动记账与编辑流程的 ViewModel。 */
@HiltViewModel
class EntryEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ledgerRepository: LedgerRepository,
    private val pendingRepository: PendingRepository,
) : ViewModel() {
    private val ledgerId: Long? = savedStateHandle.get<String>("ledgerId")?.toLongOrNull()
    private val pendingId: Long? = savedStateHandle.get<String>("pendingId")?.toLongOrNull()
    /** 快速动作面板可传入默认方向，减少用户进入表单后的二次切换。 */
    private val presetDirection: MoneyDirection? = savedStateHandle.get<String>("direction")
        ?.takeIf { it.isNotBlank() }
        ?.let { rawDirection -> MoneyDirection.entries.firstOrNull { it.name == rawDirection } }
    /** 快速动作面板可直接携带模板标识，进入页面时先预填常用字段。 */
    private val presetTemplateId: String? = savedStateHandle.get<String>("templateId")?.takeIf { it.isNotBlank() }
    private var editingLedger: LedgerEntry? = null
    private var resolvingPending: PendingEntry? = null

    /** 根据路由参数决定当前是新建、编辑还是处理待确认。 */
    private val _uiState = MutableStateFlow(
        EntryEditorUiState(
            mode = when {
                pendingId != null -> EntryEditorMode.RESOLVE_PENDING
                ledgerId != null -> EntryEditorMode.EDIT_LEDGER
                else -> EntryEditorMode.CREATE
            },
            title = when {
                pendingId != null -> "编辑后入账"
                ledgerId != null -> "编辑记账"
                presetDirection == MoneyDirection.INCOME -> "快速记收入"
                presetDirection == MoneyDirection.EXPENSE -> "快速记支出"
                else -> "新建手动记账"
            },
            canDelete = ledgerId != null,
            direction = presetDirection ?: MoneyDirection.EXPENSE,
        ),
    )
    val uiState: StateFlow<EntryEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadData()
        }
    }

    fun updateAmount(value: String) = updateState { copy(amount = value, errorMessage = null) }
    fun updateMerchant(value: String) = updateState { copy(merchant = value, errorMessage = null) }
    fun updateCategory(value: String) = updateState { copy(category = value, errorMessage = null) }
    fun updateNote(value: String) = updateState { copy(note = value, errorMessage = null) }
    fun updateOrderId(value: String) = updateState { copy(orderId = value, errorMessage = null) }
    fun updateDirection(value: MoneyDirection) = updateState { copy(direction = value, errorMessage = null) }
    fun applyTemplate(templateId: String) = updateState { applyEntryTemplate(templateId) }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            val amount = state.amount.toDoubleOrNull()
            if (amount == null || amount <= 0.0) {
                updateState { copy(errorMessage = "请输入有效金额") }
                return@launch
            }
            if (state.merchant.isBlank()) {
                updateState { copy(errorMessage = "请输入商户或备注对象") }
                return@launch
            }
            if (state.direction == MoneyDirection.UNKNOWN) {
                // 自动解析无法确定方向时，要求用户在编辑页里显式确认收入或支出。
                updateState { copy(errorMessage = "请选择收支方向") }
                return@launch
            }

            when (state.mode) {
                EntryEditorMode.CREATE -> {
                    ledgerRepository.add(
                        LedgerEntry(
                            amount = amount,
                            direction = state.direction,
                            merchant = state.merchant.trim(),
                            channel = "手动记账",
                            timestamp = System.currentTimeMillis(),
                            category = state.category.trim().ifBlank { "待分类" },
                            orderId = state.orderId.trim().ifBlank { null },
                            note = state.note.trim().ifBlank { null },
                            traceSource = "manual",
                            tracePath = "manual-entry",
                            traceModelVersion = "manual-v1",
                            editedManually = true,
                        ),
                    )
                }

                EntryEditorMode.EDIT_LEDGER -> {
                    val current = editingLedger ?: return@launch
                    ledgerRepository.update(
                        current.copy(
                            amount = amount,
                            direction = state.direction,
                            merchant = state.merchant.trim(),
                            category = state.category.trim().ifBlank { "待分类" },
                            orderId = state.orderId.trim().ifBlank { null },
                            note = state.note.trim().ifBlank { null },
                            editedManually = true,
                        ),
                    )
                }

                EntryEditorMode.RESOLVE_PENDING -> {
                    // 待确认项保存后写入账本，并从待确认队列中移除。
                    val pending = resolvingPending ?: return@launch
                    ledgerRepository.add(
                        LedgerEntry(
                            amount = amount,
                            direction = state.direction,
                            merchant = state.merchant.trim(),
                            channel = pending.channel,
                            timestamp = pending.timestamp,
                            category = state.category.trim().ifBlank { pending.categoryHint },
                            orderId = state.orderId.trim().ifBlank { pending.orderId },
                            note = state.note.trim().ifBlank { null },
                            traceSource = pending.rawSource,
                            tracePath = pending.parsePath,
                            traceModelVersion = pending.modelVersion,
                            traceNotificationId = pending.traceNotificationId,
                            editedManually = true,
                        ),
                    )
                    pendingRepository.ignore(pending.id)
                }
            }

            updateState { copy(finished = true, errorMessage = null) }
        }
    }

    fun delete() {
        val id = ledgerId ?: return
        viewModelScope.launch {
            ledgerRepository.delete(id)
            updateState { copy(finished = true) }
        }
    }

    fun consumeFinished() {
        updateState { copy(finished = false) }
    }

    private suspend fun loadData() {
        // 进入页面时按模式回填已有数据，保证表单可以编辑原始值。
        when {
            ledgerId != null -> {
                editingLedger = ledgerRepository.getById(ledgerId)
                editingLedger?.let { entry ->
                    _uiState.value = _uiState.value.copy(
                        amount = entry.amount.toString(),
                        merchant = entry.merchant,
                        category = entry.category,
                        note = entry.note.orEmpty(),
                        orderId = entry.orderId.orEmpty(),
                        direction = entry.direction,
                    )
                }
            }

            pendingId != null -> {
                resolvingPending = pendingRepository.getById(pendingId)
                resolvingPending?.let { entry ->
                    _uiState.value = _uiState.value.copy(
                        amount = entry.amount.toString(),
                        merchant = entry.merchant,
                        category = entry.categoryHint,
                        note = "",
                        orderId = entry.orderId.orEmpty(),
                        direction = entry.direction,
                    )
                }
            }
            else -> {
                // 新建模式下优先应用快捷动作传入的默认值，减少重复录入。
                presetTemplateId?.let { templateId ->
                    _uiState.value = _uiState.value.applyEntryTemplate(templateId)
                }
            }
        }
    }

    private fun updateState(transform: EntryEditorUiState.() -> EntryEditorUiState) {
        _uiState.value = _uiState.value.transform()
    }
}

/** 快捷模板只预填常用字段，避免覆盖用户已确认的金额和订单号。 */
internal fun EntryEditorUiState.applyEntryTemplate(templateId: String): EntryEditorUiState {
    val template = ENTRY_TEMPLATES.firstOrNull { it.id == templateId } ?: return this
    return copy(
        direction = template.direction,
        category = template.category,
        merchant = merchant.ifBlank { template.defaultMerchant },
        note = note.ifBlank { template.defaultNote },
        errorMessage = null,
    )
}

private val ENTRY_TEMPLATES = listOf(
    EntryTemplate(
        id = "breakfast",
        label = "早餐",
        direction = MoneyDirection.EXPENSE,
        category = "餐饮",
        defaultMerchant = "早餐",
        defaultNote = "晨间用餐",
    ),
    EntryTemplate(
        id = "lunch",
        label = "午餐",
        direction = MoneyDirection.EXPENSE,
        category = "餐饮",
        defaultMerchant = "午餐",
        defaultNote = "工作日用餐",
    ),
    EntryTemplate(
        id = "taxi",
        label = "打车",
        direction = MoneyDirection.EXPENSE,
        category = "交通",
        defaultMerchant = "滴滴出行",
        defaultNote = "城市出行",
    ),
    EntryTemplate(
        id = "shopping",
        label = "购物",
        direction = MoneyDirection.EXPENSE,
        category = "购物",
        defaultMerchant = "日常购物",
        defaultNote = "生活消费",
    ),
    EntryTemplate(
        id = "salary",
        label = "工资",
        direction = MoneyDirection.INCOME,
        category = "工资",
        defaultMerchant = "工资入账",
        defaultNote = "固定收入",
    ),
    EntryTemplate(
        id = "transfer",
        label = "转账",
        direction = MoneyDirection.EXPENSE,
        category = "转账",
        defaultMerchant = "银行卡转账",
        defaultNote = "账户间转移",
    ),
)

private val ENTRY_TEMPLATE_MODELS = ENTRY_TEMPLATES.map { template ->
    EntryTemplateUiModel(
        id = template.id,
        label = template.label,
    )
}
