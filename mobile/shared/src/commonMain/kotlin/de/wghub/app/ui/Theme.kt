package de.wghub.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The website's colours (frontend/src/assets/main.css). */
@Immutable
data class AppColors(
    val bg: Color,
    val surface: Color,
    val subtle: Color,
    val fg: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentFg: Color,
    val accentSoft: Color,
    val accentText: Color,
    val danger: Color,
    val dangerSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val avatars: Map<String, Color>,
    val avatarFg: Color,
    val notes: Map<String, Color>,
)

private val Light = AppColors(
    bg = Color(0xFFFAFAF9), surface = Color.White, subtle = Color(0xFFF4F4F2),
    fg = Color(0xFF1C1917), muted = Color(0xFF6F6A65), line = Color(0xFFE8E6E3),
    accent = Color(0xFF2F6F5E), accentFg = Color.White, accentSoft = Color(0xFFE4F1EC), accentText = Color(0xFF27604F),
    danger = Color(0xFFB83F36), dangerSoft = Color(0xFFFBEAE8), warn = Color(0xFF9A6512), warnSoft = Color(0xFFFBF1DD),
    avatars = mapOf(
        "sage" to Color(0xFFD7EADF), "sky" to Color(0xFFD6E8F8), "rose" to Color(0xFFF8DBE2),
        "amber" to Color(0xFFF9E8C4), "lilac" to Color(0xFFE4DFF8), "teal" to Color(0xFFCDECEA),
    ),
    avatarFg = Color(0xFF2B2A28),
    notes = mapOf(
        "DEFAULT" to Color.White, "YELLOW" to Color(0xFFFDF5D3), "GREEN" to Color(0xFFE3F3E6),
        "BLUE" to Color(0xFFE2EEFB), "PINK" to Color(0xFFFBE7EC), "LILAC" to Color(0xFFEEEAFB),
    ),
)

private val Dark = AppColors(
    bg = Color(0xFF161615), surface = Color(0xFF1E1E1C), subtle = Color(0xFF282826),
    fg = Color(0xFFF3F2F0), muted = Color(0xFFA5A19B), line = Color(0xFF2F2F2C),
    accent = Color(0xFF7CC4AE), accentFg = Color(0xFF10231D), accentSoft = Color(0xFF20342D), accentText = Color(0xFF8FD0BC),
    danger = Color(0xFFEC8A81), dangerSoft = Color(0xFF3A2220), warn = Color(0xFFE4B768), warnSoft = Color(0xFF362C18),
    avatars = mapOf(
        "sage" to Color(0xFF3B5A4A), "sky" to Color(0xFF35506B), "rose" to Color(0xFF6A3F4B),
        "amber" to Color(0xFF66522A), "lilac" to Color(0xFF4D4574), "teal" to Color(0xFF2D5A58),
    ),
    avatarFg = Color(0xFFF3F2F0),
    notes = mapOf(
        "DEFAULT" to Color(0xFF1E1E1C), "YELLOW" to Color(0xFF35301A), "GREEN" to Color(0xFF1E3123),
        "BLUE" to Color(0xFF1C2C3D), "PINK" to Color(0xFF3A2329), "LILAC" to Color(0xFF2B2640),
    ),
)

val LocalColors = staticCompositionLocalOf { Light }

object Wg {
    val colors: AppColors @Composable get() = LocalColors.current
}

@Composable
fun WgTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) Dark else Light
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = colors.accent,
        onPrimary = colors.accentFg,
        primaryContainer = colors.accentSoft,
        onPrimaryContainer = colors.accentText,
        secondaryContainer = colors.accentSoft,
        onSecondaryContainer = colors.accentText,
        background = colors.bg,
        onBackground = colors.fg,
        surface = colors.surface,
        onSurface = colors.fg,
        surfaceVariant = colors.subtle,
        onSurfaceVariant = colors.muted,
        surfaceContainer = colors.surface,
        surfaceContainerLow = colors.surface,
        surfaceContainerHigh = colors.surface,
        surfaceContainerHighest = colors.subtle,
        outline = colors.line,
        outlineVariant = colors.line,
        error = colors.danger,
        errorContainer = colors.dangerSoft,
    )
    CompositionLocalProvider(LocalColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
