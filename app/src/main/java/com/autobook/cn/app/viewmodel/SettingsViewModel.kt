package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.common.CurrencyFormatter
import com.autobook.cn.core.common.TimeFormatter
import com.autobook.cn.core.data.AuthRepository
import com.autobook.cn.core.data.CloudLearningRepository
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.LedgerSummaryStats
import com.autobook.cn.core.data.MembershipRepository
import com.autobook.cn.core.data.PaymentRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.model.AuthState
import com.autobook.cn.core.model.CloudLearningState
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MembershipPlan
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 用途说明：
 * - 设置页聚合后的界面状态。
 */
data class SettingsUiState(
    val autoAccountingEnabled: Boolean = true,
    val cloudAnalysisEnabled: Boolean = true,
    val authPhoneInput: String = "",
    val authCodeInput: String = "",
    val authSummary: String = "未登录，会员状态需要邮箱登录后从服务器确认。",
    val authLoggedIn: Boolean = false,
    val authCodeRequesting: Boolean = false,
    val authCodeCooldownSeconds: Int = 0,
    val authLoginInProgress: Boolean = false,
    val membershipActive: Boolean = false,
    val cloudLearningEnabled: Boolean = true,
    val cloudLearningEffectiveEnabled: Boolean = false,
    val membershipSummary: String = "免费版",
    val cloudLearningSummary: String = "免费版仅使用普通规则算法。",
    val membershipActionMessage: String = "",
    val captureEnabled: Boolean = false,
    val notificationRetentionDays: Int = 30,
    val screenshotRetentionDays: Int = 7,
    val enabledSources: Set<SettingsNotificationSource> = emptySet(),
    val bookkeepingDayCount: Int = 0,
    val bookkeepingEntryCount: Int = 0,
    val modelStatusLabel: String = "规则模型",
    val modelLastPulledLabel: String = "尚未拉取",
)

