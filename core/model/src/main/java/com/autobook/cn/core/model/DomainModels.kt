package com.autobook.cn.core.model

data class NotificationEvent(
    val id: Long = 0L,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    val rawSource: String = "notification",
    val dedupeKey: String = "",
    val sourceApp: NotificationSourceApp = NotificationSourceApp.UNKNOWN,
    val processStatus: NotificationProcessStatus = NotificationProcessStatus.RECEIVED,
    val lastError: String? = null,
    val retryCount: Int = 0,
    val screenshotRef: String? = null,
)

enum class NotificationSourceApp {
    WECHAT,
    ALIPAY,
    BANK_SMS,
    UNKNOWN,
}

enum class NotificationProcessStatus {
    RECEIVED,
    FILTERED_OUT,
    DUPLICATE_SKIPPED,
    PROCESSING,
    LEDGER_POSTED,
    PENDING_REVIEW,
    FAILED_RETRYABLE,
    FAILED_TERMINAL,
}

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
    val confidenceReason: String,
    val categoryHint: String = "待分类",
    val isDuplicateCandidate: Boolean = false,
    val rawSource: String,
    val parsePath: String,
    val modelVersion: String = "local-mock-v1",
    val sourceApp: NotificationSourceApp = NotificationSourceApp.UNKNOWN,
)

data class LedgerEntry(
    val id: Long = 0L,
    val amount: Double,
    val direction: MoneyDirection,
    val merchant: String,
    val channel: String,
    val timestamp: Long,
    val category: String,
    val orderId: String? = null,
    val note: String? = null,
    val traceSource: String,
    val tracePath: String,
    val traceModelVersion: String,
    val traceNotificationId: Long? = null,
    val editedManually: Boolean = false,
)

data class PendingEntry(
    val id: Long = 0L,
    val amount: Double,
    val direction: MoneyDirection,
    val merchant: String,
    val channel: String,
    val timestamp: Long,
    val confidence: ParseConfidence,
    val orderId: String? = null,
    val categoryHint: String = "待分类",
    val rawSource: String,
    val parsePath: String,
    val modelVersion: String,
    val reason: String,
    val traceNotificationId: Long? = null,
    val isFalsePositive: Boolean = false,
    val editedManually: Boolean = false,
)

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val autoAccountingEnabled: Boolean = true,
    val cloudAnalysisEnabled: Boolean = false,
    val captureEnabled: Boolean = false,
    val ocrEnabled: Boolean = false,
    val notificationRetentionDays: Int = 30,
    val screenshotRetentionDays: Int = 7,
    val enabledSources: Set<NotificationSourceApp> = setOf(
        NotificationSourceApp.WECHAT,
        NotificationSourceApp.ALIPAY,
        NotificationSourceApp.BANK_SMS,
    ),
)

data class NotificationProcessOutcome(
    val notificationId: Long,
    val destination: ProcessDestination,
    val status: NotificationProcessStatus,
    val message: String? = null,
)

enum class ProcessDestination {
    LEDGER,
    PENDING,
    FILTERED,
    DUPLICATE,
}

data class CaptureSession(
    val sessionId: String,
    val state: CaptureSessionState,
    val reason: String,
)

enum class CaptureSessionState {
    REQUESTED,
    AUTHORIZED,
    CAPTURED,
    CANCELLED,
    FAILED,
}
