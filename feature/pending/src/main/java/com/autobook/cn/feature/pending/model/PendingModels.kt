package com.autobook.cn.feature.pending.model

/**
 * 待确认前端共享模型定义。
 *
 * 用途说明：
 * - 把待确认列表页面仍在复用的展示模型从旧版 `PendingScreen` 中拆出来。
 * - 新版待确认工作台和对应 ViewModel 统一依赖这里，后续删除旧页面会更安全。
 */

/**
 * 待确认列表单项的展示模型。
 *
 * 参数说明：
 * - id：待确认记录主键。
 * - merchant：商户或对手方名称。
 * - amount：已经格式化好的金额文案。
 * - subtitle：副标题，通常包含渠道与时间。
 * - reason：进入待确认队列的原因说明。
 * - rawNotificationText：关联通知原文，按需展开查看。
 * - categoryHint：建议分类。
 * - orderId：订单号，没有则为空。
 * - canDirectConfirm：是否允许直接确认入账。
 * - primaryActionLabel：主按钮文案。
 * - directionHint：收支方向补充提示，没有则为空。
 */
data class PendingItemUiModel(
    val id: Long,
    val merchant: String,
    val amount: String,
    val subtitle: String,
    val reason: String,
    val rawNotificationText: String? = null,
    val categoryHint: String,
    val orderId: String?,
    val canDirectConfirm: Boolean = true,
    val primaryActionLabel: String = "确认入账",
    val directionHint: String? = null,
)

/**
 * 待确认页面的界面状态。
 *
 * 参数说明：
 * - items：当前待确认列表项。
 */
data class PendingUiState(
    val items: List<PendingItemUiModel> = emptyList(),
)
