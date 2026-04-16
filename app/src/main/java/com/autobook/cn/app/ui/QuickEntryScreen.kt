package com.autobook.cn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autobook.cn.app.viewmodel.QuickEntryCategoryUiModel
import com.autobook.cn.app.viewmodel.QuickEntryUiState
import com.autobook.cn.core.model.MoneyDirection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 用途说明：
 * - 极速记账页的正式交互版页面。
 * - 页面结构改成“顶部方向切换 + 中部分类宫格 + 底部金额与自定义键盘”，更接近 3 秒录入的目标路径。
 *
 * 参数说明：
 * - state：极速记账页当前状态。
 * - onBack：取消并返回上一级页面。
 * - onDirectionChange：切换收支方向。
 * - onCategorySelect：选择分类。
 * - onAppendDigit：追加一个金额数字。
 * - onAppendDoubleZero：快速追加两个零。
 * - onAppendDecimalPoint：追加小数点。
 * - onDeleteLastAmountCharacter：删除金额最后一个字符。
 * - onClearAmountInput：清空当前金额。
 * - onSetToday：把交易时间设置成当前时间。
 * - onNoteChange：修改备注。
 * - onSave：保存当前极速记账草稿。
 * - modifier：外层修饰符。
 */
@Composable
fun QuickEntryScreen(
    state: QuickEntryUiState,
    onBack: () -> Unit,
    onDirectionChange: (MoneyDirection) -> Unit,
    onCategorySelect: (String) -> Unit,
    onAppendDigit: (Int) -> Unit,
    onAppendDoubleZero: () -> Unit,
    onAppendDecimalPoint: () -> Unit,
    onDeleteLastAmountCharacter: () -> Unit,
    onClearAmountInput: () -> Unit,
    onSetToday: () -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            QuickEntryHeader(
                direction = state.direction,
                onDirectionChange = onDirectionChange,
                onBack = onBack,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                QuickEntryCategoryGrid(
                    categories = state.categories,
                    selectedCategoryId = state.selectedCategoryId,
                    onCategorySelect = onCategorySelect,
                )
            }
            QuickEntryAmountPanel(
                state = state,
                onNoteChange = onNoteChange,
            )
            QuickEntryKeypad(
                enabled = state.isAmountInputActive,
                onAppendDigit = onAppendDigit,
                onAppendDoubleZero = onAppendDoubleZero,
                onAppendDecimalPoint = onAppendDecimalPoint,
                onDeleteLastAmountCharacter = onDeleteLastAmountCharacter,
                onClearAmountInput = onClearAmountInput,
                onSetToday = onSetToday,
                onSave = onSave,
            )
        }
    }
}

/**
 * 用途说明：
 * - 极速记账页顶部使用暖黄色条，强调“高频入口”的轻快感，同时承载方向切换和取消动作。
 *
 * 参数说明：
 * - direction：当前收支方向。
 * - onDirectionChange：点击方向切换后的回调。
 * - onBack：取消并返回。
 */
@Composable
private fun QuickEntryHeader(
    direction: MoneyDirection,
    onDirectionChange: (MoneyDirection) -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        quickEntryHeaderTopColor,
                        quickEntryHeaderBottomColor,
                    ),
                ),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(72.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
            ) {
                QuickEntryDirectionTab(
                    label = "支出",
                    selected = direction == MoneyDirection.EXPENSE,
                    onClick = { onDirectionChange(MoneyDirection.EXPENSE) },
                )
                Spacer(modifier = Modifier.width(24.dp))
                QuickEntryDirectionTab(
                    label = "收入",
                    selected = direction == MoneyDirection.INCOME,
                    onClick = { onDirectionChange(MoneyDirection.INCOME) },
                )
            }
            TextButton(
                onClick = onBack,
                modifier = Modifier.width(72.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.titleMedium,
                    color = quickEntryHeaderTextColor,
                )
            }
        }
    }
}

/**
 * 用途说明：
 * - 顶部方向标签使用“文本 + 下划线”的方式，避免看起来像普通筛选芯片。
 *
 * 参数说明：
 * - label：方向标签文案。
 * - selected：是否为当前选中方向。
 * - onClick：点击后的切换回调。
 */
