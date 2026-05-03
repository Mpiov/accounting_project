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

/**
 * 亮色主题配色。
 *
 * 用途说明：
 * - 以白色作为主视觉面积，让设置页、账本页和列表页更轻、更干净。
 * - 米白色只承担页面背景、选中态和次级容器，浅棕色只用于主按钮和少量强调。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF8B6047),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3E6DC),
    onPrimaryContainer = Color(0xFF4F3526),
    secondary = Color(0xFFAE9274),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF7EFE6),
    onSecondaryContainer = Color(0xFF4C3A2A),
    tertiary = Color(0xFF4F8F7A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE4F2EC),
    onTertiaryContainer = Color(0xFF24483C),
    background = Color(0xFFFEFCF8),
    onBackground = Color(0xFF28231F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF28231F),
    surfaceVariant = Color(0xFFF6F0E8),
    onSurfaceVariant = Color(0xFF6D6258),
    outline = Color(0xFFE7DDD2),
    error = Color(0xFFB84F4A),
    onError = Color(0xFFFFFFFF),
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

/** 整体字号继续下调到轻设置页密度，减少各页面之间的视觉落差。 */
private val AutobookTypography = Typography(
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium),
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
