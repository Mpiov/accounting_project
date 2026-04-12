package com.autobook.cn.core.common

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CurrencyFormatter {
    private val formatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale.CHINA)

    fun format(amount: Double): String = formatter.format(amount)
}

object TimeFormatter {
    private val dateTimeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

    fun format(timestamp: Long): String = dateTimeFormat.format(Date(timestamp))
}
