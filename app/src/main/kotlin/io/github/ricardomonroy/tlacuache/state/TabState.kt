package io.github.ricardomonroy.tlacuache.state

import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import io.github.ricardomonroy.tlacuache.ffi.SortKey
import io.github.ricardomonroy.tlacuache.ffi.SortSpec
import io.github.ricardomonroy.tlacuache.ffi.arrange
import io.github.ricardomonroy.tlacuache.storage.Listing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Una pestaña: carpeta actual y su listado ordenado y filtrado por el core (ARCHITECTURE §5–§6). */
class TabState(
    private val scope: CoroutineScope,
    dir: File,
    private val isVolumeRoot: (File) -> Boolean,
) {
    data class Ui(
        val dir: File,
        /** [dir] es la raíz de un volumen: se nombra por el volumen, no por su carpeta (`0`). */
        val isVolumeRoot: Boolean = false,
        /** Entradas visibles, en orden. */
        val visible: List<FileEntry> = emptyList(),
        val loading: Boolean = true,
        /** La carpeta no se pudo leer (permiso, desmontada, borrada). */
        val failed: Boolean = false,
        /** Referencia para la columna «Edad»; se fija en cada lectura. */
        val nowMs: Long = System.currentTimeMillis(),
    )

    // shortcut: orden, filtro rápido y ocultos fijos hasta A1.7 (filtro) y A2 (encabezados de orden).
    private val sort = SortSpec(SortKey.NAME, descending = false)
    private val query = ""
    private val showHidden = false

    private val _ui = MutableStateFlow(Ui(dir, isVolumeRoot(dir)))
    val ui: StateFlow<Ui> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        reload()
    }

    /** Relee la carpeta actual; cancela una lectura en curso. Conserva la lista mientras tanto. */
    fun reload() {
        val dir = _ui.value.dir
        job?.cancel()
        _ui.update { it.copy(loading = true, failed = false, nowMs = System.currentTimeMillis()) }
        job =
            scope.launch {
                try {
                    Listing.read(dir, isVolumeRoot(dir)).collect { entries ->
                        val visible =
                            withContext(Dispatchers.Default) {
                                arrange(entries, sort, query, showHidden).map { entries[it.toInt()] }
                            }
                        _ui.update { it.copy(visible = visible) }
                    }
                    _ui.update { it.copy(loading = false) }
                } catch (_: IOException) {
                    _ui.update { it.copy(visible = emptyList(), loading = false, failed = true) }
                } catch (_: SecurityException) {
                    _ui.update { it.copy(visible = emptyList(), loading = false, failed = true) }
                }
            }
    }
}
