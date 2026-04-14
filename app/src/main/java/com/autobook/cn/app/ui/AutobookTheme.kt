package com.autobook.cn.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 亮色主题配色，整体改成米白纸张 + 棕色墨迹的暖调记账风格。 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF7B4B34),
    onPrimary = Color(0xFFFFFBF6),
    primaryContainer = Color(0xFFF4E1CD),
    onPrimaryContainer = Color(0xFF52301F),
    secondary = Color(0xFFA67C52),
    onSecondary = Color(0xFF2E1B0E),
    secondaryContainer = Color(0xFFFFF0DE),
    onSecondaryContainer = Color(0xFF4A3021),
    tertiary = Color(0xFFD6A95F),
    onTertiary = Color(0xFF3E2812),
    tertiaryContainer = Color(0xFFFFECCB),
    onTertiaryContainer = Color(0xFF5F4022),
    background = Color(0xFFFFF7EE),
    onBackground = Color(0xFF2F2119),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF2F2119),
    surfaceVariant = Color(0xFFFFF1E0),
    onSurfaceVariant = Color(0xFF6A5748),
    outline = Color(0xFFE2CDB7),
    error = Color(0xFFB35B45),
    onError = Color(0xFFFFFBF8),
)

/** 暗色主题也保持纸张与棕色气质，避免出现偏绿或偏紫观感。 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFDDB894),
    onPrimary = Color(0xFF3F2518),
    primaryContainer = Color(0xFF714B36),
    onPrimaryContainer = Color(0xFFF8EBDD),
    secondary = Color(0xFFC9A67F),
    onSecondary = Color(0xFF3A271A),
    secondaryContainer = Color(0xFF614B3D),
    onSecondaryContainer = Color(0xFFF0E2D3),
    tertiary = Color(0xFFE4BE76),
    onTertiary = Color(0xFF3B2A14),
    tertiaryContainer = Color(0xFF7A5A34),
    onTertiaryContainer = Color(0xFFF8ECD4),
    background = Color(0xFF1F1712),
    onBackground = Color(0xFFF2E7D8),
    surface = Color(0xFF31231B),
    onSurface = Color(0xFFF2E7D8),
    surfaceVariant = Color(0xFF5D4535),
    onSurfaceVariant = Color(0xFFD0C1B2),
    outline = Color(0xFFB79778),
    error = Color(0xFFD58B77),
    onError = Color(0xFF341912),
)

/** 整体字号下调半档，减少金额和标题在窄屏上的换行概率。 */
private val AutobookTypography = Typography(
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

/** 全局圆角改成更接近纸卡的方圆角，避免页面到处都是过圆的胶囊感。 */
private val AutobookShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
)

@Composable
fun AutobookTheme(
    content: @Composable () -> Unit,
) {
    /** 根据系统深浅色模式切换整套 Material3 颜色方案。 */
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AutobookTypography,
        shapes = AutobookShapes,
        content = content,
    )
}