/**
 * 用途说明：
 * - 设置页 ViewModel，负责设置读写、账号登录、会员状态和云端学习开关。
 * - 不在设置页订阅账本明细，避免设置页首帧被全量统计拖慢。
 *
 * 参数说明：
 * - settingsRepository：设置仓储。
 * - authRepository：邮箱登录/注册仓储；内部字段名暂沿用旧契约。
 * - membershipRepository：会员状态仓储。
 * - paymentRepository：会员支付仓储。
 * - cloudLearningRepository：云端学习仓储。
 * - ledgerRepository：账本仓储，仅用于设置页顶部轻量统计。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val membershipRepository: MembershipRepository,
    private val paymentRepository: PaymentRepository,
    private val cloudLearningRepository: CloudLearningRepository,
    ledgerRepository: LedgerRepository,
) : ViewModel() {
    private val membershipActionMessage = MutableStateFlow("")
    private val authPhoneInput = MutableStateFlow("")
    private val authCodeInput = MutableStateFlow("")
    private val authCodeRequesting = MutableStateFlow(false)
    private val authCodeCooldownSeconds = MutableStateFlow(0)
    private val authLoginInProgress = MutableStateFlow(false)
    private val authForm: Flow<SettingsAuthForm> = combine(
        authPhoneInput,
        authCodeInput,
    ) { phone, code ->
        SettingsAuthForm(
            phone = phone,
            code = code,
        )
    }
    private val accountState: Flow<SettingsAccountState> = combine(
        cloudLearningRepository.cloudLearningState,
        authRepository.authState,
        authForm,
    ) { cloudLearning, authState, form ->
        SettingsAccountState(
            cloudLearning = cloudLearning,
            authState = authState,
            form = form,
        )
    }
    private val authInteractionState: Flow<SettingsAuthInteractionState> = combine(
        authCodeRequesting,
        authCodeCooldownSeconds,
        authLoginInProgress,
    ) { codeRequesting, cooldownSeconds, loginInProgress ->
        SettingsAuthInteractionState(
            codeRequesting = codeRequesting,
            cooldownSeconds = cooldownSeconds,
            loginInProgress = loginInProgress,
        )
    }
    private val bookkeepingStats: Flow<LedgerSummaryStats> = ledgerRepository.observeSummaryStats()

    /**
     * 用途说明：
     * - 聚合设置页真实展示的轻量状态。
     * - 设置页不再订阅全量账本和待确认列表，避免进入页面时触发大量数据扫描。
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        accountState,
        membershipActionMessage,
        authInteractionState,
        bookkeepingStats,
    ) { settings, account, actionMessage, authInteraction, stats ->
        val cloudLearning = account.cloudLearning
        val authState = account.authState
        SettingsUiState(
            autoAccountingEnabled = settings.autoAccountingEnabled,
            cloudAnalysisEnabled = settings.cloudAnalysisEnabled,
            authPhoneInput = account.form.phone,
            authCodeInput = account.form.code,
            authSummary = authState.toSummary(),
            authLoggedIn = authState.isLoggedIn,
            authCodeRequesting = authInteraction.codeRequesting,
            authCodeCooldownSeconds = authInteraction.cooldownSeconds,
            authLoginInProgress = authInteraction.loginInProgress,
            membershipActive = cloudLearning.isMember,
            cloudLearningEnabled = cloudLearning.enabledByUser,
            cloudLearningEffectiveEnabled = cloudLearning.effectiveEnabled,
            membershipSummary = if (cloudLearning.isMember) {
                "会员已由服务器确认，机器学习解析可用。"
            } else if (authState.isLoggedIn) {
                "已登录，当前会员状态未由服务器确认。"
            } else {
                "免费版，登录并开通会员后才可使用机器学习。"
            },
            cloudLearningSummary = cloudLearning.message,
            membershipActionMessage = actionMessage,
            captureEnabled = settings.captureEnabled,
            notificationRetentionDays = settings.notificationRetentionDays,
            screenshotRetentionDays = settings.screenshotRetentionDays,
            enabledSources = settings.enabledSources.toSettingsNotificationSources(),
            bookkeepingDayCount = stats.dayCount,
            bookkeepingEntryCount = stats.entryCount,
            modelStatusLabel = cloudLearning.toModelStatusLabel(),
            modelLastPulledLabel = cloudLearning.toModelLastPulledLabel(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setAutoAccountingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoAccountingEnabled(enabled) }
    }

    fun setCloudAnalysisEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCloudAnalysisEnabled(enabled) }
    }

    /**
     * 用途说明：
     * - 更新邮箱输入框状态。
     *
     * 参数说明：
     * - phone：用户输入的邮箱；参数名沿用旧接口，后续统一重命名。
     */
    fun setAuthPhoneInput(phone: String) {
        authPhoneInput.value = phone
    }

    /**
     * 用途说明：
     * - 更新验证码输入框状态。
     *
     * 参数说明：
     * - code：用户输入的邮箱验证码。
     */
    fun setAuthCodeInput(code: String) {
        authCodeInput.value = code
    }

    /**
     * 用途说明：
     * - 请求邮箱验证码，并在客户端本地执行 60 秒限流，避免用户快速重复点击把压力打到后端。
     */
    fun requestLoginCode() {
        val phone = authPhoneInput.value
        if (!isValidAuthEmail(phone)) {
            membershipActionMessage.value = "请输入正确的邮箱地址"
            return
        }
        if (authCodeRequesting.value || authCodeCooldownSeconds.value > 0) {
            return
        }
        authCodeRequesting.value = true
        membershipActionMessage.value = "验证码发送中，请稍候。"
        startAuthCodeCooldown()
        viewModelScope.launch {
            try {
                membershipActionMessage.value = when (val result = authRepository.requestLoginCode(phone)) {
                    is OperationResult.Success -> "验证码已发送，请到邮箱查看。"
                    is OperationResult.RetryableFailure -> "${result.message}，请等待倒计时结束后重试。"
                    is OperationResult.TerminalFailure -> result.message
                }
            } finally {
                // 无论网络调用结果如何，都结束“发送中”；倒计时由本地限流协程继续维护。
                authCodeRequesting.value = false
            }
        }
    }

    /**
     * 用途说明：
     * - 使用邮箱验证码登录或注册，成功后立即刷新服务器会员状态。
     */
    fun loginOrRegisterWithCode() {
        if (authLoginInProgress.value) {
            return
        }
        authLoginInProgress.value = true
        membershipActionMessage.value = "登录中，请稍候。"
        viewModelScope.launch {
            try {
                val result = authRepository.loginOrRegisterWithCode(
                    phone = authPhoneInput.value,
                    code = authCodeInput.value,
                )
                membershipActionMessage.value = result.toMessage()
                if (result is OperationResult.Success) {
                    membershipRepository.refresh()
                }
            } finally {
                // 防止登录请求异常中断后按钮一直停留在“登录中”。
                authLoginInProgress.value = false
            }
        }
    }

    /**
     * 用途说明：
     * - 退出登录并刷新会员门禁，确保机器学习能力立即关闭。
     */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            membershipRepository.refresh()
            membershipActionMessage.value = "已退出登录，当前仅使用普通规则算法。"
        }
    }

    /**
     * 用途说明：
     * - 写入用户是否允许会员全局云端学习上传通知原文。
     *
     * 参数说明：
     * - enabled：目标开关状态，默认开启但只有会员才会真正生效。
     */
    fun setCloudLearningEnabled(enabled: Boolean) {
        viewModelScope.launch { cloudLearningRepository.setCloudLearningEnabled(enabled) }
    }

    /**
     * 用途说明：
     * - 主动刷新会员状态，并把结果转换成设置页短提示。
     */
    fun refreshMembership() {
        viewModelScope.launch {
            val status = membershipRepository.refresh()
            membershipActionMessage.value = if (status.isMember) {
                "会员状态已刷新。"
            } else {
                "当前仍是免费版。"
            }
        }
    }

    /**
     * 用途说明：
     * - 创建月卡会员支付订单。
     */
    fun startMonthlyMembershipPayment() {
        createMembershipOrder(MembershipPlan.MONTHLY)
    }

    /**
     * 用途说明：
     * - 创建年卡会员支付订单。
     */
    fun startYearlyMembershipPayment() {
        createMembershipOrder(MembershipPlan.YEARLY)
    }

    /**
     * 用途说明：
     * - 触发一次云端学习同步：先按用户开关上传样本，再按会员机器学习权限拉取全局模型。
     */
    fun syncCloudLearningNow() {
        viewModelScope.launch {
            val uploadResult = cloudLearningRepository.uploadTrainingSamples()
            val pullResult = cloudLearningRepository.pullCloudModel()
            membershipActionMessage.value = listOf(uploadResult, pullResult)
                .joinToString(separator = "；") { result -> result.toMessage() }
        }
    }

    /**
     * 用途说明：
     * - 只拉取服务器最新全局模型，供设置页顶部“模型状态”指标点击使用。
     */
    fun pullCloudModelNow() {
        viewModelScope.launch {
            membershipActionMessage.value = when (val result = cloudLearningRepository.pullCloudModel()) {
                is OperationResult.Success -> "模型已拉取，后续通知解析会使用最新会员模型。"
                is OperationResult.RetryableFailure -> result.message
                is OperationResult.TerminalFailure -> result.message
            }
        }
    }

    fun setCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCaptureEnabled(enabled) }
    }

    fun setNotificationRetentionDays(days: Int) {
        viewModelScope.launch { settingsRepository.setNotificationRetentionDays(days) }
    }

    fun setScreenshotRetentionDays(days: Int) {
        viewModelScope.launch { settingsRepository.setScreenshotRetentionDays(days) }
    }

    /**
     * 用途说明：
     * - 设置来源开关。
     *
     * 参数说明：
     * - source：设置页来源分组。
     * - enabled：目标状态。
     */
    fun setSourceEnabled(
        source: SettingsNotificationSource,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            settingsRepository.setSourcesEnabled(
                sources = source.toNotificationSourceApps(),
                enabled = enabled,
            )
        }
    }

    /**
     * 用途说明：
     * - 创建会员支付订单占位流程。
     *
     * 参数说明：
     * - plan：用户选择的会员套餐。
     */
    private fun createMembershipOrder(plan: MembershipPlan) {
        viewModelScope.launch {
            membershipActionMessage.value = when (val result = paymentRepository.createMembershipOrder(plan)) {
                is OperationResult.Success -> "订单已创建：${result.data.orderId}"
                is OperationResult.RetryableFailure -> result.message
                is OperationResult.TerminalFailure -> result.message
            }
        }
    }

    /**
     * 用途说明：
     * - 在客户端本地启动验证码倒计时，倒计时期间按钮禁用，不再向后端重复发验证码请求。
     *
     * 参数说明：
     * - 无。
     */
    private fun startAuthCodeCooldown() {
        viewModelScope.launch {
            for (remainingSeconds in AUTH_CODE_LOCAL_COOLDOWN_SECONDS downTo 1) {
                // 每秒刷新一次 UI 状态，让按钮持续显示剩余等待时间。
                authCodeCooldownSeconds.value = remainingSeconds
                delay(1_000)
            }
            authCodeCooldownSeconds.value = 0
        }
    }
}

