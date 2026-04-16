package com.autobook.cn.app

import android.app.Application
import com.autobook.cn.core.data.parser.NotificationRuleWarmup
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 应用入口，负责初始化 Hilt 依赖注入。 */
@HiltAndroidApp
class AutobookApplication : Application() {
    /**
     * 应用级后台作用域。
     *
     * 用途说明：
     * - 专门承接启动阶段的轻量预热任务，避免把规则树构建直接压在主线程上。
     * - 使用 `SupervisorJob` 避免单个预热任务异常影响后续应用生命周期。
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 应用启动时主动预热通知规则树。
     *
     * 用途说明：
     * - AC 自动机建树只做一次，后续通知解析直接复用只读结构。
     * - 预热仍会尽早执行，但不再阻塞 `Application.onCreate()`，从而减少冷启动掉帧和首屏卡顿。
     */
    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            NotificationRuleWarmup.warmUp()
        }
    }
}
