package io.github.ricardomonroy.tlacuache.state

import android.os.Environment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ricardomonroy.tlacuache.storage.fileObserverWatcher
import java.io.File

/** Estado de una ventana (ARCHITECTURE §5). Por ahora, una sola pestaña. */
class WindowViewModel(
    savedState: SavedStateHandle,
) : ViewModel() {
    // shortcut: el almacenamiento interno es la única raíz de volumen hasta A2.3 (Volumes.kt).
    @Suppress("DEPRECATION")
    private val internalRoot: File = Environment.getExternalStorageDirectory().absoluteFile

    val tab =
        TabState(
            scope = viewModelScope,
            dir = startDir(savedState.get<String>(EXTRA_PATH)),
            isVolumeRoot = { it.absoluteFile == internalRoot },
            watcher = fileObserverWatcher,
        )

    override fun onCleared() = tab.stopWatching()

    /** Barra de ruta: `~` es la raíz del almacenamiento interno. */
    val pathInput = PathInput(internalRoot)

    /** Carpeta inicial: la del extra [EXTRA_PATH] si es una carpeta, si no la raíz interna. */
    private fun startDir(path: String?): File = path?.let(::File)?.takeIf { it.isDirectory } ?: internalRoot

    companion object {
        /** Ruta inicial de la ventana (nueva ventana, A4.5; pruebas con `am start --es path …`). */
        const val EXTRA_PATH = "path"
    }
}
