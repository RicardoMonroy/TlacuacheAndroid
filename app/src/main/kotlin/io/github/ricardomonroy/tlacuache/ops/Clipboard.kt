package io.github.ricardomonroy.tlacuache.ops

import io.github.ricardomonroy.tlacuache.ffi.OpKind
import io.github.ricardomonroy.tlacuache.ffi.OpRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Portapapeles interno (UI_SPEC §4): Copiar o Cortar y luego Pegar en cualquier carpeta, pestaña o panel.
 * Es de todo el proceso, como la cola de operaciones.
 *
 * shortcut: sin el portapapeles del sistema (`core::clipboard` da los formatos de Linux); llega con
 * arrastrar y soltar entre apps (A4).
 */
object Clipboard {
    data class Clip(
        val files: List<File>,
        /** Cortar: pegar mueve y vacía el portapapeles. */
        val cut: Boolean,
    )

    private val _clip = MutableStateFlow<Clip?>(null)
    val clip: StateFlow<Clip?> = _clip.asStateFlow()

    fun set(
        files: List<File>,
        cut: Boolean,
    ) {
        if (files.isNotEmpty()) _clip.value = Clip(files, cut)
    }

    fun clear() {
        _clip.value = null
    }

    /** La operación que pega en [dir], o `null` si no hay nada; lo cortado solo se pega una vez. */
    fun paste(dir: File): OpRequest? {
        val clip = _clip.value ?: return null
        if (clip.cut) clear()
        return OpRequest(if (clip.cut) OpKind.MOVE else OpKind.COPY, clip.files.map { it.path }, dir.path, emptyList())
    }
}
