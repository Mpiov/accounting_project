package com.autobook.cn.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 用途说明：
 * - 统一管理客户端界面改版后的卡片形态、描边和柔和填充色。
 * - 这一层专门解决“卡片偏灰、圆角过圆、不同页面风格不一致”的问题。
 */
internal object AutobookUiTokens {
    /** 主要面板圆角：整体更接近方框，但边角仍然保留一点柔和感。 */
    val panelShape = RoundedCornerShape(16.dp)

    /** 紧凑卡片圆角：用于列表项和时间线卡片，视觉上更利落。 */
    val compactPanelShape = RoundedCornerShape(13.dp)

    /** 内嵌卡片圆角：用于大卡片里的小统计块，避免出现过圆的胶囊感。 */
    val innerPanelShape = RoundedCornerShape(11.dp)

    /** 按钮和标签圆角：保留轻微圆角，兼顾点击区和“方框感”。 */
    val chipShape = RoundedCornerShape(12.dp)
}

/**
 * 用途说明：
 * - 为普通卡片提供统一的浅暖白底色，避免直接使用系统默认灰感表面色。
 *
 * 参数说明：
 * - highlighted：是否为高亮卡片；高亮时会使用更明显但不刺眼的暖色底。
 */
@Composable
internal fun autobookCardColors(
    highlighted: Boolean = false,
): CardColors {
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    return CardDefaults.cardColors(containerColor = containerColor)
}

/**
 * 用途说明：
 * - 为卡片提供统一的暖色描边，让“方框轮廓”更清晰，同时避免灰边带来的脏感。
 *
 * 参数说明：
 * - emphasized：是否需要更明确的边框强度；选中态和重点区块会稍微更明显。
 */
@Composable
internal fun autobookCardBorder(
    emphasized: Boolean = false,
): BorderStroke {
    val borderColor = if (emphasized) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)
    }
    return BorderStroke(width = 1.dp, color = borderColor)
}

/**
 * 用途说明：
 * - 返回统一的柔和填充色，用于次级容器、筛选块和空状态底色。
 */
@Composable
internal fun autobookSoftFillColor(): Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f)

/**
 * 用途说明：
 * - 返回统一的时间线连线颜色，保证连线可见但不会偏灰发脏。
 */
@Composable
internal fun autobookTimelineConnectorColor(): Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
