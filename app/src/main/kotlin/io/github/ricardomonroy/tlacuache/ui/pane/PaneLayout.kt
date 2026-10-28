package io.github.ricardomonroy.tlacuache.ui.pane

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ricardomonroy.tlacuache.MainActivity
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.OpKind
import io.github.ricardomonroy.tlacuache.ffi.OpRequest
import io.github.ricardomonroy.tlacuache.ops.Clipboard
import io.github.ricardomonroy.tlacuache.ops.OpsRepository
import io.github.ricardomonroy.tlacuache.state.WindowViewModel
import io.github.ricardomonroy.tlacuache.storage.ThemeStore
import io.github.ricardomonroy.tlacuache.storage.Volumes
import io.github.ricardomonroy.tlacuache.ui.Shortcut
import io.github.ricardomonroy.tlacuache.ui.WithTooltip
import io.github.ricardomonroy.tlacuache.ui.ops.ClipboardPill
import io.github.ricardomonroy.tlacuache.ui.ops.OpsConflictDialog
import io.github.ricardomonroy.tlacuache.ui.ops.OpsIndicator
import io.github.ricardomonroy.tlacuache.ui.ops.finishedMessage
import io.github.ricardomonroy.tlacuache.ui.prefs.PreferencesScreen
import io.github.ricardomonroy.tlacuache.ui.sidebar.Sidebar
import io.github.ricardomonroy.tlacuache.ui.sidebar.SidebarItems
import io.github.ricardomonroy.tlacuache.ui.sidebar.SidebarRail
import io.github.ricardomonroy.tlacuache.ui.sidebar.SidebarWidth
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import io.github.ricardomonroy.tlacuache.ui.theme.headerDisabled
import io.github.ricardomonroy.tlacuache.ui.trash.TrashScreen
import kotlinx.coroutines.launch
import java.io.File

/** Desde este ancho de ventana caben dos paneles (clase media, UI_SPEC §1). */
internal val MEDIUM_WIDTH = 600.dp

/** Ancho mínimo de un panel con dos paneles: seis botones de 48 dp y ~110 dp de ruta. */
private val MIN_PANE_WIDTH = 400.dp

/** Tamaño de la ventana actual (no de la pantalla): en modo escritorio cambia al redimensionar. */
@Composable
internal fun windowSize(): DpSize =
    with(
        LocalDensity.current,
    ) { LocalWindowInfo.current.containerSize.let { DpSize(it.width.toDp(), it.height.toDp()) } }

/** Desde este ancho, la barra lateral es permanente (clase expandida, UI_SPEC §1). */
private val EXPANDED_WIDTH = 840.dp

/**
 * Layout de la ventana (UI_SPEC §1): barra lateral según el ancho (cajón en compacta, riel en media,
 * permanente en expandida) y, en media y expandida, paneles A y B con divisor arrastrable; en
 * compacta o con el modo dual apagado, solo el activo con el alternador A/B. Cambiar de layout no toca
 * el estado: pestañas y carpetas viven en [WindowViewModel].
 */
