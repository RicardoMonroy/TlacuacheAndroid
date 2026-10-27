package io.github.ricardomonroy.tlacuache.storage

import io.github.ricardomonroy.tlacuache.ffi.SessionState
import io.github.ricardomonroy.tlacuache.ffi.sessionFromToml
import io.github.ricardomonroy.tlacuache.ffi.sessionToToml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** `session.toml` (`core::session`, ARCHITECTURE §5): se escribe al pasar a segundo plano y se lee al abrir. */
class SessionStore(
    private val file: File,
) {
    /**
     * Sesión guardada sin las pestañas cuya carpeta ya no existe (SD retirada, carpeta borrada); `null` si
     * no hay sesión o está dañada.
     */
    suspend fun load(): SessionState? =
        withContext(Dispatchers.IO) {
            val text =
                try {
                    file.readText()
                } catch (_: IOException) {
                    return@withContext null
                }
            sessionFromToml(text)?.let { s ->
                s.copy(
                    panes =
                        s.panes.map { pane ->
                            val tabs = pane.tabs.filter { File(it.path).isDirectory }
                            val selected = pane.tabs.getOrNull(pane.selected.toInt())?.let(tabs::indexOf) ?: -1
                            pane.copy(tabs = tabs, selected = selected.coerceAtLeast(0).toUInt())
                        },
                )
            }
        }

    /** `false` si no se pudo escribir; la sesión anterior queda intacta. */
    suspend fun save(state: SessionState): Boolean =
        withContext(Dispatchers.IO) {
            try {
                writeAtomically(file, sessionToToml(state))
                true
            } catch (_: IOException) {
                false
            }
        }
}
