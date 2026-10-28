package io.github.ricardomonroy.tlacuache.ui.ops

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.NameError
import io.github.ricardomonroy.tlacuache.ffi.stemLen
import io.github.ricardomonroy.tlacuache.ffi.validateName
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import java.io.File

/**
 * Nombre de un elemento nuevo o renombrado (A3.7). Se selecciona el nombre sin la extensión (`stem_len`) y
 * los errores son los del core (`validate_name`); un nombre ocupado en [dir] también se avisa aquí.
 */
@Composable
fun NameDialog(
    @StringRes title: Int,
    initial: String,
    isDir: Boolean,
    dir: File,
    /** Renombrar: dejar el mismo nombre solo cierra. Crear: el nombre propuesto se acepta tal cual. */
    renaming: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val t = TlacuacheTheme.tokens
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, stemLen(initial, isDir).toInt()))) }
    var error by remember { mutableStateOf<Int?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val confirm = {
        val name = value.text
        error =
            validateName(name)?.message
                ?: R.string.name_exists.takeIf { !(renaming && name == initial) && File(dir, name).exists() }
        if (error == null) {
            if (renaming && name == initial) {
                onDismiss()
            } else {
                onConfirm(name)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(stringResource(it), color = t.colors.error.color) } },
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(confirm) { Text(stringResource(R.string.dialog_ok)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

private val NameError.message: Int
    get() =
        when (this) {
            NameError.EMPTY -> R.string.name_empty
            NameError.RESERVED -> R.string.name_reserved
            NameError.CONTAINS_SLASH -> R.string.name_slash
            NameError.CONTAINS_NUL -> R.string.name_nul
            NameError.TOO_LONG -> R.string.name_too_long
        }
