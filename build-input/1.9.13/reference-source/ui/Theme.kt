package com.streamdeck.iptv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColors = darkColorScheme(
    primary = Color(0xFFF2A52B),
    onPrimary = Color(0xFF241100),
    secondary = Color(0xFFFFE1A3),
    background = Color(0xFF100804),
    onBackground = Color(0xFFFFF7E8),
    surface = Color(0xFF1A0F08),
    onSurface = Color(0xFFFFF7E8),
    surfaceVariant = Color(0xFF3A2412),
    onSurfaceVariant = Color(0xFFD8C09A),
    outline = Color(0xFF7A5425),
    error = Color(0xFFFFB4AB),
)

@Composable
fun StreamDeckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        content = content,
    )
}
