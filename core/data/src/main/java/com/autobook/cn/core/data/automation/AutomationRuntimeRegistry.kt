package com.autobook.cn.core.data.automation

import android.graphics.Bitmap
import com.autobook.cn.core.model.OperationResult

/**
 * 自动化运行时能力抽象。
 *
 * 用途说明：
 * - 把无障碍服务当前持有的“打开通知栏、截图、收起系统界面”能力，以纯接口的形式暴露给数据层。
 * - 这样 core:data 不需要直接依赖 service 模块，也能通过注册表拿到当前可用的自动化实例。
 *
 * 参数说明：
 * - 本接口方法均无显式参数；调用方只关心当前运行时是否就绪以及具体执行结果。
 */
interface AutomationRuntime {
    /** 打开通知栏，给用户主动截图入口创造稳定画面。 */
    fun openNotificationShade(): Boolean

    /**
     * 对当前屏幕执行一次截图。
     *
     * 返回值说明：
     * - 成功时返回 Bitmap，交给上层保存并进入图片分析链路。
     * - 失败时返回可重试或不可重试的错误，便于上层决定是否继续回退。
     */
    suspend fun captureScreenshot(): OperationResult<Bitmap>

    /** 收起通知栏或当前系统浮层，避免截图完成后界面长期停留在系统面板。 */
    fun collapseSystemUi(): Boolean
}

/**
 * 自动化运行时注册表。
 *
 * 用途说明：
 * - 无障碍服务连接成功时把自己注册进来，销毁时注销。
 * - 数据层在执行主动截图时从这里读取当前活跃的运行时实例。
 */
object AutomationRuntimeRegistry {
    @Volatile
    private var runtime: AutomationRuntime? = null

    /**
     * 注册当前活跃的自动化运行时。
     *
     * 参数说明：
     * - runtime：由服务侧创建并持有的运行时实现。
     */
    fun register(runtime: AutomationRuntime) {
        this.runtime = runtime
    }

    /**
     * 仅在传入实例仍然是当前活跃实例时执行注销，避免并发覆盖时误清空。
     *
     * 参数说明：
     * - runtime：希望注销的运行时实现。
     */
    fun unregister(runtime: AutomationRuntime) {
        if (this.runtime === runtime) {
            this.runtime = null
        }
    }

    /**
     * 读取当前可用的自动化运行时。
     */
    fun current(): AutomationRuntime? = runtime
}
