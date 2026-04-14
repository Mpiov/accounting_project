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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autobook.cn.app.viewmodel.ChartEntryUiModel
import com.autobook.cn.app.viewmodel.ChartUiState
import com.autobook.cn.core.common.CurrencyFormatter
import com.autobook.cn.core.model.MoneyDirection
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 图表页，集中展示收入/支出的趋势和分类。
 *
 * 参数说明：
 * - state：图表页数据源。
 * - modifier：外层修饰符。
 */
@Composable
fun ChartDashboardScreen(
    state: ChartUiState,
    modifier: Modifier = Modifier,
) {
    var selectedDirection by rememberSaveable { mutableStateOf(ChartDirection.EXPENSE) }
    var selectedPeriod by rememberSaveable { mutableStateOf(ChartPeriod.WEEK) }

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
        entries = filteredEntries,
        direction = selectedDirection,
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "chart_header") {
            ChartHeaderCard(
                selectedDirection = selectedDirection,
                onDirectionSelected = { direction -> selectedDirection = direction },
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { period -> selectedPeriod = period },
                modifier = Modifier.statusBarsPadding(),
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
            )
        }
    }
}

/** 顶部卡承接收入/支出与周月年切换。 */
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                        label = { Text(direction.label) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { onPeriodSelected(period) },
                        label = { Text(period.label) },
                    )
                }
            }
        }
    }
}

/** 顶部矩形图用于展示周/月/年趋势。 */
@Composable
private fun TrendChartCard(
    selectedDirection: ChartDirection,
    selectedPeriod: ChartPeriod,
    points: List<ChartTrendPoint>,
) {
    val totalAmount = points.sumOf { it.amount }
    val averageAmount = if (points.isEmpty()) 0.0 else totalAmount / points.size

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "${selectedDirection.label}${selectedPeriod.label}趋势",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "总额 ${CurrencyFormatter.format(totalAmount)} · 平均 ${CurrencyFormatter.format(averageAmount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                points.forEach { point ->
                    val ratio = if (point.maxAmount == 0.0) 0f else (kotlin.math.abs(point.amount) / point.maxAmount).toFloat()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(26.dp)
                                .height(20.dp + 96.dp * ratio.coerceAtLeast(0.04f))
                                .background(
                                    color = if (point.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                    shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                                ),
                        )
                        Text(
                            text = point.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 下方圆环图展示分类占比，并带排行。 */
@Composable
private fun CategoryDonutCard(
    selectedDirection: ChartDirection,
    categories: List<ChartCategoryItem>,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryDonutChart(
                        categories = categories,
                        modifier = Modifier.size(140.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        categories.take(4).forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(color = item.color, shape = CircleShape),
                                    )
                                    Text(
                                        text = item.category,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                Text(
                                    text = item.ratioLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 圆环图使用暖棕色系的分段色，不再出现绿色。 */
@Composable
private fun CategoryDonutChart(
    categories: List<ChartCategoryItem>,
    modifier: Modifier = Modifier,
) {
    val innerColor = MaterialTheme.colorScheme.background
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
            radius = (size.minDimension - strokeWidth) / 2.2f,
        )
    }
}

/** 根据周/月/年切换生成矩形图点位。 */
private fun buildChartPoints(
    entries: List<ChartEntryUiModel>,
    period: ChartPeriod,
    direction: ChartDirection,
): List<ChartTrendPoint> {
    val today = LocalDate.now()
    val buckets = when (period) {
        ChartPeriod.WEEK -> (5 downTo 0).map { weekOffset ->
            val weekStart = today.minusWeeks(weekOffset.toLong()).with(DayOfWeek.MONDAY)
            ChartBucket(
                anchorDate = weekStart,
                label = if (weekOffset == 0) "本周" else "上${weekOffset}周",
            )
        }

        ChartPeriod.MONTH -> (1..12).map { monthValue ->
            ChartBucket(
                anchorDate = LocalDate.of(today.year, monthValue, 1),
                label = "${monthValue}月",
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

    val maxAmount = buckets.maxOfOrNull { bucket ->
        kotlin.math.abs(entries.sumForPeriod(period, bucket.anchorDate, direction))
    } ?: 0.0
    val currentAnchor = buckets.lastOrNull()?.anchorDate
    return buckets.map { bucket ->
        ChartTrendPoint(
            label = bucket.label,
            amount = entries.sumForPeriod(period, bucket.anchorDate, direction),
            maxAmount = maxAmount,
            isCurrent = bucket.anchorDate == currentAnchor,
        )
    }
}

/** 统计图表页分类占比。 */
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
            ChartCategoryItem("收入", totalIncome, (totalIncome / total).toFloat(), "${((totalIncome / total) * 100).toInt()}%", Color(0xFFB07454)),
            ChartCategoryItem("支出", totalExpense, (totalExpense / total).toFloat(), "${((totalExpense / total) * 100).toInt()}%", Color(0xFFD6A95F)),
        )
    }

    val total = entries.sumOf { it.amount }
    if (total <= 0.0) return emptyList()

    val palette = listOf(
        Color(0xFF7B4B34),
        Color(0xFFB07454),
        Color(0xFFD6A95F),
        Color(0xFFC28E79),
        Color(0xFF8E6A52),
    )
    return entries.groupBy { it.category.ifBlank { "未分类" } }
        .entries
        .toList()
        .mapIndexed { index, entry ->
            val category = entry.key
            val groupedEntries = entry.value
            val amount = groupedEntries.sumOf { it.amount }
            val ratio = (amount / total).toFloat()
            ChartCategoryItem(
                category = category,
                amount = amount,
                ratio = ratio,
                ratioLabel = "${(ratio * 100).toInt()}%",
                color = palette[index % palette.size],
            )
        }
        .sortedByDescending { it.amount }
}

/** 按周期聚合金额。 */
private fun List<ChartEntryUiModel>.sumForPeriod(
    period: ChartPeriod,
    anchorDate: LocalDate,
    direction: ChartDirection,
): Double {
    return when (period) {
        ChartPeriod.WEEK -> {
            val weekEnd = anchorDate.plusDays(6)
            filter { it.date >= anchorDate && it.date <= weekEnd }.sumOf { it.amountForDirection(direction) }
        }

        ChartPeriod.MONTH -> filter { it.date.year == anchorDate.year && it.date.monthValue == anchorDate.monthValue }.sumOf { it.amountForDirection(direction) }

        ChartPeriod.YEAR -> filter { it.date.year == anchorDate.year }.sumOf { it.amountForDirection(direction) }
    }
}

/** 根据当前图表模式决定金额正负。 */
private fun ChartEntryUiModel.amountForDirection(direction: ChartDirection): Double {
    return when (direction) {
        ChartDirection.EXPENSE, ChartDirection.INCOME -> amount
        ChartDirection.NET -> if (this.direction == MoneyDirection.EXPENSE) -amount else amount
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
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
}

private data class ChartTrendPoint(
    val label: String,
    val amount: Double,
    val maxAmount: Double,
    val isCurrent: Boolean,
)

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
