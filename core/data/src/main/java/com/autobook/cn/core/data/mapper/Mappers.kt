package com.autobook.cn.core.data.mapper

import com.autobook.cn.core.database.entity.LedgerEntryEntity
import com.autobook.cn.core.database.entity.NotificationEventEntity
import com.autobook.cn.core.database.entity.PendingEntryEntity
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessStatus
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.PendingEntry
import com.autobook.cn.core.model.TransactionCandidate

/**
 * 通知领域模型转为 Room 实体。
 *
 * 用途说明：
 * - 把监听服务收到的通知快照持久化到数据库。
 * - 当前不再保存通知级精确哈希，只落库处理链真正需要的字段。
 */
fun NotificationEvent.toEntity(): NotificationEventEntity = NotificationEventEntity(
    id = id,
    packageName = packageName,
    title = title,
    text = text,
    postedAt = postedAt,
    rawSource = rawSource,
    sourceApp = sourceApp.name,
    processStatus = processStatus.name,
    lastError = lastError,
    retryCount = retryCount,
    screenshotRef = screenshotRef,
)

/**
 * 通知 Room 实体转回领域模型。
 *
 * 用途说明：
 * - 把数据库中的通知记录恢复成领域对象，供处理链和界面继续复用。
 *
 * 参数说明：
 * - 接收者：数据库中的通知实体对象。
 */
fun NotificationEventEntity.toDomain(): NotificationEvent = NotificationEvent(
    id = id,
    packageName = packageName,
    title = title,
    text = text,
    postedAt = postedAt,
    rawSource = rawSource,
    sourceApp = NotificationSourceApp.valueOf(sourceApp),
    processStatus = NotificationProcessStatus.valueOf(processStatus),
    lastError = lastError,
    retryCount = retryCount,
    screenshotRef = screenshotRef,
)

/**
 * 账本领域模型转为 Room 实体。
 *
 * 用途说明：
 * - 把已经确认的账本记录写入数据库。
 * - 保留 trace 字段，方便回查来源通知与解析模型。
 */
fun LedgerEntry.toEntity(): LedgerEntryEntity = LedgerEntryEntity(
    id = id,
    amount = amount,
    direction = direction.name,
    merchant = merchant,
    channel = channel,
    timestamp = timestamp,
    category = category,
    orderId = orderId,
    note = note,
    traceSource = traceSource,
    tracePath = tracePath,
    traceModelVersion = traceModelVersion,
    traceNotificationId = traceNotificationId,
    editedManually = editedManually,
)

/**
 * 账本 Room 实体转回领域模型。
 *
 * 参数说明：
 * - 接收者：数据库中的账本实体对象。
 */
fun LedgerEntryEntity.toDomain(): LedgerEntry = LedgerEntry(
    id = id,
    amount = amount,
    direction = MoneyDirection.valueOf(direction),
    merchant = merchant,
    channel = channel,
    timestamp = timestamp,
    category = category,
    orderId = orderId,
    note = note,
    traceSource = traceSource,
    tracePath = tracePath,
    traceModelVersion = traceModelVersion,
    traceNotificationId = traceNotificationId,
    editedManually = editedManually,
)

/**
 * 将交易候选提升为正式账本记录，并保留链路追踪字段。
 *
 * 参数说明：
 * - category：最终写入账本的分类名称。
 * - notificationId：来源通知主键，用于回查通知处理链。
 * - editedManually：是否由用户手动编辑后保存。
 * - note：附加备注。
 */
fun TransactionCandidate.toLedgerEntry(
    category: String = categoryHint,
    notificationId: Long? = null,
    editedManually: Boolean = false,
    note: String? = null,
): LedgerEntry = LedgerEntry(
    amount = amount,
    direction = direction,
    merchant = merchant,
    channel = channel,
    timestamp = timestamp,
    category = category,
    orderId = orderId,
    note = note,
    traceSource = rawSource,
    tracePath = parsePath,
    traceModelVersion = modelVersion,
    traceNotificationId = notificationId,
    editedManually = editedManually,
)

/**
 * 将交易候选包装成待确认记录。
 *
 * 参数说明：
 * - reason：进入待确认的原因说明。
 * - notificationId：来源通知主键，用于链路追踪。
 */
fun TransactionCandidate.toPendingEntry(
    reason: String,
    notificationId: Long? = null,
): PendingEntry = PendingEntry(
    amount = amount,
    direction = direction,
    merchant = merchant,
    channel = channel,
    timestamp = timestamp,
    confidence = confidence,
    orderId = orderId,
    categoryHint = categoryHint,
    rawSource = rawSource,
    parsePath = parsePath,
    modelVersion = modelVersion,
    reason = reason,
    traceNotificationId = notificationId,
)

/**
 * 待确认领域模型转为 Room 实体。
 *
 * 参数说明：
 * - 接收者：待确认领域对象。
 */
fun PendingEntry.toEntity(): PendingEntryEntity = PendingEntryEntity(
    id = id,
    amount = amount,
    direction = direction.name,
    merchant = merchant,
    channel = channel,
    timestamp = timestamp,
    confidenceScore = confidence.score,
    orderId = orderId,
    categoryHint = categoryHint,
    rawSource = rawSource,
    parsePath = parsePath,
    modelVersion = modelVersion,
    reason = reason,
    traceNotificationId = traceNotificationId,
    isFalsePositive = isFalsePositive,
    editedManually = editedManually,
)

/**
 * 待确认 Room 实体转回领域模型。
 *
 * 参数说明：
 * - 接收者：数据库中的待确认实体对象。
 */
fun PendingEntryEntity.toDomain(): PendingEntry = PendingEntry(
    id = id,
    amount = amount,
    direction = MoneyDirection.valueOf(direction),
    merchant = merchant,
    channel = channel,
    timestamp = timestamp,
    confidence = ParseConfidence(confidenceScore),
    orderId = orderId,
    categoryHint = categoryHint,
    rawSource = rawSource,
    parsePath = parsePath,
    modelVersion = modelVersion,
    reason = reason,
    traceNotificationId = traceNotificationId,
    isFalsePositive = isFalsePositive,
    editedManually = editedManually,
)
