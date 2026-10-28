package io.github.ricardomonroy.tlacuache.ui.ops

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color

/** Confirmación del borrado permanente (no se puede deshacer); también para vaciar la papelera. */
@Composable
fun DeleteConfirmation(
    count: Int,
    title: Int = R.string.delete_confirm_title,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(pluralStringResource(R.plurals.delete_confirm_text, count, count)) },
        confirmButton = {
            TextButton(onConfirm) {
                Text(stringResource(R.string.delete_confirm), color = TlacuacheTheme.tokens.colors.error.color)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.ops_cancel)) } },
    )
}
