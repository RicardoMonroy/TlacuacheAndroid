package io.github.ricardomonroy.tlacuache.state

import android.os.Environment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ricardomonroy.tlacuache.storage.fileObserverWatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/** Estado de una ventana (ARCHITECTURE §5): dos paneles con sus pestañas y cuál está activo. */
class WindowViewModel(
    savedState: SavedStateHandle,
) : ViewModel() {
    // shortcut: el almacenamiento interno es la única raíz de volumen hasta A2.3 (Volumes.kt).
    @Suppress("DEPRECATION")
    private val internalRoot: File = Environment.getExternalStorageDirectory().absoluteFile

    private val startDir = startDir(savedState.get<String>(EXTRA_PATH))

    private fun newTab(dir: File) =
        TabState(
            scope = viewModelScope,
            dir = dir,
            isVolumeRoot = { it.absoluteFile == internalRoot },
            watcher = fileObserverWatcher,
        )

    /** Paneles A y B: siempre existen los dos, se vean o no (ADR-A08). */
    val panes = listOf(PaneState(startDir, ::newTab), PaneState(startDir, ::newTab))

    private val _activePane = MutableStateFlow(0)
    val activePane: StateFlow<Int> = _activePane.asStateFlow()

    fun activate(pane: Int) {
        if (pane in panes.indices) _activePane.value = pane
    }

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
