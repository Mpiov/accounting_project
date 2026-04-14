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
import androidx.compose.foundation.layout.statusBarsPadding
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
 * - 账本主页面改成“先看月份总览，再进入月份详情”的结构。
 *
 * 参数说明：
 * - state：账本主页面聚合状态。
 * - onSelectYear：切换年份时的回调。
 * - onSelectMode：切换“月账单 / 年账单”时的回调。
 * - onOpenMonth：点击某个月份行后的回调。
 * - onSelectYearRow：点击某个年份行后的回调。
 * - modifier：外层修饰符，便于主导航壳层注入布局。
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "controls") {
            LedgerBoardControls(
                state = state,
                onSelectYear = onSelectYear,
                onSelectMode = onSelectMode,
                modifier = Modifier.statusBarsPadding(),
            )
        }
        item(key = "summary") {
            LedgerBoardSummaryCard(state = state)
        }
        item(key = "table") {
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
 * - onSelectYear：点击年份后的回调。
 * - onSelectMode：点击模式切换后的回调。
 * - modifier：外层修饰符，主要用于承接状态栏间距。
 */
@Composable
private fun LedgerBoardControls(
    state: LedgerBoardUiState,
    onSelectYear: (Int) -> Unit,
    onSelectMode: (LedgerBoardMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var yearMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.clickable { yearMenuExpanded = true },
            shape = AutobookUiTokens.chipShape,
            border = autobookCardBorder(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${state.selectedYear}年",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
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
        ) {
            state.availableYears.forEach { year ->
                DropdownMenuItem(
                    text = { Text("${year}年") },
                    onClick = {
                        yearMenuExpanded = false
                        onSelectYear(year)
                    },
                )
            }
        }
        LedgerBoardModeSwitch(
            selectedMode = state.mode,
            onSelectMode = onSelectMode,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            border = autobookCardBorder(),
            color = autobookSoftFillColor(),
        ) {
            Text(
                text = state.topRightLabel,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 用途说明：
 * - 中间分段按钮用于切换“月账单 / 年账单”。
 *
 * 参数说明：
 * - selectedMode：当前选中的账单模式。
 * - onSelectMode：切换模式时的回调。
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
                        .clip(if (mode == LedgerBoardMode.MONTH) {
                            RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                        } else {
                            RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
                        })
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
                    )
                }
            }
        }
    }
}

/**
 * 用途说明：
 * - 顶部摘要卡展示当前选中年份的收入、支出与结余。
 *
 * 参数说明：
 * - state：账本主页面聚合状态。
 */
@Composable
private fun LedgerBoardSummaryCard(
    state: LedgerBoardUiState,
) {
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
                    text = state.summaryTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C241C),
                )
                Text(
                    text = state.summaryNetLabel,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F1B16),
                )
                Text(
                    text = state.summaryHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF3F3429),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "年收入 ${state.summaryIncomeLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F1B16),
                    )
                    Text(
                        text = "年支出 ${state.summaryExpenseLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F1B16),
                    )
                }
            }
        }
    }
}

/**
 * 用途说明：
 * - 表头区对齐下方列表列名，让主账本页更像真正可浏览的账单表。
 *
 * 参数说明：
 * - mode：当前展示模式，用于切换第一列标题。
 */
@Composable
private fun LedgerBoardTableHeader(
    mode: LedgerBoardMode,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LedgerTableHeaderCell(
            text = if (mode == LedgerBoardMode.MONTH) "月份" else "年份",
            modifier = Modifier.weight(1.2f),
            align = TextAlign.Start,
        )
        LedgerTableHeaderCell(text = if (mode == LedgerBoardMode.MONTH) "月收入" else "年收入", modifier = Modifier.weight(1f))
        LedgerTableHeaderCell(text = if (mode == LedgerBoardMode.MONTH) "月支出" else "年支出", modifier = Modifier.weight(1f))
        LedgerTableHeaderCell(text = if (mode == LedgerBoardMode.MONTH) "月结余" else "年结余", modifier = Modifier.weight(1f))
        Box(modifier = Modifier.width(20.dp))
    }
}

/**
 * 用途说明：
 * - 表头单元格统一样式。
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
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = AutobookUiTokens.compactPanelShape,
        border = autobookCardBorder(emphasized = item.isCurrentMonth),
        color = if (item.isCurrentMonth) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Column {
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
                    if (item.isCurrentMonth) {
                        Text(
                            text = "本月",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = item.entryCountLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
}

/**
 * 用途说明：
 * - 年账单列表行，用于在“年账单”模式下快速比较不同年份。
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
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = AutobookUiTokens.compactPanelShape,
        border = autobookCardBorder(emphasized = item.selected),
        color = if (item.selected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f)
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Column {
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
                            color = if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

/**
 * 用途说明：
 * - 表格中的金额值单元格，统一做居中对齐和重点颜色处理。
 *
 * 参数说明：
 * - text：展示文本。
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
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = ledgerBoardValueFontSize(text)),
        fontWeight = FontWeight.Bold,
        color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 用途说明：
 * - 当账本总览表格中的金额文本较长时，轻微缩小字号，减少列宽不够时的挤压感。
 *
 * 参数说明：
 * - text：当前金额文本。
 */
private fun ledgerBoardValueFontSize(text: String) = when {
    text.length >= 14 -> 11.sp
    text.length >= 11 -> 12.sp
    else -> 14.sp
}
