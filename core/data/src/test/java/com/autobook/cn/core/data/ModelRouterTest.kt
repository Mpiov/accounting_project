package com.autobook.cn.core.data

import com.autobook.cn.core.data.pipeline.ModelRouterImpl
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.TransactionCandidate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 验证通知路由器只执行本地规则解析，不再进入额外增强链路。 */
class ModelRouterTest {
    @Test
    fun `returns local extractor success directly`() = runTest {
        val expectedCandidate = TransactionCandidate(
            amount = 12.5,
            direction = MoneyDirection.EXPENSE,
            merchant = "微信支付",
            channel = "微信支付",
            timestamp = 1L,
            confidence = ParseConfidence(0.9f),
            confidenceReason = "Matched local rules",
            rawSource = "notification",
            parsePath = "local-parser",
            sourceApp = NotificationSourceApp.WECHAT,
        )
        val extractor = object : TransactionExtractor {
            /**
             * 用途说明：
             * - 返回固定本地候选，验证路由器不会继续改写 parsePath 或 modelVersion。
             *
             * 参数说明：
             * - event：测试传入的原始通知事件。
             */
            override suspend fun extract(event: NotificationEvent): OperationResult<TransactionCandidate> {
                return OperationResult.Success(expectedCandidate.copy(timestamp = event.postedAt))
            }
        }

        val result = ModelRouterImpl(localExtractor = extractor).route(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "支付成功 12.50 元",
                postedAt = 99L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals("local-parser", candidate.parsePath)
        assertEquals("local-mock-v1", candidate.modelVersion)
        assertEquals(99L, candidate.timestamp)
    }

    @Test
    fun `returns local extractor failure directly`() = runTest {
        val expectedFailure = OperationResult.TerminalFailure("Amount was not parseable")
        val extractor = object : TransactionExtractor {
            /**
             * 用途说明：
             * - 返回本地规则解析失败，验证路由器不会再尝试任何兜底链路。
             *
             * 参数说明：
             * - event：测试传入的原始通知事件。
             */
            override suspend fun extract(event: NotificationEvent): OperationResult<TransactionCandidate> {
                return expectedFailure
            }
        }

        val result = ModelRouterImpl(localExtractor = extractor).route(
            NotificationEvent(
                packageName = "com.bank.app",
                title = "服务通知",
                text = "余额变动提醒",
                postedAt = 1L,
            ),
        )

        assertSame(expectedFailure, result)
    }
}
