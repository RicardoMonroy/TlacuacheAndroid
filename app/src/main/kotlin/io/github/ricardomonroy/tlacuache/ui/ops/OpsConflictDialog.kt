package io.github.ricardomonroy.tlacuache.ui.ops

import android.content.res.Resources
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.ConflictAction
import io.github.ricardomonroy.tlacuache.ffi.Newer
import io.github.ricardomonroy.tlacuache.ffi.OpKind
import io.github.ricardomonroy.tlacuache.ffi.OpState
import io.github.ricardomonroy.tlacuache.ffi.Operation
import io.github.ricardomonroy.tlacuache.ops.OpsRepository
import io.github.ricardomonroy.tlacuache.ops.OpsRunner
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Conflicto de la operación en curso: Reemplazar (Combinar con carpetas), Omitir o Conservar ambos, con
 * «Aplicar a todos» (`core::ops::ConflictPolicy`). Cancelar o el gesto atrás cancelan la operación.
 *
 * Compara tamaño y fecha de los dos y marca el más reciente (`core::ops::newer`). Si no hay ninguna ventana
 * visible, `OpsService` avisa con una notificación que abre la app.
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
        // Un toque fuera (o al volver desde la notificación) no decide nada; atrás equivale a Cancelar.
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.conflict_title, c.existing.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SideRow(R.string.conflict_current, c.current, c.newer == Newer.EXISTING)
                SideRow(R.string.conflict_incoming, c.incoming, c.newer == Newer.INCOMING)
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

/** «En el destino: 4.0 MB · 27/10/26 18:29 · más reciente». */
@Composable
private fun SideRow(
    @StringRes label: Int,
    side: OpsRunner.Side,
    newest: Boolean,
) {
    val t = TlacuacheTheme.tokens
    val context = LocalContext.current
    val parts =
        listOfNotNull(
            if (side.isDir) {
                stringResource(
                    R.string.conflict_folder,
                )
            } else {
                Formatter.formatShortFileSize(context, side.size)
            },
            side.modifiedMs?.let {
                DateFormat
                    .getDateTimeInstance(
                        DateFormat.SHORT,
                        DateFormat.SHORT,
                    ).format(Date(it))
            },
            if (newest) stringResource(R.string.conflict_newer) else null,
        )
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(stringResource(label), color = t.colors.fgMuted.color, style = MaterialTheme.typography.labelMedium)
        Text(
            parts.joinToString(" · "),
            color = if (newest) t.colors.accent.color else t.colors.fg.color,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Choice(
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    TextButton(onClick, Modifier.fillMaxWidth()) { Text(stringResource(label), Modifier.fillMaxWidth()) }
}

/** Aviso al terminar una operación. */
fun finishedMessage(
    resources: Resources,
    op: Operation,
    volumeName: (File) -> String?,
): String {
    val n = op.sources.size
    // La raíz de un volumen se llama «Interno», no «0».
    val dest = op.dest?.let(::File)?.let { volumeName(it) ?: it.name }
    // Con cuántos y adónde (UI_SPEC §4): «Se copiaron 3 elementos a «Fotos»».
    return when {
        op.state != OpState.Done -> resources.getString(finishedMessageRes(op))
        op.kind == OpKind.COPY && dest != null -> resources.getQuantityString(R.plurals.ops_done_copy_to, n, n, dest)
        op.kind == OpKind.MOVE && dest != null -> resources.getQuantityString(R.plurals.ops_done_move_to, n, n, dest)
        op.kind == OpKind.TRASH -> resources.getQuantityString(R.plurals.ops_done_trash_count, n, n)
        else -> resources.getString(finishedMessageRes(op))
    }
}

@StringRes
private fun finishedMessageRes(op: Operation): Int =
    when (val state = op.state) {
        is OpState.Failed -> {
            when (runCatching { OpsRunner.Failure.valueOf(state.message) }.getOrNull()) {
                OpsRunner.Failure.INTO_ITSELF -> R.string.ops_failed_into_itself
                OpsRunner.Failure.PROTECTED -> R.string.ops_failed_protected
                OpsRunner.Failure.READ_ONLY_DEST -> R.string.ops_failed_read_only
                OpsRunner.Failure.SIZE_MISMATCH -> R.string.ops_failed_size
                OpsRunner.Failure.NO_TRASH -> R.string.ops_failed_no_trash
                OpsRunner.Failure.NOT_IN_TRASH -> R.string.ops_failed_not_in_trash
                OpsRunner.Failure.EXISTS -> R.string.name_exists
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
                OpKind.UNTRASH, OpKind.RESTORE -> R.string.ops_done_untrash
                OpKind.RENAME -> R.string.ops_done_rename
                OpKind.MKDIR, OpKind.CREATE_FILE -> R.string.ops_done_create
                else -> R.string.ops_done_copy
            }
        }
    }
