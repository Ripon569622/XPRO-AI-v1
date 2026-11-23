package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.model.ThemePreference

@Immutable
data class XproExtendedColors(
    val neonGlow: Color,
    val violetGlow: Color,
    val cardBorder: Color,
    val elevatedSurface: Color,
    val codeBackground: Color,
    val isDark: Boolean
)

val LocalXproColors = staticCompositionLocalOf {
    XproExtendedColors(
        neonGlow = XproNeonCyan,
        violetGlow = XproElectricViolet,
        cardBorder = XproBorderDark,
        elevatedSurface = XproDarkPurpleElevated,
        codeBackground = XproObsidianBlack,
        isDark = true
    )
}

private val XproDarkColorScheme = darkColorScheme(
    primary = XproNeonCyan,
    onPrimary = XproObsidianBlack,
    primaryContainer = XproDarkPurpleContainer,
    onPrimaryContainer = XproNeonCyan,
    secondary = XproElectricViolet,
    onSecondary = Color.White,
    secondaryContainer = XproDarkPurpleElevated,
    onSecondaryContainer = XproTextPrimaryDark,
    tertiary = XproMagentaAccent,
    onTertiary = Color.White,
    background = XproObsidianBlack,
    onBackground = XproTextPrimaryDark,
    surface = XproDeepVoid,
    onSurface = XproTextPrimaryDark,
    surfaceVariant = XproDarkPurpleSurface,
    onSurfaceVariant = XproTextSecondaryDark,
    outline = XproBorderDark,
    outlineVariant = Color(0xFF231942),
    error = XproErrorDark,
    onError = XproObsidianBlack
)

private val XproLightColorScheme = lightColorScheme(
    primary = XproDeepVioletLight,
    onPrimary = Color.White,
    primaryContainer = XproLightContainer,
    onPrimaryContainer = XproTextPrimaryLight,
    secondary = XproRoyalBlueLight,
    onSecondary = Color.White,
    secondaryContainer = XproLightSurfaceVariant,
    onSecondaryContainer = XproTextPrimaryLight,
    tertiary = XproMagentaLight,
    onTertiary = Color.White,
    background = XproLightBackground,
    onBackground = XproTextPrimaryLight,
    surface = XproLightSurface,
    onSurface = XproTextPrimaryLight,
    surfaceVariant = XproLightSurfaceVariant,
    onSurfaceVariant = XproTextSecondaryLight,
    outline = XproBorderLight,
    outlineVariant = Color(0xFFE2DCFB),
    error = XproErrorLight,
    onError = Color.White
)

private val XproShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun XproAiTheme(
    themePreference: ThemePreference = ThemePreference.DARK,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) XproDarkColorScheme else XproLightColorScheme
    val extendedColors = if (useDarkTheme) {
        XproExtendedColors(
            neonGlow = XproNeonCyan,
            violetGlow = XproElectricViolet,
            cardBorder = XproBorderDark,
            elevatedSurface = XproDarkPurpleElevated,
            codeBackground = Color(0xFF0A0614),
            isDark = true
        )
    } else {
        XproExtendedColors(
            neonGlow = XproRoyalBlueLight,
            violetGlow = XproDeepVioletLight,
            cardBorder = XproBorderLight,
            elevatedSurface = XproLightSurface,
            codeBackground = Color(0xFF1A1433),
            isDark = false
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }
        }
    }

    CompositionLocalProvider(LocalXproColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = XproTypography,
            shapes = XproShapes,
            content = content
        )
    }
}
