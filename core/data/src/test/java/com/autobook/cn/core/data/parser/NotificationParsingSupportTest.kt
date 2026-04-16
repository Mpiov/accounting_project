package com.autobook.cn.core.data.parser

import com.autobook.cn.core.model.NotificationSourceApp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** 验证通知来源识别器能够把主流支付与银行应用包名映射到正确来源。 */
class NotificationParsingSupportTest {
    @Test
    fun `classifies ccb package as bank source`() {
        val source = NotificationSourceClassifier.detect("com.chinamworld.main")

        assertEquals(NotificationSourceApp.BANK_SMS, source)
    }

    @Test
    fun `classifies sms package as bank source`() {
        val source = NotificationSourceClassifier.detect("com.android.mms")

        assertEquals(NotificationSourceApp.BANK_SMS, source)
    }
}
