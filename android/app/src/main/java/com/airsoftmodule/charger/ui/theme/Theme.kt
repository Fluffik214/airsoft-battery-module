package com.airsoftmodule.charger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Palette. Neutral tones are mixed with the selected accent, so changing the accent re-tints the
 * whole app (backgrounds, cards, outlines, secondary text), not just buttons. Backed by snapshot
 * state, so composables and Canvas draw code that read it update automatically.
 */
object Tac {
    var Bg by mutableStateOf(Color(0xFF0D0F0B)); private set
    var Surface by mutableStateOf(Color(0xFF15180F)); private set
    var Surface2 by mutableStateOf(Color(0xFF1C2015)); private set
    var Surface3 by mutableStateOf(Color(0xFF252A1C)); private set
    var Outline by mutableStateOf(Color(0xFF323926)); private set
    var Text by mutableStateOf(Color(0xFFE8EBDF)); private set
    var Dim by mutableStateOf(Color(0xFF9AA28A)); private set
    var Faint by mutableStateOf(Color(0xFF636B55)); private set
    val Ok = Color(0xFF6EE07A)
    val Warn = Color(0xFFFFC14D)
    val Bad = Color(0xFFFF5A4E)
    val Cell = listOf(Color(0xFF6FD3FF), Color(0xFFFFB23E), Color(0xFFC38BFF))

    fun applyAccent(index: Int) {
        val a = Accents.getOrElse(index) { Accents[0] }.color
        fun mix(base: Long, k: Float) = lerp(Color(base), a, k)
        Bg = mix(0xFF0A0B09, 0.045f)
        Surface = mix(0xFF121410, 0.07f)
        Surface2 = mix(0xFF191B16, 0.09f)
        Surface3 = mix(0xFF22251E, 0.12f)
        Outline = mix(0xFF2E3229, 0.22f)
        Text = mix(0xFFECEEE8, 0.06f)
        Dim = mix(0xFF9A9E94, 0.22f)
        Faint = mix(0xFF60645A, 0.18f)
    }
}

data class Accent(val name: String, val color: Color, val onColor: Color)

val Accents = listOf(
    Accent("Ranger", Color(0xFFA6D84B), Color(0xFF15200A)),
    Accent("Tracer", Color(0xFFFFB23E), Color(0xFF2A1A00)),
    Accent("Night Vision", Color(0xFF55F2A0), Color(0xFF00281A)),
    Accent("Cobalt", Color(0xFF5CC8FF), Color(0xFF00243A)),
    Accent("Coyote", Color(0xFFD9B27C), Color(0xFF2B1D08)),
    Accent("Red Dot", Color(0xFFFF6B5E), Color(0xFF330A05)),
)

val LocalAccent = staticCompositionLocalOf { Accents[0] }

/** Tabular figures so live numbers don't jitter. */
val Num = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun ChargerTheme(accentIndex: Int, content: @Composable () -> Unit) {
    val a = Accents.getOrElse(accentIndex) { Accents[0] }
    val scheme = darkColorScheme(
        primary = a.color, onPrimary = a.onColor,
        primaryContainer = a.color.copy(alpha = 0.18f), onPrimaryContainer = a.color,
        secondary = a.color, onSecondary = a.onColor,
        secondaryContainer = Tac.Surface3, onSecondaryContainer = Tac.Text,
        background = Tac.Bg, onBackground = Tac.Text,
        surface = Tac.Bg, onSurface = Tac.Text,
        surfaceVariant = Tac.Surface2, onSurfaceVariant = Tac.Dim,
        surfaceContainerLowest = Tac.Bg, surfaceContainerLow = Tac.Surface, surfaceContainer = Tac.Surface,
        surfaceContainerHigh = Tac.Surface2, surfaceContainerHighest = Tac.Surface3,
        outline = Tac.Outline, outlineVariant = Tac.Outline,
        error = Tac.Bad, onError = Color.Black,
    )
    val base = Typography()
    val typo = Typography(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
        bodyMedium = base.bodyMedium,
        bodySmall = base.bodySmall,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalAccent provides a) {
        MaterialTheme(colorScheme = scheme, typography = typo, content = content)
    }
}

val Mono = FontFamily.Monospace
