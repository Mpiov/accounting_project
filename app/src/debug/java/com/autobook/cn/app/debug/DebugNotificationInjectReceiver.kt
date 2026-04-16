package com.autobook.cn.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * debug 专用通知注入 Receiver。
 *
 * 用途说明：
 * - 只负责接收 adb 广播，并把真正的注入任务快速转交给后台 Service。
 * - 避免把本地语义校验直接放在广播生命周期内，导致调试链路被系统判定为 Broadcast ANR。
 */
class DebugNotificationInjectReceiver : BroadcastReceiver() {
    /**
     * 接收 adb 广播并立刻转交给后台服务。
     *
     * 参数说明：
     * - context：系统传入的上下文，用于启动 debug Service。
     * - intent：adb 广播携带的注入参数，会被原样透传给 Service。
     */
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != DebugNotificationInjectContract.ACTION_INJECT_NOTIFICATION) {
            return
        }

        val serviceIntent = Intent(context, DebugNotificationInjectService::class.java).apply {
            action = DebugNotificationInjectContract.ACTION_PROCESS_INJECTION
            replaceExtras(intent)
        }
        context.startService(serviceIntent)
    }
}
