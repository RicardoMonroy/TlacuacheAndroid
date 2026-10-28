package io.github.ricardomonroy.tlacuache.ui.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import java.io.File

/** Celdas de al menos este ancho: tres columnas en un teléfono vertical. */
private val CellMinWidth = 104.dp

/**
 * Vista de íconos (UI_SPEC §2): cuadrícula con miniaturas de fotos, videos y PDF, o el ícono del tipo;
 * nombre en dos líneas. Toque y pulsación larga como en la lista.
 *
 * shortcut: tamaño fijo; pellizcar para cambiarlo llega en v0.2.
 */
@Composable
fun FileGrid(
    dir: File,
    entries: List<FileEntry>,
    selected: Set<String>,
    activePane: Boolean,
    isProtected: (String) -> Boolean,
    gestures: ItemGestures,
    drag: ItemDrag,
    onAction: (FileEntry, FileAction) -> Unit,
    modifier: Modifier = Modifier,
    /** Cursor del teclado: si no se ve, la cuadrícula va hasta él. */
    cursor: String? = null,
) {
    val grid = rememberLazyGridState()
    LaunchedEffect(cursor) {
        val i = entries.indexOfFirst { it.name == cursor }
        if (i >= 0 && grid.layoutInfo.visibleItemsInfo.none { it.index == i }) grid.scrollToItem(i)
    }
    LazyVerticalGrid(
        GridCells.Adaptive(CellMinWidth),
        modifier.then(gestures.trackPointer),
        grid,
        contentPadding = PaddingValues(4.dp),
    ) {
        // El nombre es la identidad, como en la lista (ADR-A10).
        items(entries, key = { it.name }) { entry ->
            Cell(
                dir,
                entry,
                selectionColors(entry.name in selected, activePane),
                gestures,
                drag,
                isProtected(entry.name),
            ) {
                onAction(entry, it)
            }
        }
    }
}

@Composable
private fun Cell(
    dir: File,
    entry: FileEntry,
    selection: SelectionColors?,
    gestures: ItemGestures,
    drag: ItemDrag,
    protected: Boolean,
    onAction: (FileAction) -> Unit,
) {
    val t = TlacuacheTheme.tokens
    var menu by remember { mutableStateOf(false) }
    var dropHere by remember { mutableStateOf(false) }
    val shown = if (dropHere) selectionColors(selected = true, activePane = true) else selection
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(shown?.background ?: t.colors.paneBg.color)
                .itemGestures(gestures, entry) { menu = true }
                // Por dentro del clic: el arrastre ve los eventos antes que la pulsación.
                .itemDrag(drag, entry, onStart = { menu = false }) { dropHere = it }
                .semantics { selected = selection != null }
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val category = rememberCategory(entry)
            FileThumbnail(
                dir,
                entry,
                category,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(t.colors.paneAltRow.color),
            )
            val (label, labelColor) = entryLabel(entry, category)
            Text(
                label,
                color = shown?.content ?: labelColor ?: t.colors.fg.color,
                fontWeight = shown?.weight,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FileMenu(entry, protected, menu, { menu = false }, onAction)
    }
}
