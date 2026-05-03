package com.autobook.cn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.autobook.cn.app.viewmodel.EntryEditorMode
import com.autobook.cn.app.viewmodel.EntryEditorUiState
import com.autobook.cn.app.viewmodel.EntryTemplateUiModel
import com.autobook.cn.core.model.ImageImportCandidate
import com.autobook.cn.core.model.MoneyDirection
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 用途说明：
 * - 新建、编辑账本记录与处理待确认项目共用这一张编辑页。
 * - 同时整合快捷模板、图片导入候选选择和底部固定保存动作，减少多链路记账时的跳转成本。
 *
 * 参数说明：
 * - state：编辑页当前状态。
 * - onBack：返回上一级页面的回调。
 * - onAmountChange：金额输入变化回调。
 * - onEntryTimeChange：交易时间输入变化回调。
 * - onCategoryChange：分类输入变化回调。
 * - onNoteChange：备注输入变化回调。
 * - onDirectionChange：收支方向切换回调。
 * - onApplyTemplate：点击快捷模板后的回调。
 * - onImportFromImage：触发图片导入流程的回调。
 * - onSelectImageImportCandidate：切换图片识别候选的回调。
 * - onSave：点击保存后的回调。
 * - onDelete：点击删除后的回调。
 * - modifier：外层修饰符，便于导航层注入布局约束。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryEditorScreen(
    state: EntryEditorUiState,
    onBack: () -> Unit,
    onAmountChange: (String) -> Unit,
    onEntryTimeChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDirectionChange: (MoneyDirection) -> Unit,
    onApplyTemplate: (String) -> Unit,
    onImportFromImage: () -> Unit,
    onSelectImageImportCandidate: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QuickEntryCreateScreen(
        state = state,
        onBack = onBack,
        onAmountChange = onAmountChange,
        onEntryTimeChange = onEntryTimeChange,
        onCategoryChange = onCategoryChange,
        onNoteChange = onNoteChange,
        onDirectionChange = onDirectionChange,
        onSave = onSave,
        onDelete = onDelete,
        modifier = modifier,
    )
}

/**
 * 用途说明：
 * - 新建、编辑和待确认入账都复用“3 秒记账”的快速录入界面。
 * - 页面主流程只围绕收支方向、分类、时间和金额展开，避免编辑场景重新回到笨重表单。
 *
 * 参数说明：
 * - state：创建账单的当前 UI 状态，包含方向、分类、金额、备注和错误提示。
 * - onBack：点击取消时返回上一页。
 * - onAmountChange：数字键盘改写金额后的回调。
 * - onEntryTimeChange：点击日期键选择交易日期后的回调。
 * - onCategoryChange：点击分类后的回调。
 * - onNoteChange：备注输入变化后的回调。
 * - onDirectionChange：切换支出/收入页签后的回调。
 * - onSave：点击完成后的保存回调。
 * - onDelete：编辑正式账本时点击删除后的回调，新建和待确认模式不会展示入口。
 * - modifier：外层修饰符，便于导航容器注入尺寸约束。
 */
