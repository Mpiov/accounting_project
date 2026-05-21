package com.autobook.cn.service.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 验证本应用通知过滤逻辑，避免自动入账成功通知再次触发自动记账。 */
class SelfNotificationFilterTest {
    @Test
    fun `ignores notification posted by current app`() {
        val shouldIgnore = shouldIgnoreSelfNotification(
            notificationPackageName = "com.autobook.cn",
            appPackageName = "com.autobook.cn",
        )

        assertTrue(shouldIgnore)
    }

    @Test
    fun `keeps notification posted by payment app`() {
        val shouldIgnore = shouldIgnoreSelfNotification(
            notificationPackageName = "com.eg.android.AlipayGphone",
            appPackageName = "com.autobook.cn",
        )

        assertFalse(shouldIgnore)
    }

    @Test
    fun `keeps notification when package name is missing`() {
        val shouldIgnore = shouldIgnoreSelfNotification(
            notificationPackageName = null,
            appPackageName = "com.autobook.cn",
        )

        assertFalse(shouldIgnore)
    }
}
