package dev.opencode.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta inspirada en DeepSeek
val DeepBlue = Color(0xFF4D6BFE)
val DeepBlueLight = Color(0xFF6C86FF)
val DeepBlueDark = Color(0xFF3B55D4)

val DarkBg = Color(0xFF0F1115)
val DarkSurface = Color(0xFF171A21)
val DarkSurfaceHigh = Color(0xFF1E222B)
val DarkBorder = Color(0xFF2A2F3A)
val DarkText = Color(0xFFE6E6E6)
val DarkTextDim = Color(0xFF9AA0AC)

val LightBg = Color(0xFFF7F7F8)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceHigh = Color(0xFFF0F0F0)
val LightBorder = Color(0xFFE3E3E6)
val LightText = Color(0xFF1A1A1A)
val LightTextDim = Color(0xFF6B6B6B)

private val DarkColors = darkColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    primaryContainer = DeepBlueDark,
    onPrimaryContainer = Color.White,
    secondary = DeepBlueLight,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkTextDim,
    outline = DarkBorder,
    error = Color(0xFFFF6B6B),
    tertiary = Color(0xFF34D399),
)

private val LightColors = lightColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    primaryContainer = DeepBlueDark,
    onPrimaryContainer = Color.White,
    secondary = DeepBlueDark,
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightTextDim,
    outline = LightBorder,
)

@Composable
fun OpenCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}