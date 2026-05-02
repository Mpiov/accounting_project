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
 * - modelFeatureSnapshotJson：当前模型评分时使用的特征快照，不包含通知原文。
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
    val modelFeatureSnapshotJson: String = "",
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
    val cloudLearningEnabled: Boolean = true,
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
 * - 手机号登录成功后由服务器返回的账号会话。
 *
 * 参数说明：
 * - userId：服务器账号主键。
 * - phone：已绑定的手机号。
 * - accessToken：访问业务接口的短期令牌。
 * - refreshToken：刷新访问令牌的长期令牌。
 * - loggedInAt：本地记录的登录时间。
 * - source：会话来源，真实接入后应为 `server`。
 */
data class AuthSession(
    val userId: String,
    val phone: String,
    val accessToken: String,
    val refreshToken: String,
    val loggedInAt: Long,
    val source: String = "server",
)

/**
 * 用途说明：
 * - 当前账号登录状态。
 *
 * 参数说明：
 * - session：存在时表示已登录；为空时视为免费用户。
 */
data class AuthState(
    val session: AuthSession? = null,
) {
    /** 用途说明：当前是否已有手机号登录会话。 */
    val isLoggedIn: Boolean
        get() = session != null
}

/** 用途说明：会员等级。 */
enum class MembershipTier {
    FREE,
    MEMBER,
}

/** 用途说明：会员套餐类型。 */
enum class MembershipPlan {
    MONTHLY,
    YEARLY,
}

/** 用途说明：微信支付订单状态。 */
enum class MembershipPaymentState {
    CREATED,
    PAYING,
    PAID,
    CANCELLED,
    EXPIRED,
    FAILED,
}

/**
 * 用途说明：
 * - 当前用户的会员状态。
 *
 * 参数说明：
 * - userId：服务器账号主键，未登录时为空。
 * - phone：当前登录手机号，未登录时为空。
 * - tier：当前会员等级。
 * - expiresAt：会员过期时间戳，免费用户为空。
 * - serverVerified：本次会员状态是否来自服务器权威校验。
 * - serverTime：服务器返回的当前时间，用于后续对齐会员有效期。
 * - refreshedAt：本地刷新会员状态的时间。
 * - modelConfidenceEnabled：服务器是否允许该用户使用机器学习解析。
 * - cloudLearningEnabled：服务器是否允许该用户上传样本贡献全局模型。
 * - source：状态来源，便于区分服务器、占位实现或缓存。
 * - message：面向设置页的短说明。
 */
data class MembershipStatus(
    val userId: String? = null,
    val phone: String? = null,
    val tier: MembershipTier = MembershipTier.FREE,
    val expiresAt: Long? = null,
    val serverVerified: Boolean = false,
    val serverTime: Long? = null,
    val refreshedAt: Long? = null,
    val modelConfidenceEnabled: Boolean = false,
    val cloudLearningEnabled: Boolean = false,
    val source: String = "server-required",
    val message: String = "未登录，会员状态需要服务器确认。",
) {
    /** 用途说明：当前是否具备服务器确认过的会员能力。 */
    val isMember: Boolean
        get() = serverVerified &&
            tier == MembershipTier.MEMBER &&
            (expiresAt == null || expiresAt > System.currentTimeMillis())

    /** 用途说明：当前是否允许使用机器学习解析能力。 */
    val canUseMachineLearning: Boolean
        get() = isMember && modelConfidenceEnabled

    /** 用途说明：当前是否允许上传原文样本到服务器全局样本池。 */
    val canUseCloudLearning: Boolean
        get() = isMember && cloudLearningEnabled
}

/**
 * 用途说明：
 * - App 调起微信支付所需的订单参数。
 *
 * 参数说明：
 * - orderId：服务器生成的业务订单号。
 * - plan：本次购买的会员套餐。
 * - amountCents：订单金额，单位分。
 * - appId：微信开放平台移动应用 AppID。
 * - partnerId：微信支付商户号。
 * - prepayId：微信支付预支付交易会话标识。
 * - packageValue：微信 SDK 要求的 package 字段。
 * - nonceStr：签名随机串。
 * - timestamp：签名时间戳。
 * - sign：服务端生成的调起支付签名。
 */
data class MembershipPaymentOrder(
    val orderId: String,
    val plan: MembershipPlan,
    val amountCents: Long,
    val appId: String,
    val partnerId: String,
    val prepayId: String,
    val packageValue: String,
    val nonceStr: String,
    val timestamp: String,
    val sign: String,
)

/**
 * 用途说明：
 * - 云端学习能力的运行状态。
 *
 * 参数说明：
 * - enabledByUser：用户设置里的云端学习开关。
 * - isMember：当前是否会员。
 * - effectiveEnabled：是否真正允许上传原文到服务器全局样本池。
 * - lastUploadAt：最近一次上传样本时间。
 * - lastModelPullAt：最近一次拉取全局模型时间。
 * - activeModelVersion：当前生效的全局模型版本或本地规则版本。
 * - message：面向设置页的状态说明。
 */
data class CloudLearningState(
    val enabledByUser: Boolean = true,
    val isMember: Boolean = false,
    val effectiveEnabled: Boolean = false,
    val lastUploadAt: Long? = null,
    val lastModelPullAt: Long? = null,
    val activeModelVersion: String = "local-rules-v1",
    val message: String = "免费版仅使用普通规则算法。",
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
