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
