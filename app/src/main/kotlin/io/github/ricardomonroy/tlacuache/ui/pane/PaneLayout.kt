package io.github.ricardomonroy.tlacuache.ui.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.state.WindowViewModel
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color

/** Desde este ancho de ventana caben dos paneles (clase media, UI_SPEC §1). */
internal val MEDIUM_WIDTH = 600.dp

/** Ancho mínimo de un panel con dos paneles: seis botones de 48 dp y ~110 dp de ruta. */
private val MIN_PANE_WIDTH = 400.dp

/** Tamaño de la ventana actual (no de la pantalla): en modo escritorio cambia al redimensionar. */
@Composable
internal fun windowSize(): DpSize =
    with(
        LocalDensity.current,
    ) { LocalWindowInfo.current.containerSize.let { DpSize(it.width.toDp(), it.height.toDp()) } }

/**
 * Layout de la ventana (UI_SPEC §1): en media y expandida, paneles A y B con divisor arrastrable; en
 * compacta o con el modo dual apagado, solo el activo con el alternador A/B. Cambiar de layout no toca
 * el estado: pestañas y carpetas viven en [WindowViewModel].
 */
@Composable
fun PaneLayout(window: WindowViewModel) {
    val active by window.activePane.collectAsStateWithLifecycle()
    val dual by window.dual.collectAsStateWithLifecycle()
    val split by window.split.collectAsStateWithLifecycle()
    val wide = windowSize().width >= MEDIUM_WIDTH
    val t = TlacuacheTheme.tokens
    var rowWidth by remember { mutableFloatStateOf(1f) }
    // Cada panel conserva sitio para botones y ruta; en un teléfono horizontal el divisor apenas se mueve.
    val minSplit = (with(LocalDensity.current) { MIN_PANE_WIDTH.toPx() } / rowWidth).coerceAtMost(0.5f)
    val shownSplit = split.coerceIn(minSplit, 1 - minSplit)
    Row(
        Modifier
            .fillMaxSize()
            .background(t.colors.paneBg.color)
            // En horizontal, la cámara frontal queda a un lado: nada debajo de ella.
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .onSizeChanged { rowWidth = it.width.toFloat().coerceAtLeast(1f) }
            .onKeyEvent { handleWindowKey(it, window, wide) },
    ) {
        if (wide && dual) {
            window.panes.forEachIndexed { i, pane ->
                if (i == 1) {
                    PaneDivider { window.moveSplit(it / rowWidth, minSplit) }
                }
                PaneScreen(
                    pane,
                    window.pathInput,
                    active = i == active,
                    onActivate = { window.activate(i) },
                    dual = true,
                    onToggleDual = window::toggleDual,
                    modifier = Modifier.weight(if (i == 0) shownSplit else 1 - shownSplit),
                )
            }
        } else {
            PaneScreen(
                window.panes[active],
                window.pathInput,
                active = true,
                onActivate = {},
                switcher = { PaneSwitcher(active, window::activate) },
                dual = if (wide) false else null,
                onToggleDual = window::toggleDual,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Tab cambia de panel; F3 alterna 1/2 paneles, o A/B en compacta (UI_SPEC §8). */
private fun handleWindowKey(
    event: KeyEvent,
    window: WindowViewModel,
    wide: Boolean,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed || event.isShiftPressed) return false
    when (event.key) {
        Key.Tab -> window.activateOther()
        Key.F3 -> if (wide) window.toggleDual() else window.activateOther()
        else -> return false
    }
    return true
}

/** Divisor entre paneles: línea de 1 dp con zona de arrastre de 12 dp. */
@Composable
private fun PaneDivider(onDrag: (Float) -> Unit) {
    val t = TlacuacheTheme.tokens
    Box(
        Modifier
            .fillMaxHeight()
            .width(12.dp)
            .background(t.colors.headerBg.color)
            .pointerHoverIcon(PointerIcon(android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW))
            .draggable(rememberDraggableState(onDrag), Orientation.Horizontal),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxHeight().width(1.dp).background(t.colors.borderStrong.color))
    }
}

/** Alternador A/B: el panel visible es el activo; el otro conserva carpeta y pestañas. */
@Composable
private fun PaneSwitcher(
    active: Int,
    onSelect: (Int) -> Unit,
) {
    val t = TlacuacheTheme.tokens
    Row(
        Modifier
            .padding(horizontal = 4.dp)
            .border(1.dp, t.colors.border.color, MaterialTheme.shapes.small),
    ) {
        listOf(R.string.pane_a to "A", R.string.pane_b to "B").forEachIndexed { i, (label, glyph) ->
            val description = stringResource(label)
            val selected = i == active
            Box(
                Modifier
                    .size(width = 40.dp, height = 36.dp)
                    .background(
                        if (selected) t.colors.accent.color else t.colors.headerBg.color,
                        MaterialTheme.shapes.small,
                    ).clickable { onSelect(i) }
                    .semantics {
                        contentDescription = description
                        this.selected = selected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    glyph,
                    color = if (selected) t.colors.headerBg.color else t.colors.headerFg.color,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
