package com.autobook.cn.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.model.MoneyDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 用途说明：
 * - 极速记账页的分类展示模型。
 *
 * 参数说明：
 * - id：分类稳定标识，便于页面选择和状态恢复。
 * - label：分类展示文案。
 */
data class QuickEntryCategoryUiModel(
    val id: String,
    val label: String,
)

/**
 * 用途说明：
 * - 极速记账页的 UI 状态。
 * - 当前阶段先把方向、分类、金额、备注和时间的状态收口，后续 UI 可以直接围绕这份状态扩展。
 *
 * 参数说明：
 * - title：页面标题。
 * - direction：当前收支方向。
 * - categories：当前方向对应的分类列表。
 * - selectedCategoryId：当前选中的分类 id。
 * - amountInput：金额输入文本。
 * - note：备注文本。
 * - merchant：商户文本；首版保留字段，为后续“更多”跳完整编辑页做准备。
 * - entryTimestamp：当前交易时间。
 * - isAmountInputActive：是否已经进入金额输入阶段。
 * - errorMessage：页面错误提示。
 * - finished：是否已经完成保存。
 */
data class QuickEntryUiState(
    val title: String = "极速记账",
    val direction: MoneyDirection = MoneyDirection.EXPENSE,
    val categories: List<QuickEntryCategoryUiModel> = quickEntryCategoriesFor(MoneyDirection.EXPENSE),
    val selectedCategoryId: String? = null,
    val amountInput: String = "",
    val note: String = "",
    val merchant: String = "",
    val entryTimestamp: Long = System.currentTimeMillis(),
    val isAmountInputActive: Boolean = false,
    val errorMessage: String? = null,
    val finished: Boolean = false,
)

/**
 * 用途说明：
 * - 极速记账页分类的内部定义。
 *
 * 参数说明：
 * - id：分类稳定标识。
 * - label：分类显示名称。
 * - direction：该分类所属方向。
 */
private data class QuickEntryCategoryDefinition(
    val id: String,
    val label: String,
    val direction: MoneyDirection,
)

/** 极速记账页的状态管理器，负责方向切换、分类选择、金额输入和快速保存。 */
@HiltViewModel
class QuickEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {
    private val presetDirection = savedStateHandle.readMoneyDirectionArgument("direction")
        ?.takeIf { it == MoneyDirection.EXPENSE || it == MoneyDirection.INCOME }
        ?: MoneyDirection.EXPENSE

    private val _uiState = MutableStateFlow(
        QuickEntryUiState(
            direction = presetDirection,
            categories = quickEntryCategoriesFor(presetDirection),
        ),
    )
    val uiState: StateFlow<QuickEntryUiState> = _uiState.asStateFlow()

    /**
     * 用途说明：
     * - 切换当前收支方向，并同步刷新对应分类列表。
     *
     * 参数说明：
     * - direction：用户切换后的目标方向。
     */
    fun updateDirection(direction: MoneyDirection) {
        val normalizedDirection = direction.takeIf {
            it == MoneyDirection.EXPENSE || it == MoneyDirection.INCOME
        } ?: return
        updateState {
            val nextCategories = quickEntryCategoriesFor(normalizedDirection)
            val keptCategoryId = selectedCategoryId?.takeIf { currentId ->
                nextCategories.any { category -> category.id == currentId }
            }
            copy(
                direction = normalizedDirection,
                categories = nextCategories,
                selectedCategoryId = keptCategoryId,
                isAmountInputActive = keptCategoryId != null && isAmountInputActive,
                errorMessage = null,
            )
        }
    }

    /**
     * 用途说明：
     * - 选择极速记账分类，并立即进入金额输入阶段。
     *
     * 参数说明：
     * - categoryId：用户点中的分类 id。
     */
    fun selectCategory(categoryId: String) {
        updateState {
            val exists = categories.any { category -> category.id == categoryId }
            if (!exists) return@updateState this
            copy(
                selectedCategoryId = categoryId,
                isAmountInputActive = true,
                errorMessage = null,
            )
        }
    }

