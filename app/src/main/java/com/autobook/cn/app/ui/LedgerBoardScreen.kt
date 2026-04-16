package com.autobook.cn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autobook.cn.app.viewmodel.LedgerBoardMode
import com.autobook.cn.app.viewmodel.LedgerBoardMonthRowUiModel
import com.autobook.cn.app.viewmodel.LedgerBoardUiState
import com.autobook.cn.app.viewmodel.LedgerBoardYearRowUiModel
import java.time.YearMonth

/**
 * 用途说明：
 * - 账本主页面采用“先看总览，再进入月份详情”的结构。
 * - 页面首屏负责展示年份切换、月账单/年账单切换、黄色汇总卡和下方汇总列表。
 *
 * 参数说明：
 * - state：账本主页面聚合状态。
 * - onSelectYear：切换年份时的回调。
 * - onSelectMode：切换月账单或年账单时的回调。
 * - onOpenMonth：点击某个月份行后打开月份详情页的回调。
 * - onSelectYearRow：点击某个年份行后的回调。
 * - modifier：外层修饰符，便于主导航容器注入布局约束。
 */
@Composable
fun LedgerBoardScreen(
    state: LedgerBoardUiState,
    onSelectYear: (Int) -> Unit,
    onSelectMode: (LedgerBoardMode) -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
    onSelectYearRow: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "controls") {
            LedgerBoardControls(
                state = state,
                onSelectYear = onSelectYear,
                onSelectMode = onSelectMode,
                modifier = Modifier.padding(top = mainPageTopInset()),
            )
        }
        item(key = "summary") {
            LedgerBoardSummaryCard(state = state)
        }
        item(key = "table_header") {
            LedgerBoardTableHeader(mode = state.mode)
        }
        if (state.mode == LedgerBoardMode.MONTH) {
            items(items = state.monthRows, key = { item -> item.yearMonth.toString() }) { item ->
                LedgerMonthRowCard(
                    item = item,
                    onClick = { onOpenMonth(item.yearMonth) },
                )
            }
        } else {
            items(items = state.yearRows, key = { item -> item.year }) { item ->
                LedgerYearRowCard(
                    item = item,
                    onClick = { onSelectYearRow(item.year) },
                )
            }
        }
    }
}

/**
 * 用途说明：
 * - 顶部控制区负责切换年份与账单模式。
 *
 * 参数说明：
 * - state：账本主页面聚合状态。
 * - onSelectYear：选择年份后的回调。
 * - onSelectMode：选择月账单/年账单后的回调。
 * - modifier：外层修饰符，主要用于承接主页面顶部安全间距。
 */
