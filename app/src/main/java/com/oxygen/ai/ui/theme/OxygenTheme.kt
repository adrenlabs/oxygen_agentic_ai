package com.oxygen.ai.ui.theme

import android.os.Build
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object OxygenColors {
    val OxygenCyan = Color(0xFF14B8A6)
    val OxygenCyanBright = Color(0xFF2DD4BF)
    val DeepNavy = Color(0xFF07111A)
    val Navy = Color(0xFF0E1C28)
    val CardDark = Color(0xFF132333)
    val Mist = Color(0xFFF3F7F6)
    val Ink = Color(0xFF102027)
    val Coral = Color(0xFFE85D4C)
    val Amber = Color(0xFFE6B325)
    val Paper = Color(0xFFFFFFFF)
}

private val DarkScheme = darkColorScheme(
    primary = OxygenColors.OxygenCyanBright,
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF0F766E),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0B1220),
    background = OxygenColors.DeepNavy,
    onBackground = Color(0xFFE6EEF4),
    surface = OxygenColors.Navy,
    onSurface = Color(0xFFE6EEF4),
    surfaceVariant = OxygenColors.CardDark,
    onSurfaceVariant = Color(0xFFB6C4CE),
    error = OxygenColors.Coral,
    outline = Color(0xFF3D5566),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF3F5B6B),
    onSecondary = Color.White,
    background = OxygenColors.Mist,
    onBackground = OxygenColors.Ink,
    surface = OxygenColors.Paper,
    onSurface = OxygenColors.Ink,
    surfaceVariant = Color(0xFFE1EEEC),
    onSurfaceVariant = Color(0xFF3C5159),
    error = OxygenColors.Coral,
    outline = Color(0xFF7A929A),
)

object OxygenTypography {
    val value = Typography(
        displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.2.sp),
    )
}

object OxygenShapes {
    val extraSmall = RoundedCornerShape(8.dp)
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(18.dp)
    val large = RoundedCornerShape(26.dp)
}

object OxygenDimensions {
    val screenPad: Dp = 16.dp
    val cardPad: Dp = 14.dp
    val gutter: Dp = 12.dp
}

object OxygenMotion {
    fun standard(ms: Int = 280) = tween<Float>(ms)
}

data class OxygenThemeExtra(val accent: Color, val warning: Color)

val LocalOxygenExtra = staticCompositionLocalOf { OxygenThemeExtra(OxygenColors.OxygenCyan, OxygenColors.Amber) }

@Composable
fun OxygenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = OxygenTypography.value,
        content = content,
    )
}
