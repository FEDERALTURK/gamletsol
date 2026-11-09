package com.federalturk.gamletsol.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Legacy Flutter projesindeki (lib/ui/theme.dart) "Kaivolt" lacivert/turuncu
// HUD paletiyle görsel tutarlılık için aynı renkler.
private val GamletsolBackground = Color(0xFF090C12)
private val GamletsolSurface = Color(0xFF121722)
private val GamletsolOrange = Color(0xFFFF6D10)
private val GamletsolRed = Color(0xFFE5484D)

private val GamletsolColorScheme = darkColorScheme(
    primary = GamletsolOrange,
    error = GamletsolRed,
    background = GamletsolBackground,
    surface = GamletsolSurface,
)

@Composable
fun GamletsolTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GamletsolColorScheme, content = content)
}
