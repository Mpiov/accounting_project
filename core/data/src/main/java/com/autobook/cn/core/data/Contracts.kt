package com.autobook.cn.core.data

import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.AuthSession
import com.autobook.cn.core.model.AuthState
import com.autobook.cn.core.model.CaptureSession
import com.autobook.cn.core.model.ImageImportSuggestion
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.CloudLearningState
import com.autobook.cn.core.model.MembershipPaymentOrder
import com.autobook.cn.core.model.MembershipPaymentState
import com.autobook.cn.core.model.MembershipPlan
import com.autobook.cn.core.model.MembershipStatus
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessOutcome
import com.autobook.cn.core.model.NotificationProcessStatus
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.PendingEntry
import com.autobook.cn.core.model.ProcessDestination
import com.autobook.cn.core.model.TransactionCandidate
import kotlinx.coroutines.flow.Flow

/** 用途说明：通知解析阶段使用的置信度来源。 */
enum class TransactionScoringMode {
    RULES_ONLY,
    MEMBER_MACHINE_LEARNING,
}

/**
 * 用途说明：
 * - 通知入账主链路统一入口。
 */
interface NotificationIngestor {
    /**
     * 参数说明：
     * - event：当前需要处理的原始通知事件。
     */
    suspend fun ingest(event: NotificationEvent): OperationResult<NotificationProcessOutcome>
}

/**
 * 用途说明：
 * - 协调截图授权与截图执行，供截图记账入口使用。
 */
interface CaptureCoordinator {
    /**
     * 参数说明：
     * - reason：本次截图请求的业务原因描述。
     */
    suspend fun requestAuthorization(reason: String): OperationResult<CaptureSession>

    /**
     * 参数说明：
     * - sessionId：已经授权成功的截图会话标识。
     */
    suspend fun capture(sessionId: String): OperationResult<String>

    /**
     * 参数说明：
     * - sessionId：待取消的截图会话标识。
     */
    suspend fun cancel(sessionId: String)

    /**
     * 参数说明：
     * - sessionId：发生失败的截图会话标识。
     * - message：失败原因摘要。
     */
    suspend fun reportFailure(sessionId: String, message: String)
}

/**
 * 用途说明：
 * - 图片导入记账统一分析入口。
 * - 当前仅保留“云端分析 + 本地规则后处理”与“手动草稿”两条路径。
 */
interface ImageImportAnalyzer {
    /**
     * 参数说明：
     * - imageRef：图片引用，当前通常是内容 Uri 或应用私有路径。
     */
    suspend fun analyze(imageRef: String): OperationResult<ImageImportSuggestion>
}

/**
 * 用途说明：
 * - 从原始通知文本中提取结构化交易候选。
 */
interface TransactionExtractor {
    /**
     * 参数说明：
     * - event：当前待解析的通知事件。
     * - scoringMode：本次置信度来源；免费用户只能使用普通规则，会员才允许机器学习。
     */
    suspend fun extract(
        event: NotificationEvent,
        scoringMode: TransactionScoringMode = TransactionScoringMode.RULES_ONLY,
    ): OperationResult<TransactionCandidate>
}

/**
 * 用途说明：
 * - 在免费普通规则和会员机器学习解析之间做路由决策。
 */
interface ModelRouter {
    /**
     * 参数说明：
     * - event：当前待路由的通知事件。
     */
    suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate>
}

/**
 * 用途说明：
 * - 设置页等轻量入口只需要展示账本规模，不应该为了两个数字订阅整本账本。
 *
 * 参数说明：
 * - dayCount：有正式记账记录的自然日数量。
 * - entryCount：正式账本记录总笔数。
 */
data class LedgerSummaryStats(
    val dayCount: Int,
    val entryCount: Int,
)

/**
 * 用途说明：
 * - 账本仓储抽象。
 */
interface LedgerRepository {
    fun observeEntries(): Flow<List<LedgerEntry>>

