package io.github.ricardomonroy.tlacuache.state

import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import io.github.ricardomonroy.tlacuache.ffi.PathHistory
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
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        /** Falso en la raíz del volumen: más arriba no hay nada legible (`/storage/emulated`). */
        val canGoUp: Boolean = false,
        /** Migas de la barra de ruta: de la raíz del volumen a [dir] (UI_SPEC §2). */
        val crumbs: List<File> = listOf(dir),
        /** La primera miga es la raíz de un volumen (se rotula con su nombre, no con `0`). */
        val crumbsStartAtVolume: Boolean = false,
    )

    // shortcut: orden, filtro rápido y ocultos fijos hasta A1.7 (filtro) y A2 (encabezados de orden).
    private val sort = SortSpec(SortKey.NAME, descending = false)
    private val query = ""
    private val showHidden = false

    private val history = PathHistory(dir.path)
    private val _ui = MutableStateFlow(uiFor(dir))
    val ui: StateFlow<Ui> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        reload()
    }

    /** Entra a [entry] si es carpeta. Abrir archivos llega en A1.6. */
    fun open(entry: FileEntry) {
        if (entry.isDir) navigate(File(_ui.value.dir, entry.name))
    }

    fun up() {
        if (_ui.value.canGoUp) {
            _ui.value.dir.parentFile
                ?.let(::navigate)
        }
    }

    fun back() {
        history.back()?.let { show(File(it)) }
    }

    fun forward() {
        history.forward()?.let { show(File(it)) }
    }

    /** Va a una carpeta elegida en la barra de ruta (miga o texto ya resuelto). */
    fun goTo(dir: File): GoTo {
        if (volumeRootOf(dir) == null) return GoTo.OUTSIDE_STORAGE
        if (!dir.isDirectory) return GoTo.NOT_A_FOLDER
        navigate(dir)
        return GoTo.OK
    }

    enum class GoTo { OK, NOT_A_FOLDER, OUTSIDE_STORAGE }

    /** Raíz de volumen que contiene a [dir], o `null` si está fuera del almacenamiento del usuario (ADR-A04). */
    private fun volumeRootOf(dir: File): File? = generateSequence(dir) { it.parentFile }.firstOrNull(isVolumeRoot)

    /** De la raíz del volumen a [dir]; solo [dir] si está fuera de un volumen. */
    private fun crumbsOf(dir: File): List<File> {
        val root = volumeRootOf(dir) ?: return listOf(dir)
        return generateSequence(dir) { if (it == root) null else it.parentFile }.toList().reversed()
    }

    private fun navigate(dir: File) {
        if (history.navigate(dir.path)) show(dir)
    }

    /** Cambia a [dir] sin rastro de la carpeta anterior y la lee. */
    private fun show(dir: File) {
        _ui.value = uiFor(dir)
        reload()
    }

    private fun uiFor(dir: File): Ui {
        val root = isVolumeRoot(dir)
        return Ui(
            dir = dir,
            isVolumeRoot = root,
            canGoBack = history.canGoBack(),
            canGoForward = history.canGoForward(),
            canGoUp = !root && dir.parentFile != null,
            crumbs = crumbsOf(dir),
            crumbsStartAtVolume = volumeRootOf(dir) != null,
        )
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
