package com.autobook.cn.core.common

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object SettingsIntents {
    fun notificationAccess(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun accessibilityAccess(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}

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
