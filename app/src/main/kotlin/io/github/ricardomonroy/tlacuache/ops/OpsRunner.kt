package io.github.ricardomonroy.tlacuache.ops

import io.github.ricardomonroy.tlacuache.ffi.ConflictAction
import io.github.ricardomonroy.tlacuache.ffi.ConflictPolicy
import io.github.ricardomonroy.tlacuache.ffi.Journal
import io.github.ricardomonroy.tlacuache.ffi.MovedItem
import io.github.ricardomonroy.tlacuache.ffi.Newer
import io.github.ricardomonroy.tlacuache.ffi.OpKind
import io.github.ricardomonroy.tlacuache.ffi.Operation
import io.github.ricardomonroy.tlacuache.ffi.Progress
import io.github.ricardomonroy.tlacuache.ffi.newer
import io.github.ricardomonroy.tlacuache.ffi.nextFreeName
import io.github.ricardomonroy.tlacuache.ffi.replaceAllowed
import io.github.ricardomonroy.tlacuache.storage.Protected
import io.github.ricardomonroy.tlacuache.storage.TrashStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
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
    /** Papelera propia (ADR-A05). */
    private val trash: TrashStore = TrashStore(volumeRoots),
    /** Lo que llega de otra app (`content://`): nombre, tamaño si se sabe y cómo leerlo. */
    private val contentSource: (String) -> ContentSource? = { null },
) {
    class ContentSource(
        val name: String,
        val size: Long?,
        val open: () -> InputStream,
    )

    /** El destino [existing] ya existe al copiar o mover [source]. */
    class Conflict(
        val source: File,
        val existing: File,
        val replaceAllowed: Boolean,
        /** Lo que llega y lo que ya está, leídos en IO para que el diálogo los compare. */
        val incoming: Side,
        val current: Side,
        /** Cuál es más reciente (`core::ops::newer`, con un segundo de margen); `null` si falta una fecha. */
        val newer: Newer?,
    ) {
        constructor(source: File, existing: File, replaceAllowed: Boolean) : this(
            source,
            existing,
            replaceAllowed,
            Side.of(source),
            Side.of(existing),
            newer(Side.of(existing).modifiedMs, Side.of(source).modifiedMs),
        )
    }

    /** Un lado del conflicto: carpeta o archivo, tamaño (archivos) y fecha de modificación. */
    data class Side(
        val isDir: Boolean,
        val size: Long,
        val modifiedMs: Long?,
    ) {
        companion object {
            fun of(f: File) =
                Side(f.isDirectory, if (f.isDirectory) 0 else f.length(), f.lastModified().takeIf { it > 0 })
        }
    }

    data class Decision(
        val action: ConflictAction,
        val applyToAll: Boolean,
    )

    /** Por qué no se pudo; la UI lo traduce. */
    enum class Failure {
        INTO_ITSELF,
        PROTECTED,
        READ_ONLY_DEST,
        SIZE_MISMATCH,
        NO_TRASH,
        NOT_IN_TRASH,
        EXISTS,
        UNSUPPORTED,
        IO,
    }

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
            Run(op.sources.map(::File), op.dest?.let(::File), cancelled, progress, changed).run {
                when (op.kind) {
                    // Desde otra app (arrastrar y soltar): URIs `content://` (A4.4).
                    OpKind.COPY -> {
                        if (op.sources.any {
                                it.startsWith(
                                    "content://",
                                )
                            }
                        ) {
                            importAll(op.sources)
                        } else {
                            copyAll()
                        }
                    }

                    OpKind.MOVE -> {
                        moveAll()
                    }

                    OpKind.TRASH -> {
                        trashAll()
                    }

                    OpKind.DELETE -> {
                        deleteAll()
                    }

                    OpKind.UNTRASH -> {
                        untrashAll()
                    }

                    OpKind.RESTORE -> {
                        restoreAll(op)
                    }

                    OpKind.RENAME -> {
                        rename()
                    }

                    OpKind.MKDIR -> {
                        create(directory = true)
                    }

                    OpKind.CREATE_FILE -> {
                        create(directory = false)
                    }

                    else -> {
                        throw FailedException(Failure.UNSUPPORTED)
                    }
                }
            }
        }

    private inner class Run(
        val sources: List<File>,
        val target: File?,
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

        val trashed = mutableListOf<String>()
        val dest: File get() = target ?: throw FailedException(Failure.UNSUPPORTED)

        val created = mutableListOf<String>()

        fun journal() = Journal(pairs, merged, trashed, created)

        /** Renombrar en su carpeta: [dest] es el elemento ya renombrado. El diálogo validó el nombre con el core. */
        fun rename(): Journal {
            val src = sources.single()
            if (Protected.cannotRemove(src, roots)) throw FailedException(Failure.PROTECTED, src.path)
            val to = dest
            if (to.exists()) throw FailedException(Failure.EXISTS, to.path)
            try {
                atomicMove(src.toPath(), to.toPath())
            } catch (e: IOException) {
                throw FailedException(Failure.IO, src.path, e)
            }
            pairs += MovedItem(src.path, to.path)
            changed(src)
            done(to)
            return journal()
        }

        /** Carpeta o archivo vacío nuevo; deshacer lo manda a la papelera. */
        fun create(directory: Boolean): Journal {
            val item = sources.single()
            val parent = item.parentFile ?: throw FailedException(Failure.IO, item.path)
            if (Protected.cannotWriteInto(parent, roots)) throw FailedException(Failure.READ_ONLY_DEST, parent.path)
            if (item.exists()) throw FailedException(Failure.EXISTS, item.path)
            val ok =
                try {
                    if (directory) item.mkdir() else item.createNewFile()
                } catch (e: IOException) {
                    throw FailedException(Failure.IO, item.path, e)
                }
            if (!ok) throw FailedException(Failure.IO, item.path)
            created += item.absolutePath
            done(item)
            return journal()
        }

        /** A la papelera de su volumen (renombrado instantáneo); deshacer los devuelve. */
        fun trashAll(): Journal {
            for (src in sources) {
                checkCancelled()
                if (Protected.cannotRemove(src, roots)) throw FailedException(Failure.PROTECTED, src.path)
                try {
                    trash.trash(src)
                } catch (e: IOException) {
                    throw FailedException(Failure.NO_TRASH, src.path, e)
                }
                trashed += src.absolutePath
                done(src)
            }
            return journal()
        }

        /** Borrado permanente (la UI ya pidió confirmación); también vacía elementos de la papelera. */
        fun deleteAll(): Journal {
            for (src in sources) {
                checkCancelled()
                if (Protected.cannotRemove(src, roots)) throw FailedException(Failure.PROTECTED, src.path)
                val size = sizeOf(src)
                val count = countOf(src)
                if (!src.deleteRecursively()) throw FailedException(Failure.IO, src.path)
                trash.forget(src)
                bytesDone += size
                filesDone += count
                publish(src.name)
                changed(src)
            }
            return journal()
        }

        /**
         * Saca de la papelera: cada origen es un elemento de la papelera o la ruta original de lo último que se
         * envió desde ahí (deshacer). Si su sitio está ocupado, se pregunta como en un conflicto.
         */
        suspend fun untrashAll(): Journal {
            for (src in sources) {
                checkCancelled()
                val item = trash.find(src) ?: throw FailedException(Failure.NOT_IN_TRASH, src.path)
                val restored = resolveConflict(item.file, item.originalPath) ?: continue
                if (item.file.isDirectory && restored.isDirectory) {
                    // «Combinar»: el contenido pasa a la carpeta que ya existe.
                    item.file
                        .listFiles()
                        .orEmpty()
                        .forEach { moveInto(it, restored) }
                    trash.delete(item)
                } else {
                    try {
                        trash.restore(item, restored)
                    } catch (e: IOException) {
                        throw FailedException(Failure.IO, src.path, e)
                    }
                }
                pairs += MovedItem(item.file.path, restored.path)
                done(restored)
            }
            return journal()
        }

        fun done(file: File) {
            filesDone++
            publish(file.name)
            changed(file)
        }

        suspend fun copyAll(): Journal {
            checkDest()
            for (src in sources) {
                if (dest.absoluteFile.startsWith(src.absoluteFile)) throw FailedException(Failure.INTO_ITSELF, src.path)
                copyInto(src, dest)?.let { pairs += MovedItem(src.path, it.path) }
            }
            return journal()
        }

        /**
         * Copia lo que llega de otra app: nombre y tamaño del proveedor, con un nombre libre en el destino (nunca
         * reemplaza nada: no hay un archivo con el que comparar).
         */
        fun importAll(uris: List<String>): Journal {
            checkDest()
            for (uri in uris) {
                checkCancelled()
                val source = contentSource(uri) ?: throw FailedException(Failure.IO, uri)
                val name = source.name.replace('/', '_').ifBlank { uri.substringAfterLast('/') }
                val target = File(dest, nextFreeName(name, false, dest.list().orEmpty().toList()))
                copyStream(uri, name, target, source.size, null, source.open)
                pairs += MovedItem(uri, target.path)
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
            return moveResolved(src, target)
        }

        /**
         * Deshacer mover: cada origen vuelve a su ruta exacta en `targets` (puede tener otro nombre, p. ej. si
         * al moverlo quedó «a (2).txt»). Si esa ruta está ocupada, se pregunta como en un conflicto.
         */
        suspend fun restoreAll(op: Operation): Journal {
            for ((src, back) in sources.zip(op.targets.map(::File))) {
                val parent = back.parentFile ?: throw FailedException(Failure.IO, back.path)
                if (!parent.isDirectory && !parent.mkdirs()) throw FailedException(Failure.IO, parent.path)
                val target = resolveConflict(src, back) ?: continue.also { skipped(src) }
                if (src.isDirectory && target.isDirectory) {
                    src.listFiles().orEmpty().forEach { moveInto(it, target) }
                    if (src.listFiles().isNullOrEmpty()) src.delete()
                } else {
                    moveResolved(src, target)
                }
                pairs += MovedItem(src.path, target.path)
            }
            return journal()
        }

        /** Mueve [src] a [target] (ya libre o a reemplazar): renombrado atómico o, entre volúmenes, copiar y borrar. */
        suspend fun moveResolved(
            src: File,
            target: File,
        ): File {
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

        fun copyFile(
            src: File,
            target: File,
        ) = copyStream(src.path, src.name, target, src.length(), src.lastModified(), src::inputStream)

        /**
         * Copia por bloques a `.tlacuache-part` y renombra al terminar; el `.part` nunca queda. Verifica el tamaño
         * si se conoce ([expected]) y conserva la fecha si la hay ([modified]).
         */
        fun copyStream(
            origin: String,
            label: String,
            target: File,
            expected: Long?,
            modified: Long?,
            open: () -> InputStream,
        ) {
            val part = File(target.parentFile, "${target.name}$PART_SUFFIX")
            try {
                open().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(BLOCK)
                        while (true) {
                            checkCancelled()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            bytesDone += read
                            publish(label)
                        }
                        output.fd.sync()
                    }
                }
                if (expected != null && part.length() != expected) throw FailedException(Failure.SIZE_MISMATCH, origin)
                modified?.let(part::setLastModified)
                Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                filesDone++
                publish(label)
                changed(target)
            } catch (e: IOException) {
                throw FailedException(Failure.IO, origin, e)
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
