package com.autobook.cn.core.data.impl

import com.autobook.cn.core.data.LocalLlmRequest
import com.autobook.cn.core.data.LocalLlmResponse
import com.autobook.cn.core.data.LocalModelRepository
import com.autobook.cn.core.data.OnDeviceLlmEngine
import com.autobook.cn.core.model.LocalModelInstallStatus
import com.autobook.cn.core.model.MoneyDirection
import com.autobook.cn.core.model.OperationResult
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 端侧 LLM 引擎占位实现。
 *
 * 用途说明：
 * - 在真实模型文件和推理框架尚未接入前，先把“结构化输入 -> 结构化输出”的适配层跑通。
 * - 当前实现只模拟 on-device 模型行为，方便联调协议、fallback 和审计链路。
 *
 * 参数说明：
 * - localModelRepository：本地模型仓储，用于确认当前是否已有选中的模型文件可供端侧推理。
 */
@Singleton
class StubOnDeviceLlmEngine @Inject constructor(
    private val localModelRepository: LocalModelRepository,
) : OnDeviceLlmEngine {
    /**
     * 根据标准输入模拟端侧模型输出。
     *
     * 参数说明：
     * - request：标准化后的本地 LLM 请求。
     */
    override suspend fun analyze(request: LocalLlmRequest): OperationResult<LocalLlmResponse> {
        val modelSnapshot = localModelRepository.snapshot.first()
        if (modelSnapshot.installStatus != LocalModelInstallStatus.READY || modelSnapshot.selectedModelPath == null) {
            return OperationResult.RetryableFailure("Selected local model is not installed yet")
        }

        val mergedText = listOfNotNull(request.title, request.body)
            .joinToString(separator = " ")
            .lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (mergedText.isBlank()) {
            return OperationResult.RetryableFailure("Stub on-device LLM received empty text")
        }
        if (!BANK_STUB_KEYWORDS.any(mergedText::contains) || !TRANSFER_STUB_KEYWORDS.any(mergedText::contains)) {
            return OperationResult.RetryableFailure("Stub on-device LLM found insufficient bank transfer semantics")
        }

        val direction = detectDirection(mergedText = mergedText, request = request)
        if (direction == MoneyDirection.UNKNOWN) {
            return OperationResult.RetryableFailure("Stub on-device LLM could not determine direction")
        }

        val merchant = detectCounterparty(mergedText) ?: request.seedMerchant
        val score = buildScore(
            mergedText = mergedText,
            request = request,
            direction = direction,
        )
        val shouldAutoPost = score >= STUB_AUTO_POST_THRESHOLD
        return OperationResult.Success(
            LocalLlmResponse(
                direction = direction,
                categoryHint = "转账",
                merchant = merchant,
                confidenceScore = score,
                explanation = buildExplanation(
                    direction = direction,
                    merchant = merchant,
                    shouldAutoPost = shouldAutoPost,
                    mergedText = mergedText,
                ),
                shouldAutoPost = shouldAutoPost,
                modelVersion = "stub-runtime:${modelSnapshot.selectedModelId}",
            ),
        )
    }

    /**
     * 根据通知语义模拟模型的方向判断。
     *
     * 参数说明：
     * - mergedText：标准化后的通知文本。
     * - request：规则层提供的基础候选。
     */
    private fun detectDirection(
        mergedText: String,
        request: LocalLlmRequest,
    ): MoneyDirection {
        val hitIncome = INCOME_STUB_KEYWORDS.any(mergedText::contains)
        val hitExpense = EXPENSE_STUB_KEYWORDS.any(mergedText::contains)
        return when {
            hitExpense && !hitIncome -> MoneyDirection.EXPENSE
            hitIncome && !hitExpense -> MoneyDirection.INCOME
            request.seedDirection != MoneyDirection.UNKNOWN -> request.seedDirection
            else -> MoneyDirection.UNKNOWN
        }
    }

    /**
     * 模拟模型对结构化结果的置信度评分。
     *
     * 参数说明：
     * - mergedText：标准化后的通知文本。
     * - request：规则层候选。
     * - direction：模型判断后的方向。
     */
    private fun buildScore(
        mergedText: String,
        request: LocalLlmRequest,
        direction: MoneyDirection,
    ): Float {
        var score = request.seedConfidenceScore.coerceAtLeast(0.62f)
        if (BANK_STUB_KEYWORDS.any(mergedText::contains)) {
            score += 0.06f
        }
        if (TRANSFER_STUB_KEYWORDS.any(mergedText::contains)) {
            score += 0.07f
        }
        if (ACCOUNT_STUB_KEYWORDS.any(mergedText::contains)) {
            score += 0.04f
        }
        if (direction != MoneyDirection.UNKNOWN) {
            score += 0.05f
        }
        if (SETTLEMENT_STUB_KEYWORDS.any(mergedText::contains)) {
            score += 0.03f
        }
        return score.coerceIn(0.6f, 0.92f)
    }

    /**
     * 从文本中模拟抽取对手方名称。
     *
     * 参数说明：
     * - mergedText：标准化后的通知文本。
     */
    private fun detectCounterparty(mergedText: String): String? {
        return COUNTERPARTY_STUB_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(mergedText)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }

    /**
     * 生成便于审计的解释文本。
     *
     * 参数说明：
     * - direction：模型确认方向。
     * - merchant：模型建议的对手方。
     * - shouldAutoPost：模型是否建议自动入账。
     * - mergedText：标准化后的通知文本。
     */
    private fun buildExplanation(
        direction: MoneyDirection,
        merchant: String,
        shouldAutoPost: Boolean,
        mergedText: String,
    ): String {
        val directionLabel = when (direction) {
            MoneyDirection.INCOME -> "转入"
            MoneyDirection.EXPENSE -> "转出"
            MoneyDirection.UNKNOWN -> "未知"
        }
        val evidenceCount = listOf(
            BANK_STUB_KEYWORDS.any(mergedText::contains),
            TRANSFER_STUB_KEYWORDS.any(mergedText::contains),
            ACCOUNT_STUB_KEYWORDS.any(mergedText::contains),
        ).count { it }
        return "Stub on-device LLM structured result: direction=$directionLabel, merchant=$merchant, " +
            "evidenceCount=$evidenceCount, shouldAutoPost=$shouldAutoPost"
    }
}

