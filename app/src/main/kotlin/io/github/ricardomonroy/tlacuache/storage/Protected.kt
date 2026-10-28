package io.github.ricardomonroy.tlacuache.storage

import java.io.File

/**
 * Carpetas protegidas (ADR-A06): la raíz de cada volumen, las carpetas estándar en la raíz y
 * `.Tlacuache/` no se renombran, mueven ni borran; dentro de `Android/` no se escribe. Su contenido sí se
 * gestiona (salvo `Android/`).
 */
object Protected {
    private val STANDARD =
        setOf(
            "DCIM",
            "Pictures",
            "Movies",
            "Download",
            "Documents",
            "Music",
            "Ringtones",
            "Notifications",
            "Alarms",
            "Podcasts",
            "Audiobooks",
            "Recordings",
            ".Tlacuache",
        )

    /** [file] no se puede mover, renombrar ni borrar. */
    fun cannotRemove(
        file: File,
        volumeRoots: List<File>,
    ): Boolean {
        val f = file.absoluteFile
        return volumeRoots.any { root ->
            f == root || (f.parentFile == root && f.name in STANDARD) || f.startsWith(File(root, "Android"))
        }
    }

    /** Dentro de [dir] no se crea ni se escribe nada. */
    fun cannotWriteInto(
        dir: File,
        volumeRoots: List<File>,
    ): Boolean = volumeRoots.any { dir.absoluteFile.startsWith(File(it, "Android")) }
}
