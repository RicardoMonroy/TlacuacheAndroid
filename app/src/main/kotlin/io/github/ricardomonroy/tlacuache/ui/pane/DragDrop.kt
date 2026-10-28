package io.github.ricardomonroy.tlacuache.ui.pane

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.net.Uri
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import io.github.ricardomonroy.tlacuache.ffi.OpKind
import io.github.ricardomonroy.tlacuache.intents.FileIntents
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import java.io.File

/** Tipo de los datos arrastrados entre paneles, carpetas y la barra lateral (A4.3). */
private const val DRAG_MIME = "application/vnd.tlacuache.files"

/**
 * Teclas que deciden copiar o mover al soltar (UI_SPEC §4): el evento de soltar no trae modificadores, así
 * que la ventana anota Ctrl y Shift al pasar cada tecla.
 */
object DragKeys {
    @Volatile var ctrl = false

    @Volatile var shift = false
}

/**
 * Copiar o mover al soltar [files] en [dest] (UI_SPEC §4): en el mismo volumen se mueve y entre volúmenes se
 * copia; Ctrl fuerza copiar y Shift fuerza mover.
 */
fun dropKind(
    files: List<File>,
    dest: File,
    volumeRoots: List<File>,
    ctrl: Boolean = DragKeys.ctrl,
    shift: Boolean = DragKeys.shift,
): OpKind {
    if (ctrl) return OpKind.COPY
    if (shift) return OpKind.MOVE
    val target = volumeRoots.firstOrNull { dest.absoluteFile.startsWith(it) }
    val sameVolume = target != null && files.all { it.absoluteFile.startsWith(target) }
    return if (sameVolume) OpKind.MOVE else OpKind.COPY
}

/** Lo que se suelta en [dest] y no tiene sentido: nada, o la carpeta sobre sí misma o donde ya está. */
fun pointlessDrop(
    files: List<File>,
    dest: File,
): Boolean =
    files.isEmpty() ||
        files.all { it.absoluteFile == dest.absoluteFile || it.absoluteFile.parentFile == dest.absoluteFile }

/**
 * Hace arrastrable lo que devuelve [files] (o nada si devuelve `null`); [onStart] cierra menús abiertos. Los
 * archivos viajan también como `content://` de nuestro FileProvider, con lectura concedida, para que otras
 * apps los acepten (A4.4); las carpetas solo se sueltan en ventanas de Tlacuache.
 */
@Composable
fun Modifier.fileDragSource(
    files: () -> List<File>?,
    onStart: () -> Unit = {},
): Modifier {
    val view = LocalView.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val colors = TlacuacheTheme.tokens.colors
    val shadowColors = colors.accent.color.toArgb() to colors.accentFg.color.toArgb()
    val currentFiles by rememberUpdatedState(files)
    val currentStart by rememberUpdatedState(onStart)
    // Solo observa (pase Initial, sin consumir hasta empezar): el clic, la selección y el teclado siguen en
    // `itemGestures`. El origen de arrastre de Compose se quedaba con los toques y las filas no abrían.
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop

            fun moved(c: PointerInputChange) = (c.position - down.position).getDistance() > slop
            if (down.type == PointerType.Mouse) {
                // Con ratón: mover con el botón pulsado.
                while (true) {
                    val c =
                        awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                            ?: return@awaitEachGesture
                    if (!c.pressed) return@awaitEachGesture
                    if (moved(c)) break
                }
            } else {
                // Con el dedo: pulsación larga sin moverse.
                val released =
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val c =
                                awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                    ?: break
                            if (!c.pressed || moved(c)) break
                        }
                    }
                if (released != null) return@awaitEachGesture
            }
            val dragged = currentFiles() ?: return@awaitEachGesture
            currentStart()
            val label =
                dragged.singleOrNull()?.name
                    ?: resources.getQuantityString(R.plurals.pane_item_count, dragged.size, dragged.size)
            view.startDragAndDrop(
                dragClip(context, dragged),
                LabelShadow(label, shadowColors, density),
                dragged,
                dragFlags(dragged),
            )
            // Lo que queda del gesto ya es del arrastre: que no cuente como clic al soltar.
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

/**
 * Los datos del arrastre: cada elemento lleva su ruta (otras ventanas de Tlacuache: el estado local no les
 * llega, A4.5) y, si es un archivo, su `content://` de nuestro FileProvider para otras apps (A4.4).
 */
private fun dragClip(
    context: Context,
    dragged: List<File>,
): ClipData {
    val items =
        dragged.map { f ->
            val uri = if (f.isFile) runCatching { FileIntents.uri(context, f) }.getOrNull() else null
            ClipData.Item(f.path, null, uri)
        }
    val mimes = (dragged.filter(File::isFile).map(FileIntents::mimeType) + DRAG_MIME).distinct().toTypedArray()
    return ClipData(ClipDescription("Tlacuache", mimes), items.first()).apply { items.drop(1).forEach(::addItem) }
}