/**
 * 用途说明：
 * - 设置页账号输入框快照。
 */
private data class SettingsAuthForm(
    val phone: String,
    val code: String,
)

/**
 * 用途说明：
 * - 设置页账号、会员云学习和输入框的聚合快照。
 */
private data class SettingsAccountState(
    val cloudLearning: CloudLearningState,
    val authState: AuthState,
    val form: SettingsAuthForm,
)

/**
 * 用途说明：
 * - 保存账号区临时交互状态，避免验证码发送、倒计时和登录中状态散落在 UI 层自行推导。
 *
 * 参数说明：
 * - codeRequesting：当前是否正在请求验证码。
 * - cooldownSeconds：验证码按钮本地限流剩余秒数。
 * - loginInProgress：当前是否正在提交登录或注册。
 */
private data class SettingsAuthInteractionState(
    val codeRequesting: Boolean,
    val cooldownSeconds: Int,
    val loginInProgress: Boolean,
)

/**
 * 用途说明：
 * - 把云端学习状态转换为顶部模型指标的主文案。
 *
 * 接收者说明：
 * - 当前云端学习与模型状态。
 */
private fun CloudLearningState.toModelStatusLabel(): String {
    return when {
        effectiveEnabled && activeModelVersion != "local-rules-v1" -> "云端模型"
        isMember -> "会员模型"
        else -> "规则模型"
    }
}

