package io.github.ricardomonroy.tlacuache.ui.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.state.PaneState
import io.github.ricardomonroy.tlacuache.state.TabState
import io.github.ricardomonroy.tlacuache.ui.Shortcut
import io.github.ricardomonroy.tlacuache.ui.WithTooltip
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color

private val TabWidth = 160.dp

/**
 * Tira de pestañas del panel (UI_SPEC §2): ancho fijo, título a la izquierda, desplazable; tocar
 * selecciona, ✕ cierra (salvo la última), + abre una nueva en la carpeta actual. Pulsación larga o clic
 * derecho: Duplicar y Cerrar las demás.
 *
 * shortcut: arrastrar pestañas para reordenarlas y el clic central para cerrar, después (ROADMAP A4.6).
 */
@Composable
fun TabStrip(
    tabs: PaneState.Tabs,
    pane: PaneState,
) {
    val t = TlacuacheTheme.tokens
    val list = rememberLazyListState()
    LaunchedEffect(tabs.active, tabs.list.size) { list.animateScrollToItem(tabs.active) }
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(Modifier.weight(1f), list, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(tabs.list, key = { _, tab -> System.identityHashCode(tab) }) { index, tab ->
                TabButton(
                    tab,
                    active = index == tabs.active,
                    closable = tabs.list.size > 1,
                    onSelect = { pane.select(index) },
                    onDuplicate = { pane.openTab(from = index) },
                    onCloseOthers = { pane.closeOthers(index) },
                ) {
                    pane.closeTab(index)
                }
            }
        }
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
                            ui.volumeName ?: ui.dir.name,
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

@Composable
private fun TabButton(
    tab: TabState,
    active: Boolean,
    closable: Boolean,
    onSelect: () -> Unit,
    onDuplicate: () -> Unit,
    onCloseOthers: () -> Unit,
    onClose: () -> Unit,
) {
    val t = TlacuacheTheme.tokens
    val ui by tab.ui.collectAsStateWithLifecycle()
    val title = ui.volumeName ?: ui.dir.name
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier
            .width(TabWidth)
            .height(40.dp)
            .background(if (active) t.colors.paneBg.color else t.colors.headerBg.color, MaterialTheme.shapes.small)
            .semantics { selected = active }
            .pointerInput(Unit) {
                // Clic derecho: el menú de la pestaña.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            event.changes.forEach { it.consume() }
                            menu = true
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
                color = if (active) t.colors.headerFg.color else t.colors.fgMuted.color,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (closable) {
                val close = stringResource(R.string.tab_close)
                IconButton(onClose, Modifier.semantics { contentDescription = close }) {
                    Text("✕", color = t.colors.fgMuted.color, style = MaterialTheme.typography.bodySmall)
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
