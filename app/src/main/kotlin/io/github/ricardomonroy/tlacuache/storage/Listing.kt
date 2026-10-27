package io.github.ricardomonroy.tlacuache.storage

import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** Lectura de carpetas en segundo plano, por lotes (ARCHITECTURE §6, ADR-A10). */
object Listing {
    /** Primer lote pequeño para que la lista aparezca rápido; luego lotes grandes para no reordenar de más. */
    const val FIRST_BATCH = 200
    const val BATCH = 2_000

    /**
     * Emite la lista acumulada de [dir]: tras [FIRST_BATCH] entradas, luego cada [BATCH] y al final.
     * Termina con [IOException] si la carpeta no se puede leer. Cancelable entre entradas.
     * [volumeRoot] indica que [dir] es la raíz de un volumen (afecta a qué se excluye).
     */
    fun read(
        dir: File,
        volumeRoot: Boolean,
    ): Flow<List<FileEntry>> =
        flow {
            val entries = ArrayList<FileEntry>()
            var nextEmit = FIRST_BATCH
            Files.newDirectoryStream(dir.toPath()).use { stream ->
                for (path in stream) {
                    currentCoroutineContext().ensureActive()
                    val entry = entry(path)
                    if (isExcluded(entry.name, entry.isDir, volumeRoot)) continue
                    entries += entry
                    if (entries.size == nextEmit) {
                        emit(entries.toList())
                        nextEmit += BATCH
                    }
                }
            }
            emit(entries.toList())
        }.flowOn(Dispatchers.IO)

    private fun entry(path: Path): FileEntry {
        val name = path.fileName.toString()
        // Un enlace roto o un archivo que desaparece a mitad de la lectura no tumba el listado.
        val attrs =
            try {
                Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            } catch (_: IOException) {
                null
            }
        val isDir = attrs?.isDirectory ?: false
        return FileEntry(
            name = name,
            isDir = isDir,
            isHidden = name.startsWith('.'),
            size = if (isDir) 0u else (attrs?.size() ?: 0L).toULong(),
            modifiedMs = attrs?.lastModifiedTime()?.toMillis(),
        )
    }

    /**
     * `Android/` en la raíz de un volumen no se lista, ni con los ocultos visibles (ADR-A06): dentro solo
     * hay datos de otras apps, de solo lectura en el mejor caso.
     *
     * shortcut: siempre excluida; mostrarla con aviso desde Preferencias llega en A5.2.
     */
    fun isExcluded(
        name: String,
        isDir: Boolean,
        volumeRoot: Boolean,
    ): Boolean = volumeRoot && isDir && name == "Android"
}