    /**
     * 用途说明：
     * - 观察账本轻量统计，供设置页头像区展示，避免加载完整账本列表造成卡顿。
     */
    fun observeSummaryStats(): Flow<LedgerSummaryStats>

    /**
     * 参数说明：
     * - entry：待新增的账本记录。
     */
    suspend fun add(entry: LedgerEntry): Long

    /**
     * 参数说明：
     * - entry：待更新的账本记录。
     */
    suspend fun update(entry: LedgerEntry)

    /**
     * 参数说明：
     * - id：待删除账本记录主键。
     */
    suspend fun delete(id: Long)

    /**
     * 参数说明：
     * - id：目标账本记录主键。
     */
    suspend fun getById(id: Long): LedgerEntry?

    /**
     * 参数说明：
     * - candidate：通知主链路产出的交易候选。
     */
    suspend fun findPotentialDuplicate(candidate: TransactionCandidate): LedgerEntry?

    /**
     * 参数说明：
     * - timestamp：候选交易时间，允许为空。
     * - amount：候选金额。
     * - direction：候选方向。
     */
    suspend fun findPotentialImageImportDuplicate(
        timestamp: Long?,
        amount: Double,
        direction: MoneyDirection,
    ): LedgerEntry?

    /**
     * 参数说明：
     * - amount：候选金额。
     * - direction：候选方向。
     */
    suspend fun findPotentialImageImportDuplicateWithoutTimestamp(
        amount: Double,
        direction: MoneyDirection,
    ): LedgerEntry?

    suspend fun exportCsv(): String
}

/**
 * 用途说明：
 * - 待确认列表仓储抽象。
 */
interface PendingRepository {
    fun observeEntries(): Flow<List<PendingEntry>>

    /**
     * 用途说明：
     * - 观察当前仍需用户处理的待确认数量。
     * - 底部导航徽标只需要数量，使用该轻量流可以避免加载完整待确认列表。
     */
    fun observeActiveCount(): Flow<Int>

    /**
     * 参数说明：
     * - entry：待新增的待确认记录。
     */
    suspend fun add(entry: PendingEntry): Long

    /**
     * 参数说明：
     * - entry：待更新的待确认记录。
     */
    suspend fun update(entry: PendingEntry)

    /**
     * 参数说明：
     * - id：待确认记录主键。
     */
    suspend fun getById(id: Long): PendingEntry?

    /**
     * 参数说明：
     * - id：待确认记录主键。
     */
    suspend fun confirm(id: Long): PendingEntry?

    /**
     * 参数说明：
     * - id：待确认记录主键。
     */
    suspend fun confirmEdited(
        id: Long,
        correctedEntry: LedgerEntry,
    ): PendingEntry?

    /**
     * 参数说明：
     * - id：待确认记录主键。
     */
    suspend fun ignore(id: Long)

    /**
     * 参数说明：
     * - id：待确认记录主键。
     */
    suspend fun markFalsePositive(id: Long)

    /**
     * 用途说明：
     * - 云端已经确认接收误识别反馈后，清理本地隐藏的误识别待确认残留。
     */
    suspend fun purgeUploadedFalsePositives(): Int
}

/**
 * 用途说明：
 * - 记录通知解析模型的训练样本，并在用户后续操作时补充反馈标签。
 */
interface ModelTrainingSampleRepository {
    /**
     * 参数说明：
     * - notificationId：来源通知主键。
     * - event：原始通知事件，会员全局云端学习会导出其中的标题、正文和通知时间。
     * - candidate：解析器产出的交易候选。
     * - destination：本次流水线分流落点。
     * - status：通知最终处理状态。
     * - pendingEntryId：进入待确认时的待确认主键。
     * - ledgerEntryId：自动入账或后续确认入账时的账本主键。
     */
    suspend fun recordPipelineDecision(
        notificationId: Long,
        event: NotificationEvent,
        candidate: TransactionCandidate,
        destination: ProcessDestination,
        status: NotificationProcessStatus,
        pendingEntryId: Long? = null,
        ledgerEntryId: Long? = null,
    )

