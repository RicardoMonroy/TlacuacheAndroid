package io.github.ricardomonroy.tlacuache.state

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.FavoriteGroup
import io.github.ricardomonroy.tlacuache.ffi.Favorites
import io.github.ricardomonroy.tlacuache.ffi.OpKind
import io.github.ricardomonroy.tlacuache.ffi.OpRequest
import io.github.ricardomonroy.tlacuache.ffi.SessionPane
import io.github.ricardomonroy.tlacuache.ffi.SessionState
import io.github.ricardomonroy.tlacuache.ffi.SessionTab
import io.github.ricardomonroy.tlacuache.ffi.ViewMode
import io.github.ricardomonroy.tlacuache.ops.OpsRepository
import io.github.ricardomonroy.tlacuache.storage.FavoritesStore
import io.github.ricardomonroy.tlacuache.storage.PreferencesStore
import io.github.ricardomonroy.tlacuache.storage.SessionStore
import io.github.ricardomonroy.tlacuache.storage.ThemeStore
import io.github.ricardomonroy.tlacuache.storage.TrashStore
import io.github.ricardomonroy.tlacuache.storage.Volume
import io.github.ricardomonroy.tlacuache.storage.Volumes
import io.github.ricardomonroy.tlacuache.storage.fileObserverWatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Estado de una ventana (ARCHITECTURE §5): dos paneles con sus pestañas y cuál está activo. */
class WindowViewModel(
    app: Application,
    savedState: SavedStateHandle,
) : AndroidViewModel(app) {
    @Suppress("DEPRECATION")
    private val internalRoot: File = Environment.getExternalStorageDirectory().absoluteFile

    /** Volúmenes montados; hasta la primera lectura, solo el interno (sin uso de disco). */
    val volumes: StateFlow<List<Volume>> =
        Volumes.observe(app).stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            listOf(Volume(internalRoot, app.getString(R.string.volume_internal_short), Volume.Kind.INTERNAL, 0, 0)),
        )

    fun volumeName(dir: File): String? = volumes.value.firstOrNull { it.root == dir.absoluteFile }?.name

    private val startDir = startDir(savedState.get<String>(EXTRA_PATH))

    /** La ventana principal restaura y guarda la sesión; las abiertas en una carpeta (A4.5) no la tocan. */
    private val mainWindow = savedState.get<String>(EXTRA_PATH) == null

    private val preferences = PreferencesStore.shared(app)

    /** Pestaña en [dir] tal cual (la sesión le pone su vista y ocultos). */
    private fun tabAt(dir: File) =
        TabState(
            scope = viewModelScope,
            dir = dir,
            volumeName = ::volumeName,
            watcher = fileObserverWatcher,
            showAndroid = { preferences.showAndroid.value },
        )

    /** Pestaña nueva con la vista y los ocultos de `[general]` (Preferencias). */
    private fun newTab(dir: File) = tabAt(dir).apply(::applyDefaults)

    private fun applyDefaults(tab: TabState) {
        val general = preferences.general.value
        tab.setShowHidden(general.showHidden)
        // `columns` (Miller, A6.2) aún no existe en Android: lista.
        tab.setIconView(general.defaultView == ViewMode.ICONS)
    }

    /** Paneles A y B: siempre existen los dos, se vean o no (ADR-A08). */
    val panes = listOf(PaneState(startDir, ::newTab), PaneState(startDir, ::newTab))

    private val _activePane = MutableStateFlow(0)
    val activePane: StateFlow<Int> = _activePane.asStateFlow()

    fun activate(pane: Int) {
        if (pane in panes.indices) _activePane.value = pane
    }

    /** Abre [dir] (un lugar o volumen de la barra lateral) en la pestaña actual del panel activo. */
    fun openInActive(dir: File) {
        panes[_activePane.value]
            .tabs.value.current
            .goTo(dir)
    }

    /** Copia o mueve [files] a la carpeta actual del panel [target] (la cola es de todo el proceso). */
    fun transfer(
        kind: OpKind,
        files: List<File>,
        target: Int,
    ) {
        if (files.isEmpty()) return
        val dest =
            panes[target]
                .tabs.value.current.ui.value.dir
        OpsRepository.enqueue(getApplication(), OpRequest(kind, files.map { it.path }, dest.path, emptyList()))
    }

    /** A la papelera o borrado permanente (ya confirmado) de [files]. */
    fun remove(
        kind: OpKind,
        files: List<File>,
    ) {
        if (files.isNotEmpty()) {
            OpsRepository.enqueue(
                getApplication(),
                OpRequest(
                    kind,
                    files.map {
                        it.path
                    },
                    null,
                    emptyList(),
                ),
            )
        }
    }

    /** Lugares de la barra lateral: carpetas estándar del almacenamiento interno (se buscan en IO). */
    val places: StateFlow<List<Place>> =
        flow { emit(Place.existing(internalRoot)) }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val favoritesStore = FavoritesStore.shared(File(app.filesDir, "config.toml"), internalRoot)

    /** Grupos de favoritos con rutas absolutas (UI_SPEC §6). */
    val favorites: StateFlow<List<FavoriteGroup>> = favoritesStore.groups

    private val _notices = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    /** Avisos breves de la ventana (texto de `strings.xml`) para un `Snackbar`. */
    val notices: SharedFlow<Int> = _notices.asSharedFlow()

    /**
     * Reemplaza `config.toml` por uno elegido (p. ej. el de Linux) y aplica al momento sus favoritos, tema y
     * `[general]`; si no es válido, avisa con su error y no toca nada.
     */
    fun importConfig(
        uri: Uri,
        /** Aviso para quien lo pidió: Preferencias tapa los avisos de la ventana. */
        onNotice: (Int) -> Unit,
        onInvalid: (String) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = preferences.import(uri)) {
                PreferencesStore.ImportResult.Ok -> {
                    favoritesStore.load()
                    ThemeStore.shared(getApplication<Application>().filesDir).load(again = true)
                    onNotice(R.string.prefs_import_done)
                }

                is PreferencesStore.ImportResult.Invalid -> {
                    onInvalid(result.message)
                }

                PreferencesStore.ImportResult.Failed -> {
                    onNotice(R.string.prefs_import_failed)
                }
            }
        }
    }

    fun exportConfig(
        uri: Uri,
        onNotice: (Int) -> Unit,
    ) {
        viewModelScope.launch {
            onNotice(if (preferences.export(uri)) R.string.prefs_export_done else R.string.prefs_export_failed)
        }
    }

    /** Cambia los favoritos y guarda; un fallo al guardar se avisa aquí, el resto lo decide [onResult]. */
    fun editFavorites(
        change: (Favorites) -> Unit,
        onResult: (FavoritesStore.Result) -> Unit = {},
    ) {
        viewModelScope.launch {
            val result = favoritesStore.edit(change)
            if (result == FavoritesStore.Result.FAILED) _notices.tryEmit(R.string.favorites_save_error)
            onResult(result)
        }
    }

    /** Añade [dir] al primer grupo de favoritos («Favoritos» si aún no hay ninguno). */
    fun addFavorite(dir: File) {
        var added = false
        val group = getApplication<Application>().getString(R.string.favorites_default_group)
        editFavorites({ added = it.add(dir.path, group) }) {
            if (it == FavoritesStore.Result.OK) {
                _notices.tryEmit(if (added) R.string.favorite_added else R.string.favorite_exists)
            }
        }
    }

    private val _sidebarCollapsed = MutableStateFlow(false)

    /** Barra lateral permanente reducida a riel (F9; UI_SPEC §6). */
    val sidebarCollapsed: StateFlow<Boolean> = _sidebarCollapsed.asStateFlow()

    fun toggleSidebar() = _sidebarCollapsed.update { !it }

    /** Tab, alternador A/B en compacta. */
    fun activateOther() = _activePane.update { 1 - it }

    private val _dual = MutableStateFlow(true)

    /** Dos paneles lado a lado cuando la ventana es media o expandida (F3 lo alterna; UI_SPEC §1). */
    val dual: StateFlow<Boolean> = _dual.asStateFlow()

    fun toggleDual() = _dual.update { !it }

    private val _split = MutableStateFlow(0.5f)

    /** Fracción del ancho para el panel A con dos paneles. */
    val split: StateFlow<Float> = _split.asStateFlow()

    /** Arrastrar el divisor; ningún panel queda por debajo de [min] del ancho (lo fija la ventana). */
    fun moveSplit(
        delta: Float,
        min: Float,
    ) = _split.update { (it + delta).coerceIn(min, 1 - min) }

    private val sessionStore = SessionStore(File(app.filesDir, "session.toml"))
    private val _ready = MutableStateFlow(false)

    /** La sesión anterior ya se restauró (o no había); hasta entonces la UI no muestra los paneles. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch { favoritesStore.load() }
        // Purga al arrancar: lo que lleva más de 30 días en la papelera (ADR-A05).
        viewModelScope.launch(Dispatchers.IO) { TrashStore({ Volumes.roots(app) }).purge() }
        viewModelScope.launch {
            preferences.load()
            // Una ventana abierta en una carpeta concreta (A4.5) no restaura la sesión.
            val session = if (mainWindow) sessionStore.load() else null
            if (session != null) {
                restore(session)
            } else {
                // Sin sesión: las pestañas iniciales y el doble panel salen de Preferencias.
                _dual.value = preferences.general.value.dualPane
                panes.forEach { applyDefaults(it.tabs.value.current) }
            }
            _ready.value = true
        }
        // Mostrar u ocultar `Android/` se ve al momento en las pestañas abiertas.
        viewModelScope.launch {
            preferences.showAndroid.drop(1).collect {
                panes.forEach { p ->
                    p.tabs.value.list
                        .forEach(TabState::reload)
                }
            }
        }
    }

    private fun restore(s: SessionState) {
        _dual.value = s.dualPane
        _split.value = (s.panesRatio ?: 0.5).toFloat()
        _sidebarCollapsed.value = !s.sidebarVisible
        panes.zip(s.panes).forEach { (pane, saved) ->
            if (saved.tabs.isNotEmpty()) {
                val tabs =
                    saved.tabs.map { t ->
                        tabAt(File(t.path)).apply {
                            setIconView(t.iconView)
                            setShowHidden(t.showHidden)
                        }
                    }
                pane.restore(tabs, saved.selected.toInt())
            }
        }
        activate(s.activePane.toInt())
    }

    /** Guarda la sesión con `core::session` (al pasar a segundo plano: después el sistema puede matar el proceso). */
    fun saveSession() {
        if (!mainWindow) return
        val state =
            SessionState(
                dualPane = _dual.value,
                panesRatio = _split.value.toDouble(),
                activePane = _activePane.value.toUInt(),
                sidebarVisible = !_sidebarCollapsed.value,
                panes =
                    panes.map { pane ->
                        val tabs = pane.tabs.value
                        SessionPane(
                            tabs.active.toUInt(),
                            tabs.list.map {
                                it.ui.value.let { ui ->
                                    SessionTab(ui.dir.path, ui.iconView, ui.showHidden)
                                }
                            },
                        )
                    },
            )
        viewModelScope.launch { sessionStore.save(state) }
    }

    override fun onCleared() {
        panes.flatMap { it.tabs.value.list }.forEach(TabState::stopWatching)
    }

    /** Barra de ruta: `~` es la raíz del almacenamiento interno. */
    val pathInput = PathInput(internalRoot)

    /** Carpeta inicial: la del extra [EXTRA_PATH] si es una carpeta, si no la raíz interna. */
    private fun startDir(path: String?): File = path?.let(::File)?.takeIf { it.isDirectory } ?: internalRoot

    companion object {
        /** Ruta inicial de la ventana (nueva ventana, A4.5; pruebas con `am start --es path …`). */
        const val EXTRA_PATH = "path"
    }
}
