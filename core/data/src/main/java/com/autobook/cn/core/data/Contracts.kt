package com.autobook.cn.core.data

import com.autobook.cn.core.model.AppSettings
import com.autobook.cn.core.model.CaptureSession
import com.autobook.cn.core.model.ImageImportSuggestion
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.LocalModelSnapshot
import com.autobook.cn.core.model.NotificationEvent
import com.autobook.cn.core.model.NotificationProcessOutcome
import com.autobook.cn.core.model.NotificationSourceApp
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.OperationResult
import com.autobook.cn.core.model.PendingEntry
import com.autobook.cn.core.model.TransactionCandidate
import kotlinx.coroutines.flow.Flow

/** 通知监听服务写入并处理通知的统一入口。 */
interface NotificationIngestor {
    suspend fun ingest(event: NotificationEvent): OperationResult<NotificationProcessOutcome>
}

/** 协调截图授权与截图执行，供 OCR 回退链路使用。 */
interface CaptureCoordinator {
    suspend fun requestAuthorization(reason: String): OperationResult<CaptureSession>
    suspend fun capture(sessionId: String): OperationResult<String>
    suspend fun cancel(sessionId: String)
    suspend fun reportFailure(sessionId: String, message: String)
}

/** OCR 能力抽象，便于后续替换真实实现。 */
interface OcrEngine {
    suspend fun extractText(imageRef: String): OperationResult<String>
}

/**
 * 图片导入记账分析器。
 *
 * 用途说明：
 * - 统一封装“用户主动上传图片记账”链路的分析入口。
 * - 当前阶段允许用 mock 实现模拟云端多模态或本地 OCR/轻量模型路径，后续可替换为真实实现而不改上层页面。
 *
 * 参数说明：
 * - imageRef：图片引用；当前 mock 阶段只作为占位字段保留，后续可替换为 Uri、文件路径或上传后的资源标识。
 */
interface ImageImportAnalyzer {
    suspend fun analyze(imageRef: String): OperationResult<ImageImportSuggestion>
}

/**
 * 本地图片 OCR 引擎。
 *
 * 用途说明：
 * - 专门负责“用户主动上传图片记账”链路中的文字提取。
 * - 与通知自动记账主链路彻底解耦，避免 OCR 再次混入通知解析流程。
 *
 * 参数说明：
 * - imageRef：图片引用；当前阶段可为内容 Uri 或本地缓存路径。
 */
interface ImageImportLocalOcrEngine {
    suspend fun extractText(imageRef: String): OperationResult<String>
}

/**
 * 本地图片轻量模型分析器。
 *
 * 用途说明：
 * - 接收 OCR 文本后生成一份结构化记账建议。
 * - 当前已可由端侧 Qwen 生成建议；后续仍可继续替换更强的本地推理方案，而不改上层图片导入流程。
 *
 * 参数说明：
 * - imageRef：图片引用，用于后续回溯或多模态扩展。
 * - ocrText：图片 OCR 提取出的文本。
 */
interface ImageImportLocalReasoner {
    suspend fun analyze(
        imageRef: String,
        ocrText: String,
    ): OperationResult<ImageImportSuggestion>
}

/**
 * 本地 LLM 标准输入。
 *
 * 用途说明：
 * - 把通知原文与规则层候选冻结成统一协议，便于后续接入真实端侧模型时不改上层调用。
 * - 字段设计只保留模型真正需要的上下文，避免把整个领域对象直接暴露给模型适配层。
 *
 * 参数说明：
 * - title：通知标题。
 * - body：通知正文。
 * - sourceApp：通知来源应用类型。
 * - seedAmount：规则层识别出的金额。
 * - seedDirection：规则层识别出的方向。
 * - seedMerchant：规则层识别出的商户或对手方。
 * - seedCategoryHint：规则层给出的分类提示。
 * - seedConfidenceScore：规则层候选置信度。
 * - seedConfidenceReason：规则层置信度说明。
 * - seedParsePath：规则层解析链路标记。
 */
data class LocalLlmRequest(
    val title: String?,
    val body: String?,
    val sourceApp: NotificationSourceApp,
    val seedAmount: Double,
    val seedDirection: MoneyDirection,
    val seedMerchant: String,
    val seedCategoryHint: String,
    val seedConfidenceScore: Float,
    val seedConfidenceReason: String,
    val seedParsePath: String,
)

/**
 * 本地 LLM 标准输出。
 *
 * 用途说明：
 * - 对端侧模型和占位引擎统一定义结构化结果，避免把自由文本直接交给路由层消费。
 * - 路由层只采纳允许字段，金额仍以规则层金额为准。
 *
 * 参数说明：
 * - direction：模型确认后的交易方向。
 * - categoryHint：模型建议的分类。
 * - merchant：模型建议的对手方或商户。
 * - confidenceScore：模型建议的置信度。
 * - explanation：模型对本次判断的简要解释。
 * - shouldAutoPost：模型是否建议自动入账。
 * - modelVersion：当前模型或适配层版本号，用于审计追踪。
 */
