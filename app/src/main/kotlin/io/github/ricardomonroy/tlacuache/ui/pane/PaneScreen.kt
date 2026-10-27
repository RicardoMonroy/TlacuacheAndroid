package io.github.ricardomonroy.tlacuache.ui.pane

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import io.github.ricardomonroy.tlacuache.ffi.FilterKey
import io.github.ricardomonroy.tlacuache.intents.FileIntents
import io.github.ricardomonroy.tlacuache.state.BackStep
import io.github.ricardomonroy.tlacuache.state.PathInput
import io.github.ricardomonroy.tlacuache.state.TabState
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import kotlinx.coroutines.launch
import java.io.File

/** Un panel con su carpeta actual. Relee al volver a primer plano (ARCHITECTURE §6). */
@Composable
fun PaneScreen(
    tab: TabState,
    pathInput: PathInput,
) {
    val ui by tab.ui.collectAsStateWithLifecycle()
    val t = TlacuacheTheme.tokens
    // En primer plano se vigila la carpeta y se relee al volver (lo cambiado mientras tanto no generó
    // eventos). La primera reanudación no relee: la pestaña ya leyó al crearse o sobrevivió a una rotación.
    val resumed = remember { booleanArrayOf(false) }
    LifecycleResumeEffect(tab) {
        if (resumed[0]) tab.reload()
        resumed[0] = true
        tab.startWatching()
        onPauseOrDispose { tab.stopWatching() }
    }

    var editingPath by rememberSaveable { mutableStateOf(false) }
    // Campo del filtro abierto con el botón de buscar (táctil); con teclado físico basta con escribir.
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noApp = stringResource(R.string.open_no_app)
    val openWithTitle = stringResource(R.string.action_open_with)
    val shareTitle = stringResource(R.string.action_share)
    // Carpetas: se entra. Archivos: se abren o comparten con otras apps (A1.6).
    val act: (FileEntry, FileAction) -> Unit = { entry, action ->
        val file = File(ui.dir, entry.name)
        val started =
            when {
                entry.isDir -> {
                    tab.open(entry)
                    true
                }

                action == FileAction.OPEN -> {
                    FileIntents.open(context, file, openWithTitle)
                }

                action == FileAction.OPEN_WITH -> {
                    FileIntents.openWith(context, file, openWithTitle)
                }

                else -> {
                    FileIntents.share(context, listOf(file), shareTitle)
                }
            }
        if (!started) scope.launch { snackbar.showSnackbar(noApp) }
    }
    // Gesto atrás y Esc (UI_SPEC §3): la ruta en edición o el campo de filtro vacío cuentan como menú abierto.
    // La selección llega en A3.4.
    val backStep =
        BackStep.of(
            menuOpen = editingPath || (filterOpen && ui.query.isEmpty()),
            filterActive = ui.query.isNotEmpty(),
            selectionActive = false,
            canGoBack = ui.canGoBack,
        )
    BackHandler(enabled = backStep != BackStep.EXIT) {
        when (backStep) {
            BackStep.CLOSE_MENU -> {
                editingPath = false
                filterOpen = false
            }

            BackStep.CLEAR_FILTER -> {
                tab.setQuery("")
                filterOpen = false
            }

            BackStep.HISTORY_BACK -> {
                tab.back()
            }

            else -> {}
        }
    }
    // El panel recibe el foco para que los atajos funcionen sin tocar nada antes.
    val focus = remember { FocusRequester() }
    var paneHasFocus by remember { mutableStateOf(false) }
    // Tocar un control que luego desaparece (una miga) deja la ventana sin foco y los atajos dejarían de llegar.
    // shortcut: con un solo panel basta recuperarlo; con dos paneles (A2.2) debe decidirlo la ventana.
    LaunchedEffect(paneHasFocus) { if (!paneHasFocus) focus.requestFocus() }
    // Con el foco en el panel mismo, ↑/↓ lo pasan a la lista (si no, las flechas no entran a las filas).
    val focusManager = LocalFocusManager.current
    var paneFocused by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(t.colors.paneBg.color)
            .onKeyEvent { event ->
                when {
                    event.type != KeyEventType.KeyDown -> {
                        false
                    }

                    paneFocused && (event.key == Key.DirectionDown || event.key == Key.DirectionUp) -> {
                        focusManager.moveFocus(FocusDirection.Enter)
                    }

                    event.isCtrlPressed && event.key == Key.L -> {
                        editingPath = true
                        true
                    }

                    event.isCtrlPressed && event.key == Key.H -> {
                        tab.toggleHidden()
                        true
                    }

                    // Escribir filtra; Backspace, Espacio y Esc editan el filtro solo si tiene texto (ADR-006).
                    filterKey(event)?.let(tab::applyFilterKey) == true -> {
                        true
                    }

                    else -> {
                        handleNavigationKey(event, tab)
                    }
                }
            }.focusRequester(focus)
            .onFocusChanged {
                paneFocused = it.isFocused
                paneHasFocus = it.hasFocus
            }.focusable(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(t.colors.headerBg.color)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavButton("‹", R.string.nav_back, ui.canGoBack) { tab.back() }
            NavButton("›", R.string.nav_forward, ui.canGoForward) { tab.forward() }
            NavButton("↑", R.string.nav_up, ui.canGoUp) { tab.up() }
            // shortcut: conteo bajo la ruta hasta la barra de estado (A2.5).
            Column(Modifier.padding(start = 4.dp).weight(1f)) {
                PathBar(ui, tab, pathInput, editingPath, { editingPath = it }, Modifier.fillMaxWidth())
                if (!editingPath) {
                    Text(
                        pluralStringResource(R.plurals.pane_item_count, ui.visible.size, ui.visible.size),
                        color = t.colors.fgMuted.color,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            val filterLabel = stringResource(R.string.filter_open)
            IconButton({ filterOpen = true }, Modifier.semantics { contentDescription = filterLabel }) {
                SearchGlyph(t.colors.headerFg.color)
            }
            Box {
                NavButton("⋮", R.string.menu_more, enabled = true) { moreMenu = true }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_show_hidden)) },
                        trailingIcon = { Checkbox(checked = ui.showHidden, onCheckedChange = null) },
                        onClick = {
                            moreMenu = false
                            tab.toggleHidden()
                        },
                    )
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                ui.failed -> {
                    Message(stringResource(R.string.pane_read_error)) { tab.reload() }
                }

                !ui.loading && ui.visible.isEmpty() -> {
                    Message(
                        stringResource(if (ui.query.isEmpty()) R.string.pane_empty else R.string.filter_no_matches),
                        null,
                    )
                }

                else -> {
                    // Cada carpeta empieza arriba: el estado de desplazamiento es por carpeta.
                    key(ui.dir) {
                        FileList(
                            ui.visible,
                            ui.nowMs,
                            onOpen = { act(it, FileAction.OPEN) },
                            onAction = act,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                        )
                    }
                }
            }
            if (ui.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
            if (ui.query.isNotEmpty() || filterOpen) {
                QuickFilter(
                    query = ui.query,
                    matches = ui.visible.size,
                    editable = filterOpen,
                    onQueryChange = tab::setQuery,
                    onClose = {
                        tab.setQuery("")
                        filterOpen = false
                    },
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 16.dp),
                )
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }
}

