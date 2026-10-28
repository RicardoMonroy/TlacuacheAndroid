package io.github.ricardomonroy.tlacuache.ui.ops

import android.text.format.Formatter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.storage.measure
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Confirmación del borrado permanente (no se puede deshacer); también para vaciar la papelera. Dice cuánto
 * ocupa lo que se borra en cuanto termina de medirlo (UI_SPEC §5).
 */
@Composable
fun DeleteConfirmation(
    files: List<File>,
    title: Int = R.string.delete_confirm_title,
    text: Int = R.plurals.delete_confirm_text,
    confirm: Int = R.string.delete_confirm,
    /** Botón en color de error: solo para lo que no se deshace. */
    danger: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val bytes by produceState<Long?>(null, files) {
        value = withContext(Dispatchers.IO) { measure(files) { isActive }.bytes }
    }
    val count = pluralStringResource(text, files.size, files.size)
    val size = bytes?.let { stringResource(R.string.delete_confirm_size, Formatter.formatFileSize(context, it)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(listOfNotNull(count, size).joinToString(" ")) },
        confirmButton = {
            TextButton(onConfirm) {
                val colors = TlacuacheTheme.tokens.colors
                Text(stringResource(confirm), color = (if (danger) colors.error else colors.accent).color)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.ops_cancel)) } },
    )
}
