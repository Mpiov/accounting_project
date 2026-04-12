package com.autobook.cn.service.automation

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** 无障碍服务占位实现，当前仅预留拉起通知栏的能力。 */
class AutobookAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AutobookAccessService", "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    /** 通过系统全局动作拉起通知栏，供后续自动化链路使用。 */
    fun openNotificationShade(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
}
