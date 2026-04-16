package com.autobook.cn.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape

/**
 * 为圆角控件提供“先裁剪再点击”的统一交互修饰符。
 *
 * 用途说明：
 * - 许多 `Card` / `Surface` 当前把 `clickable` 直接挂在外层 `Modifier` 上，
 *   涟漪会按矩形区域扩散，导致视觉上冲出圆角边界。
 * - 这里统一先按目标形状裁剪，再应用点击行为，让涟漪严格限制在圆角内部。
 *
 * 参数说明：
 * - shape：当前点击区域应遵守的外轮廓形状。
 * - enabled：是否允许点击；默认开启。
 * - onClick：点击回调。
 */
internal fun Modifier.autobookClippedClickable(
    shape: Shape,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    return clip(shape).clickable(
        enabled = enabled,
        onClick = onClick,
    )
}
