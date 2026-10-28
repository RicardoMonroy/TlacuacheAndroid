package io.github.ricardomonroy.tlacuache.ui.trash

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import io.github.ricardomonroy.tlacuache.ffi.OpKind
import io.github.ricardomonroy.tlacuache.ops.OpsRepository
import io.github.ricardomonroy.tlacuache.storage.TrashStore
import io.github.ricardomonroy.tlacuache.storage.Volumes
import io.github.ricardomonroy.tlacuache.ui.ops.DeleteConfirmation
import io.github.ricardomonroy.tlacuache.ui.pane.FileIcon
import io.github.ricardomonroy.tlacuache.ui.pane.rememberCategory
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Papelera de todos los volúmenes (UI_SPEC §6, ADR-A05): lo más reciente primero; tocar un elemento da
 * Restaurar y Borrar permanentemente; «Vaciar» borra todo con confirmación. Todo pasa por la cola de
 * operaciones ([run]), con su notificación y su aviso.
 */
@Composable
fun TrashScreen(
    onClose: () -> Unit,
    run: (OpKind, List<File>) -> Unit,
) {
    val t = TlacuacheTheme.tokens
    val context = LocalContext.current
    // Se relee al terminar cada operación (restaurar, borrar, enviar algo nuevo).
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { OpsRepository.finished.collect { version++ } }
    val items by produceState<List<TrashStore.Item>?>(null, version) {
        value = withContext(Dispatchers.IO) { TrashStore({ Volumes.roots(context) }).items() }
    }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TrashStore.Item?>(null) }

    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(t.colors.paneBg.color).windowInsetsPadding(WindowInsets.systemBars)) {
            Row(
                Modifier.fillMaxWidth().background(t.colors.headerBg.color).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val close = stringResource(R.string.trash_close)
                IconButton(onClose, Modifier.semantics { contentDescription = close }) {
                    Text("←", color = t.colors.headerFg.color, style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    stringResource(R.string.trash_title),
                    color = t.colors.headerFg.color,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton({ confirmEmpty = true }, enabled = !items.isNullOrEmpty()) {
                    Text(stringResource(R.string.trash_empty_action))
                }
            }
            val list = items
            when {
                list == null -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                list.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.trash_is_empty), color = t.colors.fgMuted.color)
                    }
                }

                else -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.file.path }) { item ->
                            TrashRow(
                                item,
                                onRestore = { run(OpKind.UNTRASH, listOf(item.file)) },
                                onDelete = { confirmDelete = item },
                            )
                        }
                    }
                }
            }
        }
        if (confirmEmpty) {
            val all = items.orEmpty()
            DeleteConfirmation(all.map { it.file }, R.string.trash_empty_title, onDismiss = { confirmEmpty = false }) {
                run(OpKind.DELETE, all.map { it.file })
                confirmEmpty = false
            }
        }
        confirmDelete?.let { item ->
            DeleteConfirmation(listOf(item.file), onDismiss = { confirmDelete = null }) {
                run(OpKind.DELETE, listOf(item.file))
                confirmDelete = null
            }
        }
    }
}

@Composable
private fun TrashRow(
    item: TrashStore.Item,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val t = TlacuacheTheme.tokens
    var menu by remember { mutableStateOf(false) }
    val name = item.originalPath.name
    val entry = remember(item) { FileEntry(name, item.file.isDirectory, false, 0u, null) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { menu = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FileIcon(rememberCategory(entry), 24.dp)
            Column(Modifier.weight(1f)) {
                Text(name, color = t.colors.fg.color, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                Text(
                    stringResource(
                        R.string.trash_from,
                        item.originalPath.parentFile
                            ?.name
                            .orEmpty(),
                        DateUtils.getRelativeTimeSpanString(item.deletedAtMs).toString(),
                    ),
                    color = t.colors.fgMuted.color,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.trash_restore)) },
                onClick = {
                    menu = false
                    onRestore()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = {
                    menu = false
                    onDelete()
                },
            )
        }
    }
}
