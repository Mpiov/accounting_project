package com.autobook.cn.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 纸张背景宿主。
 *
 * 用途说明：
 * - 在应用主内容下方提供统一背景色，让白色界面保持干净、稳定。
 * - 设置页和长列表快速滑动时不再绘制全屏纸纹位图，减少 GPU 过度绘制和半透明混合带来的掉帧。
 *
 * 参数说明：
 * - modifier：外层修饰符，便于宿主页面传入尺寸或额外布局约束。
 * - content：前景内容；通常为整页 `Scaffold`、`NavHost` 或列表页面。
 */
@Composable
fun AutobookPaperBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 这里刻意不再叠加图片和遮罩：纯色背景更省绘制，也更符合当前白色为主的设置页风格。
        content()
    }
}
