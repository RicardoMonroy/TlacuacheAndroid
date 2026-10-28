package io.github.ricardomonroy.tlacuache.ops

import io.github.ricardomonroy.tlacuache.ffi.ConflictAction
import io.github.ricardomonroy.tlacuache.ffi.ConflictPolicy
import io.github.ricardomonroy.tlacuache.ffi.Journal
import io.github.ricardomonroy.tlacuache.ffi.MovedItem
import io.github.ricardomonroy.tlacuache.ffi.OpKind
import io.github.ricardomonroy.tlacuache.ffi.Operation
import io.github.ricardomonroy.tlacuache.ffi.Progress
import io.github.ricardomonroy.tlacuache.ffi.nextFreeName
import io.github.ricardomonroy.tlacuache.ffi.replaceAllowed
import io.github.ricardomonroy.tlacuache.storage.Protected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Ejecuta copiar y mover (ARCHITECTURE §7); el modelo (cola, conflictos, deshacer) es del core.
 *
 * - Copiar archivo: a `<nombre>.tlacuache-part` por bloques de 1 MB, con progreso y cancelación entre
 *   bloques; se verifica el tamaño, se conserva la fecha y se renombra. Si falla o se cancela, el `.part`
 *   se borra.
 * - Mover: renombrado atómico; si el sistema no puede (otro volumen), copiar y borrar el origen solo si
 *   su copia terminó.
 */
