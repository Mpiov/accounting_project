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
        get() =
            when {
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
    /** 记录本地 LLM 是否建议自动入账；为空代表当前候选未经过本地 LLM 决策。 */
    val llmSuggestedAutoPost: Boolean? = null,
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
    val localLlmConfidenceBoostEnabled: Boolean = false,
    val selectedLocalModelId: String = DEFAULT_LOCAL_MODEL_ID,
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

/**
 * 本地模型规格描述。
 *
 * 用途说明：
 * - 统一描述可供下载和选择的端侧模型，便于设置页、下载器和推理适配层共享同一份元数据。
 *
 * 参数说明：
 * - id：模型唯一标识。
 * - displayName：设置页展示名称。
 * - family：模型家族名称，例如 Qwen3。
 * - quantization：量化规格。
 * - runtimeHint：推荐运行时，例如 llama.cpp。
 * - estimatedSizeBytes：模型文件预计大小。
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

/** 本地模型当前安装状态。 */
enum class LocalModelInstallStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    FAILED,
}

/**
 * 本地模型快照。
 *
 * 用途说明：
 * - 为设置页和推理层提供单一来源的模型状态，包括当前选择、安装状态和下载进度。
 *
 * 参数说明：
 * - availableModels：当前可选模型列表。
 * - selectedModelId：当前设置中选中的模型。
 * - selectedModelPath：已落地的模型文件路径；未下载时为空。
 * - installStatus：当前安装状态。
 * - downloadProgressPercent：下载进度，未知时为空。
 * - statusMessage：面向 UI 的状态说明。
 */
data class LocalModelSnapshot(
    val availableModels: List<LocalModelSpec> = emptyList(),
    val selectedModelId: String = DEFAULT_LOCAL_MODEL_ID,
    val selectedModelPath: String? = null,
    val installStatus: LocalModelInstallStatus = LocalModelInstallStatus.NOT_DOWNLOADED,
    val downloadProgressPercent: Int? = null,
    val statusMessage: String = "尚未下载本地模型",
)

/**
 * 图片导入记账的分析路径标识。
 *
 * 用途说明：
 * - 用统一枚举标记本次图片建议是来自 `gemini-3-flash-preview` 代理链路、本地 OCR + 端侧 LLM，还是仅进入手动确认草稿。
 * - 便于后续在 UI、埋点和审计字段中统一展示来源。
 */
enum class ImageImportAnalysisPath {
    GEMINI_3_1_PRO_PREVIEW_MOCK,
    OCR_PLUS_LOCAL_LLM,
    MANUAL_CONFIRMATION_REQUIRED,
}

/**
 * 图片导入记账建议。
 *
 * 用途说明：
 * - 作为“用户主动上传图片记账”能力的统一输出协议。
 * - 当前阶段由 `gemini-3-flash-preview` 代理链路或本地 OCR/LLM 分析结果驱动编辑页预填。
 *
 * 参数说明：
 * - amount：建议金额；为空表示当前无法可靠提取金额，需要用户手动输入。
 * - direction：建议收支方向。
 * - merchant：建议商户或交易对象。
 * - category：建议分类。
 * - note：建议备注；当前也会包含“来自本地 OCR + 端侧 LLM”这类链路说明，帮助用户理解建议来源。
 * - orderId：建议订单号。
 * - tracePath：本次建议的分析路径。
 * - message：面向用户的状态说明。
 * - requiresManualConfirmation：是否要求用户手动确认后才能保存。
 */
data class ImageImportSuggestion(
    val amount: Double? = null,
    val direction: MoneyDirection = MoneyDirection.EXPENSE,
    val merchant: String = "",
    val category: String = "待分类",
    val note: String = "",
    val orderId: String? = null,
    val tracePath: ImageImportAnalysisPath,
    val message: String,
    val requiresManualConfirmation: Boolean = true,
)

/** 当前默认推荐的本地模型标识。 */
const val DEFAULT_LOCAL_MODEL_ID: String = "qwen3_1_7b_q8_0"
