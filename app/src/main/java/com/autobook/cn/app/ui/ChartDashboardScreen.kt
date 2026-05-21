package com.autobook.cn.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autobook.cn.app.viewmodel.ChartEntryUiModel
import com.autobook.cn.app.viewmodel.ChartUiState
import com.autobook.cn.core.common.CompactAmountFormatter
import com.autobook.cn.core.common.CurrencyFormatter
import com.autobook.cn.core.model.MoneyDirection
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 用途说明：
 * - 图表页集中展示收入、支出和结余的趋势与分类占比。
 *
 * 参数说明：
 * - state：图表页的数据源。
 * - modifier：外层修饰符，便于承接导航容器的布局约束。
 */
@Composable
fun ChartDashboardScreen(
    state: ChartUiState,
    onOpenCategoryRecords: (ChartCategoryDrilldownRequest) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedDirection by rememberSaveable { mutableStateOf(ChartDirection.EXPENSE) }
    var selectedPeriod by rememberSaveable { mutableStateOf(ChartPeriod.DAY) }

    val filteredEntries = state.entries.filter { entry ->
        when (selectedDirection) {
            ChartDirection.EXPENSE -> entry.direction == MoneyDirection.EXPENSE
            ChartDirection.INCOME -> entry.direction == MoneyDirection.INCOME
            ChartDirection.NET -> true
        }
    }
    val trendPoints = buildChartPoints(
        entries = filteredEntries,
        period = selectedPeriod,
        direction = selectedDirection,
    )
    val categoryBreakdown = buildChartCategories(
        entries = filteredEntries.filterByCurrentChartPeriod(selectedPeriod),
        direction = selectedDirection,
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "chart_header") {
            ChartHeaderCard(
                selectedDirection = selectedDirection,
                onDirectionSelected = { direction -> selectedDirection = direction },
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { period -> selectedPeriod = period },
                modifier = Modifier.padding(top = mainPageTopInset()),
            )
        }
        item(key = "chart_trend") {
            TrendChartCard(
                selectedDirection = selectedDirection,
                selectedPeriod = selectedPeriod,
                points = trendPoints,
            )
        }
        item(key = "chart_category") {
            CategoryDonutCard(
                selectedDirection = selectedDirection,
                categories = categoryBreakdown,
                onCategoryClick = { category ->
                    category.toDrilldownRequest(
                        selectedDirection = selectedDirection,
                        selectedPeriod = selectedPeriod,
                    )?.let(onOpenCategoryRecords)
                },
            )
        }
    }
}

/**
 * 用途说明：
 * - 图表页点击某个分类后，向导航层传递的下钻请求。
 *
 * 参数说明：
 * - direction：目标记录的真实收支方向；结余模式会被转换为收入或支出。
 * - period：目标记录的统计周期，和图表页当前选中的周期保持一致。
 * - category：目标分类；为空时表示查看当前方向在本周期内的全部记录。
 */
data class ChartCategoryDrilldownRequest(
    val direction: MoneyDirection,
    val period: String,
    val category: String,
)

/**
 * 用途说明：
 * - 顶部筛选卡承接“方向”和“周期”两组切换项，减少图表页顶部的杂乱感。
 *
 * 参数说明：
 * - selectedDirection：当前选中的金额方向。
 * - onDirectionSelected：切换金额方向后的回调。
 * - selectedPeriod：当前选中的周期。
 * - onPeriodSelected：切换周期后的回调。
 * - modifier：外层修饰符，主要用于承接状态栏间距。
 */
