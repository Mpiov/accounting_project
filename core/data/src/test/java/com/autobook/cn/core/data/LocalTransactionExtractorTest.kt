package com.autobook.cn.core.data

import com.autobook.cn.core.data.parser.LocalTransactionExtractor
import com.autobook.cn.core.model.ConfidenceLevel
import com.autobook.cn.core.model.HIGH_CONFIDENCE_THRESHOLD
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/** 验证本地通知解析器默认使用普通规则，只有显式会员模式才使用轻量置信度模型。 */
class LocalTransactionExtractorTest {
    @Test
    fun `wechat short payment reaches high confidence with free rules`() = runTest {
        val result = localTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "已支付￥0.01",
                postedAt = 123L,
                sourceApp = NotificationSourceApp.WECHAT,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(0.01, candidate.amount)
        assertEquals(MoneyDirection.EXPENSE, candidate.direction)
        assertEquals("微信支付", candidate.channel)
        assertEquals("local-rules-v1", candidate.modelVersion)
        assertTrue(candidate.modelFeatureSnapshotJson.contains("\"sourceClass\""))
        assertEquals(ConfidenceLevel.HIGH, candidate.confidence.level)
        assertTrue(candidate.confidence.score >= HIGH_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun `member mode uses machine learning model version`() = runTest {
        val result = localTransactionExtractor().extract(
            event = NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "已支付￥0.01",
                postedAt = 123L,
                sourceApp = NotificationSourceApp.WECHAT,
            ),
            scoringMode = TransactionScoringMode.MEMBER_MACHINE_LEARNING,
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals("local-linear-confidence-v1", candidate.modelVersion)
        assertEquals("local-parser/member-ml", candidate.parsePath)
    }

    @Test
    fun `member mode uses persisted cloud model weights when available`() = runTest {
        val cloudModel = CloudConfidenceModel(
            modelVersion = "global-linear-v2",
            weights = mapOf(
                // 用极低偏置压低分数，证明本次评分确实读取了服务器模型权重。
                "bias" to -12.0,
            ),
            thresholds = emptyMap(),
            publishedAt = 1_746_117_600_000L,
            trainedSampleCount = 100,
        )

        val result = LocalTransactionExtractor(
            cloudModelStore = fakeCloudModelStore(cloudModel),
        ).extract(
            event = NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "已支付￥0.01",
                postedAt = 123L,
                sourceApp = NotificationSourceApp.WECHAT,
            ),
            scoringMode = TransactionScoringMode.MEMBER_MACHINE_LEARNING,
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals("global-linear-v2", candidate.modelVersion)
        assertTrue(candidate.confidenceReason.contains("模型=global-linear-v2"))
        assertTrue(candidate.modelFeatureSnapshotJson.contains(""""modelSource":"cloud""""))
        assertTrue(candidate.confidence.score < 0.01f)
    }

    @Test
    fun `bank debit notification reaches high confidence`() = runTest {
        val result = localTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.chinamworld.main",
                title = "动账提醒",
                text = "您尾号2134的储蓄账户4月15日22时23分收入人民币1.00元。点击查看>>",
                postedAt = 456L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(1.0, candidate.amount)
        assertEquals(MoneyDirection.INCOME, candidate.direction)
        assertEquals(NotificationSourceApp.BANK_SMS, candidate.sourceApp)
        assertEquals(ConfidenceLevel.HIGH, candidate.confidence.level)
    }

    @Test
    fun `finance settled notification stays medium with ordinary rules`() = runTest {
        val result = localTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "零钱通",
                text = "零钱通利息到账0.01元",
                postedAt = 789L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals("零钱通", candidate.channel)
        assertEquals("收益", candidate.categoryHint)
        assertEquals(ConfidenceLevel.MEDIUM, candidate.confidence.level)
    }

    @Test
    fun `asset valuation notification is blocked to low confidence`() = runTest {
        val result = localTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "零钱通",
                text = "今日持仓收益 +12.30 元",
                postedAt = 999L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(MoneyDirection.UNKNOWN, candidate.direction)
        assertEquals(ConfidenceLevel.LOW, candidate.confidence.level)
        assertTrue(candidate.confidenceReason.contains("资产估值上限"))
    }

    @Test
    fun `unknown but strongly structured notification reaches high confidence with ordinary rules`() = runTest {
        val result = localTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.example.notice",
                title = "账户提醒",
                text = "您尾号2134的储蓄账户4月15日22时23分收入人民币1.00元",
                postedAt = 111L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(NotificationSourceApp.UNKNOWN, candidate.sourceApp)
        assertEquals(ConfidenceLevel.HIGH, candidate.confidence.level)
    }

    @Test
    fun `aggregated payment summary now follows pure score threshold`() = runTest {
        val result = localTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "[5条]微信支付: 已支付¥0.01",
                postedAt = 222L,
                sourceApp = NotificationSourceApp.WECHAT,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(MoneyDirection.EXPENSE, candidate.direction)
        assertEquals(ConfidenceLevel.HIGH, candidate.confidence.level)
        assertTrue(candidate.confidence.score >= HIGH_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun `expense keyword 支出 is recognized as expense direction`() = runTest {
        val result = localTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.example.notice",
                title = "账户通知",
                text = "本次支出人民币1.00元",
                postedAt = 333L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(MoneyDirection.EXPENSE, candidate.direction)
    }

    @Test
    fun `unionpay card consumption with unknown source reaches high confidence by text structure`() = runTest {
        val result = localTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.unionpay",
                title = "云闪付",
                text = "您尾号为3026的银行卡于14日17时30分消费17.20元",
                postedAt = 444L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(17.20, candidate.amount)
        assertEquals(MoneyDirection.EXPENSE, candidate.direction)
        assertEquals(NotificationSourceApp.UNKNOWN, candidate.sourceApp)
        assertEquals(ConfidenceLevel.HIGH, candidate.confidence.level)
        assertTrue(candidate.confidence.score >= HIGH_CONFIDENCE_THRESHOLD)
        assertTrue(candidate.modelFeatureSnapshotJson.contains("\"hasTailNumber\":true"), candidate.modelFeatureSnapshotJson)
        assertTrue(candidate.modelFeatureSnapshotJson.contains("\"hasTimeDayKeyword\":true"), candidate.modelFeatureSnapshotJson)
        assertTrue(candidate.modelFeatureSnapshotJson.contains("\"hasTimeHourKeyword\":true"), candidate.modelFeatureSnapshotJson)
    }

    @Test
    fun `pos card expense with unknown source reaches high confidence by scene and clock time`() = runTest {
        val result = localTransactionExtractor().extract(
            NotificationEvent(
                packageName = "com.example.notice",
                title = "交易提醒",
                text = "尾号3026卡5月14日17:30POS支出(无卡支付)17.20元。",
                postedAt = 555L,
            ),
        )

        assertTrue(result is OperationResult.Success)
        val candidate = (result as OperationResult.Success).data
        assertEquals(17.20, candidate.amount)
        assertEquals(MoneyDirection.EXPENSE, candidate.direction)
        assertEquals(NotificationSourceApp.UNKNOWN, candidate.sourceApp)
        assertEquals(ConfidenceLevel.HIGH, candidate.confidence.level)
        assertTrue(candidate.confidence.score >= HIGH_CONFIDENCE_THRESHOLD)
        assertTrue(candidate.modelFeatureSnapshotJson.contains("\"hasTimeMonthKeyword\":true"), candidate.modelFeatureSnapshotJson)
        assertTrue(candidate.modelFeatureSnapshotJson.contains("\"hasClockTime\":true"), candidate.modelFeatureSnapshotJson)
        assertTrue(candidate.modelFeatureSnapshotJson.contains("\"hasTransactionSceneKeyword\":true"), candidate.modelFeatureSnapshotJson)
    }
}

/**
 * 用途说明：
 * - 构造默认不读取云端模型的通知解析器。
 */
private fun localTransactionExtractor(): LocalTransactionExtractor {
    return LocalTransactionExtractor(cloudModelStore = NoOpCloudModelStore)
}

/**
 * 用途说明：
 * - 构造固定返回模型的仓库替身，避免解析器单测访问 Android DataStore。
 *
 * 参数说明：
 * - model：本次测试希望解析器读取到的云端模型。
 */
private fun fakeCloudModelStore(model: CloudConfidenceModel?): CloudModelStore {
    return object : CloudModelStore {
        override val activeModel: Flow<CloudConfidenceModel?> = flowOf(model)

        override suspend fun getActiveModel(): CloudConfidenceModel? = model

        override suspend fun saveGlobalModel(model: CloudConfidenceModel) = Unit

        override suspend fun clearActiveModel() = Unit
    }
}
