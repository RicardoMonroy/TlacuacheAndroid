package io.github.ricardomonroy.tlacuache.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import io.github.ricardomonroy.tlacuache.ffi.themeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * El core ya comprueba el contraste de sus tokens en los cinco temas; aquí, el de los colores que Android
 * deriva de ellos (A5.4): texto secundario sobre la cabecera y el título de la pestaña activa.
 */
class ThemeContrastTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun ratio(
        a: Color,
        b: Color,
    ): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun derivedHeaderColorsKeepTextContrastInBuiltInThemes() {
        val themes = themeCatalog(tmp.root.path).themes
        assertEquals(5, themes.size)
        themes.forEach { theme ->
            val c = theme.tokens.colors
            val bg = c.headerBg.color
            val muted = ratio(c.headerMuted.compositeOver(bg), bg)
            val tab = ratio(c.headerFg.color, c.headerSelected.compositeOver(bg))
            // WCAG AA para texto normal: 4.5:1.
            assertTrue("${theme.tokens.id}: texto secundario $muted", muted >= 4.5)
            assertTrue("${theme.tokens.id}: pestaña activa $tab", tab >= 4.5)
        }
    }
}
