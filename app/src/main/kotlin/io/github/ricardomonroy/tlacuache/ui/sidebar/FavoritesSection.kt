package io.github.ricardomonroy.tlacuache.ui.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.Favorites
import io.github.ricardomonroy.tlacuache.storage.FavoritesStore
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import java.io.File

/**
 * Favoritos por grupos (UI_SPEC §6). Táctil: tocar abre; pulsación larga da Subir, Bajar, Mover a otro
 * grupo y Quitar; tocar el nombre de un grupo da Renombrar y Eliminar; + crea un grupo.
 *
 * shortcut: reordenar y añadir arrastrando llega con arrastrar y soltar (A3.3/A4).
 */
@Composable
internal fun FavoritesSection(items: SidebarItems) {
    val t = TlacuacheTheme.tokens
    // null: cerrado; -1: grupo nuevo; n: renombrar el grupo n.
    var naming by remember { mutableStateOf<Int?>(null) }
    SectionTitle(R.string.sidebar_favorites) {
        val newGroup = stringResource(R.string.group_new)
        IconButton({ naming = -1 }, Modifier.semantics { contentDescription = newGroup }) {
            Text("+", color = t.colors.sidebarFg.color, style = MaterialTheme.typography.titleLarge)
        }
    }
    if (items.favorites.isEmpty()) {
        Text(
            stringResource(R.string.favorites_empty),
            color = t.colors.fgMuted.color,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    items.favorites.forEachIndexed { g, group ->
        GroupHeader(group.name, onRename = { naming = g }) { items.editFavorites({ it.removeGroup(g.toUInt()) }, {}) }
        group.paths.forEachIndexed { i, path ->
            FavoriteEntry(items, g, i, File(path))
        }
    }
    naming?.let { g ->
        GroupNameDialog(
            initial =
                items.favorites
                    .getOrNull(g)
                    ?.name
                    .orEmpty(),
            onDismiss = { naming = null },
        ) { name, onResult ->
            items.editFavorites({ if (g < 0) it.addGroup(name) else it.renameGroup(g.toUInt(), name) }) {
                onResult(it)
                if (it == FavoritesStore.Result.OK) naming = null
            }
        }
    }
}

@Composable
private fun GroupHeader(
    name: String,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        SectionLabel(name, Modifier.clickable { menu = true })
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.group_rename)) },
                onClick = {
                    menu = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.group_remove)) },
                onClick = {
                    menu = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun FavoriteEntry(
    items: SidebarItems,
    group: Int,
    index: Int,
    dir: File,
) {
    var menu by remember { mutableStateOf(false) }
    val edit: ((Favorites) -> Unit) -> Unit = { change ->
        menu = false
        items.editFavorites(change) {}
    }
    val g = group.toUInt()
    val i = index.toUInt()
    val last = items.favorites[group].paths.lastIndex
    Box {
        Entry(R.drawable.ic_folder, items.labelOf(dir), dir == items.current, { items.onOpen(dir) }, { menu = true })
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            if (index > 0) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.favorite_up)) },
                    onClick = { edit { it.moveFavorite(g, i, g, i - 1u) } },
                )
            }
            if (index < last) {
                // El destino cuenta antes de quitarlo: bajar uno es i + 2.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.favorite_down)) },
                    onClick = { edit { it.moveFavorite(g, i, g, i + 2u) } },
                )
            }
            items.favorites.forEachIndexed { other, target ->
                if (other != group) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.favorite_move_to, target.name)) },
                        onClick = { edit { it.moveFavorite(g, i, other.toUInt(), target.paths.size.toUInt()) } },
                    )
                }
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.favorite_remove)) },
                onClick = { edit { it.remove(g, i) } },
            )
        }
    }
}

/** Nombre de un grupo nuevo o renombrado; el core valida (vacío, repetido) y el error se muestra aquí. */
@Composable
private fun GroupNameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String, (FavoritesStore.Result) -> Unit) -> Unit,
) {
    val t = TlacuacheTheme.tokens
    var name by remember { mutableStateOf(initial) }
    var result by remember { mutableStateOf(FavoritesStore.Result.OK) }
    val error =
        when (result) {
            FavoritesStore.Result.EMPTY_NAME -> R.string.group_name_empty
            FavoritesStore.Result.DUPLICATE_NAME -> R.string.group_name_taken
            FavoritesStore.Result.FAILED -> R.string.favorites_save_error
            FavoritesStore.Result.OK -> null
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.isEmpty()) R.string.group_new else R.string.group_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    result = FavoritesStore.Result.OK
                },
                label = { Text(stringResource(R.string.group_name)) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(stringResource(it), color = t.colors.error.color) } },
            )
        },
        confirmButton = {
            TextButton(
                { onConfirm(name) { result = it } },
            ) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