/**
 * 用途说明：
 * - 把最近模型拉取时间转换为顶部模型指标的副文案。
 *
 * 接收者说明：
 * - 当前云端学习与模型状态。
 */
private fun CloudLearningState.toModelLastPulledLabel(): String {
    return lastModelPullAt?.let { pulledAt ->
        TimeFormatter.format(pulledAt)
    } ?: "尚未拉取"
}

internal data class SettingsLedgerOverview(
    val totalEntries: Int,
    val manualEntries: Int,
    val autoEntries: Int,
    val pendingEntries: Int,
    val currentMonthLabel: String,
    val currentMonthBudgetInput: String,
    val currentMonthBudgetLabel: String,
    val currentMonthBudgetUsageSummary: String,
    val currentMonthBudgetRemainingLabel: String,
    val currentMonthBudgetUsageRatio: Float,
    val currentMonthBudgetExceeded: Boolean,
    val currentMonthIncome: String,
    val currentMonthExpense: String,
    val latestEntrySummary: String,
)

/**
 * 用途说明：
 * - 构建设置页账本概览。
 *
 * 参数说明：
 * - entries：账本记录列表。
 * - pendingCount：待确认数量。
 * - monthlyBudgets：月预算映射。
 * - today：当前日期，测试中可注入。
 */
