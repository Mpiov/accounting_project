package com.autobook.cn.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    secondary = Color(0xFF2F4B7C),
    tertiary = Color(0xFFCC5500),
    background = Color(0xFFF6F1E8),
    surface = Color(0xFFFFFBF5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7ED9B7),
    secondary = Color(0xFF92A8D1),
    tertiary = Color(0xFFFFB066),
    background = Color(0xFF11161A),
    surface = Color(0xFF182027),
)

@Composable
fun AutobookTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
