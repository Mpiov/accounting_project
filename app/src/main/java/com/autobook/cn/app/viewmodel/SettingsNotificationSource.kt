package com.autobook.cn.app.viewmodel

import com.autobook.cn.core.model.NotificationSourceApp

/**
 * 设置页使用的通知来源分组。
 *
 * 用途说明：
 * - 设置页只需要向用户暴露 3 个来源桶：微信、支付宝、其它来源。
 * - 其中“其它来源”在产品语义上同时覆盖银行卡短信和未识别来源，因此不能直接复用底层单一的 `BANK_SMS` 枚举值。
 */
enum class SettingsNotificationSource {
    WECHAT,
    ALIPAY,
    OTHER,
}

/**
 * 把底层来源开关集合映射为设置页可展示的来源桶集合。
 *
 * 用途说明：
 * - UI 只关心“其它来源”是否开启，不关心它在持久层中是由 `BANK_SMS`、`UNKNOWN` 还是两者共同表示。
 * - 只要底层任意一个“其它来源”成员开启，就把设置页里的 `OTHER` 视为开启，保证旧数据也能被正确显示。
 */
internal fun Set<NotificationSourceApp>.toSettingsNotificationSources(): Set<SettingsNotificationSource> {
    return buildSet {
        if (NotificationSourceApp.WECHAT in this@toSettingsNotificationSources) {
            add(SettingsNotificationSource.WECHAT)
        }
        if (NotificationSourceApp.ALIPAY in this@toSettingsNotificationSources) {
            add(SettingsNotificationSource.ALIPAY)
        }
        if (
            NotificationSourceApp.BANK_SMS in this@toSettingsNotificationSources ||
            NotificationSourceApp.UNKNOWN in this@toSettingsNotificationSources
        ) {
            add(SettingsNotificationSource.OTHER)
        }
    }
}

/**
 * 把设置页中的来源桶展开为底层持久化与过滤逻辑使用的真实来源集合。
 *
 * 用途说明：
 * - `OTHER` 必须同时控制 `BANK_SMS` 和 `UNKNOWN`，这样“其它来源”开关的含义才和用户理解一致。
 * - 微信、支付宝则保持一一映射，避免引入额外歧义。
 */
internal fun SettingsNotificationSource.toNotificationSourceApps(): Set<NotificationSourceApp> {
    return when (this) {
        SettingsNotificationSource.WECHAT -> setOf(NotificationSourceApp.WECHAT)
        SettingsNotificationSource.ALIPAY -> setOf(NotificationSourceApp.ALIPAY)
        SettingsNotificationSource.OTHER -> setOf(
            NotificationSourceApp.BANK_SMS,
            NotificationSourceApp.UNKNOWN,
        )
    }
}
