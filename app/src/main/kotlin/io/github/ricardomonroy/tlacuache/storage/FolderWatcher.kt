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
 *
 * Un solo observador por carpeta, compartido: Android asocia los avisos a la ruta dentro del proceso, y
 * con dos observadores en la misma carpeta (los dos paneles en el mismo sitio) solo el último los recibía.
 */
val fileObserverWatcher: FolderWatcher = { dir, onChange -> SharedObservers.watch(dir.absoluteFile, onChange) }

private object SharedObservers {
    private class Shared(
        dir: File,
    ) : FileObserver(dir, MASK) {
        val listeners = mutableSetOf<() -> Unit>()

        override fun onEvent(
            event: Int,
            path: String?,
        ) {
            synchronized(SharedObservers) { listeners.toList() }.forEach { it() }
        }
    }

    private val byDir = mutableMapOf<File, Shared>()

    @Synchronized
    fun watch(
        dir: File,
        onChange: () -> Unit,
    ): FolderWatch {
        val shared = byDir.getOrPut(dir) { Shared(dir).also(FileObserver::startWatching) }
        shared.listeners += onChange
        return FolderWatch { stop(dir, onChange) }
    }

    @Synchronized
    private fun stop(
        dir: File,
        onChange: () -> Unit,
    ) {
        val shared = byDir[dir] ?: return
        shared.listeners -= onChange
        if (shared.listeners.isEmpty()) {
            shared.stopWatching()
            byDir.remove(dir)
        }
    }
}

private const val MASK =
    FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or FileObserver.MOVED_TO or
        FileObserver.CLOSE_WRITE or FileObserver.MODIFY or FileObserver.ATTRIB or
        FileObserver.DELETE_SELF or FileObserver.MOVE_SELF