internal fun buildSettingsOverview(
    entries: List<LedgerEntry>,
    pendingCount: Int,
    monthlyBudgets: Map<String, Double> = emptyMap(),
    today: LocalDate = LocalDate.now(),
): SettingsLedgerOverview {
    val currentMonth = YearMonth.from(today)
    val currentMonthEntries = entries.filter { YearMonth.from(it.timestamp.toLocalDate()) == currentMonth }
    val manualEntries = entries.count { it.editedManually || it.traceSource == "manual" }
    val currentMonthIncome = currentMonthEntries
        .filter { it.direction == MoneyDirection.INCOME }
        .sumOf { it.amount }
    val currentMonthExpense = currentMonthEntries
        .filter { it.direction == MoneyDirection.EXPENSE }
        .sumOf { it.amount }
    val currentMonthBudget = monthlyBudgets[currentMonth.toString()]?.takeIf { it > 0.0 }
    val budgetRemaining = currentMonthBudget?.minus(currentMonthExpense)
    val budgetRatio = if (currentMonthBudget == null || currentMonthBudget <= 0.0) {
        0f
    } else {
        (currentMonthExpense / currentMonthBudget).coerceIn(0.0, 1.0).toFloat()
    }
    // 设置页只展示最近一笔摘要，按最终入账顺序取第一条，和首页最近记录保持一致。
    val latestEntry = entries.sortNewestForDisplay().firstOrNull()
    return SettingsLedgerOverview(
        totalEntries = entries.size,
        manualEntries = manualEntries,
        autoEntries = entries.size - manualEntries,
        pendingEntries = pendingCount,
        currentMonthLabel = currentMonth.format(MONTH_LABEL_FORMATTER),
        currentMonthBudgetInput = formatBudgetInput(currentMonthBudget),
        currentMonthBudgetLabel = currentMonthBudget?.let(CurrencyFormatter::format) ?: "未设置",
        currentMonthBudgetUsageSummary = if (currentMonthBudget == null) {
            "清空输入后保存可移除预算，设置后总览页会自动展示剩余额度。"
        } else {
            "已用 ${((currentMonthExpense / currentMonthBudget) * 100).roundToInt()}% · 支出 ${CurrencyFormatter.format(currentMonthExpense)} / 预算 ${CurrencyFormatter.format(currentMonthBudget)}"
        },
        currentMonthBudgetRemainingLabel = if (budgetRemaining == null) {
            "去设置预算"
        } else if (budgetRemaining >= 0.0) {
            "剩余 ${CurrencyFormatter.format(budgetRemaining)}"
        } else {
            "超出 ${CurrencyFormatter.format(kotlin.math.abs(budgetRemaining))}"
        },
        currentMonthBudgetUsageRatio = budgetRatio,
        currentMonthBudgetExceeded = budgetRemaining != null && budgetRemaining < 0.0,
        currentMonthIncome = CurrencyFormatter.format(currentMonthIncome),
        currentMonthExpense = CurrencyFormatter.format(currentMonthExpense),
        latestEntrySummary = latestEntry?.let { entry ->
            "${entry.buildLedgerTitle()} · ${CurrencyFormatter.format(entry.amount)} · ${TimeFormatter.format(entry.timestamp)}"
        } ?: "暂无记账记录",
    )
}

/**
 * 用途说明：
 * - 把时间戳转换为本地日期。
 *
 * 参数说明：
 * - 接收者：毫秒时间戳。
 */
private fun Long.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

/**
 * 用途说明：
 * - 预算输入框不显示货币符号，方便直接编辑数字。
 *
 * 参数说明：
 * - amount：预算金额。
 */
private fun formatBudgetInput(amount: Double?): String {
    return when {
        amount == null -> ""
        amount % 1.0 == 0.0 -> amount.toLong().toString()
        else -> amount.toString()
    }
}

/**
 * 用途说明：
 * - 在 ViewModel 层提前校验邮箱，避免明显无效输入触发后端验证码接口。
 *
 * 参数说明：
 * - rawEmail：用户在设置页账号卡片中输入的邮箱文本。
 */
private fun isValidAuthEmail(rawEmail: String): Boolean {
    return AUTH_EMAIL_REGEX.matches(rawEmail.trim())
}

/**
 * 用途说明：
 * - 把后台操作结果转换成设置页短提示。
 *
 * 参数说明：
 * - 接收者：待展示的操作结果。
 */
private fun OperationResult<*>.toMessage(): String = when (this) {
    is OperationResult.Success -> "操作完成"
    is OperationResult.RetryableFailure -> message
    is OperationResult.TerminalFailure -> message
}

/**
 * 用途说明：
 * - 把账号状态转换成设置页摘要。
 *
 * 参数说明：
 * - 接收者：当前账号状态。
 */
private fun AuthState.toSummary(): String {
    val session = session ?: return "未登录，会员状态需要邮箱登录后从服务器确认。"
    return "已登录 ${session.phone}，会员状态以服务器刷新结果为准。"
}

private val MONTH_LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINA)
private val AUTH_EMAIL_REGEX = Regex("""^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$""", RegexOption.IGNORE_CASE)
private const val AUTH_CODE_LOCAL_COOLDOWN_SECONDS = 60