@Composable
private fun LedgerBoardControls(
    state: LedgerBoardUiState,
    onSelectYear: (Int) -> Unit,
    onSelectMode: (LedgerBoardMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var yearMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val yearSelectorShape = AutobookUiTokens.chipShape

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(ledgerBoardYearSelectorWidth)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .autobookClippedClickable(shape = yearSelectorShape) { yearMenuExpanded = true },
                shape = yearSelectorShape,
                border = autobookCardBorder(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    /**
                     * 年份选择器统一显示“年份 + 年”。
                     *
                     * 用途说明：
                     * - 按当前交互要求，左上角年份需要明确显示成“2026年”这种完整文案。
                     * - 为避免带上“年”后出现拥挤换行，配合下方固定宽度和单行限制一起使用。
                     */
                    Text(
                        text = "${state.selectedYear}年",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "选择年份",
                    )
                }
            }
            DropdownMenu(
                expanded = yearMenuExpanded,
                onDismissRequest = { yearMenuExpanded = false },
                modifier = Modifier.width(ledgerBoardYearSelectorWidth),
                shape = AutobookUiTokens.compactPanelShape,
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 10.dp,
                border = autobookCardBorder(),
            ) {
                state.availableYears.forEach { year ->
                    val selected = year == state.selectedYear
                    DropdownMenuItem(
                        text = {
                            Text(
                                // 下拉项同样带“年”字，并通过更宽的固定菜单避免文字挤压换行。
                                text = "${year}年",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                            )
                        },
                        trailingIcon = {
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape,
                                        ),
                                )
                            }
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            trailingIconColor = MaterialTheme.colorScheme.primary,
                        ),
                        onClick = {
                            yearMenuExpanded = false
                            onSelectYear(year)
                        },
                    )
                }
            }
        }
        LedgerBoardModeSwitch(
            selectedMode = state.mode,
            onSelectMode = onSelectMode,
            modifier = Modifier.weight(1f),
        )
        Surface(
            modifier = Modifier.width(ledgerBoardTopRightBadgeWidth),
            shape = RoundedCornerShape(20.dp),
            border = autobookCardBorder(),
            color = autobookSoftFillColor(),
        ) {
            Text(
                text = state.topRightLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * 用途说明：
 * - 中间分段按钮用于切换月账单与年账单视角。
 *
 * 参数说明：
 * - selectedMode：当前选中的账单模式。
 * - onSelectMode：切换账单模式时的回调。
 * - modifier：外层修饰符，便于父布局分配宽度。
 */
@Composable
private fun LedgerBoardModeSwitch(
    selectedMode: LedgerBoardMode,
    onSelectMode: (LedgerBoardMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        border = autobookCardBorder(emphasized = true),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            LedgerBoardMode.entries.forEach { mode ->
                val selected = mode == selectedMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(
                            if (mode == LedgerBoardMode.MONTH) {
                                RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                            } else {
                                RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
                            },
                        )
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        )
                        .clickable { onSelectMode(mode) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (mode == LedgerBoardMode.MONTH) "月账单" else "年账单",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 用途说明：
 * - 黄色摘要卡展示当前账本模式下的核心汇总数据。
 * - 月账单模式使用“年结余 / 年收入 / 年支出”，年账单模式使用“总结余 / 总收入 / 总支出”。
 *
 * 参数说明：
 * - state：账本主页面聚合状态。
 */
@Composable
private fun LedgerBoardSummaryCard(
    state: LedgerBoardUiState,
) {
    val summaryPrefix = if (state.mode == LedgerBoardMode.MONTH) "年" else "总"
    val warmBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFD347),
            Color(0xFFFFE27A),
        ),
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(warmBrush)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                text = "¥",
                modifier = Modifier.align(Alignment.CenterEnd),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.28f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${summaryPrefix}结余",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C241C),
                )
                Text(
                    text = state.summaryNetLabel,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = ledgerBoardSummaryFontSize(state.summaryNetLabel),
                    ),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F1B16),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    /**
                     * 汇总卡里的收入/支出使用统一语义色。
                     *
                     * 用途说明：
                     * - 用户需要快速区分收入和支出，因此这里直接复用全局语义色。
                     * - 结余仍然保留在大卡中心，收入/支出只负责辅助阅读，不改变原有布局层级。
                     */
                    Text(
                        text = "${summaryPrefix}收入 ${state.summaryIncomeLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ledgerBoardIncomeColor,
                    )
                    Text(
                        text = "${summaryPrefix}支出 ${state.summaryExpenseLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ledgerBoardExpenseColor,
                    )
                }
            }
        }
    }
}

/**
 * 用途说明：
 * - 表头区域对齐下方各列，顺手加一个浅色底条，让表格结构更清楚。
 *
 * 参数说明：
 * - mode：当前账单模式，用于切换第一列与各金额列标题。
 */
@Composable
private fun LedgerBoardTableHeader(
    mode: LedgerBoardMode,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = autobookSoftFillColor(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LedgerTableHeaderCell(
                text = if (mode == LedgerBoardMode.MONTH) "月份" else "年份",
                modifier = Modifier.weight(1.2f),
                align = TextAlign.Start,
            )
            LedgerTableHeaderCell(
                text = if (mode == LedgerBoardMode.MONTH) "月收入" else "年收入",
                modifier = Modifier.weight(1f),
            )
            LedgerTableHeaderCell(
                text = if (mode == LedgerBoardMode.MONTH) "月支出" else "年支出",
                modifier = Modifier.weight(1f),
            )
            LedgerTableHeaderCell(
                text = if (mode == LedgerBoardMode.MONTH) "月结余" else "年结余",
                modifier = Modifier.weight(1f),
            )
            Box(modifier = Modifier.width(20.dp))
        }
    }
}

/**
 * 用途说明：
 * - 表头单元格统一样式，保持列标题对齐。
 *
 * 参数说明：
 * - text：列标题文案。
 * - modifier：外层修饰符，主要用于宽度分配。
 * - align：文本对齐方式。
 */
@Composable
private fun LedgerTableHeaderCell(
    text: String,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Center,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
    )
}

/**
 * 用途说明：
 * - 月账单列表行，点击后进入对应月份详情页。
 *
 * 参数说明：
 * - item：当前月份行模型。
 * - onClick：点击当前月份行后的回调。
 */
