package io.github.ricardomonroy.tlacuache.ui.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.state.TabState
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color

/**
 * Barra de la selección (UI_SPEC §4): ✕ sale del modo selección, el número de seleccionados, «Todo» y ⋯ con
 * las acciones que valen para lo seleccionado.
 *
 * shortcut: Copiar/Mover a B como botones, Renombrar y Papelera llegan con A3.3, A3.6 y A3.5.
 */
@Composable
fun SelectionBar(
    ui: TabState.Ui,
    tab: TabState,
    modifier: Modifier = Modifier,
    onAction: (FileAction) -> Unit,
) {
    val t = TlacuacheTheme.tokens
    var menu by remember { mutableStateOf(false) }
    val selection = ui.selection
    // Con uno solo, lo mismo que su pulsación larga; con varios, lo que vale para todos.
    val actions =
        FileAction.entries.filter { action ->
            if (selection.size == 1) {
                action.appliesTo(selection.single())
            } else {
                action == FileAction.COPY_TO_OTHER ||
                    action == FileAction.MOVE_TO_OTHER ||
                    (action == FileAction.SHARE && selection.any { !it.isDir })
            }
        }
    Row(
        Modifier
            .fillMaxWidth()
            .background(t.colors.headerBg.color)
            .then(modifier)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val close = stringResource(R.string.selection_close)
        IconButton(tab::clearSelection, Modifier.semantics { contentDescription = close }) {
            Text("✕", color = t.colors.headerFg.color, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            pluralStringResource(R.plurals.selection_count, ui.selected.size, ui.selected.size),
            color = t.colors.headerFg.color,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp).weight(1f),
        )
        TextButton(tab::selectAll) { Text(stringResource(R.string.selection_all)) }
        Box {
            val more = stringResource(R.string.menu_more)
            IconButton({ menu = true }, Modifier.semantics { contentDescription = more }) {
                Text("⋮", color = t.colors.headerFg.color, style = MaterialTheme.typography.titleLarge)
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(stringResource(action.label)) },
                        onClick = {
                            menu = false
                            onAction(action)
                        },
                    )
                }
            }
        }
    }
}
