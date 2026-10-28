package io.github.ricardomonroy.tlacuache.storage

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MeasureTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun countsFilesFoldersAndBytesDeep() {
        val album = tmp.newFolder("Álbum")
        File(album, "a.jpg").writeBytes(ByteArray(10))
        File(album, "2026").apply { mkdir() }.let { File(it, "b.jpg").writeBytes(ByteArray(5)) }
        val note = tmp.newFile("nota.txt").apply { writeBytes(ByteArray(3)) }
        assertEquals(Measure(files = 3, dirs = 1, bytes = 18), measure(listOf(album, note)))
        // Cortado desde el principio (diálogo cerrado): no recorre nada.
        assertEquals(Measure(0, 0, 0), measure(listOf(album)) { false })
    }
}
