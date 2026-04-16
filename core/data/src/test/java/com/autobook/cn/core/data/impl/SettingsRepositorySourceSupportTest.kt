package com.autobook.cn.core.data.impl

import com.autobook.cn.core.model.NotificationSourceApp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsRepositorySourceSupportTest {
    @Test
    fun resolveEnabledSourcesFallsBackToDefaultOnlyWhenPreferenceMissing() {
        val resolvedSources = resolveEnabledSources(sourceNames = null)

        assertEquals(
            setOf(
                NotificationSourceApp.WECHAT,
                NotificationSourceApp.ALIPAY,
                NotificationSourceApp.BANK_SMS,
                NotificationSourceApp.UNKNOWN,
            ),
            resolvedSources,
        )
    }

    @Test
    fun resolveEnabledSourcesKeepsExplicitEmptySetAsAllDisabled() {
        val resolvedSources = resolveEnabledSources(sourceNames = emptySet())

        assertEquals(emptySet<NotificationSourceApp>(), resolvedSources)
    }

    @Test
    fun applySourceEnabledChangeDisablesGroupedSourcesInSingleStep() {
        /**
         * “其它来源”在设置页里会同时映射到 `BANK_SMS` 和 `UNKNOWN`，
         * 这里验证批量关闭时会一次性把两个底层来源都移除。
         */
        val updatedSourceNames = applySourceEnabledChange(
            currentSourceNames = setOf(
                NotificationSourceApp.WECHAT.name,
                NotificationSourceApp.ALIPAY.name,
                NotificationSourceApp.BANK_SMS.name,
                NotificationSourceApp.UNKNOWN.name,
            ),
            sources = setOf(
                NotificationSourceApp.BANK_SMS,
                NotificationSourceApp.UNKNOWN,
            ),
            enabled = false,
        )

        assertEquals(
            setOf(
                NotificationSourceApp.WECHAT.name,
                NotificationSourceApp.ALIPAY.name,
            ),
            updatedSourceNames,
        )
    }

    @Test
    fun applySourceEnabledChangeCanPersistAllSourcesDisabled() {
        val updatedSourceNames = applySourceEnabledChange(
            currentSourceNames = setOf(
                NotificationSourceApp.WECHAT.name,
                NotificationSourceApp.ALIPAY.name,
                NotificationSourceApp.BANK_SMS.name,
                NotificationSourceApp.UNKNOWN.name,
            ),
            sources = setOf(
                NotificationSourceApp.WECHAT,
                NotificationSourceApp.ALIPAY,
                NotificationSourceApp.BANK_SMS,
                NotificationSourceApp.UNKNOWN,
            ),
            enabled = false,
        )

        assertEquals(emptySet<String>(), updatedSourceNames)
    }
}
