package com.autobook.cn.core.common

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** 引导页和设置页跳转系统设置时使用的 Intent 集合。 */
object SettingsIntents {
    fun notificationAccess(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun accessibilityAccess(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}

/** 检查关键 Android 服务当前是否已开启的辅助方法。 */
object PermissionStatusChecker {
    fun hasNotificationAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return enabled.contains(context.packageName)
    }

    fun hasAccessibilityAccess(
        context: Context,
        serviceClassName: String,
    ): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val expected = ComponentName(context.packageName, serviceClassName).flattenToString()
        return enabled.contains(expected)
    }
}