@Composable
private fun LedgerMonthRowCard(
    item: LedgerBoardMonthRowUiModel,
    onClick: () -> Unit,
) {
    val rowShape = AutobookUiTokens.compactPanelShape
    Surface(
        modifier = Modifier.autobookClippedClickable(shape = rowShape, onClick = onClick),
        shape = rowShape,
        border = autobookCardBorder(emphasized = item.isCurrentMonth),
        color = if (item.isCurrentMonth) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1.2f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.monthLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (item.isCurrentMonth) "本月" else item.entryCountLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LedgerTableValueCell(text = item.incomeLabel, modifier = Modifier.weight(1f))
            LedgerTableValueCell(text = item.expenseLabel, modifier = Modifier.weight(1f))
            LedgerTableValueCell(
                text = item.netLabel,
                modifier = Modifier.weight(1f),
                highlight = item.netLabel.startsWith("+"),
            )
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = "打开月份详情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 用途说明：
 * - 年账单列表行，用于在年账单模式下快速比较不同年份。
 *
 * 参数说明：
 * - item：当前年份行模型。
 * - onClick：点击当前年份行后的回调。
 */
@Composable
private fun LedgerYearRowCard(
    item: LedgerBoardYearRowUiModel,
    onClick: () -> Unit,
) {
    val rowShape = AutobookUiTokens.compactPanelShape
    Surface(
        modifier = Modifier.autobookClippedClickable(shape = rowShape, onClick = onClick),
        shape = rowShape,
        border = autobookCardBorder(emphasized = item.selected),
        color = if (item.selected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f)
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1.2f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${item.year}年",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = item.entryCountLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LedgerTableValueCell(text = item.incomeLabel, modifier = Modifier.weight(1f))
            LedgerTableValueCell(text = item.expenseLabel, modifier = Modifier.weight(1f))
            LedgerTableValueCell(
                text = item.netLabel,
                modifier = Modifier.weight(1f),
                highlight = item.netLabel.startsWith("+"),
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (item.selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/**
 * 用途说明：
 * - 表格金额值统一使用居中样式。
 * - 当金额很长时允许两行展示，并轻微缩小字号，尽量避免省略号截断核心数字。
 *
 * 参数说明：
 * - text：当前金额文本。
 * - modifier：外层修饰符，便于父布局分配宽度。
 * - highlight：是否需要高亮显示。
 */
@Composable
private fun LedgerTableValueCell(
    text: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.titleSmall.copy(fontSize = ledgerBoardValueFontSize(text)),
        fontWeight = FontWeight.Bold,
        color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Clip,
    )
}

/**
 * 用途说明：
 * - 当账本表格金额较长时轻微缩小字号，减少列宽被大数字撑爆的情况。
 *
 * 参数说明：
 * - text：当前金额文本。
 */
private fun ledgerBoardValueFontSize(text: String) = when {
    text.length >= 18 -> 10.sp
    text.length >= 14 -> 11.sp
    text.length >= 11 -> 12.sp
    else -> 14.sp
}

/**
 * 用途说明：
 * - 当黄色汇总卡中的净额很长时轻微缩小字号，保证大金额也能尽量完整显示。
 *
 * 参数说明：
 * - text：当前净额文本。
 */
private fun ledgerBoardSummaryFontSize(text: String) = when {
    text.length >= 18 -> 17.sp
    text.length >= 14 -> 20.sp
    else -> 24.sp
}

/**
 * 年份选择器固定宽度。
 *
 * 用途说明：
 * - 现在按钮与下拉项都会显示“2026年”这类完整文案，因此适当放宽宽度。
 * - 固定宽度可以继续避免顶部控件区在不同年份之间左右抖动，同时减少换行风险。
 */
private val ledgerBoardYearSelectorWidth = 112.dp

/**
 * 右上角统计 badge 固定宽度。
 *
 * 用途说明：
 * - 统一容纳“共 x 个月”和“近 x 年”两类文案，防止月/年切换按钮随着右侧文本长度发生伸缩。
 */
private val ledgerBoardTopRightBadgeWidth = 92.dp

/** 黄色汇总卡里的收入色。 */
private val ledgerBoardIncomeColor = Color(0xFFD1495B)

/** 黄色汇总卡里的支出色。 */
private val ledgerBoardExpenseColor = Color(0xFF2E8B57)
