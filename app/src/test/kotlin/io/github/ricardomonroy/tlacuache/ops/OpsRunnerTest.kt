package io.github.ricardomonroy.tlacuache.ops

import io.github.ricardomonroy.tlacuache.ffi.ConflictAction
import io.github.ricardomonroy.tlacuache.ffi.Journal
import io.github.ricardomonroy.tlacuache.ffi.MovedItem
import io.github.ricardomonroy.tlacuache.ffi.OpKind
import io.github.ricardomonroy.tlacuache.ffi.OpState
import io.github.ricardomonroy.tlacuache.ffi.Operation
import io.github.ricardomonroy.tlacuache.ffi.Progress
import io.github.ricardomonroy.tlacuache.ffi.undoRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException

class OpsRunnerTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var src: File
    private lateinit var dst: File
    private var answer = OpsRunner.Decision(ConflictAction.KEEP_BOTH, applyToAll = false)
    private var asked = 0

    @Before
    fun setUp() {
        root = tmp.newFolder("0")
        src = File(root, "Origen").apply { mkdir() }
        dst = File(root, "Destino").apply { mkdir() }
    }

    private fun runner(otherVolume: Boolean = false) =
        OpsRunner(
            volumeRoots = { listOf(root) },
            askConflict = {
                asked++
                answer
            },
            atomicMove =
                if (otherVolume) {
                    { a, b -> throw AtomicMoveNotSupportedException(a.toString(), b.toString(), "otro volumen") }
                } else {
                    { a, b ->
                        java.nio.file.Files
                            .move(a, b, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                    }
                },
        )

    private fun op(
        kind: OpKind,
        vararg sources: File,
        dest: File = dst,
    ) = Operation(
        1u,
        kind,
        sources.map {
            it.path
        },
        dest.path,
        emptyList(),
        OpState.Running,
        Progress(0u, null, 0u, null, null),
        null,
        false,
    )

    private fun file(
        dir: File,
        name: String,
        size: Int,
    ) = File(dir, name).apply { writeBytes(ByteArray(size) { (it % 251).toByte() }) }

    private fun run(
        op: Operation,
        runner: OpsRunner = runner(),
        cancelled: () -> Boolean = { false },
        progress: MutableList<Progress> = mutableListOf(),
    ): Journal = runBlocking { runner.run(op, cancelled, { progress += it }, {}) }

    private fun parts(dir: File) = dir.walk().filter { it.name.endsWith(OpsRunner.PART_SUFFIX) }.toList()

    @Test
    fun copiesFilesAndFoldersWithProgressDateAndNoPartLeft() {
        val big = file(src, "video.mp4", 3 * (1 shl 20) + 7).apply { setLastModified(1_600_000_000_000) }
        val album = File(src, "Álbum").apply { mkdir() }
        file(album, "a.jpg", 10)
        val progress = mutableListOf<Progress>()
        val journal = run(op(OpKind.COPY, big, album), progress = progress)

        assertArrayEquals(big.readBytes(), File(dst, "video.mp4").readBytes())
        assertEquals(1_600_000_000_000, File(dst, "video.mp4").lastModified())
        assertEquals(10L, File(dst, "Álbum/a.jpg").length())
        assertTrue(big.exists())
        assertTrue(parts(dst).isEmpty())
        assertEquals(
            listOf(MovedItem(big.path, File(dst, "video.mp4").path), MovedItem(album.path, File(dst, "Álbum").path)),
            journal.pairs,
        )
        val last = progress.last()
        assertEquals(last.bytesTotal, last.bytesDone)
        assertEquals(2uL, last.filesDone)
    }

    @Test
    fun cancellingMidwayRemovesThePart() {
        val big = file(src, "grande.bin", 4 * (1 shl 20))
        var blocks = 0
        try {
            run(op(OpKind.COPY, big), cancelled = { ++blocks > 2 })
            fail("debía cancelarse")
        } catch (_: OpsRunner.CancelledException) {
        }
        assertTrue(parts(dst).isEmpty())
        assertFalse(File(dst, "grande.bin").exists())
    }

    @Test
    fun conflictsFollowTheAnswerAndApplyToAll() {
        file(src, "a.txt", 1)
        file(src, "b.txt", 2)
        file(dst, "a.txt", 5)
        file(dst, "b.txt", 5)
        answer = OpsRunner.Decision(ConflictAction.KEEP_BOTH, applyToAll = true)
        run(op(OpKind.COPY, File(src, "a.txt"), File(src, "b.txt")))
        assertEquals(1, asked)
        assertEquals(1L, File(dst, "a (2).txt").length())
        assertEquals(2L, File(dst, "b (2).txt").length())

        answer = OpsRunner.Decision(ConflictAction.REPLACE, applyToAll = false)
        run(op(OpKind.COPY, File(src, "a.txt")))
        assertEquals(1L, File(dst, "a.txt").length())

        answer = OpsRunner.Decision(ConflictAction.SKIP, applyToAll = false)
        val journal = run(op(OpKind.COPY, File(src, "b.txt")))
        assertEquals(5L, File(dst, "b.txt").length())
        assertTrue(journal.pairs.isEmpty())
    }

    @Test
    fun copyIntoTheSameFolderKeepsBoth() {
        val a = file(src, "foto.jpg", 3)
        run(op(OpKind.COPY, a, dest = src))
        assertTrue(File(src, "foto (2).jpg").exists())
    }

    @Test
    fun movesAtomicallyOrCopiesThenDeletesAcrossVolumes() {
        val a = file(src, "a.txt", 4)
        val journal = run(op(OpKind.MOVE, a))
        assertFalse(a.exists())
        assertEquals(4L, File(dst, "a.txt").length())
        assertEquals(listOf(MovedItem(a.path, File(dst, "a.txt").path)), journal.pairs)

        val album = File(src, "Álbum").apply { mkdir() }
        file(album, "b.jpg", 1 shl 20)
        run(op(OpKind.MOVE, album), runner(otherVolume = true))
        assertFalse(album.exists())
        assertEquals((1 shl 20).toLong(), File(dst, "Álbum/b.jpg").length())
        assertTrue(parts(dst).isEmpty())
    }

    @Test
    fun cancelledCrossVolumeMoveKeepsTheSource() {
        val a = file(src, "a.bin", 3 * (1 shl 20))
        var blocks = 0
        runCatching { run(op(OpKind.MOVE, a), runner(otherVolume = true), cancelled = { ++blocks > 1 }) }
        assertTrue(a.exists())
        assertTrue(parts(dst).isEmpty())
    }

    @Test
    fun refusesProtectedFoldersAndCopiesIntoThemselves() {
        val dcim = File(root, "DCIM").apply { mkdir() }
        expectFailure(OpsRunner.Failure.PROTECTED) { run(op(OpKind.MOVE, dcim)) }
        expectFailure(
            OpsRunner.Failure.INTO_ITSELF,
        ) { run(op(OpKind.COPY, src, dest = File(src, "dentro").apply { mkdir() })) }
        val android = File(root, "Android/data").apply { mkdirs() }
        expectFailure(OpsRunner.Failure.READ_ONLY_DEST) { run(op(OpKind.COPY, file(src, "x", 1), dest = android)) }
        // Copiar DCIM sí se puede: solo moverla o borrarla está prohibido.
        run(op(OpKind.COPY, dcim))
        assertTrue(File(dst, "DCIM").isDirectory)
    }

    @Test
    fun trashUndoAndPermanentDelete() {
        val a = file(src, "a.txt", 3)
        val album = File(src, "Álbum").apply { mkdir() }
        file(album, "b.jpg", 4)
        val journal = run(op(OpKind.TRASH, a, album))
        assertFalse(a.exists())
        assertFalse(album.exists())
        assertEquals(listOf(a.absolutePath, album.absolutePath), journal.trashed)
        assertTrue(File(root, ".Tlacuache/Papelera/files/Álbum/b.jpg").exists())

        // Deshacer: las rutas originales vuelven a su sitio; si está ocupado, se pregunta.
        file(src, "a.txt", 9)
        answer = OpsRunner.Decision(ConflictAction.KEEP_BOTH, applyToAll = false)
        run(op(OpKind.UNTRASH, a, album))
        assertEquals(9L, a.length())
        assertEquals(3L, File(src, "a (2).txt").length())
        assertEquals(4L, File(album, "b.jpg").length())

        // Borrar para siempre un elemento de la papelera quita también su .trashinfo.
        run(op(OpKind.TRASH, a))
        val inTrash = File(root, ".Tlacuache/Papelera/files/a.txt")
        run(op(OpKind.DELETE, inTrash))
        assertFalse(inTrash.exists())
        assertFalse(File(root, ".Tlacuache/Papelera/info/a.txt.trashinfo").exists())
        expectFailure(OpsRunner.Failure.PROTECTED) { run(op(OpKind.TRASH, File(root, "DCIM").apply { mkdir() })) }
    }

    @Test
    fun renameAndCreateWithUndoRequests() {
        val a = file(src, "a.txt", 2)
        val renamed = File(src, "b.txt")
        val journal = run(op(OpKind.RENAME, a, dest = renamed))
        assertFalse(a.exists())
        assertTrue(renamed.exists())
        assertEquals(listOf(MovedItem(a.path, renamed.path)), journal.pairs)
        expectFailure(
            OpsRunner.Failure.EXISTS,
        ) { run(op(OpKind.RENAME, renamed, dest = File(src, "Álbum").apply { mkdir() })) }
        expectFailure(OpsRunner.Failure.PROTECTED) {
            run(op(OpKind.RENAME, File(root, "DCIM").apply { mkdir() }, dest = File(root, "Fotos")))
        }

        val folder = File(src, "Carpeta nueva")
        assertEquals(listOf(folder.absolutePath), run(op(OpKind.MKDIR, folder)).created)
        assertTrue(folder.isDirectory)
        val empty = File(src, "Archivo nuevo.txt")
        run(op(OpKind.CREATE_FILE, empty))
        assertEquals(0L, empty.length())
        expectFailure(OpsRunner.Failure.EXISTS) { run(op(OpKind.MKDIR, folder)) }
        expectFailure(OpsRunner.Failure.READ_ONLY_DEST) { run(op(OpKind.MKDIR, File(root, "Android/data/x"))) }
    }

    @Test
    fun undoingAMoveRestoresTheExactNames() {
        val a = file(src, "a.txt", 3)
        file(dst, "a.txt", 7)
        answer = OpsRunner.Decision(ConflictAction.KEEP_BOTH, applyToAll = false)
        val journal = run(op(OpKind.MOVE, a))
        val undo = checkNotNull(undoRequest(OpKind.MOVE, journal))
        assertEquals(OpKind.RESTORE, undo.kind)
        run(
            Operation(
                2u,
                undo.kind,
                undo.sources,
                undo.dest,
                undo.targets,
                OpState.Running,
                Progress(0u, null, 0u, null, null),
                null,
                false,
            ),
        )
        assertEquals(3L, a.length())
        assertFalse(File(dst, "a (2).txt").exists())
        assertEquals(7L, File(dst, "a.txt").length())
    }

    @Test
    fun importsFromOtherAppsWithAFreeNameAndNoPartLeft() {
        file(dst, "foto.jpg", 1)
        val provider =
            mapOf(
                "content://media/1" to OpsRunner.ContentSource("foto.jpg", 3) { "abc".byteInputStream() },
                "content://media/2" to OpsRunner.ContentSource("a/b.txt", null) { "xy".byteInputStream() },
            )
        val importer =
            OpsRunner(volumeRoots = { listOf(root) }, askConflict = { answer }, contentSource = { provider[it] })
        val uris = listOf("content://media/1", "content://media/2")
        val journal = run(op(OpKind.COPY).copy(sources = uris), importer)
        assertEquals("abc", File(dst, "foto (2).jpg").readText())
        assertEquals("xy", File(dst, "a_b.txt").readText())
        assertEquals(1L, File(dst, "foto.jpg").length())
        assertEquals(listOf("content://media/1", "content://media/2"), journal.pairs.map { it.from })
        assertTrue(parts(dst).isEmpty())
    }

    private fun expectFailure(
        failure: OpsRunner.Failure,
        block: () -> Unit,
    ) {
        try {
            block()
            fail("debía fallar con $failure")
        } catch (e: OpsRunner.FailedException) {
            assertEquals(failure, e.failure)
        }
    }
}
