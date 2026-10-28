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
import io.github.ricardomonroy.tlacuache.storage.Protected
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
        /** Orden de la lista (encabezados; carpetas siempre primero, `core::sort`); se conserva al navegar. */
        val sort: SortSpec = SortSpec(SortKey.NAME, descending = false),
        /** Nombres de esta carpeta que no se mueven, renombran ni borran (ADR-A06): las estándar en la raíz. */
        val protectedNames: Set<String> = emptySet(),
        /** Dentro de `Android/`: nada se mueve, renombra ni borra. */
        val readOnly: Boolean = false,
        /** Nombres seleccionados (UI_SPEC §3); sobreviven a relecturas y se limpian al cambiar de carpeta. */
        val selected: Set<String> = emptySet(),
    ) {
        val selecting: Boolean get() = selected.isNotEmpty()

        fun isProtected(name: String) = readOnly || name in protectedNames

        /** Algo de lo seleccionado está protegido: mover, renombrar y borrar no se ofrecen. */
        val selectionProtected: Boolean get() = selected.any(::isProtected)

        /** Entradas seleccionadas, en el orden visible. */
        val selection: List<FileEntry> get() = visible.filter { it.name in selected }
    }

    /** Punto fijo de Shift+clic: el último elemento seleccionado con clic o toque. */
    private var anchor: String? = null

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
        // Sin vigilar (segundo plano, panel oculto, rotación) pudo cambiar algo sin aviso: se relee.
        if (missedChanges) {
            missedChanges = false
            reload()
        }
    }

    fun stopWatching() {
        if (watch != null) missedChanges = true
        watch?.stop()
        watch = null
    }

    private var missedChanges = false

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

    /** Ctrl+clic, o un toque en modo selección. */
    fun toggle(name: String) {
        anchor = name
        _ui.update { it.copy(selected = if (name in it.selected) it.selected - name else it.selected + name) }
    }

    /** Clic con ratón: solo este. */
    fun selectOnly(name: String) {
        anchor = name
        _ui.update { it.copy(selected = setOf(name)) }
    }

    /** Shift+clic: del ancla a [name] en el orden visible. */
    fun selectRange(name: String) {
        _ui.update { ui ->
            val names = ui.visible.map { it.name }
            val from = names.indexOf(anchor ?: name)
            val to = names.indexOf(name)
            if (from < 0 || to < 0) {
                ui.copy(selected = setOf(name))
            } else {
                ui.copy(selected = names.subList(minOf(from, to), maxOf(from, to) + 1).toSet())
            }
        }
    }

    /** Ctrl+A: todo lo visible (respeta el filtro). */
    fun selectAll() = _ui.update { ui -> ui.copy(selected = ui.visible.mapTo(mutableSetOf()) { it.name }) }

    /** Esc o el gesto atrás: sale del modo selección. */
    fun clearSelection() = _ui.update { it.copy(selected = emptySet()) }

    /** Tocar un encabezado: ordena por esa columna; otra vez, invierte. Fecha empieza por lo más reciente. */
    fun sortBy(key: SortKey) {
        _ui.update { ui ->
            val descending = if (ui.sort.key == key) !ui.sort.descending else key == SortKey.MODIFIED
            ui.copy(sort = SortSpec(key, descending))
        }
        rearrange()
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
            protectedNames = if (name != null) Protected.STANDARD else emptySet(),
            readOnly = volumeRootOf(dir)?.let { dir.absoluteFile.startsWith(File(it, "Android")) } ?: false,
            showHidden = prev.showHidden,
            iconView = prev.iconView,
            sort = prev.sort,
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
                    // La carpeta desapareció (borrada, movida, SD retirada): a la más cercana que siga existiendo.
                    val nearest = withContext(Dispatchers.IO) { nearestExisting(dir) }
                    if (nearest != null) {
                        navigate(nearest)
                    } else {
                        _ui.update { it.copy(visible = emptyList(), loading = false, failed = true) }
                    }
                } catch (_: SecurityException) {
                    _ui.update { it.copy(visible = emptyList(), loading = false, failed = true) }
                }
            }
    }

    /** Si [dir] ya no existe, la carpeta superior más cercana que sí, dentro de su volumen; `null` si existe. */
    private fun nearestExisting(dir: File): File? =
        if (dir.isDirectory) {
            null
        } else {
            generateSequence(dir.parentFile) { it.parentFile }
                .takeWhile { volumeRootOf(it) != null }
                .firstOrNull(File::isDirectory)
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
                arrange(read, ui.sort, ui.query, ui.showHidden).map { read[it.toInt()] }
            }
        // Descarta resultados viejos: llegó otra lectura o cambió el filtro mientras se ordenaba.
        val current = read === entries
        _ui.update { now ->
            val sameFilter = now.query == ui.query && now.showHidden == ui.showHidden && now.sort == ui.sort
            // Lo seleccionado que ya no está (borrado, renombrado o filtrado) deja de estarlo.
            val names = visible.mapTo(HashSet()) { it.name }
            if (current &&
                sameFilter
            ) {
                now.copy(visible = visible, selected = now.selected.filterTo(HashSet()) { it in names })
            } else {
                now
            }
        }
    }

    companion object {
        /** Igual que Linux (ADR-017): junta ráfagas de eventos, como una descarga que crece. */
        const val RESYNC_DELAY_MS = 300L
    }
}
