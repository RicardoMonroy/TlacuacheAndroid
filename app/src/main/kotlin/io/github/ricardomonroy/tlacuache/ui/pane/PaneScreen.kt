package io.github.ricardomonroy.tlacuache.ui.pane

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
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
import io.github.ricardomonroy.tlacuache.MainActivity
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import io.github.ricardomonroy.tlacuache.ffi.FilterKey
import io.github.ricardomonroy.tlacuache.ffi.OpKind
import io.github.ricardomonroy.tlacuache.ffi.OpRequest
import io.github.ricardomonroy.tlacuache.ffi.nextFreeName
import io.github.ricardomonroy.tlacuache.intents.FileIntents
import io.github.ricardomonroy.tlacuache.ops.Clipboard
import io.github.ricardomonroy.tlacuache.state.BackStep
import io.github.ricardomonroy.tlacuache.state.PaneState
import io.github.ricardomonroy.tlacuache.state.PathInput
import io.github.ricardomonroy.tlacuache.state.TabState
import io.github.ricardomonroy.tlacuache.storage.PreferencesStore
import io.github.ricardomonroy.tlacuache.ui.Shortcut
import io.github.ricardomonroy.tlacuache.ui.WithTooltip
import io.github.ricardomonroy.tlacuache.ui.ops.DeleteConfirmation
import io.github.ricardomonroy.tlacuache.ui.ops.NameDialog
import io.github.ricardomonroy.tlacuache.ui.theme.ThemeDialog
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import io.github.ricardomonroy.tlacuache.ui.theme.headerDisabled
import kotlinx.coroutines.launch
import java.io.File

/**
 * Un panel: su tira de pestañas y la pestaña activa (ARCHITECTURE §5). Tocarlo o darle el foco lo
 * activa; [switcher] es el alternador A/B cuando solo se ve un panel. [dual] es el estado del modo
 * dual para el menú, o `null` si la ventana es compacta y no caben dos.
 */
@Composable
fun PaneScreen(
    pane: PaneState,
    pathInput: PathInput,
    active: Boolean,
    onActivate: () -> Unit,
    dual: Boolean?,
    onToggleDual: () -> Unit,
    onAddFavorite: (File) -> Unit,
    onTransfer: (OpKind, List<File>) -> Unit,
    onRemove: (OpKind, List<File>) -> Unit,
    onRun: (OpRequest) -> Unit,
    onDrop: (List<File>, File) -> Unit,
    modifier: Modifier = Modifier,
    /** Letra del otro panel cuando solo se ve uno: la barra de la selección dice «Copiar a B». */
    otherPane: String? = null,
    switcher: (@Composable () -> Unit)? = null,
    /** El otro panel al lado (modo dual), adonde se arrastran pestañas (A4.7). */
    side: SidePane? = null,
) {
    val tabs by pane.tabs.collectAsStateWithLifecycle()
    val activate by rememberUpdatedState(onActivate)
    Box(
        modifier.pointerInput(Unit) {
            // Cualquier toque activa el panel, sin consumirlo: la fila o el botón tocado sigue funcionando.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                activate()
            }
        },
    ) {
        // Cada pestaña tiene su propio estado de UI (ruta en edición, filtro abierto, desplazamiento).
        key(tabs.current) {
            TabScreen(
                tabs.current,
                pathInput,
                Chrome(
                    active,
                    accent = switcher == null,
                    switcher,
                    dual,
                    onToggleDual,
                    activate,
                    onAddFavorite,
                    onTransfer,
                    onRemove,
                    onRun,
                    onDrop,
                    otherPane,
                ),
                tabStrip = { TabStrip(tabs, pane, side) },
                tabMenu = { TabMenuButton(tabs, pane) },
                paneKeys = { handleTabShortcut(it, pane) },
            )
        }
    }
}

