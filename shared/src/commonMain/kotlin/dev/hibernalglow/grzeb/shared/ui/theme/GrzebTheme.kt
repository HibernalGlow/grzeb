package dev.hibernalglow.grzeb.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 面向代码与全文检索工具的 M3 Expressive 调色板：
 * - Primary 采用冷静、高对比的青蓝（Slate Teal），长时间扫读不刺眼；
 * - Secondary 采用冷灰青（Slate Cyan），承担目录层级、选中行与筛选器状态；
 * - Tertiary 采用暖琥珀（Warm Amber），专用于命中高亮与计数徽章，在冷色表面上瞬间跳出；
 * - 完整配置 surfaceContainerLowest → Highest 五级表面阶梯，不靠生硬边框区分区域。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF006778),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4EBFF),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF3E636D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC1E8F4),
    onSecondaryContainer = Color(0xFF001F27),
    tertiary = Color(0xFF7C5800),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF271900),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FAFC),
    onBackground = Color(0xFF171C1E),
    surface = Color(0xFFF5FAFC),
    onSurface = Color(0xFF171C1E),
    surfaceVariant = Color(0xFFDBE4E7),
    onSurfaceVariant = Color(0xFF3F484B),
    surfaceTint = Color(0xFF006778),
    inverseSurface = Color(0xFF2C3133),
    inverseOnSurface = Color(0xFFECF2F4),
    inversePrimary = Color(0xFF6AD3EA),
    outline = Color(0xFF6F797C),
    outlineVariant = Color(0xFFBFC8CB),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF5FAFC),
    surfaceDim = Color(0xFFD5DBDD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF4F6),
    surfaceContainer = Color(0xFFE9EFF1),
    surfaceContainerHigh = Color(0xFFE3E9EB),
    surfaceContainerHighest = Color(0xFFDEE3E5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6AD3EA),
    onPrimary = Color(0xFF003640),
    primaryContainer = Color(0xFF004E5C),
    onPrimaryContainer = Color(0xFFB4EBFF),
    secondary = Color(0xFFA6CCD7),
    onSecondary = Color(0xFF0B353E),
    secondaryContainer = Color(0xFF254B55),
    onSecondaryContainer = Color(0xFFC1E8F4),
    tertiary = Color(0xFFF7BD48),
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF5E4200),
    onTertiaryContainer = Color(0xFFFFDEA6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1416),
    onBackground = Color(0xFFDEE3E5),
    surface = Color(0xFF0F1416),
    onSurface = Color(0xFFDEE3E5),
    surfaceVariant = Color(0xFF3F484B),
    onSurfaceVariant = Color(0xFFBFC8CB),
    surfaceTint = Color(0xFF6AD3EA),
    inverseSurface = Color(0xFFDEE3E5),
    inverseOnSurface = Color(0xFF2C3133),
    inversePrimary = Color(0xFF006778),
    outline = Color(0xFF899295),
    outlineVariant = Color(0xFF3F484B),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343A3C),
    surfaceDim = Color(0xFF0F1416),
    surfaceContainerLowest = Color(0xFF0A0F11),
    surfaceContainerLow = Color(0xFF171C1E),
    surfaceContainer = Color(0xFF1B2022),
    surfaceContainerHigh = Color(0xFF252B2D),
    surfaceContainerHighest = Color(0xFF303638),
)

/**
 * M3 Expressive 形状层级：
 * - extraSmall: 紧凑行号标记、微型计数胶囊
 * - small: 树节点选中背景、代码片段预览框、筛选 Chip
 * - medium: 目录选择栏、分组面板
 * - large: 主搜索框与主操作区容器
 * - extraLarge: 弹窗与浮层
 */
val GrzebShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val GrzebTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp,
    ),
)

/** 检索工具专属的语义扩展色（随深浅色切换，保证代码高亮与焦点行的对比度）。 */
@Immutable
data class GrzebExtendedColors(
    val matchHighlightContainer: Color,
    val onMatchHighlightContainer: Color,
    val focusLineContainer: Color,
    val focusLineIndicator: Color,
    val codeGutterText: Color,
)

private val LightExtendedColors = GrzebExtendedColors(
    matchHighlightContainer = Color(0xFFFFDF9E),
    onMatchHighlightContainer = Color(0xFF261900),
    focusLineContainer = Color(0xFFE1F3F9),
    focusLineIndicator = Color(0xFF006778),
    codeGutterText = Color(0xFF677377),
)

private val DarkExtendedColors = GrzebExtendedColors(
    matchHighlightContainer = Color(0xFF634600),
    onMatchHighlightContainer = Color(0xFFFFDEA6),
    focusLineContainer = Color(0xFF182F36),
    focusLineIndicator = Color(0xFF6AD3EA),
    codeGutterText = Color(0xFF7B878B),
)

val LocalGrzebExtendedColors = staticCompositionLocalOf { LightExtendedColors }

object GrzebThemeDefaults {
    val extendedColors: GrzebExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGrzebExtendedColors.current

    val codeTextStyle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GrzebTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    androidx.compose.runtime.CompositionLocalProvider(
        LocalGrzebExtendedColors provides extendedColors,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = GrzebShapes,
            typography = GrzebTypography,
            content = content,
        )
    }
}
