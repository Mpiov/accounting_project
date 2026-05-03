package com.autobook.cn.feature.pending.model

/**
 * 待确认前端共享模型定义。
 *
 * 用途说明：
 * - 把待确认工作台仍在复用的展示模型集中收口到独立模型文件。
 * - 新版待确认工作台和对应 ViewModel 统一依赖这里，后续删除旧页面会更安全。
 */

/**
 * 待确认列表单项的展示模型。
 *
 * 参数说明：
 * - id：待确认记录主键。
 * - title：列表主标题，优先展示备注首行，没有备注时展示建议分类。
 * - amount：已经格式化好的金额文案，包含收入/支出/未知方向的符号前缀。
 * - isIncome：当前金额是否为收入，用于前端选择绿色或红色金额语义。
 * - isDirectionKnown：当前待确认项是否已经识别出收支方向；未知方向时金额用中性色展示。
 * - subtitle：副标题，通常包含渠道与时间。
 * - reason：进入待确认队列的原因说明。
 * - rawNotificationText：关联通知原文，按需展开查看。
 * - categoryHint：建议分类。
 * - canDirectConfirm：是否允许直接确认入账。
 * - primaryActionLabel：主按钮文案。
 * - directionHint：收支方向补充提示，没有则为空。
 */
data class PendingItemUiModel(
    val id: Long,
    val title: String,
    val amount: String,
    val isIncome: Boolean,
    val isDirectionKnown: Boolean = true,
    val subtitle: String,
    val reason: String,
    val rawNotificationText: String? = null,
    val categoryHint: String,
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
