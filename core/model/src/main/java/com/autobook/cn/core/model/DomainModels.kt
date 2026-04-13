package com.autobook.cn.core.model

/** 通知监听服务捕获后的原始通知载荷。 */
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

/** 当前支持的通知来源类型。 */
enum class NotificationSourceApp {
    WECHAT,
    ALIPAY,
    BANK_SMS,
    UNKNOWN,
}

/** 通知在处理流水线中的状态流转。 */
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

/** 从通知内容中推断出的资金方向。 */
enum class MoneyDirection {
    INCOME,
    EXPENSE,
    UNKNOWN,
}

/** 基于原始解析分数映射出的展示级置信度等级。 */
enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW,
}

/** 本地解析或云端解析返回的数值型置信度。 */
data class ParseConfidence(
    val score: Float,
) {
    /** 将原始分数映射为更适合策略判断和界面展示的粗粒度等级。 */
    val level: ConfidenceLevel
        get() = when {
            score >= 0.85f -> ConfidenceLevel.HIGH
            score >= 0.6f -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
}

/** 在入账或进入待确认队列前的标准化交易候选。 */
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
    val screenshotRef: String? = null,
    val rawSource: String,
    val parsePath: String,
    val modelVersion: String = "local-mock-v1",
    val sourceApp: NotificationSourceApp = NotificationSourceApp.UNKNOWN,
)

/** 已确认写入账本的最终记录。 */
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

/** 低置信度或存在歧义、需要人工确认的待处理项。 */
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

/** 决定处理策略的运行时开关与留存配置。 */
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

/** 单条通知完成流水线处理后的结果摘要。 */
data class NotificationProcessOutcome(
    val notificationId: Long,
    val destination: ProcessDestination,
    val status: NotificationProcessStatus,
    val screenshotRef: String? = null,
    val message: String? = null,
)

/** 通知处理完成后最终落到的目标位置。 */
enum class ProcessDestination {
    LEDGER,
    PENDING,
    FILTERED,
    DUPLICATE,
}

/** 截图授权与截图执行流程的会话信息。 */
data class CaptureSession(
    val sessionId: String,
    val state: CaptureSessionState,
    val reason: String,
)

/** 占位实现或未来真实截图协调器会返回的会话状态。 */
enum class CaptureSessionState {
    REQUESTED,
    AUTHORIZED,
    CAPTURED,
    CANCELLED,
    FAILED,
}
