package io.github.ricardomonroy.tlacuache.ui.ops

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.OpKind
import io.github.ricardomonroy.tlacuache.ffi.OpState
import io.github.ricardomonroy.tlacuache.ffi.Operation
import io.github.ricardomonroy.tlacuache.ops.OpsRepository
import io.github.ricardomonroy.tlacuache.ops.rateText
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import java.io.File

/**
 * Indicador de operaciones (UI_SPEC §5): un chip mientras hay algo en cola o en curso («Copiando 40 %» o
 * «3 operaciones»); tocarlo abre el detalle con Cancelar, Deshacer y Limpiar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpsIndicator(
    volumeName: (File) -> String?,
    modifier: Modifier = Modifier,
) {
    val t = TlacuacheTheme.tokens
    val ops by OpsRepository.operations.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf(false) }
    val active = ops.filter { !it.finished }
    if (active.isNotEmpty()) {
        val label =
            if (active.size == 1) {
                active.single().let { op ->
                    listOfNotNull(stringResource(op.kind.label), op.percent()).joinToString(" · ")
                }
            } else {
                pluralStringResource(R.plurals.ops_active, active.size, active.size)
            }
        Text(
            "▸ $label",
            color = t.colors.accentFg.color,
            style = MaterialTheme.typography.labelLarge,
            modifier =
                modifier
                    .background(t.colors.accent.color, MaterialTheme.shapes.large)
                    .clickable(role = Role.Button) { detail = true }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
    if (detail) {
        ModalBottomSheet(onDismissRequest = { detail = false }, containerColor = t.colors.paneBg.color) {
            OpsDetail(ops, volumeName)
        }
    }
}

@Composable
private fun OpsDetail(
    ops: List<Operation>,
    volumeName: (File) -> String?,
) {
    val t = TlacuacheTheme.tokens
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.ops_title),
                color = t.colors.fg.color,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(OpsRepository::clearFinished, enabled = ops.any { it.finished }) {
                Text(stringResource(R.string.ops_clear))
            }
        }
        LazyColumn {
            // Lo más reciente arriba.
            items(ops.asReversed(), key = { it.id.toLong() }) { op ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                pluralStringResource(
                                    R.plurals.ops_items,
                                    op.sources.size,
                                    stringResource(op.kind.label),
                                    op.sources.size,
                                ),
                                color = t.colors.fg.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                op.status(volumeName),
                                color = (if (op.state is OpState.Failed) t.colors.error else t.colors.fgMuted).color,
                                style = MaterialTheme.typography.bodySmall,
                                // Un error dice qué pasó y qué hacer (BRAND §6): no se corta.
                                maxLines = if (op.state is OpState.Failed) 3 else 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!op.finished) {
                            TextButton({ OpsRepository.cancel(op.id) }) { Text(stringResource(R.string.ops_cancel)) }
                        }
                        if (op.canUndo) {
                            TextButton(
                                { OpsRepository.undo(context, op.id) },
                            ) { Text(stringResource(R.string.ops_undo)) }
                        }
                    }
                    if (op.state == OpState.Running) {
                        LinearProgressIndicator(
                            progress = { (op.fraction ?: 0.0).toFloat() },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private val Operation.finished: Boolean
    get() = state == OpState.Done || state == OpState.Cancelled || state is OpState.Failed

private fun Operation.percent(): String? =
    fraction
        ?.takeIf {
            state == OpState.Running
        }?.let { "${(it * 100).toInt()} %" }

@Composable
private fun Operation.status(volumeName: (File) -> String?): String =
    when (state) {
        OpState.Queued -> {
            stringResource(R.string.ops_queued)
        }

        OpState.Running -> {
            val rate by OpsRepository.rate.collectAsStateWithLifecycle()
            val speed = rate?.takeIf { it.first == id }?.let { rateText(LocalContext.current, it.second) }
            listOfNotNull(percent(), speed, progress.current).joinToString(" · ")
        }

        OpState.Cancelling -> {
            stringResource(R.string.ops_cancelling)
        }

        OpState.Done -> {
            finishedMessage(LocalResources.current, this, volumeName)
        }

        OpState.Cancelled -> {
            stringResource(R.string.ops_cancelled)
        }

        is OpState.Failed -> {
            finishedMessage(LocalResources.current, this, volumeName)
        }
    }

private val OpKind.label: Int
    get() =
        when (this) {
            OpKind.COPY -> R.string.kind_copy
            OpKind.MOVE -> R.string.kind_move
            OpKind.TRASH -> R.string.kind_trash
            OpKind.DELETE -> R.string.kind_delete
            OpKind.UNTRASH, OpKind.RESTORE -> R.string.kind_restore
            OpKind.RENAME, OpKind.RENAME_MANY -> R.string.kind_rename
            OpKind.MKDIR -> R.string.new_folder
            OpKind.CREATE_FILE -> R.string.new_file
        }