    /**
     * 用途说明：
     * - 追加一个金额数字，供后续自定义数字键盘直接复用。
     *
     * 参数说明：
     * - digit：待输入的数字字符。
     */
    fun appendDigit(digit: Int) {
        if (digit !in 0..9) return
        updateState {
            copy(
                amountInput = appendDigitToAmountInput(current = amountInput, digit = digit),
                errorMessage = null,
            )
        }
    }

    /** 在金额输入中追加一个小数点。 */
    fun appendDecimalPoint() {
        updateState {
            copy(
                amountInput = appendDecimalPointToAmountInput(amountInput),
                errorMessage = null,
            )
        }
    }

    /** 删除金额输入中的最后一个字符。 */
    fun deleteLastAmountCharacter() {
        updateState {
            copy(
                amountInput = deleteLastAmountInputCharacter(amountInput),
                errorMessage = null,
            )
        }
    }

    /** 清空当前金额输入，方便用户在点错金额后快速重输。 */
    fun clearAmountInput() {
        updateState {
            copy(
                amountInput = "",
                errorMessage = null,
            )
        }
    }

    /**
     * 用途说明：
     * - 更新极速记账备注。
     *
     * 参数说明：
     * - value：最新备注文本。
     */
    fun updateNote(value: String) = updateState { copy(note = value, errorMessage = null) }

    /**
     * 用途说明：
     * - 更新极速记账商户。
     * - 当前阶段先保留字段，后续“更多”跳转到完整编辑页时可以直接透传。
     *
     * 参数说明：
     * - value：最新商户文本。
     */
    fun updateMerchant(value: String) = updateState { copy(merchant = value, errorMessage = null) }

    /**
     * 用途说明：
     * - 更新极速记账时间。
     *
     * 参数说明：
     * - timestamp：用户选择后的交易时间戳。
     */
    fun updateEntryTimestamp(timestamp: Long) = updateState { copy(entryTimestamp = timestamp, errorMessage = null) }

    /** 快速保存当前极速记账草稿。 */
    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            val selectedCategory = state.categories.firstOrNull { category ->
                category.id == state.selectedCategoryId
            }
            if (selectedCategory == null) {
                updateState { copy(errorMessage = "请选择分类") }
                return@launch
            }

            when (
                val result = buildManualLedgerEntry(
                    draft = ManualLedgerEntryDraft(
                        amountInput = state.amountInput,
                        direction = state.direction,
                        merchant = state.merchant,
                        category = selectedCategory.label,
                        note = state.note,
                        entryTimestamp = state.entryTimestamp,
                        channel = "极速记账",
                        traceSource = "manual",
                        tracePath = "quick-entry",
                        traceModelVersion = "quick-entry-v1",
                    ),
                    merchantPolicy = ManualEntryMerchantPolicy.USE_CATEGORY_AS_FALLBACK,
                )
            ) {
                is ManualLedgerEntryBuildFailure -> {
                    updateState { copy(errorMessage = result.message) }
                    return@launch
                }

                is ManualLedgerEntryBuildSuccess -> {
                    ledgerRepository.add(result.entry)
                    updateState {
                        copy(
                            finished = true,
                            errorMessage = null,
                        )
                    }
                }
            }
        }
    }

    /** 消费一次性完成状态，避免页面返回时重复触发。 */
    fun consumeFinished() {
        updateState { copy(finished = false) }
    }

    private fun updateState(transform: QuickEntryUiState.() -> QuickEntryUiState) {
        _uiState.value = _uiState.value.transform()
    }
}

/**
 * 用途说明：
 * - 生成某个方向对应的极速记账分类列表。
 *
 * 参数说明：
 * - direction：当前页面所处的收支方向。
 */
internal fun quickEntryCategoriesFor(direction: MoneyDirection): List<QuickEntryCategoryUiModel> {
    return QUICK_ENTRY_CATEGORIES
        .filter { category -> category.direction == direction }
        .map { category ->
            QuickEntryCategoryUiModel(
                id = category.id,
                label = category.label,
            )
        }
}

/**
 * 用途说明：
 * - 在金额输入框尾部追加数字，同时保持最多两位小数的规则。
 *
 * 参数说明：
 * - current：当前金额文本。
 * - digit：新输入的数字。
 */
