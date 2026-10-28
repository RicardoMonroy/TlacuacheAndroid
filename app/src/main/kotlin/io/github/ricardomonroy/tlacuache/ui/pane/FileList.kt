package io.github.ricardomonroy.tlacuache.ui.pane

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.Age
import io.github.ricardomonroy.tlacuache.ffi.AgeBucket
import io.github.ricardomonroy.tlacuache.ffi.AgeStyle
import io.github.ricardomonroy.tlacuache.ffi.AgeUnit
import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import io.github.ricardomonroy.tlacuache.ffi.SortKey
import io.github.ricardomonroy.tlacuache.ffi.SortSpec
import io.github.ricardomonroy.tlacuache.ffi.age
import io.github.ricardomonroy.tlacuache.ops.Clipboard
import io.github.ricardomonroy.tlacuache.ui.rowHeight
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import java.text.DateFormat
import java.util.Date

/** Acciones sobre un elemento de la lista. */
enum class FileAction(
    @StringRes val label: Int,
) {
    // Orden del menú contextual (UI_SPEC §4).
    OPEN(R.string.action_open),
    OPEN_WITH(R.string.action_open_with),
    OPEN_IN_NEW_WINDOW(R.string.action_open_new_window),
    SHARE(R.string.action_share),
    CUT(R.string.action_cut),
    COPY(R.string.action_copy),
    PASTE(R.string.action_paste_into),
    COPY_TO_OTHER(R.string.action_copy_to_other),
    MOVE_TO_OTHER(R.string.action_move_to_other),
    RENAME(R.string.action_rename),
    TRASH(R.string.action_trash),
    ADD_FAVORITE(R.string.favorite_add),
    COPY_PATH(R.string.action_copy_path),
    PROPERTIES(R.string.action_properties),
    DELETE(R.string.action_delete),
}

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
    selected: Set<String>,
    activePane: Boolean,
    isProtected: (String) -> Boolean,
    gestures: ItemGestures,
    drag: ItemDrag,
    onAction: (FileEntry, FileAction) -> Unit,
    sort: SortSpec,
    onSort: (SortKey) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    BoxWithConstraints(modifier) {
        val columns = Columns.forWidth(maxWidth)
        Column(Modifier.fillMaxSize()) {
            SortHeader(columns, sort, onSort)
            FileRows(
                entries,
                nowMs,
                columns,
                selected,
                activePane,
                isProtected,
                gestures,
                drag,
                onAction,
                contentPadding,
            )
        }
    }
}

@Composable
private fun FileRows(
    entries: List<FileEntry>,
    nowMs: Long,
    columns: Columns,
    selected: Set<String>,
    activePane: Boolean,
    isProtected: (String) -> Boolean,
    gestures: ItemGestures,
    drag: ItemDrag,
    onAction: (FileEntry, FileAction) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(Modifier.fillMaxSize().then(gestures.trackPointer), rememberLazyListState(), contentPadding) {
        // El nombre es la identidad: releer la carpeta conserva desplazamiento y selección (ADR-A10).
        itemsIndexed(entries, key = { _, e -> e.name }) { index, entry ->
            FileRow(
                entry,
                nowMs,
                columns,
                alt = index % 2 == 1,
                selection = selectionColors(entry.name in selected, activePane),
                gestures = gestures,
                drag = drag,
                protected = isProtected(entry.name),
                onAction = { onAction(entry, it) },
            )
        }
    }
}

@Composable
private fun FileRow(
    entry: FileEntry,
    nowMs: Long,
    columns: Columns,
    alt: Boolean,
    selection: SelectionColors?,
    gestures: ItemGestures,
    drag: ItemDrag,
    protected: Boolean,
    onAction: (FileAction) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var dropHere by remember { mutableStateOf(false) }
    Box {
        RowContent(
            entry,
            nowMs,
            columns,
            alt,
            // Una carpeta con algo arrastrado encima se resalta como seleccionada.
            if (dropHere) selectionColors(selected = true, activePane = true) else selection,
            Modifier
                .itemGestures(gestures, entry) { menu = true }
                // Por dentro del clic: el arrastre ve los eventos antes que la pulsación.
                .itemDrag(drag, entry, onStart = { menu = false }) { dropHere = it },
        )
        FileMenu(entry, protected, menu, { menu = false }, onAction)
    }
}

/**
 * Acciones que tienen sentido para [items] (uno o varios): las de un solo elemento no se ofrecen con varios,
 * mover y borrar no con carpetas protegidas (ADR-A06), Pegar solo sobre una carpeta y con algo copiado.
 */