@Composable
private fun QuickEntryDirectionTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = quickEntryHeaderTextColor.copy(alpha = if (selected) 1f else 0.76f),
        )
        Box(
            modifier = Modifier
                .height(3.dp)
                .width(38.dp)
                .clip(CircleShape)
                .background(
                    color = if (selected) {
                        quickEntryHeaderTextColor
                    } else {
                        Color.Transparent
                    },
                ),
        )
    }
}

/**
 * 用途说明：
 * - 分类区使用固定 4 列宫格，并让每个分类卡保持接近正方形，便于用户快速扫视和点按。
 *
 * 参数说明：
 * - categories：当前方向下可选分类。
 * - selectedCategoryId：当前选中的分类 id。
 * - onCategorySelect：点击分类后的回调。
 */
@Composable
private fun QuickEntryCategoryGrid(
    categories: List<QuickEntryCategoryUiModel>,
    selectedCategoryId: String?,
    onCategorySelect: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(categories, key = { category -> category.id }) { category ->
            QuickEntryCategoryItem(
                category = category,
                selected = category.id == selectedCategoryId,
                onClick = { onCategorySelect(category.id) },
            )
        }
    }
}

/**
 * 用途说明：
 * - 单个分类项采用“圆形徽章 + 标签”的结构。
 * - 当前阶段先用分类首字做圆形徽章，后续如果补图标资产，可以直接替换圆心内容而不改整体布局。
 *
 * 参数说明：
 * - category：当前分类项。
 * - selected：是否处于选中态。
 * - onClick：点击后的回调。
 */
