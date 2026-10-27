package io.github.ricardomonroy.tlacuache.state

import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import io.github.ricardomonroy.tlacuache.ffi.FilterKey
import io.github.ricardomonroy.tlacuache.ffi.PathHistory
import io.github.ricardomonroy.tlacuache.ffi.SortKey
import io.github.ricardomonroy.tlacuache.ffi.SortSpec
import io.github.ricardomonroy.tlacuache.ffi.applyFilterKey
import io.github.ricardomonroy.tlacuache.ffi.arrange
import io.github.ricardomonroy.tlacuache.storage.FolderWatch
import io.github.ricardomonroy.tlacuache.storage.FolderWatcher
import io.github.ricardomonroy.tlacuache.storage.Listing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Una pestaña: carpeta actual y su listado ordenado y filtrado por el core (ARCHITECTURE §5–§6). */
@OptIn(FlowPreview::class)
class TabState(
    private val scope: CoroutineScope,
    dir: File,
    /** Nombre visible del volumen cuya raíz es el archivo dado («Interno», «SD»…), o `null`. */
    private val volumeName: (File) -> String?,
    private val watcher: FolderWatcher,
) {
    data class Ui(
        val dir: File,
        /** Nombre del volumen si [dir] es su raíz: se rotula así, no por su carpeta (`0`). */
        val volumeName: String? = null,
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
        /** Nombre del volumen de la primera miga; `null` si [dir] está fuera de un volumen. */
        val rootLabel: String? = null,
        /** Filtro rápido (UI_SPEC §8); se limpia al cambiar de carpeta. */
        val query: String = "",
        /** Ocultos visibles (Ctrl+H); se conserva al navegar. */
        val showHidden: Boolean = false,
        /** Vista de íconos (Ctrl+2) en vez de lista (Ctrl+1); se conserva al navegar. */
        val iconView: Boolean = false,
    )

    // shortcut: orden fijo hasta los encabezados de orden (A2).
    private val sort = SortSpec(SortKey.NAME, descending = false)

    /** Todo lo leído de la carpeta, sin filtrar; filtro y ocultos se reaplican sin volver a leer el disco. */
    private var entries: List<FileEntry> = emptyList()
    private var arrangeJob: Job? = null

    /** Eventos del vigilante (llegan desde otro hilo); se juntan con un debounce de [RESYNC_DELAY_MS]. */
    private val changes =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var watch: FolderWatch? = null

    private val history = PathHistory(dir.path)
    private val _ui = MutableStateFlow(uiFor(dir, Ui(dir)))
    val ui: StateFlow<Ui> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        reload()
        scope.launch { changes.debounce(RESYNC_DELAY_MS).collect { reload() } }
    }

    /**
     * Vigila la carpeta visible mientras la app está en primer plano (ARCHITECTURE §6): cualquier
     * cambio, de esta app o de otra, programa una relectura. Se mantiene al navegar.
     */
    fun startWatching() {
        watch?.stop()
        watch = watcher(_ui.value.dir) { changes.tryEmit(Unit) }
    }

    fun stopWatching() {
        watch?.stop()
        watch = null
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

    fun setQuery(query: String) {
        _ui.update { it.copy(query = query) }
        rearrange()
    }

    /**
     * Aplica una tecla al filtro rápido con las reglas del core (ADR-006 de Linux). `false` si la
     * tecla no es del filtro y debe seguir a su acción normal (Backspace sube de nivel).
     */
    fun applyFilterKey(key: FilterKey): Boolean {
        val next = applyFilterKey(_ui.value.query, key) ?: return false
        setQuery(next)
        return true
    }

    fun setIconView(on: Boolean) = _ui.update { it.copy(iconView = on) }

    fun toggleHidden() {
        _ui.update { it.copy(showHidden = !it.showHidden) }
        rearrange()
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
    private fun volumeRootOf(dir: File): File? =
        generateSequence(dir) { it.parentFile }.firstOrNull {
            volumeName(it) !=
                null
        }

    private fun isVolumeRoot(dir: File) = volumeName(dir) != null

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
        entries = emptyList()
        _ui.value = uiFor(dir, _ui.value)
        if (watch != null) startWatching()
        reload()
    }

    /** Estado de recién llegar a [dir]; de [prev] solo se conservan ocultos y vista. */
    private fun uiFor(
        dir: File,
        prev: Ui,
    ): Ui {
        val name = volumeName(dir)
        return Ui(
            dir = dir,
            volumeName = name,
            canGoBack = history.canGoBack(),
            canGoForward = history.canGoForward(),
            canGoUp = name == null && dir.parentFile != null,
            crumbs = crumbsOf(dir),
            rootLabel = volumeRootOf(dir)?.let(volumeName),
            showHidden = prev.showHidden,
            iconView = prev.iconView,
        )
    }

    /**
     * Relee la carpeta actual (Ctrl+R, volver a primer plano, cambios vigilados); cancela una lectura en
     * curso. Conserva la lista mientras tanto y solo muestra carga si aún no hay nada: no parpadea.
     */
    fun reload() {
        val dir = _ui.value.dir
        job?.cancel()
        _ui.update { it.copy(loading = entries.isEmpty(), failed = false, nowMs = System.currentTimeMillis()) }
        job =
            scope.launch {
                try {
                    Listing.read(dir, isVolumeRoot(dir)).collect { read ->
                        entries = read
                        publish(read)
                    }
                    _ui.update { it.copy(loading = false) }
                } catch (_: IOException) {
                    _ui.update { it.copy(visible = emptyList(), loading = false, failed = true) }
                } catch (_: SecurityException) {
                    _ui.update { it.copy(visible = emptyList(), loading = false, failed = true) }
                }
            }
    }

    /** Reaplica filtro y ocultos a lo ya leído, fuera del hilo principal. */
    private fun rearrange() {
        val snapshot = entries
        arrangeJob?.cancel()
        arrangeJob = scope.launch { publish(snapshot) }
    }

    /** Ordena y filtra [read] con el core y lo publica, salvo que ya haya una lectura más nueva. */
    private suspend fun publish(read: List<FileEntry>) {
        val ui = _ui.value
        val visible =
            withContext(Dispatchers.Default) {
                arrange(read, sort, ui.query, ui.showHidden).map { read[it.toInt()] }
            }
        // Descarta resultados viejos: llegó otra lectura o cambió el filtro mientras se ordenaba.
        val current = read === entries
        _ui.update { now ->
            val sameFilter = now.query == ui.query && now.showHidden == ui.showHidden
            if (current && sameFilter) now.copy(visible = visible) else now
        }
    }

    companion object {
        /** Igual que Linux (ADR-017): junta ráfagas de eventos, como una descarga que crece. */
        const val RESYNC_DELAY_MS = 300L
    }
}
