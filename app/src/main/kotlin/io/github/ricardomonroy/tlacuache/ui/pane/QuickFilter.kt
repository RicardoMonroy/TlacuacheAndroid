package io.github.ricardomonroy.tlacuache.ui.pane

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color

/**
 * Indicador flotante del filtro rápido (UI_SPEC §8): texto y número de coincidencias. Con [editable]
 * (abierto con el botón de buscar, táctil) es un campo para el teclado en pantalla; con teclado físico
 * el texto llega por las teclas del panel y aquí solo se muestra.
 */
@Composable
fun QuickFilter(
    query: String,
    matches: Int,
    editable: Boolean,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = TlacuacheTheme.tokens
    Row(
        modifier
            .background(t.colors.headerBg.color, MaterialTheme.shapes.large)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchGlyph(t.colors.fgMuted.color)
        if (editable) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = t.colors.fg.color),
                cursorBrush = SolidColor(t.colors.accent.color),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                modifier = Modifier.widthIn(min = 120.dp, max = 240.dp).focusRequester(focus),
            )
        } else {
            Text(query, color = t.colors.fg.color, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        }
        Text(
            pluralStringResource(R.plurals.filter_matches, matches, matches),
            color = t.colors.fgMuted.color,
            style = MaterialTheme.typography.bodySmall,
        )
        val close = stringResource(R.string.filter_clear)
        IconButton(onClose, Modifier.semantics { contentDescription = close }) {
            Text("✕", color = t.colors.fgMuted.color)
        }
    }
}

/** Lupa dibujada (sin biblioteca de íconos todavía). */
@Composable
fun SearchGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(20.dp)) {
        val stroke = size.minDimension / 9
        val r = size.minDimension * 0.32f
        val c = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(color, r, c, style = Stroke(stroke))
        val d = r * 0.72f
        drawLine(color, Offset(c.x + d, c.y + d), Offset(size.width * 0.92f, size.height * 0.92f), stroke)
    }
}
