package com.autobook.cn.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween

/**
 * 应用内页面切换动画时长。
 *
 * 用途说明：
 * - 当前用户希望“新页面渐变动画更快”，这里统一收口导航层的淡入淡出时长。
 * - 直接把时长压到一组更短的常量，后续如果还要继续调快或回退，只需要改这一处。
 */
internal object AutobookNavTransitions {
    /**
     * 前进进入动画。
     *
     * 用途说明：
     * - 统一使用更短的淡入动画，避免页面已经准备好但视觉上还在“慢慢浮现”。
     */
    fun enter(): EnterTransition = fadeIn(
        animationSpec = tween(durationMillis = ENTER_DURATION_MS),
    )

    /**
     * 前进退出动画。
     *
     * 用途说明：
     * - 退出动画比进入动画再短一点，减少页面切换时的拖尾感。
     */
    fun exit(): ExitTransition = fadeOut(
        animationSpec = tween(durationMillis = EXIT_DURATION_MS),
    )

    /**
     * 返回进入动画。
     *
     * 用途说明：
     * - 返回时保持与前进一致的整体观感，但继续采用较短时长，确保回退更利落。
     */
    fun popEnter(): EnterTransition = fadeIn(
        animationSpec = tween(durationMillis = ENTER_DURATION_MS),
    )

    /**
     * 返回退出动画。
     *
     * 用途说明：
     * - 回退退出继续使用短淡出，保证“去设置/返回”和底部 Tab 来回切换都更干脆。
     */
    fun popExit(): ExitTransition = fadeOut(
        animationSpec = tween(durationMillis = EXIT_DURATION_MS),
    )
}

/**
 * 进入动画时长。
 *
 * 用途说明：
 * - 110ms 在高刷新率设备上几乎只剩下瞬切体感，因此这里回调到“仍然偏快、但肉眼可见”的时长。
 */
private const val ENTER_DURATION_MS = 180

/**
 * 退出动画时长。
 *
 * 用途说明：
 * - 退出继续比进入略短，既保留淡出感，也避免拖尾过重。
 */
private const val EXIT_DURATION_MS = 140
