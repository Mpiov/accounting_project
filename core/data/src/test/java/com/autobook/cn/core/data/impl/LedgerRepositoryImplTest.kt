package com.autobook.cn.core.data.impl

import com.autobook.cn.core.database.dao.LedgerEntryDao
import com.autobook.cn.core.database.entity.LedgerEntryEntity
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.TransactionCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * 验证 `LedgerRepositoryImpl` 的通知自动记账判重行为。
 *
 * 用途说明：
 * - 锁定“30 秒时间窗”是否真正传递到了 DAO。
 * - 锁定账本级判重已经不再依赖商户字段命中。
 */
class LedgerRepositoryImplTest {
    @Test
    fun findPotentialDuplicateUsesThirtySecondWindowWithoutMerchantConstraint() = runTest {
        val ledgerEntryDao = RecordingLedgerEntryDao()
        val repository = LedgerRepositoryImpl(ledgerEntryDao = ledgerEntryDao)
        val candidate = TransactionCandidate(
            amount = 18.88,
            direction = MoneyDirection.EXPENSE,
            merchant = "支付宝商户名",
            channel = "支付宝",
            timestamp = 1_700_000L,
            orderId = "ORDER-001",
            confidence = ParseConfidence(0.95f),
            confidenceReason = "测试高置信度候选",
            rawSource = "notification",
            parsePath = "local-parser",
        )

        val duplicate = repository.findPotentialDuplicate(candidate)

        // 核心断言：当前账本级判重窗口已经从前后 15 秒扩大到前后 30 秒。
        assertEquals(candidate.timestamp - 30_000L, ledgerEntryDao.lastWindowStart)
        assertEquals(candidate.timestamp + 30_000L, ledgerEntryDao.lastWindowEnd)
        // 核心断言：仓储层不再把商户作为 DAO 判重入参，因此这里只验证其它关键条件被正确透传。
        assertEquals(candidate.orderId, ledgerEntryDao.lastOrderId)
        assertEquals(candidate.amount, ledgerEntryDao.lastAmount)
        assertEquals(candidate.direction.name, ledgerEntryDao.lastDirection)
        // 返回值仍然会正常转回领域模型，说明放宽条件没有破坏既有调用链。
        assertNotNull(duplicate)
        assertEquals("银行卡动账提醒", duplicate?.merchant)
    }
}

/**
 * 记录 `LedgerRepositoryImpl` 传给 DAO 的判重参数。
 *
 * 用途说明：
 * - 用最轻量的假实现替代真实 Room DAO，避免单测依赖数据库环境。
 * - 重点观察时间窗、金额、方向和订单号是否按预期透传。
 */
private class RecordingLedgerEntryDao : LedgerEntryDao {
    var lastOrderId: String? = null
    var lastAmount: Double? = null
    var lastDirection: String? = null
    var lastWindowStart: Long? = null
    var lastWindowEnd: Long? = null

    override suspend fun insert(entry: LedgerEntryEntity): Long = 0L

    override fun observeAll(): Flow<List<LedgerEntryEntity>> = emptyFlow()

    override suspend fun getById(id: Long): LedgerEntryEntity? = null

    override suspend fun update(entry: LedgerEntryEntity) = Unit

    override suspend fun deleteById(id: Long) = Unit

    override suspend fun findPotentialDuplicate(
        orderId: String?,
        amount: Double,
        direction: String,
        windowStart: Long,
        windowEnd: Long,
    ): LedgerEntryEntity? {
        // 把本次查询参数全部记下来，供上面的断言直接验证。
        lastOrderId = orderId
        lastAmount = amount
        lastDirection = direction
        lastWindowStart = windowStart
        lastWindowEnd = windowEnd
        return LedgerEntryEntity(
            id = 7L,
            amount = amount,
            direction = direction,
            merchant = "银行卡动账提醒",
            channel = "银行卡",
            timestamp = windowStart + 1_000L,
            category = "测试分类",
            orderId = orderId,
            note = null,
            traceSource = "notification",
            tracePath = "local-parser",
            traceModelVersion = "test-model",
            traceNotificationId = 99L,
            editedManually = false,
        )
    }

    override suspend fun findPotentialImageImportDuplicate(
        windowStart: Long?,
        windowEnd: Long?,
        amount: Double,
        direction: String,
    ): LedgerEntryEntity? = null

    override suspend fun findPotentialImageImportDuplicateWithoutTimestamp(
        amount: Double,
        direction: String,
    ): LedgerEntryEntity? = null
}
