package com.autobook.cn.core.data

import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.CaptureSession
import com.autobook.cn.core.model.ImageImportSuggestion
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessOutcome
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.PendingEntry
import com.autobook.cn.core.model.TransactionCandidate
import kotlinx.coroutines.flow.Flow

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
 * - 本地语义校验抽象。
 * - 当前实现仅保留启发式规则校验，不再接入端侧 LLM 推理。
 */
interface LocalLlmAnalyzer {
    /**
     * 参数说明：
     * - event：原始通知事件。
     * - seedCandidate：规则层先给出的基础候选。
     */
    suspend fun analyze(
        event: NotificationEvent,
        seedCandidate: TransactionCandidate,
    ): OperationResult<TransactionCandidate>
}

/**
 * 用途说明：
 * - 从原始通知文本中提取结构化交易候选。
 */
interface TransactionExtractor {
    /**
     * 参数说明：
     * - event：当前待解析的通知事件。
     */
    suspend fun extract(event: NotificationEvent): OperationResult<TransactionCandidate>
}

/**
 * 用途说明：
 * - 在本地规则、启发式语义校验与云端分析之间做路由决策。
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
 * - 账本仓储抽象。
 */
interface LedgerRepository {
    fun observeEntries(): Flow<List<LedgerEntry>>

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
    suspend fun ignore(id: Long)

    /**
     * 参数说明：
     * - id：待确认记录主键。
     */
    suspend fun markFalsePositive(id: Long)
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
     * - enabled：本地启发式语义校验目标状态。
     */
    suspend fun setLocalLlmConfidenceBoostEnabled(enabled: Boolean)

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