/** Tecla del filtro rápido, o `null` si lleva Ctrl, Alt o Meta (es un atajo) o no escribe nada. */
private fun filterKey(event: KeyEvent): FilterKey? {
    if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return null
    return when (event.key) {
        Key.Backspace -> {
            FilterKey.Backspace
        }

        Key.Escape -> {
            FilterKey.Escape
        }

        else -> {
            event.utf16CodePoint
                .takeIf {
                    it > 0 && !Character.isISOControl(it)
                }?.let { FilterKey.Char(String(Character.toChars(it))) }
        }
    }
}

/** Backspace y Alt+↑ suben; Alt+← y Alt+→ recorren el historial; Ctrl+R relee (UI_SPEC §8). */
private fun handleNavigationKey(
    event: KeyEvent,
    tab: TabState,
): Boolean {
    when {
        event.isCtrlPressed && event.key == Key.R -> tab.reload()
        event.key == Key.Backspace && !event.isAltPressed -> tab.up()
        event.isAltPressed && event.key == Key.DirectionUp -> tab.up()
        event.isAltPressed && event.key == Key.DirectionLeft -> tab.back()
        event.isAltPressed && event.key == Key.DirectionRight -> tab.forward()
        else -> return false
    }
    return true
}

/** Botón de navegación con glifo; deshabilitado se atenúa. */
@Composable
private fun NavButton(
    glyph: String,
    @StringRes label: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val t = TlacuacheTheme.tokens
    val description = stringResource(label)
    IconButton(onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = description }) {
        Text(
            glyph,
            color = if (enabled) t.colors.headerFg.color else t.colors.fgMuted.color,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun Message(
    text: String,
    onRetry: (() -> Unit)?,
) {
    val t = TlacuacheTheme.tokens
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = t.colors.fgMuted.color, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Button(onClick = onRetry) { Text(stringResource(R.string.pane_retry)) }
        }
    }
}
