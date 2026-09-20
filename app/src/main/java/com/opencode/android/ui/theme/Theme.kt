package com.opencode.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4F8CFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1E3A6E),
    onPrimaryContainer = Color(0xFFD6E3FF),
    background = Color(0xFF101014),
    onBackground = Color(0xFFE8E8EC),
    surface = Color(0xFF17171C),
    onSurface = Color(0xFFE8E8EC),
    surfaceVariant = Color(0xFF22222A),
    onSurfaceVariant = Color(0xFF9A9AA3),
    secondary = Color(0xFF66B36E),
    onSecondary = Color(0xFF06280C),
    error = Color(0xFFE5484D),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFF3A3A44)
)

@Composable
fun OpenCodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}