@Composable
fun PaneLayout(window: WindowViewModel) {
    val active by window.activePane.collectAsStateWithLifecycle()
    val dual by window.dual.collectAsStateWithLifecycle()
    val split by window.split.collectAsStateWithLifecycle()
    val collapsed by window.sidebarCollapsed.collectAsStateWithLifecycle()
    val size = windowSize()
    val wide = size.width >= MEDIUM_WIDTH
    // Con poca altura (teléfono horizontal) la barra permanente quitaría sitio a los paneles: riel.
    val permanentFits = size.width >= EXPANDED_WIDTH && size.height >= SHORT_HEIGHT
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val toggleDrawer: () -> Unit = { scope.launch { if (drawer.isOpen) drawer.close() else drawer.open() } }
    // El gesto atrás cierra el cajón antes que nada; mientras está abierto ningún panel lo atiende.
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
    var showTrash by rememberSaveable { mutableStateOf(false) }
    var showPrefs by rememberSaveable { mutableStateOf(false) }
    val t = TlacuacheTheme.tokens
    var rowWidth by remember { mutableFloatStateOf(1f) }
    // Cada panel conserva sitio para botones y ruta; en un teléfono horizontal el divisor apenas se mueve.
    val minSplit = (with(LocalDensity.current) { MIN_PANE_WIDTH.toPx() } / rowWidth).coerceAtMost(0.5f)
    val shownSplit = split.coerceIn(minSplit, 1 - minSplit)
    val context = LocalContext.current
    // Las operaciones avisan por notificación (ADR-A09): se pide el permiso al encolar la primera.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val askNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val transfer: (OpKind, List<File>, Int) -> Unit = { kind, files, target ->
        askNotifications()
        window.transfer(kind, files, target)
    }
    val remove: (OpKind, List<File>) -> Unit = { kind, files ->
        askNotifications()
        window.remove(kind, files)
    }
    val run: (OpRequest) -> Unit = { request ->
        askNotifications()
        OpsRepository.enqueue(context, request)
    }
    // Soltar archivos arrastrados (A4.3): mover en el mismo volumen, copiar entre volúmenes; Ctrl/Shift fuerzan.
    val drop: (List<File>, File) -> Unit = { files, dest ->
        run(OpRequest(dropKind(files, dest, Volumes.roots(context)), files.map { it.path }, dest.path, emptyList()))
    }
    if (showTrash) TrashScreen(onClose = { showTrash = false }, run = remove)
    if (showPrefs) PreferencesScreen(window) { showPrefs = false }
    val items =
        sidebarItems(window, active, onDrop = drop, onTrash = {
            showTrash = true
            scope.launch { drawer.close() }
        }) { dir -> window.openInActive(dir).also { scope.launch { drawer.close() } } }
    // Franja central y F5/F6: la selección del panel activo al otro (UI_SPEC §4).
    val activeTabs by window.panes[active].tabs.collectAsStateWithLifecycle()
    // Pegar el portapapeles interno en la carpeta de la pestaña activa del panel activo.
    val paste = { Clipboard.paste(activeTabs.current.ui.value.dir)?.let(run) }
    val activeUi by activeTabs.current.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val protectedMessage = stringResource(R.string.ops_failed_protected)
    val transferSelection: (OpKind) -> Unit = transfer@{ kind ->
        val ui = activeTabs.current.ui.value
        // F6 sobre una carpeta protegida: aviso, sin encolar (ADR-A06).
        if (kind == OpKind.MOVE && ui.selectionProtected) {
            scope.launch { snackbar.showSnackbar(protectedMessage) }
            return@transfer
        }
        transfer(kind, ui.selection.map { File(ui.dir, it.name) }, 1 - active)
        activeTabs.current.clearSelection()
    }
    OpsConflictDialog()
    val resources = LocalResources.current
    LaunchedEffect(window) { window.notices.collect { snackbar.showSnackbar(resources.getString(it)) } }
    // Temas de usuario rotos o con poco contraste: se aplican igual (o se usa Nord) y se avisa (THEMES.md §6).
    val themeStore = ThemeStore.shared(context.filesDir)
    LaunchedEffect(themeStore) {
        // Collect, no clave del efecto: vaciar la lista no debe cancelar los avisos que se están mostrando.
        themeStore.warnings.collect { pending ->
            if (pending.isEmpty()) return@collect
            themeStore.consumeWarnings()
            pending.forEach { w ->
                val text =
                    when (w) {
                        is ThemeStore.Warning.Broken -> {
                            resources.getString(R.string.theme_broken, w.message)
                        }

                        is ThemeStore.Warning.LowContrast -> {
                            resources.getString(R.string.theme_low_contrast, w.theme, w.pairs.joinToString(", "))
                        }
                    }
                snackbar.showSnackbar(text, duration = SnackbarDuration.Long)
            }
        }
    }
    LaunchedEffect(Unit) {
        // Con Deshacer si el core supo cómo revertirla (UI_SPEC §4).
        OpsRepository.finished.collect { op ->
            val result =
                snackbar.showSnackbar(
                    finishedMessage(resources, op, window::volumeName),
                    actionLabel = if (op.canUndo) resources.getString(R.string.ops_undo) else null,
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) OpsRepository.undo(context, op.id)
        }
    }
    // Lo que llega arrastrado desde otras apps se copia aquí (A4.4).
    val import: (List<Uri>, File) -> Unit = { uris, dest ->
        run(OpRequest(OpKind.COPY, uris.map(Uri::toString), dest.path, emptyList()))
    }
    CompositionLocalProvider(LocalDropImport provides import, LocalOpenPreferences provides { showPrefs = true }) {
        Box(
            Modifier.fillMaxSize().onPreviewKeyEvent {
                // Ctrl y Shift al soltar un arrastre (copiar o mover): el evento de soltar no los trae.
                DragKeys.ctrl = it.isCtrlPressed
                DragKeys.shift = it.isShiftPressed
                false
            },
        ) {
            ModalNavigationDrawer(
                drawerContent = {
                    // Como cajón llega al borde de la pantalla: nada bajo la cámara en horizontal.
                    val cutout = WindowInsets.displayCutout.only(WindowInsetsSides.Start)
                    Sidebar(
                        items,
                        toggleDrawer,
                        R.string.sidebar_collapse,
                        Modifier.background(t.colors.sidebarBg.color).windowInsetsPadding(cutout).width(SidebarWidth),
                    )
                },
                drawerState = drawer,
                // En compacta se abre también deslizando desde el borde; en las demás, solo con ☰.
                gesturesEnabled = !wide || drawer.isOpen,
                scrimColor =
                    t.colors.windowBg.color
                        .copy(alpha = 0.6f),
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .background(t.colors.paneBg.color)
                        // En horizontal, la cámara frontal queda a un lado: nada debajo de ella.
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .onKeyEvent { event ->
                            // Atajos de la ventana: lo que el panel no atendió (UI_SPEC §8).
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (Shortcut.of(event)) {
                                Shortcut.OTHER_PANE -> window.activateOther()
                                Shortcut.TOGGLE_DUAL -> if (wide) window.toggleDual() else window.activateOther()
                                Shortcut.SIDEBAR -> if (permanentFits) window.toggleSidebar() else toggleDrawer()
                                Shortcut.COPY_TO_OTHER -> transferSelection(OpKind.COPY)
                                Shortcut.MOVE_TO_OTHER -> transferSelection(OpKind.MOVE)
                                Shortcut.PASTE -> paste()
                                Shortcut.UNDO -> OpsRepository.undoLast(context)
                                Shortcut.NEW_WINDOW -> MainActivity.openWindow(context, activeTabs.current.ui.value.dir)
                                Shortcut.PREFERENCES -> showPrefs = true
                                else -> return@onKeyEvent false
                            }
                            true
                        },
                ) {
                    when {
                        !wide -> {}

                        permanentFits && !collapsed -> {
                            Sidebar(
                                items,
                                window::toggleSidebar,
                                R.string.sidebar_collapse,
                                Modifier.width(SidebarWidth),
                            )
                        }

                        else -> {
                            // ☰ del riel: expande la permanente si cabe; si no, abre el cajón con la barra completa.
                            val expand = if (permanentFits) window::toggleSidebar else toggleDrawer
                            SidebarRail(items, expand, R.string.sidebar_expand)
                        }
                    }
                    Row(Modifier.weight(1f).onSizeChanged { rowWidth = it.width.toFloat().coerceAtLeast(1f) }) {
                        if (wide && dual) {
                            window.panes.forEachIndexed { i, pane ->
                                if (i == 1) {
                                    PaneStrip(
                                        fromA = active == 0,
                                        enabled = activeUi.selecting,
                                        moveEnabled = activeUi.selecting && !activeUi.selectionProtected,
                                        onTransfer = transferSelection,
                                    ) { window.moveSplit(it / rowWidth, minSplit) }
                                }
                                PaneScreen(
                                    pane,
                                    window.pathInput,
                                    active = i == active && !drawer.isOpen,
                                    onActivate = { window.activate(i) },
                                    dual = true,
                                    onToggleDual = window::toggleDual,
                                    onAddFavorite = window::addFavorite,
                                    onTransfer = { kind, files -> transfer(kind, files, 1 - i) },
                                    onRemove = remove,
                                    onRun = run,
                                    onDrop = drop,
                                    side = SidePane(onRight = i == 0) { window.moveTabToOther(i, it) },
                                    modifier = Modifier.weight(if (i == 0) shownSplit else 1 - shownSplit),
                                )
                            }
                        } else {
                            PaneScreen(
                                window.panes[active],
                                window.pathInput,
                                active = !drawer.isOpen,
                                onActivate = {},
                                switcher = {
                                    if (!wide) DrawerButton(toggleDrawer)
                                    PaneSwitcher(active, window::activate)
                                },
                                dual = if (wide) false else null,
                                onToggleDual = window::toggleDual,
                                onAddFavorite = window::addFavorite,
                                onTransfer = { kind, files -> transfer(kind, files, 1 - active) },
                                onRemove = remove,
                                onRun = run,
                                onDrop = drop,
                                otherPane = if (active == 0) "B" else "A",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars),
            )
            ClipboardPill(
                onPaste = { paste() },
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 16.dp, bottom = 40.dp),
            )
            OpsIndicator(
                window::volumeName,
                Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(end = 16.dp, bottom = 40.dp),
            )
        }
    }
}

