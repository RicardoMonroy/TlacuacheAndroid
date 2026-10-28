package io.github.ricardomonroy.tlacuache.storage

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Tamaño de un conjunto de archivos y carpetas, recorridas hasta el fondo (Propiedades). */
data class Measure(
    val files: Long,
    /** Subcarpetas dentro de lo medido (las elegidas no cuentan). */
    val dirs: Long,
    val bytes: Long,
)

/**
 * Recorre [items] sin seguir enlaces simbólicos y salta lo que no se puede leer. [isActive] permite cortar
 * el recorrido (se cerró el diálogo). Llamar en IO.
 */
fun measure(
    items: List<File>,
    isActive: () -> Boolean = { true },
): Measure {
    var files = 0L
    var dirs = 0L
    var bytes = 0L
    for (item in items) {
        Files.walkFileTree(
            item.toPath(),
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    if (!isActive()) return FileVisitResult.TERMINATE
                    if (dir != item.toPath()) dirs++
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    files++
                    bytes += attrs.size()
                    return if (isActive()) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
                }

                override fun visitFileFailed(
                    file: Path,
                    exc: IOException,
                ) = FileVisitResult.CONTINUE
            },
        )
    }
    return Measure(files, dirs, bytes)
}
