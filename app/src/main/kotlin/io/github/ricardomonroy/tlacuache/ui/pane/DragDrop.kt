package io.github.ricardomonroy.tlacuache.ui.pane

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import io.github.ricardomonroy.tlacuache.ffi.OpKind
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

/** Hace arrastrable lo que devuelve [files] (o nada si devuelve `null`); [onStart] cierra menús abiertos. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.fileDragSource(
    files: () -> List<File>?,
    onStart: () -> Unit = {},
): Modifier =
    dragAndDropSource { _ ->
        val dragged =
            files().also { android.util.Log.d("TlqDrag", "transferData -> $it") } ?: return@dragAndDropSource null
        onStart()
        val clip =
            ClipData(
                ClipDescription("Tlacuache", arrayOf(DRAG_MIME)),
                ClipData.Item(dragged.joinToString("\n") { it.path }),
            )
        DragAndDropTransferData(clip, localState = dragged)
    }

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
    val target =
        remember {
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) = currentHighlight(true)

                override fun onExited(event: DragAndDropEvent) = currentHighlight(false)

                override fun onEnded(event: DragAndDropEvent) = currentHighlight(false)

                override fun onDrop(event: DragAndDropEvent): Boolean {
                    currentHighlight(false)
                    val to = currentDest ?: return false
                    val files =
                        (event.toAndroidDragEvent().localState as? List<*>)?.filterIsInstance<File>() ?: return false
                    if (pointlessDrop(files, to)) return false
                    currentDrop(files, to)
                    return true
                }
            }
        }
    return dragAndDropTarget(shouldStartDragAndDrop = {
        DRAG_MIME in it.mimeTypes() && currentDest != null
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