class OpsRunner(
    /** Raíces de los volúmenes montados, para las carpetas protegidas (ADR-A06). */
    private val volumeRoots: () -> List<File>,
    /** Pregunta a la usuaria qué hacer con un conflicto; suspende hasta la respuesta. */
    private val askConflict: suspend (Conflict) -> Decision,
    /** Renombrado atómico; en las pruebas se sustituye para simular otro volumen. */
    private val atomicMove: (Path, Path) -> Unit = { from, to -> Files.move(from, to, StandardCopyOption.ATOMIC_MOVE) },
) {
    /** El destino [existing] ya existe al copiar o mover [source]. */
    class Conflict(
        val source: File,
        val existing: File,
        val replaceAllowed: Boolean,
    )

    data class Decision(
        val action: ConflictAction,
        val applyToAll: Boolean,
    )

    /** Por qué no se pudo; la UI lo traduce. */
    enum class Failure { INTO_ITSELF, PROTECTED, READ_ONLY_DEST, SIZE_MISMATCH, UNSUPPORTED, IO }

    class FailedException(
        val failure: Failure,
        val path: String? = null,
        cause: Throwable? = null,
    ) : Exception("$failure ${path.orEmpty()}", cause)

    class CancelledException : Exception()

    /**
     * Ejecuta [op] en IO y devuelve lo que hizo realmente. [cancelled] se consulta entre bloques;
     * [progress] recibe el avance; [changed] cada ruta creada o quitada (para MediaStore).
     */
    suspend fun run(
        op: Operation,
        cancelled: () -> Boolean,
        progress: (Progress) -> Unit,
        changed: (File) -> Unit,
    ): Journal =
        withContext(Dispatchers.IO) {
            val dest = op.dest?.let(::File) ?: throw FailedException(Failure.UNSUPPORTED)
            Run(op.sources.map(::File), dest, cancelled, progress, changed).run {
                when (op.kind) {
                    OpKind.COPY -> copyAll()
                    OpKind.MOVE -> moveAll()
                    else -> throw FailedException(Failure.UNSUPPORTED)
                }
            }
        }

    private inner class Run(
        val sources: List<File>,
        val dest: File,
        val cancelled: () -> Boolean,
        val report: (Progress) -> Unit,
        val changed: (File) -> Unit,
    ) {
        val roots = volumeRoots()
        val policy = ConflictPolicy()
        val pairs = mutableListOf<MovedItem>()
        var merged = false
        var bytesDone = 0L
        var filesDone = 0L
        val bytesTotal = sources.sumOf(::sizeOf)
        val filesTotal = sources.sumOf(::countOf)

        fun journal() = Journal(pairs, merged, emptyList(), emptyList())

        suspend fun copyAll(): Journal {
            checkDest()
            for (src in sources) {
                if (dest.absoluteFile.startsWith(src.absoluteFile)) throw FailedException(Failure.INTO_ITSELF, src.path)
                copyInto(src, dest)?.let { pairs += MovedItem(src.path, it.path) }
            }
            return journal()
        }

        suspend fun moveAll(): Journal {
            checkDest()
            for (src in sources) {
                if (Protected.cannotRemove(src, roots)) throw FailedException(Failure.PROTECTED, src.path)
                if (dest.absoluteFile.startsWith(src.absoluteFile)) throw FailedException(Failure.INTO_ITSELF, src.path)
                moveInto(src, dest)?.let { pairs += MovedItem(src.path, it.path) }
            }
            return journal()
        }

        fun checkDest() {
            if (Protected.cannotWriteInto(dest, roots)) throw FailedException(Failure.READ_ONLY_DEST, dest.path)
        }

        /** Copia [src] dentro de [dir]; devuelve dónde quedó, o `null` si se omitió. */
        suspend fun copyInto(
            src: File,
            dir: File,
        ): File? {
            val target = resolveConflict(src, File(dir, src.name)) ?: return null.also { skipped(src) }
            if (src.isDirectory) {
                if (!target.isDirectory && !target.mkdir()) throw FailedException(Failure.IO, target.path)
                changed(target)
                src
                    .listFiles()
                    .orEmpty()
                    .sortedBy { it.name }
                    .forEach { copyInto(it, target) }
            } else {
                copyFile(src, target)
            }
            return target
        }

        /** Mueve [src] dentro de [dir]; devuelve dónde quedó, o `null` si se omitió. */
        suspend fun moveInto(
            src: File,
            dir: File,
        ): File? {
            val wanted = File(dir, src.name)
            // Mover a su propia carpeta no hace nada.
            if (wanted.absoluteFile == src.absoluteFile) return null.also { skipped(src) }
            val target = resolveConflict(src, wanted) ?: return null.also { skipped(src) }
            if (src.isDirectory && target.isDirectory) {
                // «Reemplazar» con carpetas combina: se mueve el contenido y se quita la carpeta vacía.
                src
                    .listFiles()
                    .orEmpty()
                    .sortedBy { it.name }
                    .forEach { moveInto(it, target) }
                if (src.listFiles().isNullOrEmpty()) src.delete()
                changed(src)
                return target
            }
            checkCancelled()
            try {
                atomicMove(src.toPath(), target.toPath())
                bytesDone += sizeOf(src)
                filesDone += countOf(src)
                publish(src.name)
                changed(src)
                changed(target)
            } catch (_: AtomicMoveNotSupportedException) {
                // Otro volumen: copiar y borrar el origen solo con la copia completa.
                if (src.isDirectory) {
                    if (!target.mkdir()) throw FailedException(Failure.IO, target.path)
                    src
                        .listFiles()
                        .orEmpty()
                        .sortedBy { it.name }
                        .forEach { copyInto(it, target) }
                } else {
                    copyFile(src, target)
                }
                if (!src.deleteRecursively()) throw FailedException(Failure.IO, src.path)
                changed(src)
            } catch (e: IOException) {
                throw FailedException(Failure.IO, src.path, e)
            }
            return target
        }

        /**
         * Destino final de [src] si [wanted] ya existe, según la política del core o la respuesta de la
         * usuaria; `null` = omitir. Reemplazar un archivo lo quita aquí: el nuevo ocupa su nombre.
         */
        suspend fun resolveConflict(
            src: File,
            wanted: File,
        ): File? {
            if (!wanted.exists()) return wanted
            val same = wanted.absoluteFile == src.absoluteFile
            val allowed = replaceAllowed(src.isDirectory, wanted.isDirectory, same)
            val action =
                policy.remembered(allowed) ?: askConflict(Conflict(src, wanted, allowed))
                    .also {
                        if (it.applyToAll) policy.remember(it.action)
                    }.action
            return when (action) {
                ConflictAction.SKIP -> {
                    null
                }

                ConflictAction.KEEP_BOTH -> {
                    val existing =
                        wanted.parentFile
                            ?.list()
                            .orEmpty()
                            .toList()
                    File(wanted.parentFile, nextFreeName(src.name, src.isDirectory, existing))
                }

                ConflictAction.REPLACE -> {
                    if (!allowed) return null
                    if (wanted.isDirectory) merged = true
                    wanted
                }
            }
        }

        /** Copia por bloques a `.tlacuache-part` y renombra al terminar; el `.part` nunca queda. */
        fun copyFile(
            src: File,
            target: File,
        ) {
            val part = File(target.parentFile, "${target.name}$PART_SUFFIX")
            try {
                src.inputStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(BLOCK)
                        while (true) {
                            checkCancelled()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            bytesDone += read
                            publish(src.name)
                        }
                        output.fd.sync()
                    }
                }
                if (part.length() != src.length()) throw FailedException(Failure.SIZE_MISMATCH, src.path)
                part.setLastModified(src.lastModified())
                Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                filesDone++
                publish(src.name)
                changed(target)
            } catch (e: IOException) {
                throw FailedException(Failure.IO, src.path, e)
            } finally {
                part.delete()
            }
        }

        fun skipped(src: File) {
            bytesDone += sizeOf(src)
            filesDone += countOf(src)
            publish(src.name)
        }

        fun checkCancelled() {
            if (cancelled()) throw CancelledException()
        }

        fun publish(current: String) =
            report(
                Progress(
                    bytesDone.toULong(),
                    bytesTotal.toULong(),
                    filesDone.toULong(),
                    filesTotal.toULong(),
                    current,
                ),
            )
    }

    companion object {
        const val PART_SUFFIX = ".tlacuache-part"
        private const val BLOCK = 1 shl 20

        private fun sizeOf(f: File): Long =
            if (f.isDirectory) f.walk().filter(File::isFile).sumOf(File::length) else f.length()

        private fun countOf(f: File): Long = if (f.isDirectory) f.walk().count { it.isFile }.toLong() else 1
    }
}