@Composable
private fun QuickEntryCreateScreen(
    state: EntryEditorUiState,
    onBack: () -> Unit,
    onAmountChange: (String) -> Unit,
    onEntryTimeChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDirectionChange: (MoneyDirection) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeDirection = if (state.direction == MoneyDirection.INCOME) {
        MoneyDirection.INCOME
    } else {
        MoneyDirection.EXPENSE
    }
    val categories = if (activeDirection == MoneyDirection.INCOME) {
        QUICK_INCOME_CATEGORIES
    } else {
        QUICK_EXPENSE_CATEGORIES
    }
    var isInputPanelVisible by rememberSaveable(state.mode) {
        // 编辑和待确认是“修改已有账单”，进入页面就应该露出时间与金额键盘；新建仍保持先选分类再展开。
        mutableStateOf(state.mode != EntryEditorMode.CREATE)
    }
    val selectedCategory = categories.firstOrNull { category -> category.label == state.category }
    val selectedCategoryLabel = selectedCategory?.label ?: state.category.ifBlank { QUICK_UNCATEGORIZED_LABEL }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = QuickEntryBackgroundColor,
        topBar = {
            QuickEntryTopBar(
                selectedDirection = activeDirection,
                onDirectionSelected = { direction ->
                    onDirectionChange(direction)
                    isInputPanelVisible = false
                    // 切换方向时清空不属于当前方向的分类，避免把“工资”误带到支出里。
                    val nextCategories = if (direction == MoneyDirection.INCOME) {
                        QUICK_INCOME_CATEGORIES
                    } else {
                        QUICK_EXPENSE_CATEGORIES
                    }
                    if (state.category !in nextCategories.map { it.label }) {
                        onCategoryChange(QUICK_UNCATEGORIZED_LABEL)
                    }
                },
                onCancel = onBack,
                showDelete = state.canDelete,
                onDelete = onDelete,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 22.dp,
                    end = 20.dp,
                    // 键盘作为覆盖层贴底显示时，底部留出等高空白，保证连续滚动时最后几行分类不被遮挡。
                    bottom = if (isInputPanelVisible) QUICK_INPUT_PANEL_RESERVED_HEIGHT else 22.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                items(
                    items = categories,
                    key = { category -> category.label },
                ) { category ->
                    QuickEntryCategoryItem(
                        category = category,
                        selected = state.category == category.label,
                        onClick = {
                            onCategoryChange(category.label)
                            // 只有用户明确选中分类后才展开键盘，让首屏先专注于快速选分类。
                            isInputPanelVisible = true
                        },
                    )
                }
            }
            if (isInputPanelVisible) {
                QuickEntryInputPanel(
                    selectedCategoryLabel = selectedCategoryLabel,
                    amount = state.amount,
                    note = state.note,
                    entryTimeText = state.entryTimeText,
                    errorMessage = state.errorMessage,
                    onAmountChange = onAmountChange,
                    onEntryTimeChange = onEntryTimeChange,
                    onNoteChange = onNoteChange,
                    onSave = onSave,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * 用途说明：
 * - 快速记账页顶部的方向切换区，改成白底浅棕强调，和应用整体主题保持一致。
 *
 * 参数说明：
 * - selectedDirection：当前选中的收支方向。
 * - onDirectionSelected：点击支出或收入页签后的回调。
 * - onCancel：点击取消后的回调。
 * - showDelete：是否展示删除入口，仅正式账本编辑模式为 true。
 * - onDelete：点击删除后的回调。
 */
@Composable
private fun QuickEntryTopBar(
    selectedDirection: MoneyDirection,
    onDirectionSelected: (MoneyDirection) -> Unit,
    onCancel: () -> Unit,
    showDelete: Boolean = false,
    onDelete: () -> Unit = {},
) {
    Surface(
        color = QuickEntryBackgroundColor,
        shadowElevation = 0.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(52.dp),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(QuickEntryTopBarLabelRowHeight)
                            .padding(horizontal = QuickEntryTopBarHorizontalPadding),
                    ) {
                        if (showDelete) {
                            Text(
                                text = "删除",
                                modifier = Modifier
                                    // 删除、方向页签和取消共享同一行中心线，编辑状态下也不破坏顶部栏的视觉对齐。
                                    .align(Alignment.CenterStart)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onDelete,
                                    ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AutobookExpenseColor,
                            )
                        }
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(38.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            QuickEntryDirectionTab(
                                label = "支出",
                                selected = selectedDirection != MoneyDirection.INCOME,
                                onClick = { onDirectionSelected(MoneyDirection.EXPENSE) },
                            )
                            QuickEntryDirectionTab(
                                label = "收入",
                                selected = selectedDirection == MoneyDirection.INCOME,
                                onClick = { onDirectionSelected(MoneyDirection.INCOME) },
                            )
                        }
                        Text(
                            text = "取消",
                            modifier = Modifier
                                // “取消”和“支出/收入”放在同一个标签行内居中对齐，避免再用单独偏移量凭感觉找水平线。
                                .align(Alignment.CenterEnd)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onCancel,
                                ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = QuickEntryPrimaryTextColor,
                        )
                    }
                    Spacer(modifier = Modifier.height(QuickEntryTopBarLabelToIndicatorGap))
                    Row(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        horizontalArrangement = Arrangement.spacedBy(38.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        QuickEntryDirectionIndicator(selected = selectedDirection != MoneyDirection.INCOME)
                        QuickEntryDirectionIndicator(selected = selectedDirection == MoneyDirection.INCOME)
                    }
                }
            }
            HorizontalDivider(
                color = QuickEntryDividerColor,
                thickness = 0.8.dp,
            )
        }
    }
}

/**
 * 用途说明：
 * - 顶部支出/收入页签，选中态用短下划线表达，减少额外边框和卡片感。
 *
 * 参数说明：
 * - label：页签文字。
 * - selected：当前页签是否处于选中态。
 * - onClick：点击页签后的回调。
 */
@Composable
private fun QuickEntryDirectionTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .width(40.dp)
            .clickable(
                interactionSource = noRippleInteractionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Text(
            text = label,
            modifier = Modifier.align(Alignment.Center),
            color = if (selected) QuickEntryBrandColor else QuickEntrySecondaryTextColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/**
 * 用途说明：
 * - 顶部方向页签的选中短横线，和文字分层布局后独立贴合底部分界线。
 *
 * 参数说明：
 * - selected：当前方向是否选中，未选中时宽度为 0 保持占位一致。
 */
@Composable
private fun QuickEntryDirectionIndicator(selected: Boolean) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(QuickEntryDirectionIndicatorHeight),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .height(QuickEntryDirectionIndicatorHeight)
                .fillMaxWidth(if (selected) 0.82f else 0f)
                .background(
                    color = QuickEntryBrandColor,
                    shape = RoundedCornerShape(percent = 50),
                ),
        )
    }
}

/**
 * 用途说明：
 * - 分类宫格里的单个分类项，保持“圆形图标 + 文案”的轻量结构。
 *
 * 参数说明：
 * - category：分类展示模型，包含文案和轻量字标。
 * - selected：该分类是否已被选中。
 * - onClick：点击分类后的回调；图标圆形区域显示圆形涟漪，文字区域只触发点击不显示方形涟漪。
 */