@Composable
private fun QuickEntryCategoryItem(
    category: QuickEntryCategoryUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .clip(AutobookUiTokens.compactPanelShape)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(
                    color = if (selected) {
                        quickEntrySelectedCategoryFillColor
                    } else {
                        quickEntryCategoryFillColor
                    },
                )
                .border(
                    width = 1.dp,
                    color = if (selected) {
                        quickEntrySelectedCategoryBorderColor
                    } else {
                        Color.Transparent
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = category.label.take(1),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) quickEntrySelectedCategoryTextColor else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = category.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 用途说明：
 * - 金额面板放在分类区和数字键盘之间，集中展示已选分类、当前金额、备注和时间提示。
 *
 * 参数说明：
 * - state：极速记账页当前状态。
 * - onNoteChange：备注输入回调。
 */
@Composable
private fun QuickEntryAmountPanel(
    state: QuickEntryUiState,
    onNoteChange: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = autobookCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = state.categories.firstOrNull { it.id == state.selectedCategoryId }?.label ?: "请选择分类",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = quickEntryDateLabel(state.entryTimestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (state.amountInput.isBlank()) "0.00" else state.amountInput,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注") },
                placeholder = { Text("点击填写备注") },
                singleLine = true,
            )
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * 用途说明：
 * - 自定义数字键盘固定在页面底部，承担金额输入的主要操作。
 * - 当前用 4 列布局，把常用动作放到最右侧，减少用户视线跳转。
 *
 * 参数说明：
 * - enabled：当前是否允许输入金额；未选分类时先禁用，提醒用户先点分类。
 * - onAppendDigit：输入单个数字。
 * - onAppendDoubleZero：快速输入两个零。
 * - onAppendDecimalPoint：输入小数点。
 * - onDeleteLastAmountCharacter：删除最后一个字符。
 * - onClearAmountInput：清空金额。
 * - onSetToday：把时间重置成现在。
 * - onSave：完成并保存。
 */
@Composable
private fun QuickEntryKeypad(
    enabled: Boolean,
    onAppendDigit: (Int) -> Unit,
    onAppendDoubleZero: () -> Unit,
    onAppendDecimalPoint: () -> Unit,
    onDeleteLastAmountCharacter: () -> Unit,
    onClearAmountInput: () -> Unit,
    onSetToday: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = autobookCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickEntryKeypadRow(
                enabled = enabled,
                keys = listOf(
                    QuickEntryKeySpec(label = "7", onClick = { onAppendDigit(7) }),
                    QuickEntryKeySpec(label = "8", onClick = { onAppendDigit(8) }),
                    QuickEntryKeySpec(label = "9", onClick = { onAppendDigit(9) }),
                    QuickEntryKeySpec(label = "今天", onClick = onSetToday),
                ),
            )
            QuickEntryKeypadRow(
                enabled = enabled,
                keys = listOf(
                    QuickEntryKeySpec(label = "4", onClick = { onAppendDigit(4) }),
                    QuickEntryKeySpec(label = "5", onClick = { onAppendDigit(5) }),
                    QuickEntryKeySpec(label = "6", onClick = { onAppendDigit(6) }),
                    QuickEntryKeySpec(label = "删除", onClick = onDeleteLastAmountCharacter),
                ),
            )
            QuickEntryKeypadRow(
                enabled = enabled,
                keys = listOf(
                    QuickEntryKeySpec(label = "1", onClick = { onAppendDigit(1) }),
                    QuickEntryKeySpec(label = "2", onClick = { onAppendDigit(2) }),
                    QuickEntryKeySpec(label = "3", onClick = { onAppendDigit(3) }),
                    QuickEntryKeySpec(label = "清空", onClick = onClearAmountInput),
                ),
            )
            QuickEntryKeypadRow(
                enabled = enabled,
                keys = listOf(
                    QuickEntryKeySpec(label = ".", onClick = onAppendDecimalPoint),
                    QuickEntryKeySpec(label = "0", onClick = { onAppendDigit(0) }),
                    QuickEntryKeySpec(label = "00", onClick = onAppendDoubleZero),
                    QuickEntryKeySpec(label = "完成", onClick = onSave, accent = true),
                ),
            )
            if (!enabled) {
                Text(
                    text = "先选择一个分类，再开始输入金额。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }
        }
    }
}

/**
 * 用途说明：
 * - 一行数字键盘按键的统一布局。
 *
 * 参数说明：
 * - enabled：这一行按键是否可用。
 * - keys：本行按键定义。
 */
@Composable
private fun QuickEntryKeypadRow(
    enabled: Boolean,
    keys: List<QuickEntryKeySpec>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        keys.forEach { key ->
            Button(
                onClick = key.onClick,
                enabled = enabled || key.accent,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                shape = AutobookUiTokens.chipShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (key.accent) quickEntryAccentKeyColor else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (key.accent) quickEntryAccentKeyTextColor else MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = if (key.accent) quickEntryAccentKeyColor.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    disabledContentColor = if (key.accent) quickEntryAccentKeyTextColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                ),
            ) {
                Text(
                    text = key.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (key.accent) FontWeight.Bold else FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * 用途说明：
 * - 描述单个数字键盘按键。
 *
 * 参数说明：
 * - label：按键文案。
 * - onClick：点击回调。
 * - accent：是否为强调按键，例如“完成”。
 */
private data class QuickEntryKeySpec(
    val label: String,
    val onClick: () -> Unit,
    val accent: Boolean = false,
)

/**
 * 用途说明：
 * - 把时间戳格式化成极速记账页的轻量日期文案。
 *
 * 参数说明：
 * - timestamp：待展示的交易时间戳。
 */
private fun quickEntryDateLabel(timestamp: Long): String {
    return quickEntryDateFormatter.format(
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime(),
    )
}

private val quickEntryDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 极速记账页顶部暖黄渐变的起始色。 */
private val quickEntryHeaderTopColor = Color(0xFFF4D96B)

/** 极速记账页顶部暖黄渐变的结束色。 */
private val quickEntryHeaderBottomColor = Color(0xFFE6BF4A)

/** 顶部标签和取消按钮的统一文字色。 */
private val quickEntryHeaderTextColor = Color(0xFF322418)

/** 未选中分类时的柔和圆形底色。 */
private val quickEntryCategoryFillColor = Color(0xFFF7F5EF)

/** 选中分类时的高亮底色，用暖黄而不是纯色强调。 */
private val quickEntrySelectedCategoryFillColor = Color(0xFFF4D96B)

/** 选中分类时的边框色，略深于底色以提升选中态识别。 */
private val quickEntrySelectedCategoryBorderColor = Color(0xFFDEB645)

/** 选中分类时的文字色。 */
private val quickEntrySelectedCategoryTextColor = Color(0xFF3A2814)

/** 底部“完成”键的强调色。 */
private val quickEntryAccentKeyColor = Color(0xFFEAC858)

/** 底部“完成”键的文字色。 */
private val quickEntryAccentKeyTextColor = Color(0xFF2E2215)
