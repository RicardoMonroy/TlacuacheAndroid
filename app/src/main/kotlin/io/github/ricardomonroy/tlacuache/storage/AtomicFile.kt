package io.github.ricardomonroy.tlacuache.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Escribe a un temporal y lo renombra: un corte a mitad no deja [file] a medias. Llamar en IO. */
fun writeAtomically(
    file: File,
    text: String,
) {
    val tmp = File(file.parentFile, "${file.name}.tmp")
    tmp.writeText(text)
    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}