@Composable
private fun ChartHeaderCard(
    selectedDirection: ChartDirection,
    onDirectionSelected: (ChartDirection) -> Unit,
    selectedPeriod: ChartPeriod,
    onPeriodSelected: (ChartPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "图表",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartDirection.entries.forEach { direction ->
                    FilterChip(
                        selected = selectedDirection == direction,
                        onClick = { onDirectionSelected(direction) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        label = { Text(direction.label) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { onPeriodSelected(period) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        label = { Text(period.label) },
                    )
                }
            }
        }
    }
}

/**
 * 用途说明：
 * - 趋势卡负责展示总额、平均值以及带左侧金额坐标的柱状趋势图。
 *
 * 参数说明：
 * - selectedDirection：当前金额方向。
 * - selectedPeriod：当前周期。
 * - points：按当前筛选条件计算出的趋势点。
 */
@Composable
private fun TrendChartCard(
    selectedDirection: ChartDirection,
    selectedPeriod: ChartPeriod,
    points: List<ChartTrendPoint>,
) {
    val totalAmount = points.sumOf { it.amount }
    val averageAmount = if (points.isEmpty()) 0.0 else totalAmount / points.size
    val scale = buildTrendScale(points, selectedDirection)

    Card(
        shape = AutobookUiTokens.panelShape,
        border = autobookCardBorder(),
        colors = autobookCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "${selectedDirection.label}${selectedPeriod.label}趋势",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "总额 ${formatChartDetailAmount(totalAmount)} · 平均 ${formatChartDetailAmount(averageAmount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TrendAmountAxis(
                    scale = scale,
                    modifier = Modifier
                        .width(72.dp)
                        .height(168.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TrendBarCanvas(
                        selectedDirection = selectedDirection,
                        points = points,
                        scale = scale,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        points.forEach { point ->
                            Text(
                                text = point.label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 用途说明：
 * - 左侧金额坐标区提供上中下三个金额刻度，帮助用户快速判断柱状图高度对应的金额。
 *
 * 参数说明：
 * - scale：当前趋势图的纵轴范围。
 * - modifier：外层修饰符，用于限定坐标区尺寸。
 */
@Composable
private fun TrendAmountAxis(
    scale: ChartTrendScale,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = formatChartAxisAmount(scale.upperBound),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatChartAxisAmount(scale.middleValue),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatChartAxisAmount(scale.lowerBound),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 用途说明：
 * - 柱状图画布负责绘制刻度线、零轴和当前各周期的金额柱。
 *
 * 参数说明：
 * - selectedDirection：当前金额方向，用于决定柱子的主色。
 * - points：趋势点列表。
 * - scale：纵轴范围。
 * - modifier：外层修饰符，用于限制画布尺寸。
 */
@Composable
private fun TrendBarCanvas(
    selectedDirection: ChartDirection,
    points: List<ChartTrendPoint>,
    scale: ChartTrendScale,
    modifier: Modifier = Modifier,
) {
    val guideColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
    val zeroLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.70f)
    val incomeBarColor = chartIncomeColor
    val expenseBarColor = chartExpenseColor

    Canvas(modifier = modifier) {
        val widthPerPoint = if (points.isEmpty()) size.width else size.width / points.size
        val barWidth = min(widthPerPoint * 0.48f, 28.dp.toPx())
        val topY = scale.toY(scale.upperBound, size.height)
        val middleY = scale.toY(scale.middleValue, size.height)
        val bottomY = scale.toY(scale.lowerBound, size.height)
        val zeroY = scale.toY(0.0, size.height)

        listOf(topY, middleY, bottomY).forEach { y ->
            drawLine(
                color = guideColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // 结余模式可能同时出现正负值，需要额外强调零轴位置，帮助用户判断盈亏分界。
        if (scale.lowerBound < 0.0 && scale.upperBound > 0.0) {
            drawLine(
                color = zeroLineColor,
                start = Offset(0f, zeroY),
                end = Offset(size.width, zeroY),
                strokeWidth = 1.5.dp.toPx(),
            )
        }

        points.forEachIndexed { index, point ->
            val centerX = widthPerPoint * index + widthPerPoint / 2f
            val valueY = scale.toY(point.amount, size.height)
            val top = min(zeroY, valueY)
            val height = max(abs(zeroY - valueY), 6.dp.toPx())
            val color = when (selectedDirection) {
                ChartDirection.INCOME -> incomeBarColor
                ChartDirection.EXPENSE -> expenseBarColor
                ChartDirection.NET -> {
                    // 结余模式继续用收入/支出色区分正负，用户扫一眼就能看出盈亏方向。
                    if (point.amount >= 0) incomeBarColor else expenseBarColor
                }
            }.copy(alpha = if (point.isCurrent) 1f else 0.48f)

            drawRoundRect(
                color = color,
                topLeft = Offset(centerX - barWidth / 2f, if (point.amount >= 0) top else zeroY),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(x = 8.dp.toPx(), y = 8.dp.toPx()),
            )
        }
    }
}

/**
 * 用途说明：
 * - 分类卡展示当前筛选条件下的分类占比以及分类列表。
 *
 * 参数说明：
 * - selectedDirection：当前金额方向。
 * - categories：分类统计结果。
 */
@Composable
private fun CategoryDonutCard(
    selectedDirection: ChartDirection,
    categories: List<ChartCategoryItem>,
    onCategoryClick: (ChartCategoryItem) -> Unit,
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
            Text(
                text = "${selectedDirection.label}分类",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (categories.isEmpty()) {
                Text(
                    text = "当前范围没有可展示的数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryDonutChart(
                        categories = categories,
                        modifier = Modifier.size(120.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        categories.take(5).forEach { item ->
                            CategoryLegendRow(
                                item = item,
                                onClick = { onCategoryClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 用途说明：
 * - 环形图用来概览分类占比，中心留白让整体视觉更轻。
 *
 * 参数说明：
 * - categories：分类统计结果。
 * - modifier：外层修饰符，用于限制环形图尺寸。
 */
@Composable
private fun CategoryDonutChart(
    categories: List<ChartCategoryItem>,
    modifier: Modifier = Modifier,
) {
    val innerColor = MaterialTheme.colorScheme.surface

    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.18f
        var startAngle = -90f
        categories.forEach { category ->
            val sweepAngle = 360f * category.ratio
            drawArc(
                color = category.color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
            )
            startAngle += sweepAngle
        }
        drawCircle(
            color = innerColor,
            radius = (size.minDimension - strokeWidth) / 2.15f,
        )
    }
}

/**
 * 用途说明：
 * - 分类图例行同时展示分类名、金额和占比，提升图表页的信息密度。
 *
 * 参数说明：
 * - item：单条分类统计项。
 */
@Composable
private fun CategoryLegendRow(
    item: ChartCategoryItem,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.autobookClippedClickable(
            shape = AutobookUiTokens.innerPanelShape,
            onClick = onClick,
        ),
        shape = AutobookUiTokens.innerPanelShape,
        border = autobookCardBorder(),
        color = autobookSoftFillColor(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color = item.color, shape = CircleShape),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatChartDetailAmount(item.amount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = item.ratioLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 用途说明：
 * - 根据当前周期生成趋势桶，日视图按最近 7 天、周视图按最近 6 周、月视图按全年 12 个月、年视图按最近 5 年展示。
 *
 * 参数说明：
 * - entries：当前筛选后的账单列表。
 * - period：周期筛选项。
 * - direction：金额方向筛选项。
 */
private fun buildChartPoints(
    entries: List<ChartEntryUiModel>,
    period: ChartPeriod,
    direction: ChartDirection,
): List<ChartTrendPoint> {
    val today = LocalDate.now()
    val buckets = when (period) {
        ChartPeriod.DAY -> (6 downTo 0).map { dayOffset ->
            val date = today.minusDays(dayOffset.toLong())
            ChartBucket(
                anchorDate = date,
                label = buildDayBucketLabel(date = date, today = today),
            )
        }

        ChartPeriod.WEEK -> (5 downTo 0).map { weekOffset ->
            val weekStart = today.minusWeeks(weekOffset.toLong()).with(DayOfWeek.MONDAY)
            ChartBucket(
                anchorDate = weekStart,
                label = if (weekOffset == 0) "本周" else "${weekOffset}周前",
            )
        }

        ChartPeriod.MONTH -> (1..12).map { monthValue ->
            ChartBucket(
                anchorDate = LocalDate.of(today.year, monthValue, 1),
                label = monthValue.toString(),
            )
        }

        ChartPeriod.YEAR -> (4 downTo 0).map { yearOffset ->
            val year = today.year - yearOffset
            ChartBucket(
                anchorDate = LocalDate.of(year, 1, 1),
                label = "${year}年",
            )
        }
    }

    val highlightedAnchor = resolveHighlightedAnchor(
        period = period,
        today = today,
    )
    return buckets.map { bucket ->
        ChartTrendPoint(
            label = bucket.label,
            amount = entries.sumForPeriod(period, bucket.anchorDate, direction),
            isCurrent = bucket.anchorDate == highlightedAnchor,
        )
    }
}

/**
 * 用途说明：
 * - 统一决定趋势图里哪一个桶需要使用“更明显”的主色强调。
 * - 日趋势强调今天，周趋势强调当前周，月趋势强调当前月份，年趋势强调当前年。
 *
 * 参数说明：
 * - period：当前趋势周期。
 * - today：当前日期，用于推导当前周和当前年。
 */
private fun resolveHighlightedAnchor(
    period: ChartPeriod,
    today: LocalDate,
): LocalDate {
    return when (period) {
        ChartPeriod.DAY -> today
        ChartPeriod.WEEK -> today.with(DayOfWeek.MONDAY)
        // 月趋势直接高亮当前月份，而不是固定高亮最右侧的 12 月。
        ChartPeriod.MONTH -> LocalDate.of(today.year, today.monthValue, 1)
        ChartPeriod.YEAR -> LocalDate.of(today.year, 1, 1)
    }
}

/**
 * 用途说明：
 * - 生成趋势图的纵轴范围；结余模式会保留负值区间，收入和支出模式则从零开始。
 *
 * 参数说明：
 * - points：趋势点列表。
 * - direction：当前金额方向。
 */
private fun buildTrendScale(
    points: List<ChartTrendPoint>,
    direction: ChartDirection,
): ChartTrendScale {
    val maxValue = points.maxOfOrNull { it.amount } ?: 0.0
    val minValue = points.minOfOrNull { it.amount } ?: 0.0

    val upperBound = max(
        0.0,
        if (maxValue == 0.0 && direction != ChartDirection.NET) 1.0 else maxValue,
    )
    val lowerBound = when (direction) {
        ChartDirection.NET -> min(0.0, minValue)
        ChartDirection.EXPENSE, ChartDirection.INCOME -> 0.0
    }

    return if (upperBound == 0.0 && lowerBound == 0.0) {
        ChartTrendScale(upperBound = 1.0, lowerBound = 0.0)
    } else if (upperBound == lowerBound) {
        ChartTrendScale(upperBound = upperBound + 1.0, lowerBound = lowerBound)
    } else {
        ChartTrendScale(upperBound = upperBound, lowerBound = lowerBound)
    }
}

/**
 * 用途说明：
 * - 统计图表页的分类占比。
 *
 * 参数说明：
 * - entries：当前筛选后的账单列表。
 * - direction：当前金额方向。
 */
private fun buildChartCategories(
    entries: List<ChartEntryUiModel>,
    direction: ChartDirection,
): List<ChartCategoryItem> {
    if (direction == ChartDirection.NET) {
        val totalIncome = entries.filter { it.direction == MoneyDirection.INCOME }.sumOf { it.amount }
        val totalExpense = entries.filter { it.direction == MoneyDirection.EXPENSE }.sumOf { it.amount }
        val total = totalIncome + totalExpense
        if (total <= 0.0) return emptyList()
        return listOf(
            ChartCategoryItem(
                category = INCOME_CATEGORY_LABEL,
                amount = totalIncome,
                ratio = (totalIncome / total).toFloat(),
                ratioLabel = "${((totalIncome / total) * 100).toInt()}%",
                color = chartIncomeColor,
            ),
            ChartCategoryItem(
                category = EXPENSE_CATEGORY_LABEL,
                amount = totalExpense,
                ratio = (totalExpense / total).toFloat(),
                ratioLabel = "${((totalExpense / total) * 100).toInt()}%",
                color = chartExpenseColor,
            ),
        )
    }

    val total = entries.sumOf { it.amount }
    if (total <= 0.0) return emptyList()

    return entries.groupBy { it.category.ifBlank { "未分类" } }
        .entries
        .map { entry ->
            val amount = entry.value.sumOf { it.amount }
            val ratio = (amount / total).toFloat()
            Triple(entry.key, amount, ratio)
        }
        .sortedByDescending { (_, amount, _) -> amount }
        .mapIndexed { index, (category, amount, ratio) ->
            ChartCategoryItem(
                category = category,
                amount = amount,
                ratio = ratio,
                ratioLabel = "${(ratio * 100).toInt()}%",
                // 分类图表使用独立多色盘，不再继承全局浅棕主题，保证数据区有足够区分度。
                color = chartCategoryPalette[index % chartCategoryPalette.size],
            )
        }
}

/**
 * 用途说明：
 * - 按周期聚合金额。
 *
 * 参数说明：
 * - period：周期类型。
 * - anchorDate：当前桶的锚点日期。
 * - direction：金额方向。
 */
private fun List<ChartEntryUiModel>.sumForPeriod(
    period: ChartPeriod,
    anchorDate: LocalDate,
    direction: ChartDirection,
): Double {
    return when (period) {
        ChartPeriod.DAY -> filter { it.date == anchorDate }
            .sumOf { it.amountForDirection(direction) }

        ChartPeriod.WEEK -> {
            val weekEnd = anchorDate.plusDays(6)
            filter { it.date >= anchorDate && it.date <= weekEnd }
                .sumOf { it.amountForDirection(direction) }
        }

        ChartPeriod.MONTH -> filter {
            it.date.year == anchorDate.year && it.date.monthValue == anchorDate.monthValue
        }.sumOf { it.amountForDirection(direction) }

        ChartPeriod.YEAR -> filter { it.date.year == anchorDate.year }
            .sumOf { it.amountForDirection(direction) }
    }
}

/**
 * 用途说明：
 * - 根据当前图表模式决定金额正负；结余模式中支出用负值参与计算。
 *
 * 参数说明：
 * - direction：当前图表模式。
 */
/**
 * 用途说明：
 * - 将分类占比数据收窄到图表页当前选中的周期，保证右侧分类金额和点入后的明细口径一致。
 *
 * 参数说明：
 * - period：当前图表周期。
 */
private fun List<ChartEntryUiModel>.filterByCurrentChartPeriod(
    period: ChartPeriod,
): List<ChartEntryUiModel> {
    val today = LocalDate.now()
    return when (period) {
        ChartPeriod.DAY -> filter { entry -> entry.date == today }
        ChartPeriod.WEEK -> {
            val weekStart = today.with(DayOfWeek.MONDAY)
            val weekEnd = weekStart.plusDays(6)
            filter { entry -> entry.date >= weekStart && entry.date <= weekEnd }
        }
        ChartPeriod.MONTH -> filter { entry ->
            entry.date.year == today.year && entry.date.monthValue == today.monthValue
        }
        ChartPeriod.YEAR -> filter { entry -> entry.date.year == today.year }
    }
}

/**
 * 用途说明：
 * - 将分类统计项转换成可导航的记录明细请求。
 *
 * 参数说明：
 * - selectedDirection：图表页当前方向。
 * - selectedPeriod：图表页当前周期。
 */
private fun ChartCategoryItem.toDrilldownRequest(
    selectedDirection: ChartDirection,
    selectedPeriod: ChartPeriod,
): ChartCategoryDrilldownRequest? {
    val targetDirection = when (selectedDirection) {
        ChartDirection.EXPENSE -> MoneyDirection.EXPENSE
        ChartDirection.INCOME -> MoneyDirection.INCOME
        ChartDirection.NET -> when (category) {
            INCOME_CATEGORY_LABEL -> MoneyDirection.INCOME
            EXPENSE_CATEGORY_LABEL -> MoneyDirection.EXPENSE
            else -> return null
        }
    }
    return ChartCategoryDrilldownRequest(
        direction = targetDirection,
        period = selectedPeriod.name,
        // 结余模式里的“收入/支出”是聚合入口，不是真实分类，所以传空分类展示对应方向全部记录。
        category = if (selectedDirection == ChartDirection.NET) "" else category,
    )
}

private fun ChartEntryUiModel.amountForDirection(direction: ChartDirection): Double {
    return when (direction) {
        ChartDirection.EXPENSE, ChartDirection.INCOME -> amount
        ChartDirection.NET -> if (this.direction == MoneyDirection.EXPENSE) -amount else amount
    }
}

/**
 * 用途说明：
 * - 统一格式化图表金额，兼顾正负值显示。
 *
 * 参数说明：
 * - amount：待格式化金额。
 */
private fun formatChartDetailAmount(amount: Double): String = CurrencyFormatter.format(amount)

/**
 * 用途说明：
 * - 生成日趋势底部标签，优先使用“今天/昨天”，更早日期使用短日期。
 *
 * 参数说明：
 * - date：当前趋势桶的日期。
 * - today：今天日期，用于判断当前桶是否为今天或昨天。
 */
private fun buildDayBucketLabel(
    date: LocalDate,
    today: LocalDate,
): String {
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> "${date.monthValue}/${date.dayOfMonth}"
    }
}

private fun formatChartAxisAmount(amount: Double): String {
    val absAmount = abs(amount)
    return if (absAmount >= 1_000) {
        CompactAmountFormatter.formatCurrency(amount)
    } else {
        CurrencyFormatter.format(amount)
    }
}

private enum class ChartDirection(
    val label: String,
) {
    EXPENSE("支出"),
    INCOME("收入"),
    NET("结余"),
    ;
}

private enum class ChartPeriod(
    val label: String,
) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
    ;
}

private data class ChartTrendPoint(
    val label: String,
    val amount: Double,
    val isCurrent: Boolean,
)

private data class ChartTrendScale(
    val upperBound: Double,
    val lowerBound: Double,
) {
    val middleValue: Double = (upperBound + lowerBound) / 2

    /**
     * 用途说明：
     * - 将金额映射到画布 y 轴坐标，供趋势图绘制柱子和刻度线。
     *
     * 参数说明：
     * - value：当前金额值。
     * - chartHeight：图表画布高度。
     */
    fun toY(
        value: Double,
        chartHeight: Float,
    ): Float {
        if (upperBound == lowerBound) return chartHeight
        val ratio = ((upperBound - value) / (upperBound - lowerBound)).toFloat()
        return chartHeight * ratio.coerceIn(0f, 1f)
    }
}

private data class ChartBucket(
    val anchorDate: LocalDate,
    val label: String,
)

private data class ChartCategoryItem(
    val category: String,
    val amount: Double,
    val ratio: Float,
    val ratioLabel: String,
    val color: Color,
)

/** 用途说明：收入图表色，统一使用绿色，和全局收入金额语义保持一致。 */
/** 用途说明：结余分类图中代表收入聚合入口的固定文案。 */
private const val INCOME_CATEGORY_LABEL = "收入"

/** 用途说明：结余分类图中代表支出聚合入口的固定文案。 */
private const val EXPENSE_CATEGORY_LABEL = "支出"

private val chartIncomeColor = AutobookIncomeColor

/** 用途说明：支出图表色，统一使用红色，和全局支出金额语义保持一致。 */
private val chartExpenseColor = AutobookExpenseColor

/**
 * 用途说明：
 * - 分类图表专用多色盘，避免数据可视化也被浅棕主题同化。
 * - 颜色覆盖蓝、红、绿、橙、紫、青等方向，便于用户快速区分不同分类。
 */
private val chartCategoryPalette = listOf(
    Color(0xFF3B82F6),
    Color(0xFFE15B64),
    Color(0xFF2E8B57),
    Color(0xFFF59E0B),
    Color(0xFF8B5CF6),
    Color(0xFF06A7A0),
    Color(0xFFEC4899),
)
