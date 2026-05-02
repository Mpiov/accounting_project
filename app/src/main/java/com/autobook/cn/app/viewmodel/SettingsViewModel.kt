package com.autobook.cn.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobook.cn.core.common.CurrencyFormatter
import com.autobook.cn.core.common.TimeFormatter
import com.autobook.cn.core.data.AuthRepository
import com.autobook.cn.core.data.CloudLearningRepository
import com.autobook.cn.core.data.LedgerRepository
import com.autobook.cn.core.data.MembershipRepository
import com.autobook.cn.core.data.PendingRepository
import com.autobook.cn.core.data.PaymentRepository
import com.autobook.cn.core.data.SettingsRepository
import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.AuthState
import com.autobook.cn.core.model.CloudLearningState
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MembershipPlan
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.PendingEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 用途说明：
 * - 设置页聚合后的界面状态。
 */
data class SettingsUiState(
    val autoAccountingEnabled: Boolean = true,
    val cloudAnalysisEnabled: Boolean = true,
    val authPhoneInput: String = "",
    val authCodeInput: String = "",
    val authSummary: String = "未登录，会员状态需要手机号登录后从服务器确认。",
    val authLoggedIn: Boolean = false,
    val cloudLearningEnabled: Boolean = true,
    val cloudLearningEffectiveEnabled: Boolean = false,
    val membershipSummary: String = "免费版",
    val cloudLearningSummary: String = "免费版仅使用普通规则算法。",
    val membershipActionMessage: String = "",
    val captureEnabled: Boolean = false,
    val notificationRetentionDays: Int = 30,
    val screenshotRetentionDays: Int = 7,
    val enabledSources: Set<SettingsNotificationSource> = emptySet(),
    val totalEntries: Int = 0,
    val manualEntries: Int = 0,
    val autoEntries: Int = 0,
    val pendingEntries: Int = 0,
    val currentMonthLabel: String = "本月",
    val currentMonthBudgetInput: String = "",
    val currentMonthBudgetLabel: String = "未设置",
    val currentMonthBudgetUsageSummary: String = "保存预算后，这里会自动展示本月剩余额度。",
    val currentMonthBudgetRemainingLabel: String = "去设置预算",
    val currentMonthBudgetUsageRatio: Float = 0f,
    val currentMonthBudgetExceeded: Boolean = false,
    val currentMonthIncome: String = "¥0.00",
    val currentMonthExpense: String = "¥0.00",
    val latestEntrySummary: String = "暂无记账记录",
    val exportPreview: String = "",
    val exportPreviewSummary: String = "",
)

