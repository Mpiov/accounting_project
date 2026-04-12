package com.autobook.cn.core.model

data class NotificationEvent(
    val id: Long = 0L,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    val rawSource: String = "notification",
)

enum class MoneyDirection {
    INCOME,
    EXPENSE,
}

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW,
}

data class ParseConfidence(
    val score: Float,
) {
    val level: ConfidenceLevel
        get() = when {
            score >= 0.85f -> ConfidenceLevel.HIGH
            score >= 0.6f -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
}

data class TransactionCandidate(
    val amount: Double,
    val direction: MoneyDirection,
    val merchant: String,
    val channel: String,
    val timestamp: Long,
    val orderId: String? = null,
    val confidence: ParseConfidence,
    val rawSource: String,
    val parsePath: String,
    val modelVersion: String = "local-mock-v1",
)

data class LedgerEntry(
    val id: Long = 0L,
    val amount: Double,
    val direction: MoneyDirection,
    val merchant: String,
    val channel: String,
    val timestamp: Long,
    val category: String,
    val note: String? = null,
    val traceSource: String,
    val tracePath: String,
    val traceModelVersion: String,
)

data class PendingEntry(
    val id: Long = 0L,
    val amount: Double,
    val direction: MoneyDirection,
    val merchant: String,
    val channel: String,
    val timestamp: Long,
    val confidence: ParseConfidence,
    val rawSource: String,
    val parsePath: String,
    val modelVersion: String,
    val reason: String,
)

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val autoAccountingEnabled: Boolean = true,
    val cloudAnalysisEnabled: Boolean = false,
)

data class NotificationProcessOutcome(
    val notificationId: Long,
    val destination: ProcessDestination,
)

enum class ProcessDestination {
    LEDGER,
    PENDING,
}
