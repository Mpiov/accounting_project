package com.autobook.cn.core.data.mapper

import com.autobook.cn.core.database.entity.LedgerEntryEntity
import com.autobook.cn.core.database.entity.NotificationEventEntity
import com.autobook.cn.core.database.entity.PendingEntryEntity
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.ParseConfidence
import com.autobook.cn.core.model.PendingEntry
import com.autobook.cn.core.model.TransactionCandidate

fun NotificationEvent.toEntity(): NotificationEventEntity =
    NotificationEventEntity(
        id = id,
        packageName = packageName,
        title = title,
        text = text,
        postedAt = postedAt,
        rawSource = rawSource,
    )

fun LedgerEntry.toEntity(): LedgerEntryEntity =
    LedgerEntryEntity(
        id = id,
        amount = amount,
        direction = direction.name,
        merchant = merchant,
        channel = channel,
        timestamp = timestamp,
        category = category,
        note = note,
        traceSource = traceSource,
        tracePath = tracePath,
        traceModelVersion = traceModelVersion,
    )

fun LedgerEntryEntity.toDomain(): LedgerEntry =
    LedgerEntry(
        id = id,
        amount = amount,
        direction = MoneyDirection.valueOf(direction),
        merchant = merchant,
        channel = channel,
        timestamp = timestamp,
        category = category,
        note = note,
        traceSource = traceSource,
        tracePath = tracePath,
        traceModelVersion = traceModelVersion,
    )

fun TransactionCandidate.toLedgerEntry(category: String = "待分类"): LedgerEntry =
    LedgerEntry(
        amount = amount,
        direction = direction,
        merchant = merchant,
        channel = channel,
        timestamp = timestamp,
        category = category,
        traceSource = rawSource,
        tracePath = parsePath,
        traceModelVersion = modelVersion,
    )

fun TransactionCandidate.toPendingEntry(reason: String): PendingEntry =
    PendingEntry(
        amount = amount,
        direction = direction,
        merchant = merchant,
        channel = channel,
        timestamp = timestamp,
        confidence = confidence,
        rawSource = rawSource,
        parsePath = parsePath,
        modelVersion = modelVersion,
        reason = reason,
    )

fun PendingEntry.toEntity(): PendingEntryEntity =
    PendingEntryEntity(
        id = id,
        amount = amount,
        direction = direction.name,
        merchant = merchant,
        channel = channel,
        timestamp = timestamp,
        confidenceScore = confidence.score,
        rawSource = rawSource,
        parsePath = parsePath,
        modelVersion = modelVersion,
        reason = reason,
    )

fun PendingEntryEntity.toDomain(): PendingEntry =
    PendingEntry(
        id = id,
        amount = amount,
        direction = MoneyDirection.valueOf(direction),
        merchant = merchant,
        channel = channel,
        timestamp = timestamp,
        confidence = ParseConfidence(confidenceScore),
        rawSource = rawSource,
        parsePath = parsePath,
        modelVersion = modelVersion,
        reason = reason,
    )
