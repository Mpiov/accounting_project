package com.autobook.cn.core.data.parser

import com.autobook.cn.core.model.NotificationSourceApp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** 验证通知来源识别器能够把主流支付与银行应用包名映射到正确来源。 */
class NotificationParsingSupportTest {
    @Test
    fun `classifies wechat package as wechat source`() {
        val source = NotificationSourceClassifier.detect("com.tencent.mm")

        assertEquals(NotificationSourceApp.WECHAT, source)
    }

    @Test
    fun `classifies alipay package as alipay source`() {
        val source = NotificationSourceClassifier.detect("com.eg.android.AlipayGphone")

        assertEquals(NotificationSourceApp.ALIPAY, source)
    }

    @Test
    fun `classifies ccb package as bank source`() {
        val source = NotificationSourceClassifier.detect("com.chinamworld.main")

        assertEquals(NotificationSourceApp.BANK_SMS, source)
    }

    @Test
    fun `classifies common bank packages as bank source`() {
        val bankPackages = listOf(
            "com.icbc",
            "com.android.bankabc",
            "com.cmbchina.ccd.pluto.cmbActivity",
            "com.bankcomm.Bankcomm",
            "cn.com.spdb.mobilebank.per",
        )

        bankPackages.forEach { packageName ->
            assertEquals(
                NotificationSourceApp.BANK_SMS,
                NotificationSourceClassifier.detect(packageName),
                "packageName=$packageName",
            )
        }
    }

    @Test
    fun `classifies sms package as bank source`() {
        val source = NotificationSourceClassifier.detect("com.android.mms")

        assertEquals(NotificationSourceApp.BANK_SMS, source)
    }

    @Test
    fun `keeps generic wallet package unknown to avoid bank false positive`() {
        val source = NotificationSourceClassifier.detect("com.example.wallet")

        assertEquals(NotificationSourceApp.UNKNOWN, source)
    }

    @Test
    fun `keeps unrelated package unknown even when it contains bank text`() {
        val source = NotificationSourceClassifier.detect("com.example.databank.reader")

        assertEquals(NotificationSourceApp.UNKNOWN, source)
    }
}
