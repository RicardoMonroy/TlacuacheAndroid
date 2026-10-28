package io.github.ricardomonroy.tlacuache.ui.pane

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.summarize
import io.github.ricardomonroy.tlacuache.state.TabState
import io.github.ricardomonroy.tlacuache.storage.Volumes
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import io.github.ricardomonroy.tlacuache.ui.theme.headerMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Barra de estado del panel (UI_SPEC §2): elementos visibles o «N seleccionados · tamaño» a la izquierda y
 * espacio libre del volumen a la derecha (no en compacta). El espacio se relee con cada lectura de la
 * carpeta. En un panel estrecho los textos se acortan: con selección no se repiten los elementos y el
 * espacio dice solo lo libre (A4.11).
 */
@Composable
fun StatusBar(
    ui: TabState.Ui,
    showFreeSpace: Boolean,
) {
    val t = TlacuacheTheme.tokens
    val context = LocalContext.current
    // La primera miga es la raíz del volumen cuando la carpeta está dentro de uno.
    val root = ui.crumbs.first().takeIf { ui.rootLabel != null }
    val space by produceState<Pair<Long, Long>?>(null, root, ui.nowMs, showFreeSpace) {
        value =
            if (root != null && showFreeSpace) withContext(Dispatchers.IO) { Volumes.space(context, root) } else null
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(t.colors.headerBg.color)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .heightIn(min = 28.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val narrow = maxWidth < NarrowWidth
        Row(verticalAlignment = Alignment.CenterVertically) {
            val style = MaterialTheme.typography.bodySmall
            // Con selección: «3 seleccionados · 12 MB» (`core::summary`; las carpetas no suman tamaño).
            val selection = ui.selection
            val summary = remember(selection) { if (selection.isEmpty()) null else summarize(selection) }
            val items = pluralStringResource(R.plurals.pane_item_count, ui.visible.size, ui.visible.size)
            val text =
                summary?.let {
                    val count = pluralStringResource(R.plurals.selection_count, selection.size, selection.size)
                    val sized =
                        if (it.files > 0u) {
                            stringResource(
                                R.string.status_selection,
                                count,
                                Formatter.formatShortFileSize(context, it.bytes.toLong()),
                            )
                        } else {
                            count
                        }
                    if (showFreeSpace && !narrow) stringResource(R.string.status_selection, items, sized) else sized
                } ?: items
            Text(
                text,
                color = t.colors.headerMuted,
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            space?.let { (free, size) ->
                Text(
                    if (narrow) {
                        stringResource(R.string.volume_free_short, Formatter.formatShortFileSize(context, free))
                    } else {
                        stringResource(
                            R.string.volume_free,
                            Formatter.formatShortFileSize(context, free),
                            Formatter.formatShortFileSize(context, size),
                        )
                    },
                    color = t.colors.headerMuted,
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Por debajo, la barra de estado acorta sus textos para que quepan con la selección. */
private val NarrowWidth = 400.dp