/** Global (otras ventanas y apps) y, si hay archivos, con lectura concedida de sus URIs. */
private fun dragFlags(dragged: List<File>) =
    View.DRAG_FLAG_GLOBAL or if (dragged.any(File::isFile)) View.DRAG_FLAG_GLOBAL_URI_READ else 0

/** Sombra del arrastre: una etiqueta con el nombre o «3 elementos», en el color de acento del tema. */
private class LabelShadow(
    private val text: String,
    colors: Pair<Int, Int>,
    density: Float,
) : View.DragShadowBuilder() {
    private val background = colors.first
    private val foreground = colors.second
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16 * density }
    private val pad = 12 * density
    private val width = (paint.measureText(text) + 2 * pad).toInt().coerceAtMost((320 * density).toInt())
    private val height = (paint.textSize + 2 * pad).toInt()

    override fun onProvideShadowMetrics(
        size: Point,
        touch: Point,
    ) {
        size.set(width, height)
        touch.set(width / 2, height / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        paint.color = background
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), pad, pad, paint)
        paint.color = foreground
        canvas.drawText(text, pad, pad - paint.ascent(), paint)
    }
}

/** Importar lo que llega arrastrado desde otra app (URIs `content://`) a una carpeta; lo da la ventana. */
val LocalDropImport = compositionLocalOf<(List<Uri>, File) -> Unit> { { _, _ -> } }

/**
 * Destino de soltar archivos de esta ventana en [dest]; [highlight] dice si hay algo encima para resaltarlo.
 * `null` en [dest] = no acepta.
 */
@Composable
fun Modifier.fileDropTarget(
    dest: File?,
    onDrop: (List<File>, File) -> Unit,
    highlight: (Boolean) -> Unit = {},
): Modifier {
    val currentDest by rememberUpdatedState(dest)
    val currentDrop by rememberUpdatedState(onDrop)
    val currentHighlight by rememberUpdatedState(highlight)
    val activity = LocalActivity.current
    val currentImport by rememberUpdatedState(LocalDropImport.current)
    val target =
        remember {
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) = currentHighlight(true)

                override fun onExited(event: DragAndDropEvent) = currentHighlight(false)

                override fun onEnded(event: DragAndDropEvent) = currentHighlight(false)

                override fun onDrop(event: DragAndDropEvent): Boolean {
                    currentHighlight(false)
                    val to = currentDest ?: return false
                    val drag = event.toAndroidDragEvent()
                    // De Tlacuache: el estado local (esta ventana) o las rutas (otra ventana, A4.5).
                    val ours =
                        (drag.localState as? List<*>)?.filterIsInstance<File>()
                            ?: drag.clipData?.takeIf { it.description.hasMimeType(DRAG_MIME) }?.let { c ->
                                (0 until c.itemCount).mapNotNull {
                                    c
                                        .getItemAt(it)
                                        .text
                                        ?.toString()
                                        ?.let(::File)
                                }
                            }
                    if (ours != null) {
                        if (pointlessDrop(ours, to)) return false
                        currentDrop(ours, to)
                        return true
                    }
                    // De otra app: URIs que se leen con el permiso que concede el arrastre (A4.4).
                    val clip = drag.clipData ?: return false
                    val uris =
                        (0 until clip.itemCount).mapNotNull {
                            clip.getItemAt(it).uri?.takeIf { u ->
                                u.scheme ==
                                    "content"
                            }
                        }
                    if (uris.isEmpty()) return false
                    activity?.requestDragAndDropPermissions(drag)
                    currentImport(uris, to)
                    return true
                }
            }
        }
    // Acepta lo propio y lo de otras apps; al soltar se ve si trae archivos.
    return dragAndDropTarget(shouldStartDragAndDrop = {
        currentDest != null && it.mimeTypes().isNotEmpty()
    }, target = target)
}

/** Arrastrar desde y soltar en los elementos de una carpeta ([dir]) de la lista o la cuadrícula. */
class ItemDrag(
    val dir: File,
    /** Lo que se arrastra al empezar sobre un elemento, o `null` si ese gesto no arrastra. */
    val files: (FileEntry) -> List<File>?,
    val onDrop: (List<File>, File) -> Unit,
)

/** Origen de arrastre y, si es carpeta, destino; [hovered] marca si algo está encima para resaltarla. */
@Composable
fun Modifier.itemDrag(
    drag: ItemDrag,
    entry: FileEntry,
    onStart: () -> Unit,
    hovered: (Boolean) -> Unit,
): Modifier {
    val source = fileDragSource({ drag.files(entry) }, onStart)
    return if (entry.isDir) source.fileDropTarget(File(drag.dir, entry.name), drag.onDrop, hovered) else source
}
