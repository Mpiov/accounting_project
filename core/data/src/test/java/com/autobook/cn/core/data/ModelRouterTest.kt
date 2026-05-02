package com.autobook.cn.core.data

import com.autobook.cn.core.data.pipeline.ModelRouterImpl
import com.autobook.cn.core.model.MembershipStatus
import com.autobook.cn.core.model.MembershipTier
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.TransactionCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 验证通知路由器按服务器会员门禁选择普通规则或会员机器学习。 */
class ModelRouterTest {
    @Test
    fun `free users are routed to rules only scoring`() = runTest {
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
        var receivedMode: TransactionScoringMode? = null
        val extractor = object : TransactionExtractor {
            /**
             * 用途说明：
             * - 返回固定本地候选，并记录路由器传入的评分模式。
             *
             * 参数说明：
             * - event：测试传入的原始通知事件。
             * - scoringMode：路由器根据会员状态选择的评分模式。
             */
            override suspend fun extract(
                event: NotificationEvent,
                scoringMode: TransactionScoringMode,
            ): OperationResult<TransactionCandidate> {
                receivedMode = scoringMode
                return OperationResult.Success(expectedCandidate.copy(timestamp = event.postedAt))
            }
        }

        val result = ModelRouterImpl(
            localExtractor = extractor,
            membershipRepository = fakeMembershipRepository(MembershipStatus()),
        ).route(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "支付成功 12.50 元",
                postedAt = 99L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(TransactionScoringMode.RULES_ONLY, receivedMode)
        assertEquals("local-parser", candidate.parsePath)
        assertEquals("local-mock-v1", candidate.modelVersion)
        assertEquals(99L, candidate.timestamp)
    }

    @Test
    fun `server verified members are routed to machine learning scoring`() = runTest {
        val expectedFailure = OperationResult.TerminalFailure("Amount was not parseable")
        var receivedMode: TransactionScoringMode? = null
        val extractor = object : TransactionExtractor {
            /**
             * 用途说明：
             * - 返回本地解析失败，并记录会员用户是否进入机器学习评分模式。
             *
             * 参数说明：
             * - event：测试传入的原始通知事件。
             * - scoringMode：路由器根据会员状态选择的评分模式。
             */
            override suspend fun extract(
                event: NotificationEvent,
                scoringMode: TransactionScoringMode,
            ): OperationResult<TransactionCandidate> {
                receivedMode = scoringMode
                return expectedFailure
            }
        }

        val result = ModelRouterImpl(
            localExtractor = extractor,
            membershipRepository = fakeMembershipRepository(
                MembershipStatus(
                    tier = MembershipTier.MEMBER,
                    expiresAt = System.currentTimeMillis() + 86_400_000L,
                    serverVerified = true,
                    modelConfidenceEnabled = true,
                    source = "server",
                ),
            ),
        ).route(
            NotificationEvent(
                packageName = "com.bank.app",
                title = "服务通知",
                text = "余额变动提醒",
                postedAt = 1L,
            ),
        )

        assertSame(expectedFailure, result)
        assertEquals(TransactionScoringMode.MEMBER_MACHINE_LEARNING, receivedMode)
    }
}

/**
 * 用途说明：
 * - 构造固定会员状态的仓储替身。
 *
 * 参数说明：
 * - status：测试期望暴露给路由器的会员状态。
 */
private fun fakeMembershipRepository(status: MembershipStatus): MembershipRepository {
    return object : MembershipRepository {
        override val membershipStatus: Flow<MembershipStatus> = flowOf(status)

        override suspend fun refresh(): MembershipStatus = status
    }
}
