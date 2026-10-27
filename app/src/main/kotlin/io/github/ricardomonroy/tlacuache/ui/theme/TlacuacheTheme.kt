package io.github.ricardomonroy.tlacuache.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.ThemeTokens

/** Color ARGB opaco del core (`0xFFRRGGBB`) a `Color` de Compose. */
val UInt.color: Color get() = Color(toInt())

// shortcut: solo se empaqueta JetBrains Mono (la `font_mono` de los cinco temas incluidos);
// otra `font_mono` de un tema de usuario cae en esta misma. Revisar en A5.1.
val MonoFamily =
    FontFamily(
        Font(R.font.jetbrains_mono_regular),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    )

private val LocalThemeTokens =
    staticCompositionLocalOf<ThemeTokens> { error("TlacuacheTheme no envuelve este contenido") }

/** Acceso a los tokens del tema activo (ADR-A07): todo color, radio y fuente sale de aquí. */
object TlacuacheTheme {
    val tokens: ThemeTokens
        @Composable @ReadOnlyComposable
        get() = LocalThemeTokens.current
}

@Composable
fun TlacuacheTheme(
    tokens: ThemeTokens,
    content: @Composable () -> Unit,
) {
    val scheme = remember(tokens) { colorScheme(tokens) }
    // shortcut: `font_ui` no vacía se ignora (fuente del sistema); fuentes de UI propias en A5.1.
    val typography = remember(tokens) { if (tokens.style.monoUi) Typography().withFamily(MonoFamily) else Typography() }
    val shapes =
        remember(tokens) {
            val r = RoundedCornerShape(tokens.style.radius.toInt().dp)
            Shapes(extraSmall = r, small = r, medium = r, large = r)
        }
    CompositionLocalProvider(LocalThemeTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}

/** Esquema de Material 3 para los componentes estándar, derivado de los tokens semánticos. */
private fun colorScheme(tokens: ThemeTokens) =
    with(tokens.colors) {
        (if (tokens.dark) darkColorScheme() else lightColorScheme()).copy(
            primary = accent.color,
            onPrimary = accentFg.color,
            secondary = accent.color,
            onSecondary = accentFg.color,
            background = windowBg.color,
            onBackground = fg.color,
            surface = paneBg.color,
            onSurface = fg.color,
            surfaceVariant = sidebarBg.color,
            onSurfaceVariant = fgMuted.color,
            surfaceContainer = headerBg.color,
            outline = borderStrong.color,
            outlineVariant = border.color,
            error = error.color,
        )
    }

private fun Typography.withFamily(family: FontFamily) =
    Typography(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family),
    )
