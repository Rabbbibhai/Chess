package com.saucyfy.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Brand {
    val Bg = Color(0xFF0A0F1C)
    val Sky = Color(0xFF4DB2FF)
    val Grass = Color(0xFF8BE05B)
    val Amber = Color(0xFFFFC857)
    val Coral = Color(0xFFFF7A7A)
}

private val scheme = darkColorScheme(
    primary = Brand.Sky,
    onPrimary = Color(0xFF00243D),
    primaryContainer = Color(0xFF12365A),
    onPrimaryContainer = Color(0xFFD4E9FF),
    secondary = Brand.Grass,
    onSecondary = Color(0xFF12300A),
    background = Brand.Bg,
    onBackground = Color(0xFFE8EEF9),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE8EEF9),
    surfaceVariant = Color(0xFF1B2437),
    onSurfaceVariant = Color(0xFF9AA7BD),
    outline = Color(0xFF3A465E),
    outlineVariant = Color(0xFF263148),
    error = Brand.Coral,
    surfaceContainer = Color(0xFF131B2C),
    surfaceContainerHigh = Color(0xFF182136),
    surfaceContainerHighest = Color(0xFF1E2840)
)

@Composable
fun SaucyfyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
