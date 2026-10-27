package io.github.ricardomonroy.tlacuache.state

import android.os.Environment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ricardomonroy.tlacuache.storage.fileObserverWatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
