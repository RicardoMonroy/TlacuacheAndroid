package io.github.ricardomonroy.tlacuache.ui.pane

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.state.WindowViewModel
import io.github.ricardomonroy.tlacuache.ui.sidebar.Sidebar
import io.github.ricardomonroy.tlacuache.ui.sidebar.SidebarItems
import io.github.ricardomonroy.tlacuache.ui.sidebar.SidebarRail
import io.github.ricardomonroy.tlacuache.ui.sidebar.SidebarWidth
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
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
    val items =
        sidebarItems(window, active) { dir -> window.openInActive(dir).also { scope.launch { drawer.close() } } }
    val t = TlacuacheTheme.tokens
    var rowWidth by remember { mutableFloatStateOf(1f) }
    // Cada panel conserva sitio para botones y ruta; en un teléfono horizontal el divisor apenas se mueve.
    val minSplit = (with(LocalDensity.current) { MIN_PANE_WIDTH.toPx() } / rowWidth).coerceAtMost(0.5f)
    val shownSplit = split.coerceIn(minSplit, 1 - minSplit)
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
                    if (event.type == KeyEventType.KeyDown && event.key == Key.F9) {
                        if (permanentFits) window.toggleSidebar() else toggleDrawer()
                        true
                    } else {
                        handleWindowKey(event, window, wide)
                    }
                },
        ) {
            when {
                !wide -> {}

                permanentFits && !collapsed -> {
                    Sidebar(items, window::toggleSidebar, R.string.sidebar_collapse, Modifier.width(SidebarWidth))
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
                            PaneDivider { window.moveSplit(it / rowWidth, minSplit) }
                        }
                        PaneScreen(
                            pane,
                            window.pathInput,
                            active = i == active && !drawer.isOpen,
                            onActivate = { window.activate(i) },
                            dual = true,
                            onToggleDual = window::toggleDual,
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
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Contenido de la barra lateral; resalta la carpeta de la pestaña activa del panel activo. */
@Composable
private fun sidebarItems(
    window: WindowViewModel,
    active: Int,
    onOpen: (File) -> Unit,
): SidebarItems {
    val places by window.places.collectAsStateWithLifecycle()
    val volumes by window.volumes.collectAsStateWithLifecycle()
    val tabs by window.panes[active].tabs.collectAsStateWithLifecycle()
    val ui by tabs.current.ui.collectAsStateWithLifecycle()
    return SidebarItems(places, volumes, ui.dir, onOpen)
}

/** ☰ en la cabecera del panel (compacta): abre el cajón con la barra lateral. */
@Composable
private fun DrawerButton(onClick: () -> Unit) {
    val t = TlacuacheTheme.tokens
    IconButton(onClick) {
        Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.sidebar_open), tint = t.colors.headerFg.color)
    }
}

/** Tab cambia de panel; F3 alterna 1/2 paneles, o A/B en compacta (UI_SPEC §8). */
private fun handleWindowKey(
    event: KeyEvent,
    window: WindowViewModel,
    wide: Boolean,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed || event.isShiftPressed) return false
    when (event.key) {
        Key.Tab -> window.activateOther()
        Key.F3 -> if (wide) window.toggleDual() else window.activateOther()
        else -> return false
    }
    return true
}

/** Divisor entre paneles: línea de 1 dp con zona de arrastre de 12 dp. */
@Composable
private fun PaneDivider(onDrag: (Float) -> Unit) {
    val t = TlacuacheTheme.tokens
    Box(
        Modifier
            .fillMaxHeight()
            .width(12.dp)
            .background(t.colors.headerBg.color)
            .pointerHoverIcon(PointerIcon(android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW))
            .draggable(rememberDraggableState(onDrag), Orientation.Horizontal),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxHeight().width(1.dp).background(t.colors.borderStrong.color))
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
