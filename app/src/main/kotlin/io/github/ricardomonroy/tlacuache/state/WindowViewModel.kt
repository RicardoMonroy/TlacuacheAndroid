package io.github.ricardomonroy.tlacuache.state

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.storage.Volume
import io.github.ricardomonroy.tlacuache.storage.Volumes
import io.github.ricardomonroy.tlacuache.storage.fileObserverWatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    private fun volumeName(dir: File): String? = volumes.value.firstOrNull { it.root == dir.absoluteFile }?.name

    private val startDir = startDir(savedState.get<String>(EXTRA_PATH))

    private fun newTab(dir: File) =
        TabState(
            scope = viewModelScope,
            dir = dir,
            volumeName = ::volumeName,
            watcher = fileObserverWatcher,
        )

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

    /** Lugares de la barra lateral: carpetas estándar del almacenamiento interno (se buscan en IO). */
    val places: StateFlow<List<Place>> =
        flow { emit(Place.existing(internalRoot)) }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _sidebarCollapsed = MutableStateFlow(false)

    /** Barra lateral permanente reducida a riel (F9; UI_SPEC §6). */
    val sidebarCollapsed: StateFlow<Boolean> = _sidebarCollapsed.asStateFlow()

    fun toggleSidebar() = _sidebarCollapsed.update { !it }

    /** Tab, alternador A/B en compacta. */
    fun activateOther() = _activePane.update { 1 - it }

    // shortcut: modo dual y divisor viven solo en memoria hasta la sesión (A2.7).
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
