package io.github.ricardomonroy.tlacuache.state

import io.github.ricardomonroy.tlacuache.ffi.complete
import io.github.ricardomonroy.tlacuache.ffi.completionMatches
import io.github.ricardomonroy.tlacuache.ffi.expandTilde
import io.github.ricardomonroy.tlacuache.ffi.splitForCompletion
import java.io.File

/**
 * Ruta escrita en la barra (UI_SPEC §2): `~` es la raíz del almacenamiento interno ([home]); una
 * ruta relativa parte de [base]. Lectura de disco: llamar fuera del hilo principal salvo [display].
 */
class PathInput(
    private val home: File,
) {
    /** Texto inicial del modo editable: `~/DCIM/Camera` dentro de [home], absoluta fuera. */
    fun display(dir: File): String {
        val abs = dir.absoluteFile
        if (!abs.startsWith(home.absoluteFile)) return abs.path
        val rel = abs.relativeTo(home.absoluteFile).path
        return if (rel.isEmpty()) "~" else "~/$rel"
    }

    /** Carpeta a la que apunta [input], ya normalizada (sin `.` ni `..`). */
    fun resolve(
        input: String,
        base: File,
    ): File {
        val expanded = File(expandTilde(input.trim(), home.path))
        val absolute = if (expanded.isAbsolute) expanded else File(base, expanded.path)
        return absolute.toPath().normalize().toFile()
    }

    /** Subcarpetas que siguen a lo escrito, en orden natural (lista desplegable). */
    fun suggestions(
        input: String,
        base: File,
    ): List<String> {
        val split = splitForCompletion(input)
        return completionMatches(split.partial, subdirs(split.dir, base))
    }

    /** Texto completado por Tab (núcleo de Linux), o `null`. */
    fun complete(
        input: String,
        base: File,
    ): String? = complete(input, subdirs(splitForCompletion(input).dir, base))

    private fun subdirs(
        typedDir: String,
        base: File,
    ): List<String> {
        val dir = if (typedDir.isEmpty()) base else resolve(typedDir, base)
        return dir
            .listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            .orEmpty()
    }
}