@Composable
private fun QuickEntryCategoryItem(
    category: QuickEntryCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = noRippleInteractionSource,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    color = if (selected) QuickEntrySelectedCategoryColor else QuickEntryCategoryCircleColor,
                    shape = CircleShape,
                )
                .border(
                    width = if (selected) 1.2.dp else 0.dp,
                    color = if (selected) QuickEntryBrandColor.copy(alpha = 0.42f) else Color.Transparent,
                    shape = CircleShape,
                )
                // 点击反馈只挂在圆形图标容器上，涟漪会被 CircleShape 裁剪，不再铺满整个宫格单元。
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            AutobookIcon(
                asset = category.icon,
                contentDescription = category.label,
                modifier = Modifier.size(30.dp),
                tint = if (selected) QuickEntryBrandColor else QuickEntryIconColor,
            )
        }
        Text(
            text = category.label,
            color = QuickEntryPrimaryTextColor,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * 用途说明：
 * - 快速记账页底部固定输入区，承载金额展示、备注输入、错误提示和数字键盘。
 *
 * 参数说明：
 * - selectedCategoryLabel：当前已选分类文案，用于在键盘上方提示用户正在改哪一类账。
 * - amount：当前金额文本。
 * - note：当前备注文本。
 * - entryTimeText：当前交易时间文本，日期选择器会基于它初始化并写回。
 * - errorMessage：保存校验失败时的错误提示。
 * - onAmountChange：金额变化回调。
 * - onEntryTimeChange：日期选择后的时间变化回调。
 * - onNoteChange：备注变化回调。
 * - onSave：点击完成后的保存回调。
 * - modifier：外层修饰符，便于作为覆盖层贴到底部。
 */
@Composable
private fun QuickEntryInputPanel(
    selectedCategoryLabel: String,
    amount: String,
    note: String,
    entryTimeText: String,
    errorMessage: String?,
    onAmountChange: (String) -> Unit,
    onEntryTimeChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = QuickEntryBackgroundColor,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            HorizontalDivider(color = QuickEntryDividerColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = QuickEntrySelectedCategoryColor,
                    shape = RoundedCornerShape(percent = 50),
                    border = autobookCardBorder(emphasized = true),
                ) {
                    Text(
                        text = selectedCategoryLabel.ifBlank { QUICK_UNCATEGORIZED_LABEL },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = QuickEntryBrandColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatQuickAmountForDisplay(amount),
                    color = QuickEntryPrimaryTextColor,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
            QuickEntryCompactNoteField(
                note = note,
                onNoteChange = onNoteChange,
            )
            errorMessage?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            } ?: Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = QuickEntryDividerColor)
            QuickEntryKeypad(
                amount = amount,
                entryTimeText = entryTimeText,
                onAmountChange = onAmountChange,
                onEntryTimeChange = onEntryTimeChange,
                onSave = onSave,
            )
        }
    }
}

/**
 * 用途说明：
 * - 快速记账页的轻量备注输入框，替代 Material TextField，降低键盘展开后的测量和绘制成本。
 *
 * 参数说明：
 * - note：当前备注文本。
 * - onNoteChange：备注变化回调。
 */
