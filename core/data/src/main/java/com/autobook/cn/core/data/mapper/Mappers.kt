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

/** 通知领域模型转为 Room 实体。 */
fun NotificationEvent.toEntity(): NotificationEventEntity = NotificationEventEntity(
    id = id,
    packageName = packageName,
    title = title,
    text = text,
    postedAt = postedAt,
    rawSource = rawSource,
    dedupeKey = dedupeKey,
    sourceApp = sourceApp.name,
    processStatus = processStatus.name,
    lastError = lastError,
    retryCount = retryCount,
    screenshotRef = screenshotRef,
)

/** 通知 Room 实体转回领域模型。 */
fun NotificationEventEntity.toDomain(): NotificationEvent = NotificationEvent(
    id = id,
    packageName = packageName,
    title = title,
    text = text,
    postedAt = postedAt,
    rawSource = rawSource,
    dedupeKey = dedupeKey,
    sourceApp = NotificationSourceApp.valueOf(sourceApp),
    processStatus = NotificationProcessStatus.valueOf(processStatus),
    lastError = lastError,
    retryCount = retryCount,
    screenshotRef = screenshotRef,
)

/** 账本领域模型转为 Room 实体。 */
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

/** 账本 Room 实体转回领域模型。 */
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

/** 将交易候选提升为正式账本记录，并保留链路追踪字段。 */
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

/** 将交易候选包装成待确认记录。 */
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

/** 待确认领域模型转为 Room 实体。 */
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

/** 待确认 Room 实体转回领域模型。 */
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