    /**
     * 参数说明：
     * - notificationId：来源通知主键，可能为空。
     * - ledgerEntryId：最终账本记录主键。
     */
    suspend fun linkLedgerEntry(
        notificationId: Long?,
        ledgerEntryId: Long,
    )

    /**
     * 参数说明：
     * - pendingEntryId：待确认记录主键。
     * - editedManually：确认前是否手动编辑过金额、方向、分类、备注等字段。
     * - correctedEntry：用户最终确认入账的记录；为空表示直接确认，字段与候选一致。
     */
    suspend fun markPendingConfirmed(
        pendingEntryId: Long,
        editedManually: Boolean,
        correctedEntry: LedgerEntry? = null,
    )

    /**
     * 参数说明：
     * - pendingEntryId：被忽略的待确认记录主键。
     */
    suspend fun markPendingIgnored(pendingEntryId: Long)

    /**
     * 参数说明：
     * - pendingEntryId：被标记为误识别的待确认记录主键。
     */
    suspend fun markPendingFalsePositive(pendingEntryId: Long)

    /**
     * 参数说明：
     * - entry：被用户编辑后的账本记录。
     */
    suspend fun markLedgerEdited(entry: LedgerEntry)

    /**
     * 参数说明：
     * - entry：被用户删除前的账本记录快照。
     */
    suspend fun markLedgerDeleted(entry: LedgerEntry)

    /**
     * 参数说明：
     * - limit：单次上传最多读取多少条待同步样本。
     *
     * 返回值说明：
     * - 本地样本主键集合和对应 JSONL 文本；没有待上传样本时返回空批次。
     */
    suspend fun loadUploadBatch(limit: Int): ModelTrainingSampleUploadBatch

    /**
     * 参数说明：
     * - sampleIds：已经发起上传的本地样本主键集合。
     * - attemptedAt：本次上传尝试的本地时间戳。
     */
    suspend fun markUploadBatchAttempted(
        sampleIds: List<Long>,
        attemptedAt: Long,
    )

    /**
     * 参数说明：
     * - sampleIds：已被服务端确认接收或更新的本地样本主键集合。
     * - uploadedAt：本次上传成功的本地时间戳。
     */
    suspend fun markUploadBatchUploaded(
        sampleIds: List<Long>,
        uploadedAt: Long,
    )

    /**
     * 返回值说明：
     * - 每行一个 JSON 对象，供离线训练脚本直接读取。
     */
    suspend fun exportJsonl(): String
}

/**
 * 用途说明：
 * - 云端学习上传批次，保留 JSONL 原文和本地主键，方便上传成功后精确回写同步状态。
 *
 * 参数说明：
 * - sampleIds：本批样本在本地 Room 表中的主键集合。
 * - jsonl：本批样本序列化后的 JSONL 文本。
 */
data class ModelTrainingSampleUploadBatch(
    val sampleIds: List<Long>,
    val jsonl: String,
)

/**
 * 用途说明：
 * - 邮箱验证码登录/注册仓储。
 * - 当前只定义客户端骨架，真实验证码和令牌均应由服务器返回。
 */
interface AuthRepository {
    val authState: Flow<AuthState>

    /**
     * 用途说明：
     * - 对齐后端 `POST /v1/auth/sms-code`。
     *
     * 参数说明：
     * - phone：用户输入的邮箱；字段名沿用旧契约，后续统一重命名为 email。
     */
    suspend fun requestLoginCode(phone: String): OperationResult<Unit>

    /**
     * 用途说明：
     * - 对齐后端 `POST /v1/auth/login`。
     *
     * 参数说明：
     * - phone：用户输入的邮箱；字段名沿用旧契约，后续统一重命名为 email。
     * - code：邮箱验证码。
     */
    suspend fun loginOrRegisterWithCode(
        phone: String,
        code: String,
    ): OperationResult<AuthSession>