/** Contenido de la barra lateral; resalta la carpeta de la pestaña activa del panel activo. */
@Composable
private fun sidebarItems(
    window: WindowViewModel,
    active: Int,
    onDrop: (List<File>, File) -> Unit,
    onTrash: () -> Unit,
    onOpen: (File) -> Unit,
): SidebarItems {
    val places by window.places.collectAsStateWithLifecycle()
    val volumes by window.volumes.collectAsStateWithLifecycle()
    val favorites by window.favorites.collectAsStateWithLifecycle()
    val tabs by window.panes[active].tabs.collectAsStateWithLifecycle()
    val ui by tabs.current.ui.collectAsStateWithLifecycle()
    return SidebarItems(places, volumes, favorites, ui.dir, onOpen, window::editFavorites, onTrash, onDrop)
}

/** ☰ en la cabecera del panel (compacta): abre el cajón con la barra lateral. */
@Composable
private fun DrawerButton(onClick: () -> Unit) {
    val t = TlacuacheTheme.tokens
    val label = stringResource(R.string.sidebar_open)
    WithTooltip(label, Shortcut.SIDEBAR) {
        IconButton(onClick) {
            Icon(painterResource(R.drawable.ic_menu), label, tint = t.colors.headerFg.color)
        }
    }
}

/**
 * Franja central entre paneles (UI_SPEC §4): **Copiar** y **Mover** con la flecha del panel activo al otro,
 * deshabilitados sin selección. También es el divisor: se arrastra para cambiar la proporción.
 */