internal fun appendDigitToAmountInput(current: String, digit: Int): String {
    val normalizedCurrent = current.ifBlank { "" }
    val decimalIndex = normalizedCurrent.indexOf('.')
    if (decimalIndex >= 0) {
        // 小数点后最多保留两位，超过后直接忽略，避免数字键盘把非法状态写进 ViewModel。
        val decimalLength = normalizedCurrent.length - decimalIndex - 1
        if (decimalLength >= 2) return normalizedCurrent
    }
    return when {
        normalizedCurrent == "0" -> digit.toString()
        else -> normalizedCurrent + digit
    }
}

/**
 * 用途说明：
 * - 在金额输入框中插入小数点，保证同一金额只出现一个小数点。
 *
 * 参数说明：
 * - current：当前金额文本。
 */
internal fun appendDecimalPointToAmountInput(current: String): String = when {
    current.contains('.') -> current
    current.isBlank() -> "0."
    else -> "$current."
}

/**
 * 用途说明：
 * - 删除金额输入中的最后一个字符。
 *
 * 参数说明：
 * - current：当前金额文本。
 */
internal fun deleteLastAmountInputCharacter(current: String): String = current.dropLast(1)

private val QUICK_ENTRY_CATEGORIES = listOf(
    QuickEntryCategoryDefinition(id = "expense_food", label = "餐饮", direction = MoneyDirection.EXPENSE),
    QuickEntryCategoryDefinition(id = "expense_shopping", label = "购物", direction = MoneyDirection.EXPENSE),
    QuickEntryCategoryDefinition(id = "expense_daily", label = "日用", direction = MoneyDirection.EXPENSE),
    QuickEntryCategoryDefinition(id = "expense_transport", label = "交通", direction = MoneyDirection.EXPENSE),
    QuickEntryCategoryDefinition(id = "expense_vegetable", label = "蔬菜", direction = MoneyDirection.EXPENSE),
    QuickEntryCategoryDefinition(id = "expense_fruit", label = "水果", direction = MoneyDirection.EXPENSE),
    QuickEntryCategoryDefinition(id = "expense_snack", label = "零食", direction = MoneyDirection.EXPENSE),
    QuickEntryCategoryDefinition(id = "expense_entertainment", label = "娱乐", direction = MoneyDirection.EXPENSE),
    QuickEntryCategoryDefinition(id = "expense_phone", label = "通讯", direction = MoneyDirection.EXPENSE),
    QuickEntryCategoryDefinition(id = "expense_clothes", label = "服饰", direction = MoneyDirection.EXPENSE),
    QuickEntryCategoryDefinition(id = "expense_housing", label = "住房", direction = MoneyDirection.EXPENSE),
    QuickEntryCategoryDefinition(id = "expense_home", label = "居家", direction = MoneyDirection.EXPENSE),
    QuickEntryCategoryDefinition(id = "expense_medical", label = "医疗", direction = MoneyDirection.EXPENSE),
    QuickEntryCategoryDefinition(id = "expense_study", label = "学习", direction = MoneyDirection.EXPENSE),
    QuickEntryCategoryDefinition(id = "expense_gift_money", label = "礼金", direction = MoneyDirection.EXPENSE),
    QuickEntryCategoryDefinition(id = "expense_pet", label = "宠物", direction = MoneyDirection.EXPENSE),
    QuickEntryCategoryDefinition(id = "income_salary", label = "工资", direction = MoneyDirection.INCOME),
    QuickEntryCategoryDefinition(id = "income_bonus", label = "奖金", direction = MoneyDirection.INCOME),
    QuickEntryCategoryDefinition(id = "income_reimbursement", label = "报销", direction = MoneyDirection.INCOME),
    QuickEntryCategoryDefinition(id = "income_part_time", label = "兼职", direction = MoneyDirection.INCOME),
    QuickEntryCategoryDefinition(id = "income_finance", label = "理财", direction = MoneyDirection.INCOME),
    QuickEntryCategoryDefinition(id = "income_red_packet", label = "红包", direction = MoneyDirection.INCOME),
    QuickEntryCategoryDefinition(id = "income_collection", label = "收款", direction = MoneyDirection.INCOME),
    QuickEntryCategoryDefinition(id = "income_transfer", label = "转账收入", direction = MoneyDirection.INCOME),
)