    /**
     * 用途说明：
     * - 清除本地登录会话；服务器会员状态下次必须重新通过 `GET /v1/membership/status` 拉取。
     */
    suspend fun logout()
}

/**
 * 用途说明：
 * - 会员状态仓储，以服务器返回的数据作为唯一权威来源。
 */
interface MembershipRepository {
    val membershipStatus: Flow<MembershipStatus>

    /**
     * 用途说明：
     * - 对齐后端 `GET /v1/membership/status`。
     *
     * 返回值说明：
     * - 刷新后的会员状态；后端未配置时返回免费状态。
     */
    suspend fun refresh(): MembershipStatus
}

/**
 * 用途说明：
 * - 会员支付仓储，负责向服务器创建微信支付订单并查询订单状态。
 */
interface PaymentRepository {
    /**
     * 用途说明：
     * - 对齐后端 `POST /v1/payments/membership-orders`。
     *
     * 参数说明：
     * - plan：用户要购买的会员套餐。
     */
    suspend fun createMembershipOrder(plan: MembershipPlan): OperationResult<MembershipPaymentOrder>

    /**
     * 用途说明：
     * - 对齐后端 `GET /v1/payments/membership-orders/{orderId}`。
     *
     * 参数说明：
     * - orderId：服务器业务订单号。
     */
    suspend fun queryPaymentState(orderId: String): OperationResult<MembershipPaymentState>
}

/**
 * 用途说明：
 * - 客户端可执行的服务器全局置信度模型。
 *
 * 参数说明：
 * - modelVersion：服务器发布的模型版本号。
 * - modelScope：模型作用域，当前应为 `GLOBAL`，表示综合全体会员样本训练。
 * - modelType：模型类型，当前预期为轻量线性模型或逻辑回归模型。
 * - weights：模型权重表，键为稳定特征名，值为该特征对 logit 的贡献权重。
 * - thresholds：风险阈值表，只允许服务器收紧客户端保护上限，不能放宽安全边界。
 * - publishedAt：服务器发布时间戳。
 * - validUntil：模型有效期，过期后客户端自动回退内置会员模型。
 * - trainedSampleCount：训练该版本时使用的样本数量。
 * - signature：服务器签名，当前先落盘保留，后续接入验签。
 */
data class CloudConfidenceModel(
    val modelVersion: String,
    val modelScope: String = "GLOBAL",
    val modelType: String? = null,
    val weights: Map<String, Double> = emptyMap(),
    val thresholds: Map<String, Double> = emptyMap(),
    val publishedAt: Long? = null,
    val validUntil: Long? = null,
    val trainedSampleCount: Int = 0,
    val signature: String? = null,
) {
    /**
     * 用途说明：
     * - 判断模型是否可以参与本地会员机器学习评分。
     *
     * 参数说明：
     * - now：本地当前时间戳，默认使用系统时间。
     */
    fun isUsableAt(now: Long = System.currentTimeMillis()): Boolean {
        return modelVersion.isNotBlank() &&
            weights.isNotEmpty() &&
            (validUntil == null || validUntil > now)
    }
}

/**
 * 用途说明：
 * - 保存和读取服务器发布的全局模型，让 App 重启后仍能使用最新会员模型权重。
 */
interface CloudModelStore {
    /** 用途说明：观察当前本地保存的服务器全局模型。 */
    val activeModel: Flow<CloudConfidenceModel?>

    /**
     * 用途说明：
     * - 读取当前本地保存的服务器全局模型快照。
     */
    suspend fun getActiveModel(): CloudConfidenceModel?

    /**
     * 用途说明：
     * - 保存服务器下发的全局模型。
     *
     * 参数说明：
     * - model：已通过基础格式校验的服务器模型。
     */
    suspend fun saveGlobalModel(model: CloudConfidenceModel)

