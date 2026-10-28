package io.github.ricardomonroy.tlacuache.ui.pane

import android.text.format.Formatter
import android.webkit.MimeTypeMap
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.storage.Measure
import io.github.ricardomonroy.tlacuache.storage.measure
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Propiedades de uno o varios elementos (UI_SPEC §4): nombre, tipo, ubicación, tamaño y fecha. El tamaño de
 * las carpetas se mide en segundo plano y se corta al cerrar el diálogo.
 */
@Composable
fun PropertiesDialog(
    files: List<File>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val one = files.singleOrNull()
    val size by produceState<Measure?>(null, files) {
        value = withContext(Dispatchers.IO) { measure(files) { isActive } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(one?.name ?: pluralStringResource(R.plurals.selection_count, files.size, files.size))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (one != null) {
                    Property(R.string.props_type, one.typeLabel())
                }
                files.first().parent?.let { Property(R.string.props_location, it) }
                Property(
                    R.string.props_size,
                    size?.let { m ->
                        val bytes = Formatter.formatShortFileSize(context, m.bytes)
                        if (files.any(File::isDirectory)) {
                            stringResource(
                                R.string.props_size_contents,
                                bytes,
                                pluralStringResource(R.plurals.props_files, m.files.toInt(), m.files.toInt()),
                                pluralStringResource(R.plurals.props_folders, m.dirs.toInt(), m.dirs.toInt()),
                            )
                        } else {
                            bytes
                        }
                    } ?: stringResource(R.string.props_calculating),
                )
                if (one != null) {
                    Property(
                        R.string.props_modified,
                        DateFormat
                            .getDateTimeInstance(
                                DateFormat.MEDIUM,
                                DateFormat.SHORT,
                            ).format(Date(one.lastModified())),
                    )
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.dialog_ok)) } },
    )
}

@Composable
private fun Property(
    @StringRes label: Int,
    value: String,
) {
    val t = TlacuacheTheme.tokens
    Column {
        Text(stringResource(label), color = t.colors.fgMuted.color, style = MaterialTheme.typography.labelMedium)
        Text(value, color = t.colors.fg.color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun File.typeLabel(): String =
    if (isDirectory) {
        stringResource(R.string.conflict_folder)
    } else {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: stringResource(R.string.props_unknown_type)
    }
