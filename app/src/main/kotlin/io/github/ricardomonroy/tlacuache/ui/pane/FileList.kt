package io.github.ricardomonroy.tlacuache.ui.pane

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.Age
import io.github.ricardomonroy.tlacuache.ffi.AgeBucket
import io.github.ricardomonroy.tlacuache.ffi.AgeStyle
import io.github.ricardomonroy.tlacuache.ffi.AgeUnit
import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import io.github.ricardomonroy.tlacuache.ffi.age
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import java.text.DateFormat
import java.util.Date

/** Acciones sobre un elemento de la lista. */
enum class FileAction(
    @StringRes val label: Int,
) {
    OPEN(R.string.action_open),
    OPEN_WITH(R.string.action_open_with),
    SHARE(R.string.action_share),
    ADD_FAVORITE(R.string.favorite_add),

    // shortcut: en el menú hasta la franja central y la barra con destino (A3.3).
    COPY_TO_OTHER(R.string.action_copy_to_other),
    MOVE_TO_OTHER(R.string.action_move_to_other),
}

// shortcut: filas de 48 dp (táctil); la densidad según el puntero llega en A4.2.
private val RowHeight = 48.dp

/** Columnas que caben según el ancho del panel: nombre siempre; luego edad, tamaño y fecha (UI_SPEC §2). */
private data class Columns(
    val age: Boolean,
    val size: Boolean,
    val date: Boolean,
) {
    companion object {
        fun forWidth(width: Dp) = Columns(age = width >= 280.dp, size = width >= 440.dp, date = width >= 640.dp)
    }
}

@Composable
fun FileList(
    entries: List<FileEntry>,
    nowMs: Long,
    onOpen: (FileEntry) -> Unit,
    onAction: (FileEntry, FileAction) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    BoxWithConstraints(modifier) {
        val columns = Columns.forWidth(maxWidth)
        LazyColumn(Modifier.fillMaxSize(), rememberLazyListState(), contentPadding) {
            // El nombre es la identidad: releer la carpeta conserva desplazamiento y selección (ADR-A10).
            itemsIndexed(entries, key = { _, e -> e.name }) { index, entry ->
                FileRow(
                    entry,
                    nowMs,
                    columns,
                    alt = index % 2 == 1,
                    onOpen = { onOpen(entry) },
                    onAction = { onAction(entry, it) },
                )
            }
        }
    }
}

@Composable
private fun FileRow(
    entry: FileEntry,
    nowMs: Long,
    columns: Columns,
    alt: Boolean,
    onOpen: () -> Unit,
    onAction: (FileAction) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        RowContent(entry, nowMs, columns, alt, onOpen, onLongPress = { menu = true })
        FileMenu(entry, menu, { menu = false }, onAction)
    }
}

/** Menú de pulsación larga de un elemento (lista y cuadrícula). */
@Composable
internal fun FileMenu(
    entry: FileEntry,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (FileAction) -> Unit,
) {
    // shortcut: menú mínimo por pulsación larga hasta la selección (A3.4) y el menú contextual completo (A3.11).
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        FileAction.entries
            .filter {
                when (it) {
                    FileAction.OPEN, FileAction.COPY_TO_OTHER, FileAction.MOVE_TO_OTHER -> true
                    FileAction.ADD_FAVORITE -> entry.isDir
                    FileAction.OPEN_WITH, FileAction.SHARE -> !entry.isDir
                }
            }.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.label)) },
                    onClick = {
                        onDismiss()
                        onAction(action)
                    },
                )
            }
    }
}

@Composable
private fun RowContent(
    entry: FileEntry,
    nowMs: Long,
    columns: Columns,
    alt: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val t = TlacuacheTheme.tokens
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .background(if (alt) t.colors.paneAltRow.color else t.colors.paneBg.color)
            // shortcut: un toque o Enter abre; selección y doble clic con ratón llegan en A3.4 y A4.2.
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FileIcon(rememberCategory(entry), 20.dp)
        Text(
            entry.name,
            color = t.colors.fg.color,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (columns.size) {
            Text(
                if (entry.isDir) "" else Formatter.formatShortFileSize(context, entry.size.toLong()),
                color = t.colors.fgMuted.color,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.width(72.dp),
            )
        }
        if (columns.date) {
            val date =
                entry.modifiedMs?.let {
                    remember(it) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }
                }
            Text(
                date.orEmpty(),
                color = t.colors.fgMuted.color,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.width(132.dp),
            )
        }
        if (columns.age) {
            Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
                entry.modifiedMs?.let { AgeChip(remember(it, nowMs) { age(it, nowMs) }) }
            }
        }
    }
}

/** Edad compacta con el color de su bucket, como chip o como texto según el tema (THEMES.md §3). */
@Composable
private fun AgeChip(age: Age) {
    val t = TlacuacheTheme.tokens
    val bucket =
        when (age.bucket) {
            AgeBucket.HOUR -> t.age.hour
            AgeBucket.DAY -> t.age.day
            AgeBucket.WEEK -> t.age.week
            AgeBucket.MONTH -> t.age.month
            AgeBucket.YEAR -> t.age.year
        }.color
    val n = age.amount.toInt()
    val label =
        when (age.unit) {
            AgeUnit.NOW -> stringResource(R.string.age_now)
            AgeUnit.MINUTES -> stringResource(R.string.age_minutes, n)
            AgeUnit.HOURS -> stringResource(R.string.age_hours, n)
            AgeUnit.DAYS -> stringResource(R.string.age_days, n)
            AgeUnit.MONTHS -> stringResource(R.string.age_months, n)
            AgeUnit.YEARS -> stringResource(R.string.age_years, n)
        }
    if (t.style.ageStyle == AgeStyle.CHIP) {
        Text(
            label,
            color = t.age.chipFg.color,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier =
                Modifier
                    .background(bucket, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    } else {
        Text(label, color = bucket, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
