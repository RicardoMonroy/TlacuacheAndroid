package io.github.ricardomonroy.tlacuache.ui.pane

import android.os.SystemClock
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import io.github.ricardomonroy.tlacuache.state.TabState

/**
 * Gestos sobre los elementos según el tipo de puntero (UI_SPEC §3):
 *
 * | | Táctil | Ratón |
 * |---|---|---|
 * | Abrir | toque | doble clic |
 * | Seleccionar | pulsación larga entra al modo selección (y empieza a arrastrar); luego el toque alterna | clic; Ctrl+clic alterna; Shift+clic rango |
 * | Menú | ⋮ de la barra de la selección | clic derecho |
 * | Arrastrar | pulsación larga sobre lo seleccionado y mover | arrastrar |
 *
 * Enter con teclado abre. El tipo de puntero lo anota [trackPointer] en la lista, antes que las filas.
 */
@Stable
class ItemGestures(
    private val tab: TabState,
    private val open: (FileEntry) -> Unit,
) {
    /** El último puntero fue un ratón (también decide si hay doble clic). */
    var mouse by mutableStateOf(false)
        private set
    private var ctrl = false
    private var shift = false
    private var pressAt = 0L

    /** Anota tipo de puntero y modificadores de cada pulsación sobre la lista. */
    val trackPointer: Modifier =
        Modifier.pointerInput(this) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    mouse = change.type == PointerType.Mouse
                    if (event.type == PointerEventType.Press) {
                        ctrl = event.keyboardModifiers.isCtrlPressed
                        shift = event.keyboardModifiers.isShiftPressed
                        pressAt = SystemClock.uptimeMillis()
                    }
                }
            }
        }

    /** Clic, toque o Enter sobre [entry]. */
    fun click(entry: FileEntry) {
        // Sin una pulsación reciente, el clic vino del teclado (Enter): abre.
        val fromPointer = SystemClock.uptimeMillis() - pressAt < POINTER_CLICK_MS
        when {
            !fromPointer -> open(entry)
            mouse && ctrl -> tab.toggle(entry.name)
            mouse && shift -> tab.selectRange(entry.name)
            mouse -> tab.selectOnly(entry.name)
            tab.ui.value.selecting -> tab.toggle(entry.name)
            else -> open(entry)
        }
    }

    /** Doble clic con ratón: abre. */
    fun doubleClick(entry: FileEntry) = open(entry)

    private companion object {
        const val POINTER_CLICK_MS = 1_000L
    }
}

/** Gestos de un elemento: toque/clic, pulsación larga, doble clic con ratón y clic derecho para el menú. */
@Composable
fun Modifier.itemGestures(
    gestures: ItemGestures,
    entry: FileEntry,
    showMenu: () -> Unit,
): Modifier {
    val onClick = remember(gestures, entry) { { gestures.click(entry) } }
    return this
        .pointerHoverIcon(PointerIcon.Hand)
        .pointerInput(entry.name) {
            // Clic derecho: el menú, sin seleccionar ni abrir.
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        event.changes.forEach { it.consume() }
                        showMenu()
                    }
                }
            }
        }.combinedClickable(
            onClick = onClick,
            // La pulsación larga con el dedo la atiende el arrastre (selecciona y arrastra, A4.3); el menú está en ⋮.
            onLongClick = null,
            // Solo con ratón: con táctil, esperar un posible doble toque retrasaría cada apertura.
            onDoubleClick = if (gestures.mouse) ({ gestures.doubleClick(entry) }) else null,
        )
}