@Composable
private fun PaneStrip(
    fromA: Boolean,
    enabled: Boolean,
    moveEnabled: Boolean,
    onTransfer: (OpKind) -> Unit,
    onDrag: (Float) -> Unit,
) {
    val t = TlacuacheTheme.tokens
    Box(
        Modifier
            .fillMaxHeight()
            .width(StripWidth)
            .background(t.colors.headerBg.color)
            .pointerHoverIcon(PointerIcon(android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW))
            .draggable(rememberDraggableState(onDrag), Orientation.Horizontal),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxHeight().width(1.dp).background(t.colors.borderStrong.color))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val arrow = if (fromA) "→" else "←"
            StripButton(arrow, R.string.strip_copy, R.string.action_copy_to_other, enabled, Shortcut.COPY_TO_OTHER) {
                onTransfer(OpKind.COPY)
            }
            StripButton(
                arrow,
                R.string.strip_move,
                R.string.action_move_to_other,
                moveEnabled,
                Shortcut.MOVE_TO_OTHER,
            ) {
                onTransfer(OpKind.MOVE)
            }
        }
    }
}

private val StripWidth = 48.dp

@Composable
private fun StripButton(
    arrow: String,
    @StringRes label: Int,
    @StringRes description: Int,
    enabled: Boolean,
    shortcut: Shortcut,
    onClick: () -> Unit,
) {
    val t = TlacuacheTheme.tokens
    val color = if (enabled) t.colors.headerFg.color else t.colors.headerDisabled
    val text = stringResource(description)
    WithTooltip(text, shortcut) {
        Column(
            Modifier
                .width(StripWidth)
                .background(t.colors.headerBg.color, MaterialTheme.shapes.small)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = text }
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(arrow, color = color, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(label), color = color, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Alternador A/B: el panel visible es el activo; el otro conserva carpeta y pestañas. */
@Composable
private fun PaneSwitcher(
    active: Int,
    onSelect: (Int) -> Unit,
) {
    val t = TlacuacheTheme.tokens
    Row(
        Modifier
            .padding(horizontal = 4.dp)
            .border(1.dp, t.colors.border.color, MaterialTheme.shapes.small),
    ) {
        listOf(R.string.pane_a to "A", R.string.pane_b to "B").forEachIndexed { i, (label, glyph) ->
            val description = stringResource(label)
            val selected = i == active
            Box(
                Modifier
                    .size(width = 40.dp, height = 36.dp)
                    .background(
                        if (selected) t.colors.accent.color else t.colors.headerBg.color,
                        MaterialTheme.shapes.small,
                    ).clickable { onSelect(i) }
                    .semantics {
                        contentDescription = description
                        this.selected = selected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    glyph,
                    color = if (selected) t.colors.headerBg.color else t.colors.headerFg.color,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** Abrir Preferencias desde el menú ⋮ de cualquier panel; lo da la ventana. */
val LocalOpenPreferences = compositionLocalOf<() -> Unit> { {} }
