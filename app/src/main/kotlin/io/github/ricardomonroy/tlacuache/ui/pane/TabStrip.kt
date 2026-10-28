package io.github.ricardomonroy.tlacuache.ui.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.state.PaneState
import io.github.ricardomonroy.tlacuache.state.TabState
import io.github.ricardomonroy.tlacuache.ui.Shortcut
import io.github.ricardomonroy.tlacuache.ui.WithTooltip
import io.github.ricardomonroy.tlacuache.ui.shiftWheelScroll
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import io.github.ricardomonroy.tlacuache.ui.theme.headerMuted
import io.github.ricardomonroy.tlacuache.ui.theme.headerSelected
import kotlin.math.roundToInt

private val TabWidth = 160.dp
private val TabGap = 2.dp

/** El otro panel, al lado en modo dual: de qué lado está y cómo pasarle la pestaña [take] (A4.7). */
class SidePane(
    val onRight: Boolean,
    val take: (Int) -> Unit,
)

/** Arrastre de una pestaña con el ratón: empieza, se mueve [dx] con el puntero en [root] y termina. */
private class TabDrag(
    val start: (TabState) -> Unit,
    val move: (TabState, Float, Offset) -> Unit,
    val end: (TabState, Boolean) -> Unit,
)

/**
 * Tira de pestañas del panel (UI_SPEC §2): ancho fijo, título a la izquierda, desplazable; tocar
 * selecciona, ✕ o el clic central cierra (salvo la última), + abre una nueva en la carpeta actual.
 * Pulsación larga o clic derecho: Duplicar y Cerrar las demás. Arrastrar una pestaña (con el ratón, o con
 * el dedo tras la pulsación larga) la reordena; soltarla más allá del borde del lado de [side] la pasa a
 * ese panel.
 */
@Composable
fun TabStrip(
    tabs: PaneState.Tabs,
    pane: PaneState,
    side: SidePane? = null,
) {
    val t = TlacuacheTheme.tokens
    val list = rememberLazyListState()
    var dragged by remember { mutableStateOf<TabState?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var outside by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val currentSide by rememberUpdatedState(side)
    val step = with(LocalDensity.current) { (TabWidth + TabGap).toPx() }
    // Mientras se arrastra no se desplaza: la pestaña arrastrada es la activa y saltaría al inicio.
    LaunchedEffect(tabs.active, tabs.list.size, dragged) { if (dragged == null) list.animateScrollToItem(tabs.active) }
    val drag =
        remember(pane) {
            TabDrag(
                start = { tab ->
                    dragged = tab
                    dragX = 0f
                    pane.select(pane.indexOf(tab))
                },
                move = { tab, dx, root ->
                    // Cruza media pestaña: cambia de lugar con la vecina y el desplazamiento se descuenta.
                    dragX += dx
                    var i = pane.indexOf(tab)
                    while (dragX > step / 2 && i < pane.tabs.value.list.lastIndex) {
                        pane.move(i, ++i)
                        dragX -= step
                    }
                    while (dragX < -step / 2 && i > 0) {
                        pane.move(i, --i)
                        dragX += step
                    }
                    pointer = root
                    val s = currentSide
                    outside = s != null && if (s.onRight) root.x > bounds.right else root.x < bounds.left
                },
                end = { tab, drop ->
                    val i = pane.indexOf(tab)
                    if (drop && outside && i >= 0) currentSide?.take?.invoke(i)
                    dragged = null
                    dragX = 0f
                    outside = false
                },
            )
        }
    Row(
        Modifier.fillMaxWidth().height(40.dp).onGloballyPositioned { bounds = it.boundsInRoot() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            Modifier.weight(1f).shiftWheelScroll(list),
            list,
            horizontalArrangement = Arrangement.spacedBy(TabGap),
        ) {
            itemsIndexed(tabs.list, key = { _, tab -> System.identityHashCode(tab) }) { index, tab ->
                val moving = dragged === tab
                TabButton(
                    tab,
                    active = index == tabs.active,
                    closable = tabs.list.size > 1,
                    drag = drag,
                    onSelect = { pane.select(index) },
                    onDuplicate = { pane.openTab(from = index) },
                    onCloseOthers = { pane.closeOthers(index) },
                    onClose = { pane.closeTab(index) },
                    modifier =
                        Modifier.zIndex(if (moving) 1f else 0f).graphicsLayer {
                            translationX = if (moving) dragX else 0f
                            alpha = if (moving && outside) 0.5f else 1f
                        },
                )
            }
        }
        val ghost = dragged
        if (ghost != null && outside) TabGhost(ghost, pointer - bounds.topLeft)
        val newTab = stringResource(R.string.tab_new)
        WithTooltip(newTab, Shortcut.NEW_TAB) {
            IconButton(pane::openTab, Modifier.semantics { contentDescription = newTab }) {
                Text("+", color = t.colors.headerFg.color, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/**
 * Pestañas cuando no cabe la tira (poca altura; en compacta también, A2.2): un botón con el número de
 * pestañas que abre la lista para elegir, cerrar o abrir una nueva.
 */
@Composable
fun TabMenuButton(
    tabs: PaneState.Tabs,
    pane: PaneState,
) {
    val t = TlacuacheTheme.tokens
    var open by remember { mutableStateOf(false) }
    val label = pluralStringResource(R.plurals.tab_count, tabs.list.size, tabs.list.size)
    Box {
        IconButton({ open = true }, Modifier.semantics { contentDescription = label }) {
            Text(
                tabs.list.size.toString(),
                color = t.colors.headerFg.color,
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    Modifier
                        .border(1.5.dp, t.colors.headerFg.color, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            tabs.list.forEachIndexed { index, tab ->
                val ui by tab.ui.collectAsStateWithLifecycle()
                DropdownMenuItem(
                    text = {
                        Text(
                            tabTitle(ui.volumeName ?: ui.dir.name),
                            color = if (index == tabs.active) t.colors.accent.color else t.colors.fg.color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        open = false
                        pane.select(index)
                    },
                    trailingIcon = {
                        if (tabs.list.size > 1) {
                            val close = stringResource(R.string.tab_close)
                            IconButton({ pane.closeTab(index) }, Modifier.semantics { contentDescription = close }) {
                                Text("✕", color = t.colors.fgMuted.color)
                            }
                        }
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tab_new)) },
                onClick = {
                    open = false
                    pane.openTab()
                },
            )
        }
    }
}

/**
 * Título de pestaña según el tema: `┤ nombre ├` con `bracket_titles` (`core::Theme::pane_title`) y en
 * mayúsculas con `uppercase_headers` (THEMES.md §5.3).
 */
@Composable
private fun tabTitle(name: String): String {
    val style = TlacuacheTheme.tokens.style
    val text = if (style.uppercaseHeaders) name.uppercase() else name
    return if (style.bracketTitles) "┤ $text ├" else text
}

@Composable
private fun TabButton(
    tab: TabState,
    active: Boolean,
    closable: Boolean,
    drag: TabDrag,
    onSelect: () -> Unit,
    onDuplicate: () -> Unit,
    onCloseOthers: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = TlacuacheTheme.tokens
    val ui by tab.ui.collectAsStateWithLifecycle()
    val title = tabTitle(ui.volumeName ?: ui.dir.name)
    var menu by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentClose by rememberUpdatedState(if (closable) onClose else null)
    Box(
        modifier
            .width(TabWidth)
            .height(40.dp)
            .background(if (active) t.colors.headerSelected else t.colors.headerBg.color, MaterialTheme.shapes.small)
            .semantics { selected = active }
            .onGloballyPositioned { coords = it }
            .pointerInput(Unit) {
                // Solo observa (pase Initial): tocar y la pulsación larga siguen en `combinedClickable`.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val buttons = currentEvent.buttons
                    when {
                        buttons.isTertiaryPressed -> {
                            down.consume()
                            currentClose?.invoke()
                        }

                        buttons.isSecondaryPressed -> {
                            down.consume()
                            menu = true
                        }

                        down.type == PointerType.Mouse -> {
                            dragTab(down, tab, drag, { coords }) {}
                        }

                        // Con el dedo: pulsación larga (abre el menú) y luego mover; el menú se cierra.
                        heldStill(down) -> {
                            dragTab(down, tab, drag, { coords }) { menu = false }
                        }
                    }
                }
            }.combinedClickable(onClick = onSelect, onLongClick = { menu = true }),
    ) {
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tab_duplicate)) },
                onClick = {
                    menu = false
                    onDuplicate()
                },
            )
            if (closable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tab_close_others)) },
                    onClick = {
                        menu = false
                        onCloseOthers()
                    },
                )
            }
        }
        Row(Modifier.padding(start = 12.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = if (active) t.colors.headerFg.color else t.colors.headerMuted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (active) FontWeight.Bold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (closable) {
                val close = stringResource(R.string.tab_close)
                IconButton(onClose, Modifier.semantics { contentDescription = close }) {
                    Text("✕", color = t.colors.headerMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (active) {
            Box(
                Modifier
                    .align(
                        Alignment.BottomCenter,
                    ).fillMaxWidth()
                    .height(2.dp)
                    .background(t.colors.paneActiveBorder.color),
            )
        }
    }
}

/**
 * Si el dedo de [down] se queda quieto hasta la pulsación larga. Antes, moverlo desplaza la tira.
 */
private suspend fun AwaitPointerEventScope.heldStill(down: PointerInputChange): Boolean =
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) break
        }
    } == null