data class LocalLlmResponse(
    val direction: MoneyDirection,
    val categoryHint: String?,
    val merchant: String?,
    val confidenceScore: Float,
    val explanation: String,
    val shouldAutoPost: Boolean,
    val modelVersion: String,
)

/**
 * 端侧 LLM 引擎抽象。
 *
 * 用途说明：
 * - 作为真实 on-device 模型运行时的稳定接入点，可对接 ONNX、GGUF 或其他本地推理方案。
 * - 当前阶段也允许用占位引擎先联调协议与回退路径。
 *
 * 参数说明：
 * - request：标准化后的模型输入。
 */
interface OnDeviceLlmEngine {
    suspend fun analyze(request: LocalLlmRequest): OperationResult<LocalLlmResponse>
}

/**
 * 本地 LLM 分析器抽象。
 *
 * 用途说明：
 * - 专门处理“规则层已经识别出基础字段，但整体置信度仍处于中间区间”的交易。
 * - 输入原始通知事件和规则层候选，输出一份经过端侧语义增强后的候选结果。
 *
 * 参数说明：
 * - event：当前待分析的原始通知事件。
 * - seedCandidate：第一层本地规则已经给出的基础候选，用于为端侧模型提供稳定先验。
 */
interface LocalLlmAnalyzer {
    suspend fun analyze(
        event: NotificationEvent,
        seedCandidate: TransactionCandidate,
    ): OperationResult<TransactionCandidate>
}

/** 从原始通知中提取标准化交易候选。 */
interface TransactionExtractor {
    suspend fun extract(event: NotificationEvent): OperationResult<TransactionCandidate>
}

/** 在本地解析、OCR 回退与云端分析之间做路由决策。 */
interface ModelRouter {
    suspend fun route(event: NotificationEvent): OperationResult<TransactionCandidate>
}

/** 账本数据的持久化与导出接口。 */
interface LedgerRepository {
    fun observeEntries(): Flow<List<LedgerEntry>>

    suspend fun add(entry: LedgerEntry): Long
    suspend fun update(entry: LedgerEntry)
    suspend fun delete(id: Long)
    suspend fun getById(id: Long): LedgerEntry?
    suspend fun findPotentialDuplicate(candidate: TransactionCandidate): LedgerEntry?
    /**
     * 针对图片/截图导入场景执行更宽松的历史判重。
     *
     * 用途说明：
     * - 截图里出现的往往是历史交易列表，不能再依赖“当前时间 ±5 秒”的实时窗口。
     * - 因此这里优先按 `orderId` 精确匹配，其次退回到“同方向 + 同金额 + 同商户”的历史查重。
     *
     * 参数说明：
     * - orderId：候选订单号；命中时优先视为重复。
     * - amount：候选金额。
     * - direction：候选方向。
     * - merchant：候选商户或交易对象。
     */
    suspend fun findPotentialImageImportDuplicate(
        orderId: String?,
        amount: Double,
        direction: MoneyDirection,
        merchant: String,
    ): LedgerEntry?
    suspend fun exportCsv(): String
}

/** 待确认队列的持久化接口。 */
interface PendingRepository {
    fun observeEntries(): Flow<List<PendingEntry>>

    suspend fun add(entry: PendingEntry): Long
    suspend fun update(entry: PendingEntry)
    suspend fun getById(id: Long): PendingEntry?
    suspend fun confirm(id: Long): PendingEntry?

    suspend fun ignore(id: Long)
    suspend fun markFalsePositive(id: Long)
}

/** 驱动权限、能力开关与留存策略的设置存储接口。 */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setAutoAccountingEnabled(enabled: Boolean)

    suspend fun setCloudAnalysisEnabled(enabled: Boolean)
    suspend fun setLocalLlmConfidenceBoostEnabled(enabled: Boolean)
    suspend fun setSelectedLocalModelId(modelId: String)
    suspend fun setCaptureEnabled(enabled: Boolean)
    suspend fun setOcrEnabled(enabled: Boolean)
    suspend fun setNotificationRetentionDays(days: Int)
    suspend fun setScreenshotRetentionDays(days: Int)
    /**
     * 写入指定月份的预算金额。
     *
     * 参数说明：
     * - monthKey：`yyyy-MM` 格式的月份键。
     * - amount：预算金额，单位为元。
     */
    suspend fun setMonthlyBudget(monthKey: String, amount: Double) = Unit

    /**
     * 清除指定月份的预算金额。
     *
     * 参数说明：
     * - monthKey：`yyyy-MM` 格式的月份键。
     */
    suspend fun clearMonthlyBudget(monthKey: String) = Unit
    suspend fun setSourceEnabled(source: NotificationSourceApp, enabled: Boolean)
}

/**
 * 本地模型仓储接口。
 *
 * 用途说明：
 * - 负责端侧模型的目录管理、下载状态和当前可用模型快照。
 * - 把模型文件管理从具体推理引擎中分离出来，便于后续替换运行时。
 */
interface LocalModelRepository {
    val snapshot: Flow<LocalModelSnapshot>
    suspend fun refresh()
    suspend fun selectModel(modelId: String)
    suspend fun downloadSelectedModel()
}