    /**
     * 用途说明：
     * - 清空本地模型缓存，后续解析会回退到内置会员模型或普通规则。
     */
    suspend fun clearActiveModel()
}

/**
 * 用途说明：
 * - 无模型仓库实现，供纯单元测试或未注入环境使用。
 */
object NoOpCloudModelStore : CloudModelStore {
    override val activeModel: Flow<CloudConfidenceModel?> = kotlinx.coroutines.flow.flowOf(null)

    override suspend fun getActiveModel(): CloudConfidenceModel? = null

    override suspend fun saveGlobalModel(model: CloudConfidenceModel) = Unit

    override suspend fun clearActiveModel() = Unit
}

/**
 * 用途说明：
 * - 云端学习仓储，负责会员开关、原文样本上传到全局样本池，以及全局模型拉取。
 */
interface CloudLearningRepository {
    val cloudLearningState: Flow<CloudLearningState>

    /**
     * 参数说明：
     * - enabled：用户是否允许会员全局云端学习上传通知原文。
     */
    suspend fun setCloudLearningEnabled(enabled: Boolean)

    /**
     * 用途说明：
     * - 对齐后端 `POST /v1/cloud-learning/training-samples`。
     * - 样本上传后进入服务器全局训练池，不生成个人定制模型。
     *
     * 返回值说明：
     * - 上传训练样本的操作结果。
     */
    suspend fun uploadTrainingSamples(): OperationResult<Unit>

    /**
     * 用途说明：
     * - 对齐后端 `GET /v1/cloud-learning/global-model`。
     *
     * 返回值说明：
     * - 拉取服务器最新全局模型包的操作结果。
     */
    suspend fun pullCloudModel(): OperationResult<Unit>
}

/**
 * 用途说明：
 * - 提供通知原文回看能力。
 */
interface NotificationTraceRepository {
    fun observeRawTextById(): Flow<Map<Long, String>>
}

/**
 * 用途说明：
 * - 统一管理运行时设置。
 */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    /**
     * 参数说明：
     * - completed：是否已完成引导流程。
     */
    suspend fun setOnboardingCompleted(completed: Boolean)

    /**
     * 参数说明：
     * - enabled：自动记账总开关目标状态。
     */
    suspend fun setAutoAccountingEnabled(enabled: Boolean)

    /**
     * 参数说明：
     * - enabled：云端分析开关目标状态。
     */
    suspend fun setCloudAnalysisEnabled(enabled: Boolean)

    /**
     * 参数说明：
     * - enabled：会员全局云端学习开关目标状态。
     */
    suspend fun setCloudLearningEnabled(enabled: Boolean)

    /**
     * 参数说明：
     * - enabled：截图记账入口目标状态。
     */
    suspend fun setCaptureEnabled(enabled: Boolean)

    /**
     * 参数说明：
     * - days：通知原文保留天数。
     */
    suspend fun setNotificationRetentionDays(days: Int)

    /**
     * 参数说明：
     * - days：截图保留天数。
     */
    suspend fun setScreenshotRetentionDays(days: Int)

    /**
     * 参数说明：
     * - monthKey：`yyyy-MM` 格式的月份键。
     * - amount：预算金额，单位元。
     */
    suspend fun setMonthlyBudget(monthKey: String, amount: Double) = Unit

    /**
     * 参数说明：
     * - monthKey：`yyyy-MM` 格式的月份键。
     */
    suspend fun clearMonthlyBudget(monthKey: String) = Unit

    /**
     * 参数说明：
     * - sources：待统一切换的来源集合。
     * - enabled：目标状态。
     */
    suspend fun setSourcesEnabled(
        sources: Set<NotificationSourceApp>,
        enabled: Boolean,
    ) {
        sources.forEach { source ->
            setSourceEnabled(source, enabled)
        }
    }

    /**
     * 参数说明：
     * - source：单个通知来源。
     * - enabled：目标状态。
     */
    suspend fun setSourceEnabled(source: NotificationSourceApp, enabled: Boolean)
}
