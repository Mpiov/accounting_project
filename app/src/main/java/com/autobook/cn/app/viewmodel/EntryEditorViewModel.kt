package com.autobook.cn.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.autobook.cn.core.data.ImageImportAnalyzer
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.model.ImageImportCandidate
import com.autobook.cn.core.model.ImageImportAnalysisPath
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.PendingEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject
import kotlin.math.absoluteValue
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
    val title: String = "快速记账",
    val amount: String = "",
    /** 当前表单对应的交易时间；图片导入识别出时间时会优先写入这里。 */
    val entryTimestamp: Long? = null,
    /** 时间输入框文本，保存时按固定格式解析为毫秒时间戳。 */
    val entryTimeText: String = formatEntryTime(System.currentTimeMillis()),
    val category: String = "待分类",
    val note: String = "",
    val direction: MoneyDirection = MoneyDirection.EXPENSE,
    val canDelete: Boolean = false,
    val errorMessage: String? = null,
    val imageImportMessage: String? = null,
    val imageImportTracePath: ImageImportAnalysisPath? = null,
    val importedImageRef: String? = null,
    /** 图片分析后保留下来的候选列表，默认按后处理层给出的优先级排序。 */
    val imageImportCandidates: List<ImageImportCandidate> = emptyList(),
    /** 当前编辑页正在展示的候选索引；没有候选时为空。 */
    val selectedImageImportCandidateIndex: Int? = null,
    /** 本次图片分析中被本地过滤掉的候选总数。 */
    val filteredOutCandidateCount: Int = 0,
    /** 因本地账本判重被过滤的候选数。 */
    val duplicateFilteredCount: Int = 0,
    /** 因已退还/已撤销状态被过滤的候选数。 */
    val inactiveFilteredCount: Int = 0,
    /** 图片导入分析结果默认只作为建议，是否仍需用户手动确认后保存。 */
    val imageImportRequiresManualConfirmation: Boolean = false,
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
    val defaultNote: String = "",
)

