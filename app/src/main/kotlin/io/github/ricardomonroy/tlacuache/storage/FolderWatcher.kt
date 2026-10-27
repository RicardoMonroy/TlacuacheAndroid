package io.github.ricardomonroy.tlacuache.storage

import android.os.FileObserver
import java.io.File

/** Vigilancia de una carpeta; se detiene con [stop]. */
fun interface FolderWatch {
    fun stop()
}

/** Crea la vigilancia de una carpeta: llama a `onChange` (desde otro hilo) con cada evento. */
typealias FolderWatcher = (dir: File, onChange: () -> Unit) -> FolderWatch

/**
 * Vigilante real con [FileObserver] (inotify). Incluye MODIFY para que una descarga que crece se
 * vea crecer (ADR-017 de Linux); quien lo usa junta los eventos con un debounce.
 */
val fileObserverWatcher: FolderWatcher = { dir, onChange ->
    val observer =
        object : FileObserver(dir, MASK) {
            override fun onEvent(
                event: Int,
                path: String?,
            ) = onChange()
        }
    observer.startWatching()
    FolderWatch(observer::stopWatching)
}

private const val MASK =
    FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or FileObserver.MOVED_TO or
        FileObserver.CLOSE_WRITE or FileObserver.MODIFY or FileObserver.ATTRIB or
        FileObserver.DELETE_SELF or FileObserver.MOVE_SELF