/** 占位端侧模型识别银行语义的关键词。 */
private val BANK_STUB_KEYWORDS = listOf("银行", "工行", "农行", "建行", "招行", "账户", "卡号", "尾号")

/** 占位端侧模型识别账户语义的关键词。 */
private val ACCOUNT_STUB_KEYWORDS = listOf("账户", "卡号", "尾号", "借记卡", "信用卡")

/** 占位端侧模型识别转账语义的关键词。 */
private val TRANSFER_STUB_KEYWORDS = listOf("转账", "转出", "转入", "汇出", "汇入", "汇款")

/** 占位端侧模型识别结算完成语义的关键词。 */
private val SETTLEMENT_STUB_KEYWORDS = listOf("成功", "到账", "已到账", "已转出", "已汇出")

/** 占位端侧模型识别入账方向的关键词。 */
private val INCOME_STUB_KEYWORDS = listOf("转入", "收款", "收入", "到账", "入账")

/** 占位端侧模型识别出账方向的关键词。 */
private val EXPENSE_STUB_KEYWORDS = listOf("转出", "付款", "支出", "扣款", "汇出")

/** 占位端侧模型只有在分数足够高时才建议自动入账。 */
private const val STUB_AUTO_POST_THRESHOLD = 0.85f

/** 占位端侧模型抽取常见对手方名称时使用的模式。 */
private val COUNTERPARTY_STUB_PATTERNS = listOf(
    Regex("""向(.+?)转(?:账|出)"""),
    Regex("""收款人[:：]?\s*(.+?)(?:\s|$)"""),
    Regex("""付款对象[:：]?\s*(.+?)(?:\s|$)"""),
    Regex("""交易对手[:：]?\s*(.+?)(?:\s|$)"""),
)
