package com.saucyfy.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Semantic colors that stay the same in every theme. */
object Brand {
    val Sky = Color(0xFF4DB2FF)
    val Grass = Color(0xFF8BE05B)
    val Amber = Color(0xFFFFC857)
    val Coral = Color(0xFFFF7A7A)
}

class AppTheme(val id: String, val name: String, val dark: Boolean, val scheme: ColorScheme)

private fun darkTheme(
    id: String,
    name: String,
    bg: Long,
    card: Long,
    primary: Long,
    secondary: Long,
    primaryContainer: Long,
    onPrimary: Long = 0xFF08111F
): AppTheme {
    val c = Color(card)
    return AppTheme(
        id, name, true,
        darkColorScheme(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(0xFFEAF2FF),
            secondary = Color(secondary),
            onSecondary = Color(0xFF0B1A0A),
            background = Color(bg),
            onBackground = Color(0xFFE8EEF9),
            surface = Color(bg),
            onSurface = Color(0xFFE8EEF9),
            surfaceVariant = lerp(c, Color.White, 0.06f),
            onSurfaceVariant = Color(0xFFA0ACC2),
            outline = lerp(c, Color.White, 0.22f),
            outlineVariant = lerp(c, Color.White, 0.12f),
            error = Brand.Coral,
            surfaceContainer = c,
            surfaceContainerHigh = lerp(c, Color.White, 0.04f),
            surfaceContainerHighest = lerp(c, Color.White, 0.08f)
        )
    )
}

object Themes {
    val all: List<AppTheme> = listOf(
        darkTheme("midnight", "Midnight", 0xFF0A0F1C, 0xFF131B2C, 0xFF4DB2FF, 0xFF8BE05B, 0xFF12365A),
        darkTheme("amoled", "AMOLED", 0xFF000000, 0xFF0F0F10, 0xFF7CC4FF, 0xFF8BE05B, 0xFF123049),
        darkTheme("ocean", "Ocean", 0xFF06141F, 0xFF0D2233, 0xFF2DD4BF, 0xFF38BDF8, 0xFF0F4C4A),
        darkTheme("forest", "Forest", 0xFF08140D, 0xFF11261A, 0xFF4ADE80, 0xFFA3E635, 0xFF14532D),
        darkTheme("sunset", "Sunset", 0xFF1A0F14, 0xFF2A1820, 0xFFFB923C, 0xFFF472B6, 0xFF6B3410, 0xFF2A1200),
        darkTheme("sakura", "Sakura", 0xFF1A0E17, 0xFF2A1726, 0xFFF9A8D4, 0xFFC4B5FD, 0xFF6B2A52, 0xFF2A0E1E),
        AppTheme(
            "daylight", "Daylight", false,
            lightColorScheme(
                primary = Color(0xFF1E7FE0),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFD6E8FF),
                onPrimaryContainer = Color(0xFF0B2A52),
                secondary = Color(0xFF3C9A1F),
                background = Color(0xFFF6F8FC),
                onBackground = Color(0xFF111827),
                surface = Color(0xFFF6F8FC),
                onSurface = Color(0xFF111827),
                surfaceVariant = Color(0xFFE6EBF4),
                onSurfaceVariant = Color(0xFF55627A),
                outline = Color(0xFFB6C0D2),
                outlineVariant = Color(0xFFD5DCE8),
                error = Color(0xFFD64545),
                surfaceContainer = Color(0xFFEEF2F9),
                surfaceContainerHigh = Color(0xFFE8EDF6),
                surfaceContainerHighest = Color(0xFFE1E7F2)
            )
        )
    )
}

fun themeById(id: String): AppTheme = Themes.all.firstOrNull { it.id == id } ?: Themes.all.first()

@Composable
fun SaucyfyTheme(themeId: String, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = themeById(themeId).scheme, content = content)
}