/**
 * 用途说明：
 * - 设置页 ViewModel，负责设置读写与账本概览。
 *
 * 参数说明：
 * - settingsRepository：设置仓储。
 * - ledgerRepository：账本仓储。
 * - pendingRepository：待确认仓储。
 * - authRepository：手机号登录/注册仓储。
 * - membershipRepository：会员状态仓储。
 * - paymentRepository：会员支付仓储。
 * - cloudLearningRepository：云端学习仓储。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ledgerRepository: LedgerRepository,
    private val pendingRepository: PendingRepository,
    private val authRepository: AuthRepository,
    private val membershipRepository: MembershipRepository,
    private val paymentRepository: PaymentRepository,
    private val cloudLearningRepository: CloudLearningRepository,
) : ViewModel() {
    private val exportPreview = MutableStateFlow("")
    private val membershipActionMessage = MutableStateFlow("")
    private val authPhoneInput = MutableStateFlow("")
    private val authCodeInput = MutableStateFlow("")
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
    private val monthlyBudgets: Flow<Map<String, Double>> = settingsRepository.settings
        .map { settings: AppSettings -> settings.monthlyBudgets }
        .distinctUntilChanged()

    /**
     * 用途说明：
     * - 集中计算设置页依赖的账本统计与预算概览。
     */
    private val derivedData: Flow<SettingsDerivedData> = combine(
        ledgerRepository.observeEntries(),
        pendingRepository.observeEntries(),
        monthlyBudgets,
    ) { entries: List<LedgerEntry>, pendingEntries: List<PendingEntry>, monthlyBudgetsValue: Map<String, Double> ->
        SettingsDerivedInput(
            entries = entries,
            pendingCount = pendingEntries.size,
            monthlyBudgets = monthlyBudgetsValue,
        )
    }.mapLatest { input: SettingsDerivedInput ->
        // 把全量统计放到后台线程，避免设置页被大账本数据拖慢。
        withContext(Dispatchers.Default) {
            SettingsDerivedData(
                overview = buildSettingsOverview(
                    entries = input.entries,
                    pendingCount = input.pendingCount,
                    monthlyBudgets = input.monthlyBudgets,
                ),
                exportPreviewSummary = buildExportPreviewSummary(input.entries),
            )
        }
    }

    /**
     * 用途说明：
     * - 聚合设置快照、账本概览和导出预览。
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        accountState,
        derivedData,
        exportPreview,
        membershipActionMessage,
    ) { settings: AppSettings, account: SettingsAccountState, derived: SettingsDerivedData, preview: String, actionMessage: String ->
        val overview = derived.overview
        val cloudLearning = account.cloudLearning
        val authState = account.authState
        SettingsUiState(
            autoAccountingEnabled = settings.autoAccountingEnabled,
            cloudAnalysisEnabled = settings.cloudAnalysisEnabled,
            authPhoneInput = account.form.phone,
            authCodeInput = account.form.code,
            authSummary = authState.toSummary(),
            authLoggedIn = authState.isLoggedIn,
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
            totalEntries = overview.totalEntries,
            manualEntries = overview.manualEntries,
            autoEntries = overview.autoEntries,
            pendingEntries = overview.pendingEntries,
            currentMonthLabel = overview.currentMonthLabel,
            currentMonthBudgetInput = overview.currentMonthBudgetInput,
            currentMonthBudgetLabel = overview.currentMonthBudgetLabel,
            currentMonthBudgetUsageSummary = overview.currentMonthBudgetUsageSummary,
            currentMonthBudgetRemainingLabel = overview.currentMonthBudgetRemainingLabel,
            currentMonthBudgetUsageRatio = overview.currentMonthBudgetUsageRatio,
            currentMonthBudgetExceeded = overview.currentMonthBudgetExceeded,
            currentMonthIncome = overview.currentMonthIncome,
            currentMonthExpense = overview.currentMonthExpense,
            latestEntrySummary = overview.latestEntrySummary,
            exportPreview = preview,
            exportPreviewSummary = if (preview.isBlank()) "" else derived.exportPreviewSummary,
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
     * - 更新手机号输入框状态。
     *
     * 参数说明：
     * - phone：用户输入的手机号。
     */
    fun setAuthPhoneInput(phone: String) {
        authPhoneInput.value = phone
    }

    /**
     * 用途说明：
     * - 更新验证码输入框状态。
     *
     * 参数说明：
     * - code：用户输入的短信验证码。
     */
    fun setAuthCodeInput(code: String) {
        authCodeInput.value = code
    }

    /**
     * 用途说明：
     * - 请求手机号验证码；后端未配置时会返回占位提示。
     */
    fun requestLoginCode() {
        viewModelScope.launch {
            membershipActionMessage.value = authRepository.requestLoginCode(authPhoneInput.value).toMessage()
        }
    }

    /**
     * 用途说明：
     * - 使用手机号验证码登录或注册，成功后立即刷新服务器会员状态。
     */
    fun loginOrRegisterWithCode() {
        viewModelScope.launch {
            val result = authRepository.loginOrRegisterWithCode(
                phone = authPhoneInput.value,
                code = authCodeInput.value,
            )
            membershipActionMessage.value = result.toMessage()
            if (result is OperationResult.Success) {
                membershipRepository.refresh()
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
     * - 保存当前月份预算。
     *
     * 参数说明：
     * - amountInput：输入框中的预算文本；留空表示清除预算。
     */
    fun saveCurrentMonthBudget(amountInput: String) {
        viewModelScope.launch {
            val normalizedInput = amountInput.trim()
            val currentMonthKey = YearMonth.now().toString()
            if (normalizedInput.isBlank()) {
                settingsRepository.clearMonthlyBudget(currentMonthKey)
                return@launch
            }
            val amount = normalizedInput.toDoubleOrNull() ?: return@launch
            settingsRepository.setMonthlyBudget(currentMonthKey, amount)
        }
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
     * - 按需生成 CSV 预览，避免每次进入设置页都拼装导出文本。
     */
    fun loadExportPreview() {
        viewModelScope.launch {
            exportPreview.value = ledgerRepository.exportCsv()
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
 * - 设置页统计所需输入快照。
 */
private data class SettingsDerivedInput(
    val entries: List<LedgerEntry>,
    val pendingCount: Int,
    val monthlyBudgets: Map<String, Double>,
)

/**
 * 用途说明：
 * - 设置页聚合统计结果。
 */
private data class SettingsDerivedData(
    val overview: SettingsLedgerOverview,
    val exportPreviewSummary: String,
)

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
    val latestEntry = entries.maxByOrNull { it.timestamp }
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
            "${entry.merchant} · ${CurrencyFormatter.format(entry.amount)} · ${TimeFormatter.format(entry.timestamp)}"
        } ?: "暂无记账记录",
    )
}

/**
 * 用途说明：
 * - 构建导出预览摘要。
 *
 * 参数说明：
 * - entries：账本记录列表。
 */
internal fun buildExportPreviewSummary(entries: List<LedgerEntry>): String {
    if (entries.isEmpty()) {
        return "当前账本为空，导出的 CSV 将只包含表头。"
    }
    val earliest = entries.minByOrNull { it.timestamp } ?: return ""
    val latest = entries.maxByOrNull { it.timestamp } ?: return ""
    val manualEntries = entries.count { it.editedManually || it.traceSource == "manual" }
    return "共 ${entries.size} 条记录，覆盖 ${earliest.timestamp.toLocalDate().format(EXPORT_DATE_FORMATTER)} 至 ${latest.timestamp.toLocalDate().format(EXPORT_DATE_FORMATTER)}，其中手动补记 $manualEntries 条。"
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
    val session = session ?: return "未登录，会员状态需要手机号登录后从服务器确认。"
    return "已登录 ${session.phone}，会员状态以服务器刷新结果为准。"
}

private val MONTH_LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINA)
private val EXPORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.CHINA)