/** 手动记账与编辑流程的 ViewModel。 */
@HiltViewModel
class EntryEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val imageImportAnalyzer: ImageImportAnalyzer,
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
                else -> "快速记账"
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
    fun updateEntryTime(value: String) = updateState { copy(entryTimeText = value, errorMessage = null) }
    fun updateCategory(value: String) = updateState { copy(category = value, errorMessage = null) }
    fun updateNote(value: String) = updateState { copy(note = value, errorMessage = null) }
    fun updateDirection(value: MoneyDirection) = updateState { copy(direction = value, errorMessage = null) }
    fun applyTemplate(templateId: String) = updateState { applyEntryTemplate(templateId) }

    /**
     * 切换当前正在查看的图片分析候选。
     *
     * 用途说明：
     * - 当一张截图识别出多条候选后，允许用户在编辑页里切换当前要采纳的候选。
     * - 切换时会把表单字段回填为对应候选的内容，便于用户继续微调。
     *
     * 参数说明：
     * - index：希望切换到的候选索引。
     */
    fun selectImageImportCandidate(index: Int) {
        updateState { applyImageImportCandidate(index) }
    }

    /**
     * 触发一次图片导入记账建议填充。
     *
     * 用途说明：
     * - 当前阶段已经接上真实选图入口，并会根据设置决定走云端分析或退回手动确认草稿。
     * - `imageRef` 当前直接使用图片 Uri 字符串透传到底层分析器，避免在页面层重复做文件拷贝。
     */
    fun importFromImage(imageRef: String) {
        viewModelScope.launch {
            updateState {
                copy(
                    importedImageRef = imageRef,
                    imageImportMessage = "图片分析中，请稍候...",
                    imageImportTracePath = null,
                    imageImportRequiresManualConfirmation = true,
                    errorMessage = null,
                )
            }

            try {
                when (val result = imageImportAnalyzer.analyze(imageRef = imageRef)) {
                    is com.autobook.cn.core.model.OperationResult.Success -> {
                        val suggestion = result.data
                        updateState {
                            val selectedIndex = suggestion.candidates.indices.firstOrNull()
                            val baseState = copy(
                                amount = suggestion.amount?.toString() ?: amount,
                                entryTimestamp = suggestion.timestamp ?: entryTimestamp,
                                entryTimeText = suggestion.timestamp?.let(::formatEntryTime) ?: entryTimeText,
                                direction = suggestion.direction,
                                category = suggestion.category.ifBlank { category },
                                note = suggestion.note.ifBlank { note },
                                imageImportMessage = suggestion.message,
                                imageImportTracePath = suggestion.tracePath,
                                importedImageRef = imageRef,
                                imageImportCandidates = suggestion.candidates,
                                selectedImageImportCandidateIndex = selectedIndex,
                                filteredOutCandidateCount = suggestion.filteredOutCandidateCount,
                                duplicateFilteredCount = suggestion.duplicateFilteredCount,
                                inactiveFilteredCount = suggestion.inactiveFilteredCount,
                                imageImportRequiresManualConfirmation = suggestion.requiresManualConfirmation,
                                errorMessage = null,
                            )
                            if (selectedIndex != null) {
                                baseState.applyImageImportCandidate(selectedIndex)
                            } else {
                                baseState
                            }
                        }
                    }

                    is com.autobook.cn.core.model.OperationResult.RetryableFailure -> {
                        updateState {
                            copy(
                                imageImportMessage = result.message,
                                imageImportTracePath = null,
                                importedImageRef = imageRef,
                                imageImportCandidates = emptyList(),
                                selectedImageImportCandidateIndex = null,
                                filteredOutCandidateCount = 0,
                                duplicateFilteredCount = 0,
                                inactiveFilteredCount = 0,
                                imageImportRequiresManualConfirmation = true,
                                errorMessage = null,
                            )
                        }
                    }

                    is com.autobook.cn.core.model.OperationResult.TerminalFailure -> {
                        updateState {
                            copy(
                                imageImportMessage = result.message,
                                imageImportTracePath = null,
                                importedImageRef = imageRef,
                                imageImportCandidates = emptyList(),
                                selectedImageImportCandidateIndex = null,
                                filteredOutCandidateCount = 0,
                                duplicateFilteredCount = 0,
                                inactiveFilteredCount = 0,
                                imageImportRequiresManualConfirmation = true,
                                errorMessage = null,
                            )
                        }
                    }
                }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Image import analysis failed for $imageRef", throwable)
                updateState {
                    copy(
                        imageImportMessage = "图片分析失败：${throwable.message ?: throwable.javaClass.simpleName}",
                        imageImportTracePath = null,
                        importedImageRef = imageRef,
                        imageImportCandidates = emptyList(),
                        selectedImageImportCandidateIndex = null,
                        filteredOutCandidateCount = 0,
                        duplicateFilteredCount = 0,
                        inactiveFilteredCount = 0,
                        imageImportRequiresManualConfirmation = true,
                        errorMessage = null,
                    )
                }
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            val amount = state.amount.toDoubleOrNull()
            if (amount == null || amount <= 0.0) {
                updateState { copy(errorMessage = "请输入有效金额") }
                return@launch
            }
            val entryTimestamp = parseEntryTime(state.entryTimeText)
            if (entryTimestamp == null) {
                updateState { copy(errorMessage = "请输入正确时间，格式如 2026-05-03 18:30") }
                return@launch
            }
            if (state.direction == MoneyDirection.UNKNOWN) {
                // 自动解析无法确定方向时，要求用户在编辑页里显式确认收入或支出。
                updateState { copy(errorMessage = "请选择收支方向") }
                return@launch
            }
            val category = state.category.trim().ifBlank { "待分类" }
            val note = state.note.trim().ifBlank { null }

            when (state.mode) {
                EntryEditorMode.CREATE -> {
                    ledgerRepository.add(
                        LedgerEntry(
                            amount = amount,
                            direction = state.direction,
                            channel = if (state.importedImageRef != null) "图片导入" else "手动记账",
                            timestamp = entryTimestamp,
                            category = category,
                            note = note,
                            traceSource = if (state.importedImageRef != null) "image-import" else "manual",
                            tracePath = state.imageImportTracePath?.name?.lowercase() ?: "manual-entry",
                            traceModelVersion = if (state.importedImageRef != null) "image-import-v1" else "manual-v1",
                            editedManually = true,
                        ),
                    )
                }

                EntryEditorMode.EDIT_LEDGER -> {
                    val current = editingLedger ?: return@launch
                    ledgerRepository.update(
                        current.copy(
                            amount = amount,
                            timestamp = entryTimestamp,
                            direction = state.direction,
                            category = category,
                            note = note,
                            editedManually = true,
                        ),
                    )
                }

                EntryEditorMode.RESOLVE_PENDING -> {
                    // 待确认项保存后写入账本，并从待确认队列中移除。
                    val pending = resolvingPending ?: return@launch
                    val correctedEntry = LedgerEntry(
                        sortId = pending.sortId,
                        amount = amount,
                        direction = state.direction,
                        channel = pending.channel,
                        timestamp = entryTimestamp,
                        category = category.ifBlank { pending.categoryHint },
                        note = note,
                        traceSource = pending.rawSource,
                        tracePath = pending.parsePath,
                        traceModelVersion = pending.modelVersion,
                        traceNotificationId = pending.traceNotificationId,
                        editedManually = true,
                    )
                    val ledgerEntryId = ledgerRepository.add(correctedEntry)
                    pendingRepository.confirmEdited(
                        id = pending.id,
                        correctedEntry = correctedEntry.copy(id = ledgerEntryId),
                    )
                }
            }

            updateState {
                copy(
                    finished = true,
                    errorMessage = null,
                    imageImportRequiresManualConfirmation = false,
                )
            }
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
                        amount = entry.amount.absoluteValue.toString(),
                        entryTimestamp = entry.timestamp,
                        entryTimeText = formatEntryTime(entry.timestamp),
                        category = entry.category,
                        note = entry.note.orEmpty(),
                        direction = entry.direction,
                    )
                }
            }

            pendingId != null -> {
                resolvingPending = pendingRepository.getById(pendingId)
                resolvingPending?.let { entry ->
                    _uiState.value = _uiState.value.copy(
                        amount = entry.amount.absoluteValue.toString(),
                        entryTimestamp = entry.timestamp,
                        entryTimeText = formatEntryTime(entry.timestamp),
                        category = entry.categoryHint,
                        note = entry.note.orEmpty(),
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

/** 快捷模板只预填常用字段，避免覆盖用户已确认的金额和时间。 */
internal fun EntryEditorUiState.applyEntryTemplate(templateId: String): EntryEditorUiState {
    val template = ENTRY_TEMPLATES.firstOrNull { it.id == templateId } ?: return this
    return copy(
        direction = template.direction,
        category = template.category,
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
        defaultNote = "晨间用餐",
    ),
    EntryTemplate(
        id = "lunch",
        label = "午餐",
        direction = MoneyDirection.EXPENSE,
        category = "餐饮",
        defaultNote = "工作日用餐",
    ),
    EntryTemplate(
        id = "taxi",
        label = "打车",
        direction = MoneyDirection.EXPENSE,
        category = "交通",
        defaultNote = "滴滴出行\n城市出行",
    ),
    EntryTemplate(
        id = "shopping",
        label = "购物",
        direction = MoneyDirection.EXPENSE,
        category = "购物",
        defaultNote = "生活消费",
    ),
    EntryTemplate(
        id = "salary",
        label = "工资",
        direction = MoneyDirection.INCOME,
        category = "工资",
        defaultNote = "固定收入",
    ),
    EntryTemplate(
        id = "transfer",
        label = "转账",
        direction = MoneyDirection.EXPENSE,
        category = "转账",
        defaultNote = "账户间转移",
    ),
)

private val ENTRY_TEMPLATE_MODELS = ENTRY_TEMPLATES.map { template ->
    EntryTemplateUiModel(
        id = template.id,
        label = template.label,
    )
}

/**
 * 把指定图片候选应用到当前表单。
 *
 * 参数说明：
 * - index：待应用候选的索引。
 */
internal fun EntryEditorUiState.applyImageImportCandidate(index: Int): EntryEditorUiState {
    val candidate = imageImportCandidates.getOrNull(index) ?: return this
    return copy(
        amount = candidate.amount?.toString() ?: amount,
        entryTimestamp = candidate.timestamp ?: entryTimestamp,
        entryTimeText = candidate.timestamp?.let(::formatEntryTime) ?: entryTimeText,
        direction = candidate.direction,
        category = candidate.category.ifBlank { category },
        note = candidate.note.ifBlank { note },
        selectedImageImportCandidateIndex = index,
        errorMessage = null,
    )
}

/**
 * 用途说明：
 * - 把毫秒时间戳格式化成编辑页统一展示和输入的时间文本。
 *
 * 参数说明：
 * - timestamp：交易发生时间戳。
 */
internal fun formatEntryTime(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(ENTRY_TIME_FORMATTER)
}

/**
 * 用途说明：
 * - 把用户输入的时间文本解析成毫秒时间戳。
 * - 解析失败返回空，由保存流程给出错误提示，避免用户输入过程中反复弹错。
 *
 * 参数说明：
 * - value：时间输入框中的文本，格式为 `yyyy-MM-dd HH:mm`。
 */
private fun parseEntryTime(value: String): Long? {
    return try {
        LocalDateTime.parse(value.trim(), ENTRY_TIME_FORMATTER)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}

private val ENTRY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)

private const val TAG = "EntryEditorViewModel"
