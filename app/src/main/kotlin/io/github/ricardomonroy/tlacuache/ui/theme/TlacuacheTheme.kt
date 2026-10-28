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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.ThemeColors
import io.github.ricardomonroy.tlacuache.ffi.ThemeTokens

/** Color ARGB opaco del core (`0xFFRRGGBB`) a `Color` de Compose. */
val UInt.color: Color get() = Color(toInt())

/**
 * Texto secundario y deshabilitado sobre la cabecera (pestañas, ruta, barra de estado): `header_fg`
 * atenuado, como `alpha(currentColor, …)` en Linux. `fg_muted` solo contrasta sobre el panel.
 */
val ThemeColors.headerMuted: Color get() = headerFg.color.copy(alpha = 0.72f)
val ThemeColors.headerDisabled: Color get() = headerFg.color.copy(alpha = 0.38f)

/** Fondo de la pestaña activa sobre la cabecera (`.tl-tab.selected` de Linux). */
val ThemeColors.headerSelected: Color get() = headerFg.color.copy(alpha = 0.14f)

// shortcut: solo se empaqueta JetBrains Mono (la `font_mono` de los cinco temas incluidos);
// otra `font_mono` de un tema de usuario cae en esta misma; sin fuentes descargables (no hay Play Services) habría que importar el .ttf: cuando un tema de usuario lo pida.
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
    // shortcut: `font_ui` no vacía se ignora (fuente del sistema); igual que `font_mono`: importar el .ttf cuando un tema lo pida.
    val typography = remember(tokens) { if (tokens.style.monoUi) Typography().withFamily(MonoFamily) else Typography() }
    val shapes =
        remember(tokens) {
            val r =
                RoundedCornerShape(
                    tokens.style.radius
                        .toInt()
                        .dp,
                )
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
            // Menús, diálogos y hojas: sobre el fondo del panel, con su texto (`fg`).
            surfaceContainerLowest = paneBg.color,
            surfaceContainerLow = paneBg.color,
            surfaceContainer = paneBg.color,
            surfaceContainerHigh = paneBg.color,
            surfaceContainerHighest = paneBg.color,
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

/**
 * Para pantallas que son un `Dialog` a pantalla completa (Papelera, Preferencias): el diálogo tiene su
 * propia ventana y no hereda el estilo de barras de la actividad; con un tema claro sus íconos salían
 * blancos sobre blanco.
 */
@Composable
fun DialogSystemBars() {
    val view = LocalView.current
    val dark = TlacuacheTheme.tokens.dark
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
