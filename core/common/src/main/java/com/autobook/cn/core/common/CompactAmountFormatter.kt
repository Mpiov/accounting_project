package com.autobook.cn.core.common

import java.text.DecimalFormat
import kotlin.math.absoluteValue

/**
 * 用途说明：
 * - 统一处理客户端界面中的金额缩写显示逻辑。
 * - 当金额较大时自动转换成 `k`、`w` 这种更紧凑的展示形式，避免卡片或图表中出现省略号。
 */
object CompactAmountFormatter {
    private val oneDecimalFormatter = DecimalFormat("0.#")
    private val twoDecimalFormatter = DecimalFormat("0.##")

    /**
     * 用途说明：
     * - 将纯数字金额格式化为紧凑文本，不带货币符号。
     *
     * 参数说明：
     * - amount：待格式化金额。
     */
    fun formatNumber(
        amount: Double,
    ): String {
        val absAmount = amount.absoluteValue
        return when {
            absAmount >= 10_000 -> "${oneDecimalFormatter.format(absAmount / 10_000)}w"
            absAmount >= 1_000 -> "${oneDecimalFormatter.format(absAmount / 1_000)}k"
            else -> twoDecimalFormatter.format(absAmount)
        }
    }

    /**
     * 用途说明：
     * - 将金额格式化为适合界面展示的货币文本，金额较大时自动压缩为 `k`、`w`。
     *
     * 参数说明：
     * - amount：待格式化金额。
     * - signed：是否在正负金额前主动补 `+` / `-` 号。
     */
    fun formatCurrency(
        amount: Double,
        signed: Boolean = false,
    ): String {
        val sign = when {
            signed && amount > 0 -> "+"
            amount < 0 -> "-"
            else -> ""
        }
        val absAmount = amount.absoluteValue
        val body = if (absAmount >= 1_000) {
            "¥${formatNumber(absAmount)}"
        } else {
            CurrencyFormatter.format(absAmount)
        }
        return sign + body
    }
}
