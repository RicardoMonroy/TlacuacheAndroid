package io.github.ricardomonroy.tlacuache.ui.ops

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.ConflictAction
import io.github.ricardomonroy.tlacuache.ffi.OpKind
import io.github.ricardomonroy.tlacuache.ffi.OpState
import io.github.ricardomonroy.tlacuache.ffi.Operation
import io.github.ricardomonroy.tlacuache.ops.OpsRepository
import io.github.ricardomonroy.tlacuache.ops.OpsRunner

/**
 * Conflicto de la operación en curso: Reemplazar (Combinar con carpetas), Omitir o Conservar ambos, con
 * «Aplicar a todos» (`core::ops::ConflictPolicy`). Cerrarlo cancela la operación.
 *
 * shortcut: sin comparar tamaños ni fechas (`newer`) ni aviso por notificación si no hay ventana visible.
 */
@Composable
fun OpsConflictDialog() {
    val pending by OpsRepository.conflict.collectAsStateWithLifecycle()
    val p = pending ?: return
    var applyToAll by remember(p) { mutableStateOf(false) }
    val answer = { action: ConflictAction -> p.answer(OpsRunner.Decision(action, applyToAll)) }
    val c = p.conflict
    AlertDialog(
        onDismissRequest = {
            OpsRepository.cancel(p.opId)
            answer(ConflictAction.SKIP)
        },
        title = { Text(stringResource(R.string.conflict_title, c.existing.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (c.replaceAllowed) {
                    Choice(if (c.source.isDirectory) R.string.conflict_merge else R.string.conflict_replace) {
                        answer(ConflictAction.REPLACE)
                    }
                }
                Choice(R.string.conflict_keep_both) { answer(ConflictAction.KEEP_BOTH) }
                Choice(R.string.conflict_skip) { answer(ConflictAction.SKIP) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Checkbox) { applyToAll = !applyToAll },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(applyToAll, onCheckedChange = null)
                    Text(stringResource(R.string.conflict_apply_all))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton({
                OpsRepository.cancel(p.opId)
                answer(ConflictAction.SKIP)
            }) { Text(stringResource(R.string.ops_cancel)) }
        },
    )
}

@Composable
private fun Choice(
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    TextButton(onClick, Modifier.fillMaxWidth()) { Text(stringResource(label), Modifier.fillMaxWidth()) }
}

/** Aviso al terminar una operación. */
@StringRes
fun finishedMessage(op: Operation): Int =
    when (val state = op.state) {
        is OpState.Failed -> {
            when (runCatching { OpsRunner.Failure.valueOf(state.message) }.getOrNull()) {
                OpsRunner.Failure.INTO_ITSELF -> R.string.ops_failed_into_itself
                OpsRunner.Failure.PROTECTED -> R.string.ops_failed_protected
                OpsRunner.Failure.READ_ONLY_DEST -> R.string.ops_failed_read_only
                OpsRunner.Failure.SIZE_MISMATCH -> R.string.ops_failed_size
                OpsRunner.Failure.NO_TRASH -> R.string.ops_failed_no_trash
                OpsRunner.Failure.NOT_IN_TRASH -> R.string.ops_failed_not_in_trash
                else -> R.string.ops_failed_io
            }
        }

        OpState.Cancelled -> {
            R.string.ops_cancelled
        }

        else -> {
            when (op.kind) {
                OpKind.MOVE -> R.string.ops_done_move
                OpKind.TRASH -> R.string.ops_done_trash
                OpKind.DELETE -> R.string.ops_done_delete
                OpKind.UNTRASH -> R.string.ops_done_untrash
                else -> R.string.ops_done_copy
            }
        }
    }
