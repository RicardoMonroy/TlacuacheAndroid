package io.github.ricardomonroy.tlacuache.ui.sidebar

import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.UsageLevel
import io.github.ricardomonroy.tlacuache.ffi.usage
import io.github.ricardomonroy.tlacuache.state.Place
import io.github.ricardomonroy.tlacuache.storage.Volume
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import java.io.File

/** Ancho de la barra lateral completa (permanente o cajón). */
val SidebarWidth: Dp = 260.dp

/** Lo que muestra la barra lateral y adónde lleva tocarla (UI_SPEC §6). */
class SidebarItems(
    val places: List<Place>,
    val volumes: List<Volume>,
    /** Carpeta de la pestaña activa: su lugar o volumen se resalta. */
    val current: File?,
    val onOpen: (File) -> Unit,
)

/**
 * Barra lateral completa: lugares y volúmenes con su uso de disco. [onMenu] es el botón ☰ de la
 * cabecera (reducir a riel o cerrar el cajón).
 */
@Composable
fun Sidebar(
    items: SidebarItems,
    onMenu: () -> Unit,
    @StringRes menuLabel: Int,
    modifier: Modifier = Modifier,
) {
    val t = TlacuacheTheme.tokens
    Column(
        modifier
            .fillMaxHeight()
            .background(t.colors.sidebarBg.color)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        MenuButton(menuLabel, onMenu)
        SectionTitle(R.string.sidebar_places)
        items.places.forEach { place ->
            Entry(place.kind.icon, stringResource(place.label), place.dir == items.current, { items.onOpen(place.dir) })
        }
        SectionTitle(R.string.sidebar_volumes)
        items.volumes.forEach { volume ->
            Entry(volume.kind.icon, volume.name, volume.root == items.current, { items.onOpen(volume.root) }) {
                VolumeUsage(volume)
            }
        }
    }
}

/** Riel: íconos con rótulo corto, para media o con la barra reducida. */
@Composable
fun SidebarRail(
    items: SidebarItems,
    onMenu: () -> Unit,
    @StringRes menuLabel: Int,
    modifier: Modifier = Modifier,
) {
    val t = TlacuacheTheme.tokens
    val colors =
        NavigationRailItemDefaults.colors(
            selectedIconColor = t.colors.accentFg.color,
            selectedTextColor = t.colors.sidebarFg.color,
            indicatorColor = t.colors.accent.color,
            unselectedIconColor = t.colors.sidebarFg.color,
            unselectedTextColor = t.colors.fgMuted.color,
        )

    @Composable
    fun item(
        @DrawableRes icon: Int,
        label: String,
        dir: File,
    ) = NavigationRailItem(
        selected = dir == items.current,
        onClick = { items.onOpen(dir) },
        icon = { Icon(painterResource(icon), contentDescription = null) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = colors,
    )
    Column(
        modifier
            .fillMaxHeight()
            .width(RailWidth)
            .background(t.colors.sidebarBg.color)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MenuButton(menuLabel, onMenu)
        items.places.forEach { item(it.kind.icon, stringResource(it.label), it.dir) }
        items.volumes.forEach { item(it.kind.icon, it.name, it.root) }
    }
}

private val RailWidth = 80.dp

@Composable
private fun MenuButton(
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    val t = TlacuacheTheme.tokens
    IconButton(onClick, Modifier.padding(4.dp)) {
        Icon(painterResource(R.drawable.ic_menu), stringResource(label), tint = t.colors.sidebarFg.color)
    }
}

@Composable
private fun SectionTitle(
    @StringRes text: Int,
) {
    val t = TlacuacheTheme.tokens
    Text(
        stringResource(text).uppercase(),
        color = t.colors.fgMuted.color,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Entry(
    @DrawableRes icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    below: (@Composable () -> Unit)? = null,
) {
    val t = TlacuacheTheme.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp)
            .background(
                if (selected) t.colors.selectionBg.color else t.colors.sidebarBg.color,
                MaterialTheme.shapes.small,
            ).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (selected) t.colors.selectionFg.color else t.colors.sidebarFg.color
        Icon(painterResource(icon), contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(label, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            below?.invoke()
        }
    }
}

/** Barra de uso con aviso ≥ 80 % y crítico ≥ 90 % (`core::usage`) y el espacio libre. */
@Composable
private fun VolumeUsage(volume: Volume) {
    if (volume.size <= 0) return
    val t = TlacuacheTheme.tokens
    val context = LocalContext.current
    val used = remember(volume) { usage(volume.free.toULong(), volume.size.toULong()) }
    val bar =
        when (used.level) {
            UsageLevel.NORMAL -> t.colors.accent
            UsageLevel.WARNING -> t.colors.warning
            UsageLevel.CRITICAL -> t.colors.error
        }
    LinearProgressIndicator(
        progress = { used.fraction.toFloat() },
        color = bar.color,
        trackColor = t.colors.border.color,
        drawStopIndicator = {},
        gapSize = 0.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
    Text(
        stringResource(
            R.string.volume_free,
            Formatter.formatShortFileSize(context, volume.free),
            Formatter.formatShortFileSize(context, volume.size),
        ),
        color = t.colors.fgMuted.color,
        style = MaterialTheme.typography.bodySmall,
    )
}

private val Place.Kind.icon: Int
    get() =
        when (this) {
            Place.Kind.IMAGE -> R.drawable.ic_place_image
            Place.Kind.VIDEO -> R.drawable.ic_place_video
            Place.Kind.DOWNLOAD -> R.drawable.ic_place_download
            Place.Kind.DOCUMENT -> R.drawable.ic_place_document
            Place.Kind.AUDIO -> R.drawable.ic_place_audio
        }

private val Volume.Kind.icon: Int
    get() =
        when (this) {
            Volume.Kind.INTERNAL -> R.drawable.ic_volume_internal
            Volume.Kind.SD -> R.drawable.ic_volume_sd
            Volume.Kind.USB -> R.drawable.ic_volume_usb
        }
