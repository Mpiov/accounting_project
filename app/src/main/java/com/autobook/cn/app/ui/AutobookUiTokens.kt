package com.autobook.cn.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 用途说明：
 * - 统一管理客户端界面改版后的卡片形态、描边和柔和填充色。
 * - 当前视觉以白色为主，米白和浅棕只作为层次与强调，避免页面整体显得厚重。
 */
internal object AutobookUiTokens {
    /** 主要面板圆角：参考轻量设置页的克制圆角，避免卡片过圆显得笨重。 */
    val panelShape = RoundedCornerShape(12.dp)

    /** 紧凑卡片圆角：用于列表项和时间线卡片，视觉上更利落。 */
    val compactPanelShape = RoundedCornerShape(10.dp)

    /** 内嵌卡片圆角：用于大卡片里的小统计块，避免出现过圆的胶囊感。 */
    val innerPanelShape = RoundedCornerShape(8.dp)

    /** 按钮和标签圆角：保留轻微圆角，兼顾点击区和“方框感”。 */
    val chipShape = RoundedCornerShape(10.dp)
}

/**
 * 用途说明：
 * - 统一收入金额的语义色，避免不同页面各自维护颜色导致收入、支出认知反复。
 * - 产品约定为“收入=绿色”，这里使用不过饱和的绿色，保证白色页面上可读。
 */
internal val AutobookIncomeColor = Color(0xFF2E8B57)

/**
 * 用途说明：
 * - 统一支出金额的语义色，避免历史页面继续沿用相反的红绿口径。
 * - 产品约定为“支出=红色”，这里使用柔和红色，强调金额但不刺眼。
 */
internal val AutobookExpenseColor = Color(0xFFD1495B)

/**
 * 用途说明：
 * - 为普通卡片提供统一白底，避免页面出现大面积深暖色。
 *
 * 参数说明：
 * - highlighted：是否为高亮卡片；高亮时会使用更明显但不刺眼的暖色底。
 */
@Composable
internal fun autobookCardColors(
    highlighted: Boolean = false,
): CardColors {
    val containerColor = if (highlighted) {
        // 高亮卡片只给轻米白底，重点交给文字和按钮表达，避免卡片本身压住内容。
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    return CardDefaults.cardColors(containerColor = containerColor)
}

/**
 * 用途说明：
 * - 为卡片提供统一浅暖描边，让白色卡片在白色页面上仍有清晰边界。
 *
 * 参数说明：
 * - emphasized：是否需要更明确的边框强度；选中态和重点区块会稍微更明显。
 */
@Composable
internal fun autobookCardBorder(
    emphasized: Boolean = false,
): BorderStroke {
    val borderColor = if (emphasized) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.68f)
    }
    return BorderStroke(width = 1.dp, color = borderColor)
}

/**
 * 用途说明：
 * - 返回统一的柔和填充色，用于次级容器、筛选块和空状态底色。
 * - 这里保留米白色作为“辅色”，但透明度较低，避免堆叠后变成偏黄页面。
 */
@Composable
internal fun autobookSoftFillColor(): Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)

/**
 * 用途说明：
 * - 返回统一的时间线连线颜色，保证连线可见但不会偏灰发脏。
 */
@Composable
internal fun autobookTimelineConnectorColor(): Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)

/**
 * 用途说明：
 * - 统一计算主页面首屏内容的顶部安全间距。
 * - 这里把系统状态栏安全区压缩到约一半，让页面整体稍微上移，但仍尽量避开状态栏图标区域。
 */
@Composable
internal fun mainPageTopInset(): Dp {
    return 12.dp
}
