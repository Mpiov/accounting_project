package com.autobook.cn.core.common

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 统一金额格式化，保证各个页面展示一致。 */
object CurrencyFormatter {
    private val formatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale.CHINA)

    fun format(amount: Double): String = formatter.format(amount)
}

/** 统一时间格式化，供列表和详情页复用。 */
object TimeFormatter {
    private val dateTimeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

    fun format(timestamp: Long): String = dateTimeFormat.format(Date(timestamp))
}
