package com.autobook.cn.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.autobook.cn.R

/**
 * 纸张背景宿主。
 *
 * 用途说明：
 * - 在应用主内容下方铺一层暖色纸纹背景，让现有“暖色纸张账本”主题真正有底图支撑。
 * - 亮色模式使用浅米色纸纹，暗色模式切换为深棕纸纹，避免深色主题下直接出现发亮的浅纸图。
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
    val darkTheme = isSystemInDarkTheme()
    val backgroundResId = if (darkTheme) {
        R.drawable.autobook_paper_dark
    } else {
        R.drawable.autobook_paper_light
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        /**
         * 背景图只负责提供纸张气质，因此保持弱存在感。
         *
         * 用途说明：
         * - `ContentScale.Crop` 让同一张底图适配不同长宽比手机。
         * - 深色模式降低一点透明度，避免纸纹把正文对比度吃掉。
         */
        Image(
            painter = painterResource(id = backgroundResId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = if (darkTheme) 0.68f else 0.92f,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(autobookPaperScrimColor()),
        )
        content()
    }
}

/**
 * 返回纸张背景上的柔和遮罩色。
 *
 * 用途说明：
 * - 让背景纹理和前景卡片之间留出一点呼吸感，避免纸纹直接抢正文注意力。
 * - 深色模式下额外加深遮罩，优先保证可读性。
 */
@Composable
private fun autobookPaperScrimColor(): Color {
    return if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.background.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.background.copy(alpha = 0.08f)
    }
}