@Composable
private fun QuickEntryCompactNoteField(
    note: String,
    onNoteChange: (String) -> Unit,
) {
    BasicTextField(
        value = note,
        onValueChange = onNoteChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = QuickEntryPrimaryTextColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(34.dp)
            .background(QuickEntryNoteBackgroundColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        decorationBox = { innerTextField ->
            if (note.isBlank()) {
                Text(
                    text = "备注",
                    color = QuickEntrySecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            innerTextField()
        },
    )
}

/**
 * 用途说明：
 * - 数字键盘负责把点击转成金额字符串，用户无需唤起系统软键盘即可完成录入。
 *
 * 参数说明：
 * - amount：当前金额文本。
 * - entryTimeText：当前交易时间文本，用于打开日期选择器时定位默认日期。
 * - onAmountChange：金额变化回调。
 * - onEntryTimeChange：日期选择后的时间变化回调。
 * - onSave：点击完成后的保存回调。
 */
@Composable
private fun QuickEntryKeypad(
    amount: String,
    entryTimeText: String,
    onAmountChange: (String) -> Unit,
    onEntryTimeChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val dateLabel = quickEntryDateTimeKeyLabel(entryTimeText)
    var isDateTimePickerVisible by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            QuickEntryKeyCell(label = "7", onClick = { onAmountChange(appendQuickAmountKey(amount, "7")) })
            QuickEntryKeyCell(label = "8", onClick = { onAmountChange(appendQuickAmountKey(amount, "8")) })
            QuickEntryKeyCell(label = "9", onClick = { onAmountChange(appendQuickAmountKey(amount, "9")) })
            QuickEntryKeyCell(
                label = dateLabel,
                iconAsset = AutobookIconAsset.CalendarToday,
                onClick = {
                    // 时间键直接打开同一套年月日时分滚轮，不再先跳系统日期再跳应用内时分。
                    isDateTimePickerVisible = true
                },
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            QuickEntryKeyCell(label = "4", onClick = { onAmountChange(appendQuickAmountKey(amount, "4")) })
            QuickEntryKeyCell(label = "5", onClick = { onAmountChange(appendQuickAmountKey(amount, "5")) })
            QuickEntryKeyCell(label = "6", onClick = { onAmountChange(appendQuickAmountKey(amount, "6")) })
            QuickEntryKeyCell(label = "+", onClick = { onAmountChange(appendQuickAmountOperator(amount, "+")) })
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            QuickEntryKeyCell(label = "1", onClick = { onAmountChange(appendQuickAmountKey(amount, "1")) })
            QuickEntryKeyCell(label = "2", onClick = { onAmountChange(appendQuickAmountKey(amount, "2")) })
            QuickEntryKeyCell(label = "3", onClick = { onAmountChange(appendQuickAmountKey(amount, "3")) })
            QuickEntryKeyCell(label = "-", onClick = { onAmountChange(appendQuickAmountOperator(amount, "-")) })
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            QuickEntryKeyCell(label = ".", onClick = { onAmountChange(appendQuickAmountKey(amount, ".")) })
            QuickEntryKeyCell(label = "0", onClick = { onAmountChange(appendQuickAmountKey(amount, "0")) })
            QuickEntryKeyCell(
                label = "删除",
                iconAsset = AutobookIconAsset.Backspace,
                onClick = { onAmountChange(deleteQuickAmountKey(amount)) },
            )
            QuickEntryKeyCell(
                label = "完成",
                emphasized = true,
                onClick = { commitQuickAmountAndSave(amount, onAmountChange, onSave) },
            )
        }
    }
    if (isDateTimePickerVisible) {
        QuickEntryDateTimeWheelDialog(
            initialDateTime = parseQuickEntryDateTime(entryTimeText) ?: LocalDateTime.now(),
            onDismiss = { isDateTimePickerVisible = false },
            onConfirm = { selectedDateTime ->
                onEntryTimeChange(selectedDateTime.format(QUICK_ENTRY_TIME_FORMATTER))
                isDateTimePickerVisible = false
            },
        )
    }
}

/**
 * 用途说明：
 * - 快速记账的日期时间选择弹窗，把年、月、日、小时、分钟放在同一个界面里。
 * - 五列都使用上下滚动的滚轮，避免系统日期和系统时间控件混用造成割裂。
 *
 * 参数说明：
 * - initialDateTime：当前账单时间，五个滚轮会以它作为初始位置。
 * - onDismiss：点击外部或取消时关闭弹窗。
 * - onConfirm：确认时回传完整的年月日时分。
 */
@Composable
private fun QuickEntryDateTimeWheelDialog(
    initialDateTime: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit,
) {
    var selectedYear by remember(initialDateTime) { mutableStateOf(initialDateTime.year) }
    var selectedMonth by remember(initialDateTime) { mutableStateOf(initialDateTime.monthValue) }
    var selectedDay by remember(initialDateTime) { mutableStateOf(initialDateTime.dayOfMonth) }
    var selectedHour by remember(initialDateTime) { mutableStateOf(initialDateTime.hour) }
    var selectedMinute by remember(initialDateTime) { mutableStateOf(initialDateTime.minute) }
    val yearValues = remember(initialDateTime.year) { quickEntryYearValues(initialDateTime.year) }
    val dayValues = remember(selectedYear, selectedMonth) {
        (1..quickEntryDaysInMonth(selectedYear, selectedMonth)).toList()
    }

    LaunchedEffect(dayValues, selectedDay) {
        if (selectedDay > dayValues.last()) {
            // 月份或年份切换后，如果原日期不存在，例如 31 日切到 2 月，就自动夹到该月最后一天。
            selectedDay = dayValues.last()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            color = QuickEntryBackgroundColor,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "选择日期时间",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = QuickEntryPrimaryTextColor,
                    )
                    Text(
                        text = "${selectedYear}年${selectedMonth}月${selectedDay}日 %02d:%02d".format(
                            selectedHour,
                            selectedMinute,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = QuickEntrySecondaryTextColor,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuickEntryTimeWheel(
                        title = "年",
                        values = yearValues,
                        initialValue = initialDateTime.year,
                        onValueCentered = { selectedYear = it },
                        valueFormatter = { value -> value.toString() },
                        modifier = Modifier.weight(1.25f),
                    )
                    QuickEntryTimeWheel(
                        title = "月",
                        values = QUICK_ENTRY_MONTHS,
                        initialValue = initialDateTime.monthValue,
                        onValueCentered = { selectedMonth = it },
                        modifier = Modifier.weight(1f),
                    )
                    QuickEntryTimeWheel(
                        title = "日",
                        values = dayValues,
                        initialValue = selectedDay.coerceAtMost(dayValues.last()),
                        onValueCentered = { selectedDay = it },
                        modifier = Modifier.weight(1f),
                    )
                    QuickEntryTimeWheel(
                        title = "时",
                        values = QUICK_ENTRY_HOURS,
                        initialValue = initialDateTime.hour,
                        onValueCentered = { selectedHour = it },
                        modifier = Modifier.weight(1f),
                    )
                    QuickEntryTimeWheel(
                        title = "分",
                        values = QUICK_ENTRY_MINUTES,
                        initialValue = initialDateTime.minute,
                        onValueCentered = { selectedMinute = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    TextButton(
                        onClick = {
                            // 确认时把五列滚轮的结果合成同一个 LocalDateTime，再交给 ViewModel 按统一格式保存。
                            onConfirm(
                                LocalDate.of(selectedYear, selectedMonth, selectedDay)
                                    .atTime(selectedHour, selectedMinute)
                                    .withSecond(0)
                                    .withNano(0),
                            )
                        },
                    ) {
                        Text("确定", color = QuickEntryBrandColor)
                    }
                }
            }
        }
    }
}

/**
 * 用途说明：
 * - 单列时间滚轮，负责展示小时或分钟，并把当前居中的值回传给父组件。
 * - 选中态使用中间浅色带高亮，用户上下滑动时可以稳定看到当前会被确认的值。
 *
 * 参数说明：
 * - title：列标题，例如“小时”或“分钟”。
 * - values：可选择的整数列表。
 * - initialValue：初始选中值。
 * - onValueCentered：滚动过程中居中值变化后的回调。
 * - valueFormatter：数字显示格式；年份显示 4 位，月日时分显示 2 位。
 * - modifier：外层布局修饰符。
 */
@Composable
private fun QuickEntryTimeWheel(
    title: String,
    values: List<Int>,
    initialValue: Int,
    onValueCentered: (Int) -> Unit,
    valueFormatter: (Int) -> String = { value -> "%02d".format(value) },
    modifier: Modifier = Modifier,
) {
    val initialIndex = values.indexOf(initialValue).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center,
    )
    val centeredValue by remember(listState, values, initialValue) {
        derivedStateOf {
            selectedQuickEntryTimeWheelValue(
                listState = listState,
                values = values,
                fallback = initialValue,
            )
        }
    }

    LaunchedEffect(initialIndex) {
        // 弹窗刚出现时把初始值放到高亮带中间，避免用户一打开就需要重新找当前值。
        listState.scrollToItem(initialIndex)
    }
    LaunchedEffect(centeredValue) {
        onValueCentered(centeredValue)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = QuickEntrySecondaryTextColor,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(QuickEntryTimeWheelHeight),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(QuickEntryTimeWheelItemHeight)
                    .background(
                        color = QuickEntrySelectedCategoryColor,
                        shape = RoundedCornerShape(6.dp),
                    ),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 松手后的惯性滚动交给 Compose snap 处理，最近的时间项会自动吸附到中间高亮带。
                flingBehavior = snapFlingBehavior,
                contentPadding = PaddingValues(vertical = QuickEntryTimeWheelVerticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                lazyColumnItems(
                    items = values,
                    key = { value -> value },
                ) { value ->
                    val selected = value == centeredValue
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(QuickEntryTimeWheelItemHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = valueFormatter(value),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) QuickEntryBrandColor else QuickEntrySecondaryTextColor,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 用途说明：
 * - 根据 LazyColumn 当前可见项，计算最接近滚轮中心线的时间值。
 * - 这个函数把“选中”定义为视觉中心项，确保用户上下滑动后确认的是中间高亮的数字。
 *
 * 参数说明：
 * - listState：滚轮列表状态，提供当前可见项的位置。
 * - values：滚轮对应的原始时间值列表。
 * - fallback：布局尚未完成或索引越界时使用的兜底值。
 */
private fun selectedQuickEntryTimeWheelValue(
    listState: LazyListState,
    values: List<Int>,
    fallback: Int,
): Int {
    val layoutInfo = listState.layoutInfo
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val centeredItem = layoutInfo.visibleItemsInfo.minByOrNull { itemInfo ->
        val itemCenter = itemInfo.offset + itemInfo.size / 2
        kotlin.math.abs(itemCenter - viewportCenter)
    } ?: return fallback
    return values.getOrNull(centeredItem.index) ?: fallback
}

/**
 * 用途说明：
 * - 数字键盘里的单个按键，统一控制尺寸、分隔线和完成按钮高亮。
 *
 * 参数说明：
 * - label：按键文案。
 * - iconAsset：可选图标资源，用于日期和删除这类需要图形识别的按键。
 * - emphasized：是否为主要动作按钮。
 * - onClick：点击按键后的回调。
 */
@Composable
private fun RowScope.QuickEntryKeyCell(
    label: String,
    iconAsset: AutobookIconAsset? = null,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .border(width = 0.5.dp, color = QuickEntryDividerColor)
            .background(if (emphasized) QuickEntryBrandColor else QuickEntryKeyBackgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (iconAsset != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AutobookIcon(
                    asset = iconAsset,
                    contentDescription = label,
                    modifier = Modifier.size(20.dp),
                    tint = if (emphasized) Color.White else QuickEntryPrimaryTextColor,
                )
                if (label != "删除") {
                    Text(
                        text = label,
                        color = if (emphasized) Color.White else QuickEntryPrimaryTextColor,
                        // 日期时间键会展示“今天 18:30”这类较长文案，使用小一号字号避免挤压同排数字键。
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        } else {
            Text(
                text = label,
                color = if (emphasized) Color.White else QuickEntryPrimaryTextColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/**
 * 用途说明：
 * - 快速记账分类的轻量展示模型，避免 UI 直接散落分类名和图标映射。
 *
 * 参数说明：
 * - label：分类名称，会写入账单分类字段。
 * - icon：分类圆形图标，回滚后重新走 Material 矢量图标。
 */
private data class QuickEntryCategory(
    val label: String,
    val icon: AutobookIconAsset,
)

/**
 * 用途说明：
 * - 生成键盘时间键展示文案，同时露出日期和小时分钟，避免用户以为只能改日期。
 *
 * 参数说明：
 * - entryTimeText：当前交易时间文本。
 */
private fun quickEntryDateTimeKeyLabel(entryTimeText: String): String {
    val dateTime = parseQuickEntryDateTime(entryTimeText) ?: return "时间"
    val date = dateTime.toLocalDate()
    val time = "%02d:%02d".format(dateTime.hour, dateTime.minute)
    return if (date == LocalDate.now()) {
        "今天 $time"
    } else {
        "${date.monthValue}/${date.dayOfMonth} $time"
    }
}

/**
 * 用途说明：
 * - 解析快速记账页当前的时间文本，解析失败时返回空交给调用方兜底。
 *
 * 参数说明：
 * - value：格式为 yyyy-MM-dd HH:mm 的时间文本。
 */
private fun parseQuickEntryDateTime(value: String): LocalDateTime? {
    return try {
        LocalDateTime.parse(value.trim(), QUICK_ENTRY_TIME_FORMATTER)
    } catch (_: DateTimeParseException) {
        null
    }
}

/**
 * 用途说明：
 * - 生成日期时间滚轮的年份范围，既覆盖当前年份附近，也保证历史账单原年份一定能显示。
 *
 * 参数说明：
 * - anchorYear：当前账单时间中的年份，用于确保编辑历史账单时不会丢失原年份。
 */
private fun quickEntryYearValues(anchorYear: Int): List<Int> {
    val currentYear = LocalDate.now().year
    val startYear = minOf(anchorYear, currentYear) - QUICK_ENTRY_YEAR_BACKWARD_PADDING
    val endYear = maxOf(anchorYear, currentYear) + QUICK_ENTRY_YEAR_FORWARD_PADDING
    return (startYear..endYear).toList()
}

/**
 * 用途说明：
 * - 按年份和月份计算该月天数，让日期滚轮能正确处理大小月和闰年二月。
 *
 * 参数说明：
 * - year：当前选择的年份。
 * - month：当前选择的月份，范围为 1 到 12。
 */
private fun quickEntryDaysInMonth(year: Int, month: Int): Int {
    return LocalDate.of(year, month, 1).lengthOfMonth()
}

/**
 * 用途说明：
 * - 把数字键输入追加到金额文本后面，同时限制整数长度和小数位数。
 *
 * 参数说明：
 * - current：追加前的金额文本。
 * - key：本次点击的数字或小数点。
 */
private fun appendQuickAmountKey(current: String, key: String): String {
    val value = current.trim()
    val operatorIndex = findQuickAmountOperatorIndex(value)
    val prefix = if (operatorIndex >= 0) value.substring(0, operatorIndex + 1) else ""
    val operand = if (operatorIndex >= 0) value.substring(operatorIndex + 1) else value
    if (key == ".") {
        // 当前操作数只能有一个小数点，空金额点小数时自动补成 0.。
        return when {
            operand.contains(".") -> value
            operand.isBlank() -> "${prefix}0."
            else -> "$prefix$operand."
        }
    }
    if (key !in "0123456789") {
        return value
    }
    if (operand.contains(".")) {
        val decimalLength = operand.substringAfter(".").length
        // 账单金额按人民币常规只保留两位小数，避免保存时再出现无效格式。
        if (decimalLength >= 2) {
            return value
        }
    }
    val nextOperand = when {
        operand.isBlank() -> key
        operand == "0" && key != "0" -> key
        operand == "0" && key == "0" -> operand
        else -> operand + key
    }
    return if (nextOperand.substringBefore(".").length > QUICK_AMOUNT_MAX_INTEGER_DIGITS) {
        value
    } else {
        "$prefix$nextOperand"
    }
}

/**
 * 用途说明：
 * - 处理金额键盘上的加减号；如果已经存在一个表达式，会先折算成结果再追加新运算符。
 *
 * 参数说明：
 * - current：当前金额或简单表达式文本。
 * - operator：本次追加的运算符，只接受 + 或 -。
 */
private fun appendQuickAmountOperator(current: String, operator: String): String {
    val value = current.trim()
    if (value.isBlank() || operator !in listOf("+", "-")) {
        return value
    }
    if (value.last() == '+' || value.last() == '-') {
        return value.dropLast(1) + operator
    }
    val normalized = evaluateQuickAmountExpression(value) ?: value
    return "$normalized$operator"
}

/**
 * 用途说明：
 * - 删除金额文本末尾一位，给自定义数字键盘提供退格能力。
 *
 * 参数说明：
 * - current：删除前的金额文本。
 */
private fun deleteQuickAmountKey(current: String): String {
    return current.trim().dropLast(1)
}

/**
 * 用途说明：
 * - 格式化快速录入页展示金额，空值时显示 0.00，避免页面底部看起来像缺内容。
 *
 * 参数说明：
 * - amount：当前金额文本。
 */
private fun formatQuickAmountForDisplay(amount: String): String {
    return amount.ifBlank { "0.00" }
}

/**
 * 用途说明：
 * - 点击完成时先把简单加减表达式折算成最终金额，再交给 ViewModel 保存。
 *
 * 参数说明：
 * - amount：当前金额或简单表达式文本。
 * - onAmountChange：写回最终金额的回调。
 * - onSave：保存账单的回调。
 */
private fun commitQuickAmountAndSave(
    amount: String,
    onAmountChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    evaluateQuickAmountExpression(amount)?.let(onAmountChange)
    onSave()
}

/**
 * 用途说明：
 * - 解析快速键盘产生的单次加减表达式，例如 12+3 或 20-4。
 *
 * 参数说明：
 * - value：待解析的金额文本。
 */
private fun evaluateQuickAmountExpression(value: String): String? {
    val normalized = value.trim().trimEnd('+', '-')
    if (normalized.isBlank()) {
        return null
    }
    val operatorIndex = findQuickAmountOperatorIndex(normalized)
    if (operatorIndex < 0) {
        return normalizeQuickAmountNumber(normalized)
    }
    val left = normalized.substring(0, operatorIndex).toDoubleOrNull() ?: return null
    val right = normalized.substring(operatorIndex + 1).toDoubleOrNull() ?: return null
    val result = when (normalized[operatorIndex]) {
        '+' -> left + right
        '-' -> left - right
        else -> return null
    }
    return if (result > 0.0) {
        formatQuickCalculatedAmount(result)
    } else {
        null
    }
}

/**
 * 用途说明：
 * - 找到快速金额表达式里的加减运算符位置。
 *
 * 参数说明：
 * - value：金额文本。
 */
private fun findQuickAmountOperatorIndex(value: String): Int {
    val plusIndex = value.indexOf('+')
    val minusIndex = value.indexOf('-')
    return listOf(plusIndex, minusIndex)
        .filter { index -> index >= 0 }
        .minOrNull() ?: -1
}

/**
 * 用途说明：
 * - 规范化单个金额数字，去掉末尾孤立小数点和多余的尾零。
 *
 * 参数说明：
 * - value：待规范化的金额文本。
 */
private fun normalizeQuickAmountNumber(value: String): String? {
    val number = value.trimEnd('.').toDoubleOrNull() ?: return null
    return if (number > 0.0) {
        formatQuickCalculatedAmount(number)
    } else {
        null
    }
}

/**
 * 用途说明：
 * - 把计算结果格式化成最多两位小数，避免 12.0 这类显示进入账本。
 *
 * 参数说明：
 * - value：计算后的金额。
 */
private fun formatQuickCalculatedAmount(value: Double): String {
    val rounded = Math.round(value * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
    }
}

private const val QUICK_UNCATEGORIZED_LABEL = "待分类"
private const val QUICK_AMOUNT_MAX_INTEGER_DIGITS = 8
private const val QUICK_ENTRY_YEAR_BACKWARD_PADDING = 10
private const val QUICK_ENTRY_YEAR_FORWARD_PADDING = 1
private val QUICK_INPUT_PANEL_RESERVED_HEIGHT = 314.dp
private val QUICK_ENTRY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
// 月、时、分滚轮使用固定列表，避免每次重组都重新创建候选。
private val QUICK_ENTRY_MONTHS = (1..12).toList()
private val QUICK_ENTRY_HOURS = (0..23).toList()
private val QUICK_ENTRY_MINUTES = (0..59).toList()

private val QuickEntryBrandColor = Color(0xFF8B6047)
private val QuickEntryBackgroundColor = Color(0xFFFFFDF9)
private val QuickEntrySelectedCategoryColor = Color(0xFFF3E6DC)
private val QuickEntryCategoryCircleColor = Color(0xFFF8F3EC)
private val QuickEntryNoteBackgroundColor = Color(0xFFF8F4EE)
private val QuickEntryKeyBackgroundColor = Color(0xFFFFFEFC)
private val QuickEntryDividerColor = Color(0xFFECE2D8)
private val QuickEntryPrimaryTextColor = Color(0xFF28231F)
private val QuickEntrySecondaryTextColor = Color(0xFF8D8176)
private val QuickEntryIconColor = Color(0xFF6D6258)
// 顶部栏的文字层使用固定行高承载“支出/收入/取消”，三者在同一个 Box 内按中心线对齐。
private val QuickEntryTopBarLabelRowHeight = 32.dp
// 右侧“取消”的横向内边距独立成常量，方便和整体页面留白统一调整。
private val QuickEntryTopBarHorizontalPadding = 22.dp
// 标签行到短横线之间只保留一个结构间距；短横线底部仍贴住顶栏分界线。
private val QuickEntryTopBarLabelToIndicatorGap = 14.dp
// 选中短横线高度单独维护，避免文字对齐调整时误改底部分界线贴合关系。
private val QuickEntryDirectionIndicatorHeight = 3.dp
// 时分滚轮整体高度固定，保证中间高亮带不会随着文字或列表内容发生抖动。
private val QuickEntryTimeWheelHeight = 156.dp
// 单个时间项高度固定，用于计算上下留白和视觉中心。
private val QuickEntryTimeWheelItemHeight = 44.dp
// 上下留白让第一项和最后一项也能滚动到中间高亮区域。
private val QuickEntryTimeWheelVerticalPadding = 56.dp

private val QUICK_EXPENSE_CATEGORIES = listOf(
    QuickEntryCategory("餐饮", AutobookIconAsset.CatFood),
    QuickEntryCategory("购物", AutobookIconAsset.CatShopping),
    QuickEntryCategory("日用", AutobookIconAsset.CatDaily),
    QuickEntryCategory("交通", AutobookIconAsset.CatTransport),
    QuickEntryCategory("蔬菜", AutobookIconAsset.CatVegetable),
    QuickEntryCategory("水果", AutobookIconAsset.CatFruit),
    QuickEntryCategory("零食", AutobookIconAsset.CatSnack),
    QuickEntryCategory("运动", AutobookIconAsset.CatSport),
    QuickEntryCategory("娱乐", AutobookIconAsset.CatFun),
    QuickEntryCategory("通讯", AutobookIconAsset.CatPhone),
    QuickEntryCategory("服饰", AutobookIconAsset.CatClothes),
    QuickEntryCategory("美容", AutobookIconAsset.CatBeauty),
    QuickEntryCategory("住房", AutobookIconAsset.CatHouse),
    QuickEntryCategory("居家", AutobookIconAsset.CatHome),
    QuickEntryCategory("孩子", AutobookIconAsset.CatChild),
    QuickEntryCategory("长辈", AutobookIconAsset.CatElder),
    QuickEntryCategory("社交", AutobookIconAsset.CatSocial),
    QuickEntryCategory("旅行", AutobookIconAsset.CatTravel),
    QuickEntryCategory("烟酒", AutobookIconAsset.CatWine),
    QuickEntryCategory("数码", AutobookIconAsset.CatDigital),
    QuickEntryCategory("汽车", AutobookIconAsset.CatCar),
    QuickEntryCategory("医疗", AutobookIconAsset.CatMedical),
    QuickEntryCategory("书籍", AutobookIconAsset.CatBook),
    QuickEntryCategory("学习", AutobookIconAsset.CatStudy),
    QuickEntryCategory("宠物", AutobookIconAsset.CatPet),
    QuickEntryCategory("礼金", AutobookIconAsset.CatCashGift),
    QuickEntryCategory("礼物", AutobookIconAsset.CatGift),
    QuickEntryCategory("办公", AutobookIconAsset.CatOffice),
    QuickEntryCategory("维修", AutobookIconAsset.CatRepair),
    QuickEntryCategory("捐赠", AutobookIconAsset.CatDonate),
    QuickEntryCategory("彩票", AutobookIconAsset.CatLottery),
    QuickEntryCategory("亲友", AutobookIconAsset.CatFamily),
    QuickEntryCategory("其他", AutobookIconAsset.CatOther),
)

private val QUICK_INCOME_CATEGORIES = listOf(
    QuickEntryCategory("工资", AutobookIconAsset.CatSalary),
    QuickEntryCategory("奖金", AutobookIconAsset.CatBonus),
    QuickEntryCategory("投资", AutobookIconAsset.CatInvest),
    QuickEntryCategory("兼职", AutobookIconAsset.CatPartTime),
    QuickEntryCategory("红包", AutobookIconAsset.CatRedPacket),
    QuickEntryCategory("报销", AutobookIconAsset.CatReimburse),
    QuickEntryCategory("退款", AutobookIconAsset.CatRefund),
    QuickEntryCategory("收款", AutobookIconAsset.CatReceive),
    QuickEntryCategory("其他", AutobookIconAsset.CatOther),
)

/**
 * 用途说明：
 * - 展示图片导入记账入口、导入提示、过滤摘要和多候选切换区域。
 * - 把云端分析建议与手动确认草稿统一收敛到同一块 UI，避免用户理解两套流程。
 *
 * 参数说明：
 * - state：编辑页当前状态，包含图片导入链路产生的候选、提示和过滤统计。
 * - onImportFromImage：触发选图与分析流程的回调。
 * - onSelectImageImportCandidate：切换当前候选结果的回调。
 */
@Composable
private fun ImageImportSection(
    state: EntryEditorUiState,
    onImportFromImage: () -> Unit,
    onSelectImageImportCandidate: (Int) -> Unit,
) {
    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("图片导入记账", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "上传图片后，会调用云端AI进行分析",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onImportFromImage,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("选择图片并生成建议")
            }
            state.importedImageRef?.let { imageRef ->
                Text(
                    text = "当前图片：$imageRef",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.imageImportMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.filteredOutCandidateCount > 0) {
                Card {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "本地过滤摘要",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = buildFilteredSummary(
                                filteredOutCount = state.filteredOutCandidateCount,
                                duplicateFilteredCount = state.duplicateFilteredCount,
                                inactiveFilteredCount = state.inactiveFilteredCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (state.imageImportCandidates.isNotEmpty()) {
                CandidateSelectorSection(
                    candidates = state.imageImportCandidates,
                    selectedIndex = state.selectedImageImportCandidateIndex,
                    onSelectCandidate = onSelectImageImportCandidate,
                )
            }
            state.imageImportTracePath?.let { tracePath ->
                Text(
                    text = "分析路径：$tracePath",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 用途说明：
 * - 在多候选图片识别结果之间切换，并展示当前候选的金额、对象、状态与证据。
 * - 这样可以把多模态建议与本地建议都纳入同一个确认流程，减少误记风险。
 *
 * 参数说明：
 * - candidates：当前可选的图片导入候选列表。
 * - selectedIndex：当前选中的候选下标。
 * - onSelectCandidate：点击某个候选后的切换回调。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateSelectorSection(
    candidates: List<ImageImportCandidate>,
    selectedIndex: Int?,
    onSelectCandidate: (Int) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("候选结果", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                candidates.forEachIndexed { index, candidate ->
                    FilterChip(
                        selected = selectedIndex == index,
                        onClick = { onSelectCandidate(index) },
                        label = {
                            Text(buildCandidateChipLabel(index = index, candidate = candidate))
                        },
                    )
                }
            }
            val selectedCandidate = selectedIndex?.let(candidates::getOrNull)
            if (selectedCandidate != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "当前候选：${buildCandidateDetail(selectedCandidate)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (selectedCandidate.stateReason.isNotBlank()) {
                        AssistChip(
                            onClick = {},
                            label = { Text("状态：${selectedCandidate.stateReason}") },
                        )
                    }
                    if (selectedCandidate.evidence.isNotBlank()) {
                        Text(
                            text = "证据：${selectedCandidate.evidence}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 用途说明：
 * - 快捷模板区负责帮助用户一键填入常用的方向、分类与备注组合。
 *
 * 参数说明：
 * - templates：可用模板列表。
 * - onApplyTemplate：点击某个模板后的回调。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateSection(
    templates: List<EntryTemplateUiModel>,
    onApplyTemplate: (String) -> Unit,
) {
    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("快捷模板", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "一键预填方向、分类和常用备注，不会覆盖金额和时间。",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                templates.forEach { template ->
                    SuggestionChip(
                        onClick = { onApplyTemplate(template.id) },
                        label = { Text(template.label) },
                    )
                }
            }
        }
    }
}

/** 将过滤统计拼成简洁可读的一句话。 */
private fun buildFilteredSummary(
    filteredOutCount: Int,
    duplicateFilteredCount: Int,
    inactiveFilteredCount: Int,
): String {
    val parts = buildList {
        add("共剔除 $filteredOutCount 条候选")
        if (duplicateFilteredCount > 0) {
            add("重复 $duplicateFilteredCount 条")
        }
        if (inactiveFilteredCount > 0) {
            add("已退还/已撤销 $inactiveFilteredCount 条")
        }
    }
    return parts.joinToString(separator = "，")
}

/** 生成候选筛选 chip 文案。 */
private fun buildCandidateChipLabel(
    index: Int,
    candidate: ImageImportCandidate,
): String {
    val amountLabel = candidate.amount?.let { "¥${"%.2f".format(it)}" } ?: "金额待确认"
    val categoryLabel = candidate.category.ifBlank { "待分类" }
    return "${index + 1}. $categoryLabel $amountLabel"
}

/** 生成候选详细说明。 */
private fun buildCandidateDetail(candidate: ImageImportCandidate): String {
    val directionLabel = when (candidate.direction) {
        MoneyDirection.INCOME -> "收入"
        MoneyDirection.EXPENSE -> "支出"
        MoneyDirection.UNKNOWN -> "方向待确认"
    }
    val confidenceLabel = "置信度 ${"%.2f".format(candidate.confidence.score)}"
    return listOfNotNull(
        candidate.category.takeIf { it.isNotBlank() },
        candidate.note.takeIf { it.isNotBlank() },
        directionLabel,
        confidenceLabel,
    ).joinToString(separator = " · ")
}
