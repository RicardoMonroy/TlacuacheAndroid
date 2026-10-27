package io.github.ricardomonroy.tlacuache.ui.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color

private val TabWidth = 160.dp

/**
 * Tira de pestañas del panel (UI_SPEC §2): ancho fijo, título a la izquierda, desplazable; tocar
 * selecciona, ✕ cierra (salvo la última), + abre una nueva en la carpeta actual.
 *
 * shortcut: siempre la tira; en compacta será un botón con el número de pestañas (A2.2). Arrastrar y menú, después.
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
                ) {
                    pane.closeTab(index)
                }
            }
        }
        val newTab = stringResource(R.string.tab_new)
        IconButton(pane::openTab, Modifier.semantics { contentDescription = newTab }) {
            Text("+", color = t.colors.headerFg.color, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun TabButton(
    tab: TabState,
    active: Boolean,
    closable: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val t = TlacuacheTheme.tokens
    val ui by tab.ui.collectAsStateWithLifecycle()
    val title = if (ui.isVolumeRoot) stringResource(R.string.volume_internal_short) else ui.dir.name
    Box(
        Modifier
            .width(TabWidth)
            .height(40.dp)
            .background(if (active) t.colors.paneBg.color else t.colors.headerBg.color, MaterialTheme.shapes.small)
            .semantics { selected = active }
            .clickable(onClick = onSelect),
    ) {
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
