package com.autobook.cn.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.autobook.cn.core.model.LedgerEntry
import com.autobook.cn.core.model.MoneyDirection

/**
 * 用途说明：
 * - 描述“手动新建一笔账单”时需要的最小草稿。
 * - 完整编辑页和极速记账页都可以把各自的表单状态映射成这份草稿，再走统一校验和组装流程。
 *
 * 参数说明：
 * - amountInput：当前金额输入文本。
 * - direction：当前收支方向。
 * - merchant：商户或对方名称；极速记账页允许为空并回退到分类名。
 * - category：当前分类名称。
 * - note：备注文本。
 * - orderId：订单号文本。
 * - entryTimestamp：交易时间；为空时由调用方或当前时间兜底。
 * - channel：写入账单后的展示渠道。
 * - traceSource：追踪来源。
 * - tracePath：追踪路径。
 * - traceModelVersion：追踪模型版本。
 * - traceNotificationId：通知链路生成的追踪 id；手动记账通常为空。
 */
internal data class ManualLedgerEntryDraft(
    val amountInput: String,
    val direction: MoneyDirection,
    val merchant: String,
    val category: String,
    val note: String = "",
    val orderId: String = "",
    val entryTimestamp: Long? = null,
    val channel: String = "手动记账",
    val traceSource: String = "manual",
    val tracePath: String = "manual-entry",
    val traceModelVersion: String = "manual-v1",
    val traceNotificationId: Long? = null,
)

/**
 * 用途说明：
 * - 控制手动建账时商户字段的校验口径。
 * - 完整编辑页要求显式填写商户；极速记账页允许回退到分类名，避免阻塞 3 秒录入链路。
 */
internal enum class ManualEntryMerchantPolicy {
    REQUIRE_EXPLICIT_MERCHANT,
    USE_CATEGORY_AS_FALLBACK,
}

/**
 * 用途说明：
 * - 统一表达手动建账草稿组装后的结果，避免页面层自己重复拼装 `LedgerEntry`。
 */
internal sealed interface ManualLedgerEntryBuildResult

/**
 * 用途说明：
 * - 手动建账草稿校验通过后，返回最终可入库的账单实体。
 *
 * 参数说明：
 * - entry：可直接写入仓储层的账单实体。
 */
internal data class ManualLedgerEntryBuildSuccess(
    val entry: LedgerEntry,
) : ManualLedgerEntryBuildResult

/**
 * 用途说明：
 * - 手动建账草稿校验失败时，返回页面可直接展示的错误文案。
 *
 * 参数说明：
 * - message：给页面展示的错误提示。
 */
internal data class ManualLedgerEntryBuildFailure(
    val message: String,
) : ManualLedgerEntryBuildResult

/**
 * 用途说明：
 * - 统一校验手动建账草稿，并把它映射成 `LedgerEntry`。
 * - 这样完整编辑页与极速记账页只需要负责各自的状态管理，不需要各自维护一套入库规则。
 *
 * 参数说明：
 * - draft：待校验的手动建账草稿。
 * - merchantPolicy：商户字段的校验策略。
 * - nowProvider：当前时间提供器，测试时可注入固定时间。
 */
internal fun buildManualLedgerEntry(
    draft: ManualLedgerEntryDraft,
    merchantPolicy: ManualEntryMerchantPolicy,
    nowProvider: () -> Long = { System.currentTimeMillis() },
): ManualLedgerEntryBuildResult {
    val amount = draft.amountInput.toDoubleOrNull()
    if (amount == null || amount <= 0.0) {
        return ManualLedgerEntryBuildFailure(message = "请输入有效金额")
    }
    if (draft.direction == MoneyDirection.UNKNOWN) {
        return ManualLedgerEntryBuildFailure(message = "请选择收支方向")
    }

    val normalizedCategory = draft.category.trim().ifBlank { "待分类" }
    val normalizedMerchant = when (merchantPolicy) {
        ManualEntryMerchantPolicy.REQUIRE_EXPLICIT_MERCHANT -> draft.merchant.trim()
        // 极速记账允许用户只选分类和金额，因此这里用分类名回退商户展示文本。
        ManualEntryMerchantPolicy.USE_CATEGORY_AS_FALLBACK -> draft.merchant.trim().ifBlank { normalizedCategory }
    }
    if (normalizedMerchant.isBlank()) {
        return ManualLedgerEntryBuildFailure(message = "请输入商户或备注对象")
    }

    return ManualLedgerEntryBuildSuccess(
        entry = LedgerEntry(
            amount = amount,
            direction = draft.direction,
            merchant = normalizedMerchant,
            channel = draft.channel,
            timestamp = draft.entryTimestamp ?: nowProvider(),
            category = normalizedCategory,
            orderId = draft.orderId.trim().ifBlank { null },
            note = draft.note.trim().ifBlank { null },
            traceSource = draft.traceSource,
            tracePath = draft.tracePath,
            traceModelVersion = draft.traceModelVersion,
            traceNotificationId = draft.traceNotificationId,
            editedManually = true,
        ),
    )
}

/**
 * 用途说明：
 * - 从导航参数中读取收支方向并转成领域枚举，避免多个 ViewModel 重复手写同样的解析逻辑。
 *
 * 参数说明：
 * - key：导航参数名。
 */
internal fun SavedStateHandle.readMoneyDirectionArgument(key: String): MoneyDirection? {
    return get<String>(key)
        ?.takeIf { it.isNotBlank() }
        ?.let { rawDirection -> MoneyDirection.entries.firstOrNull { it.name == rawDirection } }
}