/**
 * Arrastrar la pestaña (botón principal del ratón, o el dedo tras la pulsación larga): pasado el umbral, el
 * resto del gesto es del arrastre y no cuenta como clic. Las posiciones van en la raíz porque la pestaña
 * cambia de lugar mientras.
 */
private suspend fun AwaitPointerEventScope.dragTab(
    down: PointerInputChange,
    tab: TabState,
    drag: TabDrag,
    coords: () -> LayoutCoordinates?,
    onStart: () -> Unit,
) {
    var last: Offset? = null
    var dropped = false
    try {
        while (true) {
            val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            val at = coords()?.takeIf { it.isAttached } ?: break
            if (!c.pressed) {
                if (last != null) c.consume()
                dropped = true
                break
            }
            if (last == null) {
                if ((c.position - down.position).getDistance() <= viewConfiguration.touchSlop) continue
                onStart()
                drag.start(tab)
                last = at.localToRoot(down.position)
            }
            c.consume()
            val root = at.localToRoot(c.position)
            drag.move(tab, root.x - last.x, root)
            last = root
        }
    } finally {
        if (last != null) drag.end(tab, dropped)
    }
}

/**
 * La pestaña que se arrastra fuera de su tira (que la recorta): una etiqueta centrada en el puntero, [at]
 * relativo a la tira, en el color de acento como la sombra de arrastrar archivos.
 */
@Composable
private fun TabGhost(
    tab: TabState,
    at: Offset,
) {
    val t = TlacuacheTheme.tokens
    val ui by tab.ui.collectAsStateWithLifecycle()
    val position =
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ) = IntOffset(
                anchorBounds.left + at.x.roundToInt() - popupContentSize.width / 2,
                anchorBounds.top + at.y.roundToInt() - popupContentSize.height / 2,
            )
        }
    Popup(position, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        Text(
            tabTitle(ui.volumeName ?: ui.dir.name),
            color = t.colors.accentFg.color,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .width(TabWidth)
                    .background(t.colors.accent.color, MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
