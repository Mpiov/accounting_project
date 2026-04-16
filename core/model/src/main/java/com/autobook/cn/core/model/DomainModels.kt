package com.autobook.cn.core.model

/**
 * 通知监听服务捕获后的原始通知载荷。
 *
 * 用途说明：
 * - 保存通知来源、标题、正文、时间戳和处理状态。
 * - 作为通知自动记账链路的统一输入与回溯对象。
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

/** 支持的通知来源类型。 */
enum class NotificationSourceApp {
    WECHAT,
    ALIPAY,
    BANK_SMS,
    UNKNOWN,
}

/** 通知在处理流水线中的状态。 */
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

/** 资金方向。 */
enum class MoneyDirection {
    INCOME,
    EXPENSE,
    UNKNOWN,
}

/** 供界面展示和策略判断使用的置信度等级。 */
enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW,
}

/** 自动入账所需的高置信度阈值。 */
const val HIGH_CONFIDENCE_THRESHOLD: Float = 0.86f

/** 进入待确认所需的中置信度阈值。 */
const val MEDIUM_CONFIDENCE_THRESHOLD: Float = 0.62f

/** 待确认上限分数，用于把未过高置信门槛的候选压回到可人工确认区间。 */
const val PENDING_CONFIDENCE_MAX_SCORE: Float = 0.85f

/**
 * 数值型置信度。
 *
 * 参数说明：
 * - score：范围在 0 到 1 之间的原始分数。
 */
data class ParseConfidence(
    val score: Float,
) {
    /**
     * 把原始分数映射成粗粒度等级，便于策略判断和界面展示。
     */
    val level: ConfidenceLevel
        get() =
            when {
                score >= HIGH_CONFIDENCE_THRESHOLD -> ConfidenceLevel.HIGH
                score >= MEDIUM_CONFIDENCE_THRESHOLD -> ConfidenceLevel.MEDIUM
                else -> ConfidenceLevel.LOW
            }
}

/**
 * 入账或进入待确认前的标准化交易候选。
 *
 * 用途说明：
 * - 承载解析器产出的金额、方向、商户、分类建议和链路追踪信息。
 * - 由后续流水线统一决定是自动入账、待确认还是过滤。
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
    /** 记录本地 LLM 是否建议自动入账；为空表示当前候选未经过本地 LLM 决策。 */
    val llmSuggestedAutoPost: Boolean? = null,
    val sourceApp: NotificationSourceApp = NotificationSourceApp.UNKNOWN,
)

/**
 * 已确认写入账本的最终记录。
 *
 * 用途说明：
 * - 保存正式账本信息和链路追踪字段。
 * - 支持后续从账本反查来源通知、解析路径与模型版本。
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
 * 低置信度或存在歧义、需要人工确认的待处理项。
 *
 * 用途说明：
 * - 保留候选交易的主要结构化字段。
 * - 允许用户后续确认、忽略或标记为误报。
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
 * 运行时开关与留存配置。
 *
 * 用途说明：
 * - 汇总自动记账、模型、截图与来源开关等设置。
 * - 作为设置页和处理链统一读取的配置快照。
 */
data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val autoAccountingEnabled: Boolean = true,
    val cloudAnalysisEnabled: Boolean = true,
    val localLlmConfidenceBoostEnabled: Boolean = false,
    val selectedLocalModelId: String = DEFAULT_LOCAL_MODEL_ID,
    val captureEnabled: Boolean = false,
    val ocrEnabled: Boolean = false,
    val notificationRetentionDays: Int = 30,
    val screenshotRetentionDays: Int = 7,
    /** 以 `yyyy-MM` 为键保存每月预算。 */
    val monthlyBudgets: Map<String, Double> = emptyMap(),
    /**
     * 来源开关默认全部开启。
     *
     * 用途说明：
     * - “其它来源”在产品语义上同时覆盖 `BANK_SMS` 和 `UNKNOWN`。
     * - 默认集合同时保留这两个来源，避免升级后无意缩窄监听范围。
     */
    val enabledSources: Set<NotificationSourceApp> = setOf(
        NotificationSourceApp.WECHAT,
        NotificationSourceApp.ALIPAY,
        NotificationSourceApp.BANK_SMS,
        NotificationSourceApp.UNKNOWN,
    ),
)

/** 单条通知完成处理后的结果摘要。 */
data class NotificationProcessOutcome(
    val notificationId: Long,
    val destination: ProcessDestination,
    val status: NotificationProcessStatus,
    val screenshotRef: String? = null,
    val message: String? = null,
)

/** 通知处理完成后最终落到的位置。 */
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

/** 截图会话状态。 */
enum class CaptureSessionState {
    REQUESTED,
    AUTHORIZED,
    CAPTURED,
    CANCELLED,
    FAILED,
}

/**
 * 本地模型规格描述。
 *
 * 参数说明：
 * - id：模型唯一标识。
 * - displayName：界面显示名称。
 * - family：模型家族名称。
 * - quantization：量化规格。
 * - runtimeHint：推荐运行时。
 * - estimatedSizeBytes：预计文件大小。
 * - downloadUrl：下载地址。
 * - isRecommended：是否为当前推荐模型。
 */
data class LocalModelSpec(
    val id: String,
    val displayName: String,
    val family: String,
    val quantization: String,
    val runtimeHint: String,
    val estimatedSizeBytes: Long,
    val downloadUrl: String,
    val isRecommended: Boolean = false,
)

/** 本地模型安装状态。 */
enum class LocalModelInstallStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    FAILED,
}

/**
 * 本地模型状态快照。
 *
 * 用途说明：
 * - 汇总当前可选模型、安装状态、下载进度和状态文案。
 * - 供设置页和端侧推理层统一读取。
 */
data class LocalModelSnapshot(
    val availableModels: List<LocalModelSpec> = emptyList(),
    val selectedModelId: String = DEFAULT_LOCAL_MODEL_ID,
    val selectedModelPath: String? = null,
    val installStatus: LocalModelInstallStatus = LocalModelInstallStatus.NOT_DOWNLOADED,
    val downloadProgressPercent: Int? = null,
    val statusMessage: String = "尚未下载本地模型",
)

/** 图片导入记账的分析路径标识。 */
enum class ImageImportAnalysisPath {
    CLOUD_LLM_PROXY,
    OCR_PLUS_LOCAL_LLM,
    MANUAL_CONFIRMATION_REQUIRED,
}

/** 图片候选交易当前状态。 */
enum class ImageImportTransactionState {
    ACTIVE,
    REFUNDED,
    RETURNED,
    REVERSED,
    CANCELLED,
    UNKNOWN,
}

/**
 * 图片导入分析出的单条候选交易。
 *
 * 用途说明：
 * - 承载大模型或本地模型从截图中提取出的单笔候选记录。
 * - 后续本地过滤层会基于状态、重复风险和证据继续筛选。
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
 * 图片导入记账建议。
 *
 * 用途说明：
 * - 作为用户主动上传图片记账时的统一输出协议。
 * - 附带保留候选列表、过滤统计和面向用户的说明文案。
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

/** 当前默认推荐的本地模型标识。 */
const val DEFAULT_LOCAL_MODEL_ID: String = "qwen3_1_7b_q8_0"