/** Lo que la ventana decide sobre un panel: si está activo, su acento, el alternador y el modo dual. */
private class Chrome(
    val active: Boolean,
    val accent: Boolean,
    val switcher: (@Composable () -> Unit)?,
    val dual: Boolean?,
    val onToggleDual: () -> Unit,
    val onActivate: () -> Unit,
    val onAddFavorite: (File) -> Unit,
    /** Copiar o mover al otro panel (A3.2; la franja central llega en A3.3). */
    val onTransfer: (OpKind, List<File>) -> Unit,
    /** Papelera o borrado permanente (este, ya confirmado). */
    val onRemove: (OpKind, List<File>) -> Unit,
    /** Renombrar y crear (A3.7). */
    val onRun: (OpRequest) -> Unit,
    /** Soltar archivos arrastrados en una carpeta (A4.3); la ventana decide copiar o mover. */
    val onDrop: (List<File>, File) -> Unit,
    val otherPane: String?,
)

/** Contenido de una pestaña. Relee al volver a primer plano y vigila su carpeta (ARCHITECTURE §6). */
@Composable
private fun TabScreen(
    tab: TabState,
    pathInput: PathInput,
    chrome: Chrome,
    tabStrip: @Composable () -> Unit,
    tabMenu: @Composable () -> Unit,
    paneKeys: (Shortcut) -> Boolean,
) {
    // Con poca altura (teléfono en horizontal) el encabezado se reduce a una fila: el contenido manda.
    val window = windowSize()
    val shortHeight = window.height < SHORT_HEIGHT
    // Con un solo panel en compacta, la tira de pestañas cede su sitio al alternador A/B.
    val compactWidth = window.width < MEDIUM_WIDTH
    val ui by tab.ui.collectAsStateWithLifecycle()
    val t = TlacuacheTheme.tokens
    // En primer plano se vigila la carpeta; al volver a vigilar, la pestaña relee si pudo perderse algo.
    LifecycleResumeEffect(tab) {
        tab.startWatching()
        onPauseOrDispose { tab.stopWatching() }
    }

    var editingPath by rememberSaveable { mutableStateOf(false) }
    // Campo del filtro abierto con el botón de buscar (táctil); con teclado físico basta con escribir.
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    val openPreferences = LocalOpenPreferences.current
    val confirmTrash by PreferencesStore.shared(LocalContext.current).general.collectAsStateWithLifecycle()
    var trashTargets by remember { mutableStateOf<List<File>?>(null) }
    // Borrar permanentemente siempre pide confirmación (CLAUDE.md, regla 6).
    var confirmDelete by remember { mutableStateOf<List<File>?>(null) }
    // Diálogo de nombre abierto: renombrar este elemento, o crear una carpeta o un archivo vacío.
    var naming by remember { mutableStateOf<Naming?>(null) }
    var properties by remember { mutableStateOf<List<File>?>(null) }
    val pathLabel = stringResource(R.string.action_copy_path)
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noApp = stringResource(R.string.open_no_app)
    val openWithTitle = stringResource(R.string.action_open_with)
    val shareTitle = stringResource(R.string.action_share)
    // Carpetas: se entra. Archivos: se abren o comparten con otras apps (A1.6). Sobre un elemento seleccionado,
    // copiar, mover y compartir actúan sobre toda la selección.
    val protectedMessage = stringResource(R.string.ops_failed_protected)
    val act: (FileEntry, FileAction) -> Unit = act@{ entry, action ->
        val file = File(ui.dir, entry.name)
        val targets = (if (entry.name in ui.selected) ui.selection else listOf(entry)).map { File(ui.dir, it.name) }
        val removes =
            action == FileAction.CUT ||
                action == FileAction.MOVE_TO_OTHER ||
                action == FileAction.RENAME ||
                action == FileAction.TRASH ||
                action == FileAction.DELETE
        // Teclas y menús pasan por aquí: una carpeta protegida no se mueve ni se borra (ADR-A06).
        if (removes && targets.any { ui.isProtected(it.name) }) {
            scope.launch { snackbar.showSnackbar(protectedMessage) }
            return@act
        }
        val started =
            when {
                action == FileAction.COPY_TO_OTHER || action == FileAction.MOVE_TO_OTHER -> {
                    chrome.onTransfer(if (action == FileAction.COPY_TO_OTHER) OpKind.COPY else OpKind.MOVE, targets)
                    tab.clearSelection()
                    true
                }

                action == FileAction.OPEN_IN_NEW_WINDOW -> {
                    MainActivity.openWindow(context, file)
                    true
                }

                action == FileAction.PASTE -> {
                    Clipboard.paste(file)?.let(chrome.onRun)
                    true
                }

                action == FileAction.COPY_PATH -> {
                    // Rutas al portapapeles del sistema, una por línea, para pegarlas en otra app.
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                        ClipData.newPlainText(pathLabel, targets.joinToString("\n") { it.path }),
                    )
                    tab.clearSelection()
                    true
                }

                action == FileAction.PROPERTIES -> {
                    properties = targets
                    true
                }

                action == FileAction.COPY || action == FileAction.CUT -> {
                    Clipboard.set(targets, cut = action == FileAction.CUT)
                    tab.clearSelection()
                    true
                }

                action == FileAction.TRASH -> {
                    // `confirm_trash` de config.toml: preguntar antes (se puede restaurar, así que por defecto no).
                    if (confirmTrash.confirmTrash) {
                        trashTargets = targets
                    } else {
                        chrome.onRemove(OpKind.TRASH, targets)
                        tab.clearSelection()
                    }
                    true
                }

                action == FileAction.DELETE -> {
                    confirmDelete = targets
                    true
                }

                action == FileAction.RENAME -> {
                    naming = Naming(OpKind.RENAME, entry.name, entry.isDir)
                    true
                }

                entry.isDir && action == FileAction.ADD_FAVORITE -> {
                    chrome.onAddFavorite(file)
                    true
                }

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
                    FileIntents.share(context, targets.filter(File::isFile), shareTitle)
                }
            }
        if (!started) scope.launch { snackbar.showSnackbar(noApp) }
    }
    val currentAct by rememberUpdatedState(act)
    properties?.let { PropertiesDialog(it) { properties = null } }
    naming?.let { n ->
        NameDialog(
            n.title,
            n.initial,
            n.isDir,
            ui.dir,
            n.kind == OpKind.RENAME,
            onDismiss = { naming = null },
        ) { name ->
            val target = File(ui.dir, name)
            chrome.onRun(
                when (n.kind) {
                    OpKind.RENAME -> {
                        OpRequest(
                            OpKind.RENAME,
                            listOf(File(ui.dir, n.initial).path),
                            target.path,
                            emptyList(),
                        )
                    }

                    else -> {
                        OpRequest(n.kind, listOf(target.path), null, emptyList())
                    }
                },
            )
            tab.clearSelection()
            naming = null
        }
    }
    val newFolderName = stringResource(R.string.new_folder_name)
    val newFileName = stringResource(R.string.new_file_name)
    // Nombre libre en esta carpeta («Carpeta nueva (2)»), con las reglas del core.
    val startNew: (Boolean) -> Unit = { directory ->
        val base = if (directory) newFolderName else newFileName
        val name = nextFreeName(base, directory, ui.visible.map { it.name })
        naming = Naming(if (directory) OpKind.MKDIR else OpKind.CREATE_FILE, name, directory)
    }
    trashTargets?.let { targets ->
        DeleteConfirmation(
            targets,
            R.string.trash_confirm_title,
            text = R.plurals.trash_confirm_text,
            confirm = R.string.trash_confirm,
            danger = false,
            onDismiss = { trashTargets = null },
        ) {
            chrome.onRemove(OpKind.TRASH, targets)
            tab.clearSelection()
            trashTargets = null
        }
    }
    confirmDelete?.let { targets ->
        DeleteConfirmation(targets, onDismiss = { confirmDelete = null }) {
            chrome.onRemove(OpKind.DELETE, targets)
            tab.clearSelection()
            confirmDelete = null
        }
    }
    val gestures = remember(tab) { ItemGestures(tab) { currentAct(it, FileAction.OPEN) } }
    // Arrastrar (UI_SPEC §3): lo seleccionado si el elemento es parte de ello; si no, ese elemento. Con el dedo,
    // la pulsación larga sobre algo sin seleccionar además lo selecciona (entra al modo selección): si se suelta
    // sin mover, solo queda seleccionado.
    val drag =
        ItemDrag(ui.dir, files = { entry ->
            when {
                entry.name in ui.selected -> ui.selection.map { File(ui.dir, it.name) }
                gestures.mouse -> listOf(File(ui.dir, entry.name))
                else -> listOf(File(ui.dir, entry.name)).also { tab.toggle(entry.name) }
            }
        }, onDrop = chrome.onDrop)
    // Gesto atrás y Esc (UI_SPEC §3): la ruta en edición o el campo de filtro vacío cuentan como menú abierto.
    val backStep =
        BackStep.of(
            menuOpen = editingPath || (filterOpen && ui.query.isEmpty()),
            filterActive = ui.query.isNotEmpty(),
            selectionActive = ui.selecting,
            canGoBack = ui.canGoBack,
        )
    // Con dos paneles, el gesto atrás es del activo.
    BackHandler(enabled = chrome.active && backStep != BackStep.EXIT) {
        when (backStep) {
            BackStep.CLOSE_MENU -> {
                editingPath = false
                filterOpen = false
            }

            BackStep.CLEAR_FILTER -> {
                tab.setQuery("")
                filterOpen = false
            }

            BackStep.CLEAR_SELECTION -> {
                tab.clearSelection()
            }

            BackStep.HISTORY_BACK -> {
                tab.back()
            }

            else -> {}
        }
    }

    /** Atajos de la pestaña (UI_SPEC §8); `false` si no aplica aquí y debe seguir a la ventana. */
    fun runShortcut(shortcut: Shortcut): Boolean {
        val single = ui.selection.singleOrNull()
        when (shortcut) {
            Shortcut.EDIT_PATH -> {
                editingPath = true
            }

            Shortcut.RELOAD -> {
                tab.reload()
            }

            Shortcut.TOGGLE_HIDDEN -> {
                tab.toggleHidden()
            }

            Shortcut.LIST_VIEW, Shortcut.ICON_VIEW -> {
                tab.setIconView(shortcut == Shortcut.ICON_VIEW)
            }

            Shortcut.BACK -> {
                tab.back()
            }

            Shortcut.FORWARD -> {
                tab.forward()
            }

            Shortcut.SELECT_ALL -> {
                tab.selectAll()
            }

            Shortcut.NEW_FOLDER, Shortcut.NEW_FILE -> {
                startNew(shortcut == Shortcut.NEW_FOLDER)
            }

            Shortcut.RENAME -> {
                single?.let { currentAct(it, FileAction.RENAME) } ?: return false
            }

            Shortcut.COPY, Shortcut.CUT, Shortcut.TRASH, Shortcut.DELETE -> {
                if (!ui.selecting) return false
                val action =
                    when (shortcut) {
                        Shortcut.COPY -> FileAction.COPY
                        Shortcut.CUT -> FileAction.CUT
                        Shortcut.TRASH -> FileAction.TRASH
                        else -> FileAction.DELETE
                    }
                currentAct(ui.selection.first(), action)
            }

            else -> {
                return false
            }
        }
        return true
    }

    // El panel recibe el foco para que los atajos funcionen sin tocar nada antes.
    val focus = remember { FocusRequester() }
    var paneHasFocus by remember { mutableStateOf(false) }
    // Tocar un control que luego desaparece (una miga) deja la ventana sin foco y los atajos dejarían de llegar.
    // Solo el panel activo lo recupera; así Tab y los toques mueven el foco al otro.
    LaunchedEffect(paneHasFocus, chrome.active) { if (chrome.active && !paneHasFocus) focus.requestFocus() }
    // Con el foco en el panel mismo, ↑/↓ lo pasan a la lista (si no, las flechas no entran a las filas).
    val focusManager = LocalFocusManager.current
    var paneFocused by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(t.colors.paneBg.color)
            // Botones laterales del ratón: atrás y adelante en el historial de la pestaña (UI_SPEC §3).
            .pointerInput(tab) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.type != PointerEventType.Press) continue
                        when {
                            e.buttons.isBackPressed -> tab.back()
                            e.buttons.isForwardPressed -> tab.forward()
                            else -> continue
                        }
                        e.changes.forEach { it.consume() }
                    }
                }
            }.onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                // Con el foco en el panel mismo, ↑/↓ entran a las filas.
                if (paneFocused && (event.key == Key.DirectionDown || event.key == Key.DirectionUp)) {
                    return@onKeyEvent focusManager.moveFocus(FocusDirection.Enter)
                }
                val shortcut = Shortcut.of(event)
                when {
                    // Backspace sube solo si el filtro no lo usa (ADR-006): se atiende después del filtro.
                    shortcut != null && shortcut != Shortcut.UP && (paneKeys(shortcut) || runShortcut(shortcut)) -> {
                        true
                    }

                    // Esc limpia primero el filtro (abajo) y, sin filtro, la selección (UI_SPEC §3).
                    event.key == Key.Escape && ui.query.isEmpty() && ui.selecting -> {
                        tab.clearSelection()
                        true
                    }

                    // Escribir filtra; Backspace, Espacio y Esc editan el filtro solo si tiene texto (ADR-006).
                    filterKey(event)?.let(tab::applyFilterKey) == true -> {
                        true
                    }

                    shortcut == Shortcut.UP -> {
                        tab.up()
                        true
                    }

                    // Lo de la ventana (Tab, F3, F5…) sigue hacia arriba.
                    else -> {
                        false
                    }
                }
            }.focusRequester(focus)
            .onFocusChanged {
                paneFocused = it.isFocused
                paneHasFocus = it.hasFocus
                if (it.hasFocus) chrome.onActivate()
            }.focusable(),
    ) {
        Column(
            Modifier.fillMaxWidth().background(t.colors.headerBg.color).windowInsetsPadding(WindowInsets.statusBars),
        ) {
            // Acento del panel activo con dos paneles (UI_SPEC §2).
            if (chrome.accent) {
                val line = if (chrome.active) t.colors.paneActiveBorder else t.colors.headerBg
                Box(Modifier.fillMaxWidth().height(2.dp).background(line.color))
            }
            when {
                shortHeight -> {}

                chrome.switcher == null -> {
                    tabStrip()
                }

                else -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        chrome.switcher()
                        if (compactWidth) {
                            Spacer(Modifier.weight(1f))
                            tabMenu()
                        } else {
                            Box(Modifier.weight(1f)) { tabStrip() }
                        }
                    }
                }
            }
        }
        // Con selección, la fila de navegación cede su sitio a la barra de la selección (UI_SPEC §4).
        if (ui.selecting) {
            SelectionBar(
                ui,
                tab,
                Modifier.padding(vertical = if (shortHeight) 0.dp else 8.dp),
            ) { act(ui.selection.first(), it) }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(t.colors.headerBg.color)
                    .padding(horizontal = 4.dp, vertical = if (shortHeight) 0.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavButton("‹", R.string.nav_back, ui.canGoBack, Shortcut.BACK) { tab.back() }
                NavButton("›", R.string.nav_forward, ui.canGoForward, Shortcut.FORWARD) { tab.forward() }
                NavButton("↑", R.string.nav_up, ui.canGoUp, Shortcut.UP) { tab.up() }
                PathBar(
                    ui,
                    tab,
                    pathInput,
                    editingPath,
                    { editingPath = it },
                    Modifier.padding(start = 4.dp).weight(1f),
                )
                if (shortHeight) {
                    chrome.switcher?.invoke()
                    tabMenu()
                }
                val filterLabel = stringResource(R.string.filter_open)
                WithTooltip(filterLabel, null) {
                    IconButton({ filterOpen = true }, Modifier.semantics { contentDescription = filterLabel }) {
                        SearchGlyph(t.colors.headerFg.color)
                    }
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
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_icon_view)) },
                            trailingIcon = { Checkbox(checked = ui.iconView, onCheckedChange = null) },
                            onClick = {
                                moreMenu = false
                                tab.setIconView(!ui.iconView)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_window)) },
                            onClick = {
                                moreMenu = false
                                MainActivity.openWindow(context, ui.dir)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_folder)) },
                            onClick = {
                                moreMenu = false
                                startNew(true)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_file)) },
                            onClick = {
                                moreMenu = false
                                startNew(false)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.favorite_add)) },
                            onClick = {
                                moreMenu = false
                                chrome.onAddFavorite(ui.dir)
                            },
                        )
                        chrome.dual?.let { dual ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_dual_pane)) },
                                trailingIcon = { Checkbox(checked = dual, onCheckedChange = null) },
                                onClick = {
                                    moreMenu = false
                                    chrome.onToggleDual()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.theme_menu)) },
                            onClick = {
                                moreMenu = false
                                themeDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.prefs_menu)) },
                            onClick = {
                                moreMenu = false
                                openPreferences()
                            },
                        )
                    }
                }
                if (themeDialog) ThemeDialog { themeDialog = false }
            }
        }
        // Soltar en la lista (fuera de una carpeta) deja los archivos en la carpeta de la pestaña.
        var dropHere by remember { mutableStateOf(false) }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .fileDropTarget(ui.dir, chrome.onDrop) { dropHere = it }
                .then(if (dropHere) Modifier.border(2.dp, t.colors.accent.color) else Modifier),
        ) {
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
                    key(ui.dir, ui.iconView) {
                        if (ui.iconView) {
                            FileGrid(
                                ui.dir,
                                ui.visible,
                                selected = ui.selected,
                                activePane = chrome.active,
                                isProtected = ui::isProtected,
                                gestures = gestures,
                                drag = drag,
                                onAction = act,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            FileList(
                                ui.visible,
                                ui.nowMs,
                                selected = ui.selected,
                                activePane = chrome.active,
                                isProtected = ui::isProtected,
                                gestures = gestures,
                                drag = drag,
                                onAction = act,
                                sort = ui.sort,
                                onSort = tab::sortBy,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
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
                            .padding(bottom = 16.dp),
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
        // Con un solo panel no hay franja central: la barra inferior lleva la selección al otro (UI_SPEC §4).
        if (ui.selecting && chrome.otherPane != null) {
            SelectionActions(ui, chrome.otherPane) { act(ui.selection.first(), it) }
        }
        StatusBar(ui, showFreeSpace = !compactWidth)
    }
}

/** Por debajo de esta altura de ventana el encabezado ocupa una sola fila. */
internal val SHORT_HEIGHT = 480.dp

/** Pestañas del panel: Ctrl+T, Ctrl+W y Ctrl+Tab / Ctrl+Shift+Tab (UI_SPEC §8). */
private fun handleTabShortcut(
    shortcut: Shortcut,
    pane: PaneState,
): Boolean {
    when (shortcut) {
        Shortcut.NEW_TAB -> pane.openTab()
        Shortcut.CLOSE_TAB -> pane.closeTab()
        Shortcut.NEXT_TAB -> pane.cycle(1)
        Shortcut.PREVIOUS_TAB -> pane.cycle(-1)
        else -> return false
    }
    return true
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

/** Botón de navegación con glifo; deshabilitado se atenúa. */
@Composable
private fun NavButton(
    glyph: String,
    @StringRes label: Int,
    enabled: Boolean,
    shortcut: Shortcut? = null,
    onClick: () -> Unit,
) {
    val t = TlacuacheTheme.tokens
    val description = stringResource(label)
    WithTooltip(description, shortcut) {
        IconButton(
            onClick,
            enabled = enabled,
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).semantics { contentDescription = description },
        ) {
            Text(
                glyph,
                color = if (enabled) t.colors.headerFg.color else t.colors.headerDisabled,
                style = MaterialTheme.typography.titleLarge,
            )
        }
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

/** Diálogo de nombre: renombrar [initial], o crear con ese nombre propuesto. */
private class Naming(
    val kind: OpKind,
    val initial: String,
    val isDir: Boolean,
) {
    val title: Int
        get() =
            when (kind) {
                OpKind.RENAME -> R.string.action_rename
                OpKind.MKDIR -> R.string.new_folder
                else -> R.string.new_file
            }
}
