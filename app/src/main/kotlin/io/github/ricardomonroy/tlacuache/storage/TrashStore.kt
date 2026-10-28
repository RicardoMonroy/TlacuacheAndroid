package io.github.ricardomonroy.tlacuache.storage

import io.github.ricardomonroy.tlacuache.ffi.nextFreeName
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Papelera propia por volumen (ADR-A05, ARCHITECTURE §8): `<raíz>/.Tlacuache/Papelera/{files,info}` con el
 * formato de freedesktop y un `.nomedia` en `.Tlacuache/`. Enviar es renombrar dentro del mismo volumen
 * (instantáneo). Hace E/S bloqueante: llamar en IO.
 */
class TrashStore(
    private val volumeRoots: () -> List<File>,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Un elemento en la papelera: dónde está ahora y de dónde salió. */
    data class Item(
        val file: File,
        val info: File,
        val originalPath: File,
        val deletedAtMs: Long,
    )

    /** Envía [file] a la papelera de su volumen; si el nombre está ocupado ahí, usa «nombre (2)». */
    fun trash(file: File): Item {
        val root = volumeRootOf(file) ?: throw IOException("fuera de los volúmenes: $file")
        val base = trashDir(root)
        val files = File(base, FILES)
        val infos = File(base, INFO)
        if (!files.isDirectory && !files.mkdirs()) throw IOException("no se pudo crear $files")
        if (!infos.isDirectory && !infos.mkdirs()) throw IOException("no se pudo crear $infos")
        // La galería no indexa lo que hay en la papelera.
        File(root, "$APP_DIR/.nomedia").takeUnless(File::exists)?.createNewFile()
        val taken = files.list().orEmpty().toList() + infos.list().orEmpty().map { it.removeSuffix(INFO_SUFFIX) }
        val name = nextFreeName(file.name, file.isDirectory, taken)
        val deletedAt = now()
        // Primero el .trashinfo (como freedesktop): si algo falla después, solo queda un .trashinfo huérfano.
        val info = File(infos, name + INFO_SUFFIX)
        info.writeText(
            "[Trash Info]\nPath=${encodePath(file.absolutePath)}\nDeletionDate=${format(deletedAt)}\n",
        )
        val target = File(files, name)
        try {
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            info.delete()
            throw e
        }
        return Item(target, info, file.absoluteFile, deletedAt)
    }

    /** Todo lo que hay en las papeleras de los volúmenes montados, lo más reciente primero. */
    fun items(): List<Item> =
        volumeRoots()
            .flatMap { root ->
                val base = trashDir(root)
                File(base, INFO).listFiles { f -> f.name.endsWith(INFO_SUFFIX) }.orEmpty().mapNotNull { info ->
                    val file = File(File(base, FILES), info.name.removeSuffix(INFO_SUFFIX))
                    if (file.exists()) parse(info, file) else null
                }
            }.sortedByDescending { it.deletedAtMs }

    /**
     * El elemento que corresponde a [path]: si está dentro de una papelera, ese mismo; si es una ruta
     * original, lo último que se envió desde ahí (deshacer «enviar a la papelera»).
     */
    fun find(path: File): Item? {
        val all = items()
        return all.firstOrNull { it.file.absoluteFile == path.absoluteFile }
            ?: all.firstOrNull { it.originalPath == path.absoluteFile }
    }

    /** Devuelve [item] a [dest] (su ruta original salvo conflicto); recrea la carpeta si ya no existe. */
    fun restore(
        item: Item,
        dest: File = item.originalPath,
    ): File {
        val parent = dest.parentFile ?: throw IOException("sin carpeta: $dest")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("no se pudo crear $parent")
        Files.move(item.file.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE)
        item.info.delete()
        return dest
    }

    /** Borrado permanente de [item]. */
    fun delete(item: Item) {
        if (!item.file.deleteRecursively()) throw IOException("no se pudo borrar ${item.file}")
        item.info.delete()
    }

    /** Si [file] está en una papelera, quita su `.trashinfo` (tras borrarlo de forma permanente). */
    fun forget(file: File) {
        val dir = file.absoluteFile.parentFile ?: return
        if (dir.name == FILES && dir.parentFile?.name == TRASH_DIR) {
            File(File(dir.parentFile, INFO), file.name + INFO_SUFFIX).delete()
        }
    }

    /** Purga al arrancar: borra lo que lleva más de [maxAgeMs] en la papelera. Devuelve cuántos. */
    fun purge(maxAgeMs: Long = PURGE_AFTER_MS): Int {
        val limit = now() - maxAgeMs
        return items().filter { it.deletedAtMs < limit }.count { runCatching { delete(it) }.isSuccess }
    }

    private fun volumeRootOf(file: File) = volumeRoots().firstOrNull { file.absoluteFile.startsWith(it) }

    private fun parse(
        info: File,
        file: File,
    ): Item? {
        val lines =
            try {
                info.readLines()
            } catch (_: IOException) {
                return null
            }
        val values =
            lines.mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }.associate {
                it[0] to
                    it[1]
            }
        val path = values["Path"]?.let(::decodePath) ?: return null
        val deletedAt =
            try {
                values["DeletionDate"]?.let {
                    LocalDateTime
                        .parse(it)
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli()
                }
            } catch (_: DateTimeParseException) {
                null
            } ?: info.lastModified()
        return Item(file, info, File(path), deletedAt)
    }

    private fun format(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone).format(DATE)

    companion object {
        const val APP_DIR = ".Tlacuache"
        private const val TRASH_DIR = "Papelera"
        private const val FILES = "files"
        private const val INFO = "info"
        private const val INFO_SUFFIX = ".trashinfo"
        const val PURGE_AFTER_MS = 30L * 24 * 60 * 60 * 1000
        private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        fun trashDir(root: File) = File(root, "$APP_DIR/$TRASH_DIR")

        /** `Path` va escapado como URL (freedesktop): todo salvo letras, cifras, `-._~` y `/`. */
        fun encodePath(path: String): String =
            buildString {
                for (b in path.toByteArray(StandardCharsets.UTF_8)) {
                    val c = b.toInt() and 0xFF
                    if ((c < 0x80 && c.toChar().isLetterOrDigit()) || c.toChar() in "-._~/") {
                        append(c.toChar())
                    } else {
                        append('%').append("%02X".format(c))
                    }
                }
            }

        // Nunca se escribe «+» sin escapar: decodificar con URLDecoder es seguro.
        fun decodePath(value: String): String = URLDecoder.decode(value, "UTF-8")
    }
}
