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

/** 用途说明：会员状态尚未从服务器刷新前的本地展示兜底值；真实额度和扣减以服务器配置为准。 */
const val AUTO_LEDGER_FREE_TRIAL_LIMIT: Int = 10

/**
 * 用途说明：
 * - 服务端发布给客户端的 Android 新版本元数据。
 * - 当前只用于提示用户并跳转浏览器下载，不在 App 内静默下载 APK，避免占用小带宽服务器。
 *
 * 参数说明：
 * - latestVersionCode：服务端最新版本号，用于和本机 versionCode 做可靠比较。
 * - latestVersionName：服务端最新版本展示名，例如 0.1.1。
 * - minSupportedVersionCode：服务端建议的最低可用版本号，低于该值时可提示强制更新。
 * - forceUpdate：是否要求用户尽快更新；第一版仍只做提示，不阻断主流程。
 * - apkUrl：APK 下载地址，客户端会交给系统浏览器打开。
 * - apkSizeBytes：APK 文件大小，便于下载前给用户预估流量。
 * - sha256：APK 摘要，后续如果改成 App 内下载时可用于本地校验。
 * - releaseNotes：版本更新说明。
 * - publishedAt：发布时间戳，单位毫秒。
 * - notModified：服务端确认当前客户端已经是最新版本。
 */
data class AppReleaseInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minSupportedVersionCode: Int,
    val forceUpdate: Boolean,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val sha256: String?,
    val releaseNotes: List<String>,
    val publishedAt: Long?,
    val notModified: Boolean,
) {
    /**
     * 用途说明：
     * - 判断当前响应是否代表确实存在可展示的新版本。
     */
    val hasUpdate: Boolean
        get() = !notModified && latestVersionCode > 0
}

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
 * - channel：识别来源渠道。
 * - timestamp：交易时间戳。
 * - note：备注文本；商户、对手方、订单号等非核心信息统一折叠到这里。
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
    val channel: String,
    val timestamp: Long,
    val note: String? = null,
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
    val sortId: Long = 0L,
    val amount: Double,
    val direction: MoneyDirection,
    val channel: String,
    val timestamp: Long,
    val category: String,
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
    val sortId: Long = 0L,
    val amount: Double,
    val direction: MoneyDirection,
    val channel: String,
    val timestamp: Long,
    val confidence: ParseConfidence,
    val categoryHint: String = "待分类",
    val note: String? = null,
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
    /** 用途说明：自动记账是否因为免费体验耗尽被系统关闭；会员生效后可据此安全恢复。 */
    val autoAccountingDisabledByTrialExhaustion: Boolean = false,
    /** 用途说明：自动入账成功后是否弹出可点击通知，便于用户回到首页检查或修改账单。 */
    val autoLedgerSuccessNotificationEnabled: Boolean = true,
    val cloudAnalysisEnabled: Boolean = true,
    val cloudLearningEnabled: Boolean = true,
    /** 用途说明：用户是否加入改善计划；开启后本地通知原文样本会按固定周期上传到服务器全局样本池。 */
    val userImprovementPlanEnabled: Boolean = true,
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
 * - 邮箱登录成功后由服务器返回的账号会话。
 *
 * 参数说明：
 * - userId：服务器账号主键。
 * - phone：已绑定的邮箱；字段名暂沿用旧契约。
 * - accessToken：访问业务接口的短期令牌。
 * - refreshToken：刷新访问令牌的长期令牌。
 * - accessTokenExpiresAt：访问令牌过期毫秒时间戳，服务端判定过期后客户端用刷新令牌换新会话。
 * - refreshTokenExpiresAt：刷新令牌过期毫秒时间戳，过期后才需要用户重新登录。
 * - loggedInAt：本地记录的登录时间。
 * - source：会话来源，真实接入后应为 `server`。
 */
data class AuthSession(
    val userId: String,
    val phone: String,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Long = 0L,
    val refreshTokenExpiresAt: Long = 0L,
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
    /** 用途说明：当前是否已有邮箱登录会话。 */
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
    TWO_YEAR,
    QUARTERLY,
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
 * - phone：当前登录邮箱，未登录时为空；字段名暂沿用旧契约。
 * - tier：当前会员等级。
 * - expiresAt：会员过期时间戳，免费用户为空。
 * - serverVerified：本次会员状态是否来自服务器权威校验。
 * - serverTime：服务器返回的当前时间，用于后续对齐会员有效期。
 * - refreshedAt：本地刷新会员状态的时间。
 * - modelConfidenceEnabled：服务器是否允许该用户使用机器学习解析。
 * - cloudLearningEnabled：历史云端学习字段，当前样本上传门禁已改为本地“用户改善计划”开关。
 * - autoLedgerTrialLimit：服务器配置的免费自动记账体验总次数。
 * - autoLedgerTrialUsed：服务器记录的免费自动记账已用次数。
 * - autoLedgerTrialRemaining：服务器计算后的免费自动记账剩余次数。
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
    val autoLedgerTrialLimit: Int = AUTO_LEDGER_FREE_TRIAL_LIMIT,
    val autoLedgerTrialUsed: Int = 0,
    val autoLedgerTrialRemaining: Int = AUTO_LEDGER_FREE_TRIAL_LIMIT,
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

    /** 用途说明：当前是否存在可归属试用额度的登录账号。 */
    val hasLoggedInAccount: Boolean
        get() = !userId.isNullOrBlank() || !phone.isNullOrBlank()
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
 * - expiresAt：服务器订单过期时间，未返回时为空。
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
    val expiresAt: Long? = null,
)

/**
 * 用途说明：
 * - 云端学习能力的运行状态。
 *
 * 参数说明：
 * - enabledByUser：历史云端学习开关。
 * - isMember：当前是否会员。
 * - effectiveEnabled：当前机器学习模型能力是否可用。
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
    val category: String = "待分类",
    val note: String = "",
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
    val category: String = "待分类",
    val note: String = "",
    val confidence: ParseConfidence = ParseConfidence(0f),
    val tracePath: ImageImportAnalysisPath,
    val message: String,
    val requiresManualConfirmation: Boolean = true,
    val candidates: List<ImageImportCandidate> = emptyList(),
    val filteredOutCandidateCount: Int = 0,
    val duplicateFilteredCount: Int = 0,
    val inactiveFilteredCount: Int = 0,
)