internal fun FileAction.appliesTo(
    items: List<FileEntry>,
    protected: Boolean,
    canPaste: Boolean,
): Boolean {
    val one = items.singleOrNull()
    return when (this) {
        FileAction.OPEN -> one != null
        FileAction.OPEN_IN_NEW_WINDOW -> one?.isDir == true
        FileAction.OPEN_WITH -> one?.isDir == false
        FileAction.SHARE -> items.any { !it.isDir }
        FileAction.CUT, FileAction.MOVE_TO_OTHER, FileAction.TRASH, FileAction.DELETE -> !protected
        FileAction.RENAME -> one != null && !protected
        FileAction.COPY, FileAction.COPY_TO_OTHER, FileAction.COPY_PATH, FileAction.PROPERTIES -> true
        FileAction.PASTE -> canPaste && one?.isDir == true
        FileAction.ADD_FAVORITE -> one?.isDir == true
    }
}

/** Menú de pulsación larga de un elemento (lista y cuadrícula). */
@Composable
internal fun FileMenu(
    entry: FileEntry,
    protected: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (FileAction) -> Unit,
) {
    // Menú contextual (UI_SPEC §4): pulsación larga sobre lo seleccionado o clic derecho.
    val canPaste = Clipboard.clip.collectAsStateWithLifecycle().value != null
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        FileAction.entries
            .filter { it.appliesTo(listOf(entry), protected, canPaste) }
            .forEach { action ->
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

/** Encabezados de columna (UI_SPEC §3): tocar uno ordena por él; otra vez invierte el sentido. */
@Composable
private fun SortHeader(
    columns: Columns,
    sort: SortSpec,
    onSort: (SortKey) -> Unit,
) {
    val t = TlacuacheTheme.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .background(t.colors.paneBg.color)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // El ícono de 20dp de las filas: el nombre queda alineado con su columna.
        HeaderCell(R.string.column_name, SortKey.NAME, sort, onSort, Modifier.weight(1f).padding(start = 32.dp))
        if (columns.size) {
            HeaderCell(R.string.column_size, SortKey.SIZE, sort, onSort, Modifier.width(72.dp), TextAlign.End)
        }
        if (columns.date) HeaderCell(R.string.column_modified, SortKey.MODIFIED, sort, onSort, Modifier.width(132.dp))
        // La edad es la fecha vista de otro modo: ordena por fecha, y marca el sentido si no hay columna de fecha.
        if (columns.age) {
            HeaderCell(
                R.string.column_age,
                SortKey.MODIFIED,
                sort.takeIf { !columns.date },
                onSort,
                Modifier.width(56.dp),
                TextAlign.End,
            )
        }
    }
}

@Composable
private fun HeaderCell(
    @StringRes label: Int,
    key: SortKey,
    sort: SortSpec?,
    onSort: (SortKey) -> Unit,
    modifier: Modifier,
    align: TextAlign = TextAlign.Start,
) {
    val t = TlacuacheTheme.tokens
    val active = sort?.key == key
    val arrow =
        when {
            !active -> ""
            sort.descending -> " ▼"
            else -> " ▲"
        }
    val state =
        if (active) stringResource(if (sort.descending) R.string.sort_descending else R.string.sort_ascending) else ""
    Text(
        stringResource(label) + arrow,
        color = if (active) t.colors.fg.color else t.colors.fgMuted.color,
        style = MaterialTheme.typography.labelLarge,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .clickable(role = Role.Button) { onSort(key) }
                .semantics { stateDescription = state }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(vertical = 8.dp),
    )
}

@Composable
private fun RowContent(
    entry: FileEntry,
    nowMs: Long,
    columns: Columns,
    alt: Boolean,
    selection: SelectionColors?,
    modifier: Modifier,
) {
    val t = TlacuacheTheme.tokens
    val context = LocalContext.current
    // Hover del ratón (UI_SPEC §3): resalta la fila bajo el puntero.
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val background =
        selection?.background ?: when {
            hovered -> t.colors.hoverBg.color
            alt -> t.colors.paneAltRow.color
            else -> t.colors.paneBg.color
        }
    Row(
        Modifier
            .fillMaxWidth()
            .height(rowHeight())
            .hoverable(hover)
            .background(background)
            .then(modifier)
            .semantics { selected = selection != null }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FileIcon(rememberCategory(entry), 20.dp)
        Text(
            entry.name,
            color = selection?.content ?: t.colors.fg.color,
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

/** Colores de un elemento seleccionado: acento en el panel activo, apagado en el otro (THEMES.md). */
class SelectionColors(
    val background: Color,
    val content: Color,
)

@Composable
fun selectionColors(
    selected: Boolean,
    activePane: Boolean,
): SelectionColors? {
    if (!selected) return null
    val c = TlacuacheTheme.tokens.colors
    return if (activePane) {
        SelectionColors(c.selectionBg.color, c.selectionFg.color)
    } else {
        SelectionColors(c.selectionInactiveBg.color, c.fg.color)
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
