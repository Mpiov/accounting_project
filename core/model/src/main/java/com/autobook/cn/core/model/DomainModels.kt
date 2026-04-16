package com.autobook.cn.core.model

/**
 * 用途说明：
 * - 通知监听服务捕获后的原始通知载荷。
 */
data class NotificationEvent(
    val id: Long = 0L,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    val rawSource: String = "notification",
    val sourceApp: NotificationSourceApp = NotificationSourceApp.UNKNOWN,
    val processStatus: NotificationProcessStatus = NotificationProcessStatus.RECEIVED,
    val lastError: String? = null,
    val retryCount: Int = 0,
    val screenshotRef: String? = null,
)

/** 用途说明：支持的通知来源类型。 */
enum class NotificationSourceApp {
    WECHAT,
    ALIPAY,
    BANK_SMS,
    UNKNOWN,
}

/** 用途说明：通知在处理流水线中的状态。 */
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

/** 用途说明：交易方向。 */
enum class MoneyDirection {
    INCOME,
    EXPENSE,
    UNKNOWN,
}

/** 用途说明：供策略判断和界面展示使用的置信度等级。 */
enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW,
}

/** 用途说明：自动入账所需的高置信度阈值。 */
const val HIGH_CONFIDENCE_THRESHOLD: Float = 0.86f

/** 用途说明：进入待确认所需的中置信度阈值。 */
const val MEDIUM_CONFIDENCE_THRESHOLD: Float = 0.62f

/** 用途说明：待确认区间上限分数。 */
const val PENDING_CONFIDENCE_MAX_SCORE: Float = 0.85f

/**
 * 用途说明：
 * - 数值型置信度封装。
 *
 * 参数说明：
 * - score：范围在 0 到 1 之间的原始分数。
 */
data class ParseConfidence(
    val score: Float,
) {
    /**
     * 用途说明：
     * - 把原始分数映射成粗粒度等级，便于策略判断和界面展示。
     */
    val level: ConfidenceLevel
        get() = when {
            score >= HIGH_CONFIDENCE_THRESHOLD -> ConfidenceLevel.HIGH
            score >= MEDIUM_CONFIDENCE_THRESHOLD -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
}

/**
 * 用途说明：
 * - 入账前的标准化交易候选。
 *
 * 参数说明：
 * - amount：识别出的金额。
 * - direction：识别出的收支方向。
 * - merchant：商户或对手方。
 * - channel：识别来源渠道。
 * - timestamp：交易时间戳。
 * - orderId：订单号，可为空。
 * - confidence：候选置信度。
 * - confidenceReason：置信度原因说明。
 * - categoryHint：建议分类。
 * - isDuplicateCandidate：是否疑似重复。
 * - screenshotRef：关联截图引用，可为空。
 * - rawSource：原始来源标签。
 * - parsePath：解析链路标签。
 * - modelVersion：当前解析实现版本号。
 * - llmSuggestedAutoPost：本地语义校验是否建议自动入账，可为空。
 * - sourceApp：来源应用类型。
 */
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
    val llmSuggestedAutoPost: Boolean? = null,
    val sourceApp: NotificationSourceApp = NotificationSourceApp.UNKNOWN,
)

/**
 * 用途说明：
 * - 已写入账本的最终记录。
 */
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

/**
 * 用途说明：
 * - 待确认记录。
 */
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

/**
 * 用途说明：
 * - 运行时开关与留存配置。
 */
data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val autoAccountingEnabled: Boolean = true,
    val cloudAnalysisEnabled: Boolean = true,
    val localLlmConfidenceBoostEnabled: Boolean = false,
    val captureEnabled: Boolean = false,
    val notificationRetentionDays: Int = 30,
    val screenshotRetentionDays: Int = 7,
    val monthlyBudgets: Map<String, Double> = emptyMap(),
    val enabledSources: Set<NotificationSourceApp> = setOf(
        NotificationSourceApp.WECHAT,
        NotificationSourceApp.ALIPAY,
        NotificationSourceApp.BANK_SMS,
        NotificationSourceApp.UNKNOWN,
    ),
)

/**
 * 用途说明：
 * - 单条通知完成处理后的结果摘要。
 */
data class NotificationProcessOutcome(
    val notificationId: Long,
    val destination: ProcessDestination,
    val status: NotificationProcessStatus,
    val screenshotRef: String? = null,
    val message: String? = null,
)

/** 用途说明：通知处理完成后的最终落点。 */
enum class ProcessDestination {
    LEDGER,
    PENDING,
    FILTERED,
    DUPLICATE,
}

/**
 * 用途说明：
 * - 截图授权与截图执行流程中的会话信息。
 */
data class CaptureSession(
    val sessionId: String,
    val state: CaptureSessionState,
    val reason: String,
)

/** 用途说明：截图会话状态。 */
enum class CaptureSessionState {
    REQUESTED,
    AUTHORIZED,
    CAPTURED,
    CANCELLED,
    FAILED,
}

/** 用途说明：图片导入分析链路标签。 */
enum class ImageImportAnalysisPath {
    CLOUD_LLM_PROXY,
    MANUAL_CONFIRMATION_REQUIRED,
}

/** 用途说明：图片候选交易状态。 */
enum class ImageImportTransactionState {
    ACTIVE,
    REFUNDED,
    RETURNED,
    REVERSED,
    CANCELLED,
    UNKNOWN,
}

/**
 * 用途说明：
 * - 图片导入分析出的单条候选交易。
 */
data class ImageImportCandidate(
    val amount: Double? = null,
    val timestamp: Long? = null,
    val direction: MoneyDirection = MoneyDirection.EXPENSE,
    val merchant: String = "",
    val category: String = "待分类",
    val note: String = "",
    val orderId: String? = null,
    val confidence: ParseConfidence = ParseConfidence(0f),
    val transactionState: ImageImportTransactionState = ImageImportTransactionState.UNKNOWN,
    val stateReason: String = "",
    val evidence: String = "",
    val filterReason: String? = null,
)

/**
 * 用途说明：
 * - 图片导入给编辑页的统一建议输出。
 */
data class ImageImportSuggestion(
    val amount: Double? = null,
    val timestamp: Long? = null,
    val direction: MoneyDirection = MoneyDirection.EXPENSE,
    val merchant: String = "",
    val category: String = "待分类",
    val note: String = "",
    val orderId: String? = null,
    val confidence: ParseConfidence = ParseConfidence(0f),
    val tracePath: ImageImportAnalysisPath,
    val message: String,
    val requiresManualConfirmation: Boolean = true,
    val candidates: List<ImageImportCandidate> = emptyList(),
    val filteredOutCandidateCount: Int = 0,
    val duplicateFilteredCount: Int = 0,
    val inactiveFilteredCount: Int = 0,
)
