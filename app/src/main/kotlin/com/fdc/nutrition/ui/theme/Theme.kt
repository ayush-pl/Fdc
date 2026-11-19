package com.fdc.nutrition.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fdc.nutrition.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Teal50, onPrimary = Grey0, primaryContainer = Teal95, onPrimaryContainer = Teal20,
    secondary = Teal40, onSecondary = Grey0, secondaryContainer = Teal90, onSecondaryContainer = Teal10,
    tertiary = Color(0xFF4C7DF0), onTertiary = Grey0,
    background = Grey50, onBackground = Grey900,
    surface = Grey0, onSurface = Grey900, surfaceVariant = Grey100, onSurfaceVariant = Grey600,
    surfaceContainerLowest = Grey0, surfaceContainerLow = Grey0, surfaceContainer = Grey50, surfaceContainerHigh = Grey100, surfaceContainerHighest = Grey200,
    outline = Grey400, outlineVariant = Grey200, error = Color(0xFFC62828), onError = Grey0,
    errorContainer = Color(0xFFFDE4E1), onErrorContainer = Color(0xFF5F1412),
)

private val DarkColors = darkColorScheme(
    primary = Teal80, onPrimary = Teal20, primaryContainer = Teal30, onPrimaryContainer = Teal90,
    secondary = Teal80, onSecondary = Teal20, secondaryContainer = Color(0xFF1F3D37), onSecondaryContainer = Teal90,
    tertiary = Color(0xFF86A8FF), onTertiary = Color(0xFF0C2160),
    background = Charcoal, onBackground = Color(0xFFE6E9EB),
    surface = CharcoalSurface, onSurface = Color(0xFFE6E9EB), surfaceVariant = CharcoalSurfaceHigh, onSurfaceVariant = Color(0xFFA9B1B8),
    surfaceContainerLowest = Charcoal, surfaceContainerLow = CharcoalSurface, surfaceContainer = CharcoalSurface, surfaceContainerHigh = CharcoalSurfaceHigh, surfaceContainerHighest = Color(0xFF2B3035),
    outline = Color(0xFF6B737B), outlineVariant = CharcoalOutline, error = Color(0xFFFF8A80), onError = Color(0xFF5F1412),
    errorContainer = Color(0xFF5F1412), onErrorContainer = Color(0xFFFDE4E1),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Immutable
data class MacroPalette(val calories: Color, val protein: Color, val carbs: Color, val fat: Color, val fibre: Color, val water: Color, val weight: Color, val over: Color)

val LocalMacroPalette = staticCompositionLocalOf {
    MacroPalette(NutrientColors.calories, NutrientColors.protein, NutrientColors.carbs, NutrientColors.fat, NutrientColors.fibre, NutrientColors.water, NutrientColors.weight, NutrientColors.over)
}

@Composable
fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun FdcTheme(mode: ThemeMode = ThemeMode.SYSTEM, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = resolveDark(mode)
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val palette = if (dark) MacroPalette(Teal80, NutrientColors.proteinDark, NutrientColors.carbsDark, NutrientColors.fatDark, Color(0xFFA5D66F), Color(0xFF6CCBF3), Color(0xFFB39BFF), Color(0xFFFF8A73))
    else MacroPalette(NutrientColors.calories, NutrientColors.protein, NutrientColors.carbs, NutrientColors.fat, NutrientColors.fibre, NutrientColors.water, NutrientColors.weight, NutrientColors.over)
    CompositionLocalProvider(LocalMacroPalette provides palette, LocalIsDark provides dark) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

val LocalIsDark = staticCompositionLocalOf { false }
