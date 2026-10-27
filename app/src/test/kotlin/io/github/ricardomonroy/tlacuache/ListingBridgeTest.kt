package io.github.ricardomonroy.tlacuache

import io.github.ricardomonroy.tlacuache.ffi.AgeBucket
import io.github.ricardomonroy.tlacuache.ffi.AgeUnit
import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import io.github.ricardomonroy.tlacuache.ffi.SortKey
import io.github.ricardomonroy.tlacuache.ffi.SortSpec
import io.github.ricardomonroy.tlacuache.ffi.age
import io.github.ricardomonroy.tlacuache.ffi.arrange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.measureTimedValue

/** `arrange` y `age` del core a través de JNA, como los usará la app. */
class ListingBridgeTest {
    private val nameAsc = SortSpec(SortKey.NAME, descending = false)

    private fun file(
        name: String,
        isDir: Boolean = false,
    ) = FileEntry(name, isDir, isHidden = name.startsWith('.'), size = 0u, modifiedMs = null)

    @Test
    fun foldersFirstNaturalOrderAndQuickFilter() {
        val entries =
            listOf(
                file("img10.jpg"),
                file(".nomedia"),
                file("Canción.mp3"),
                file("zeta", isDir = true),
                file("img2.jpg"),
            )
        val shown = arrange(entries, nameAsc, "", showHidden = false).map { entries[it.toInt()].name }
        assertEquals(listOf("zeta", "Canción.mp3", "img2.jpg", "img10.jpg"), shown)
        val filtered = arrange(entries, nameAsc, "cancion", showHidden = false).map { entries[it.toInt()].name }
        assertEquals(listOf("Canción.mp3"), filtered)
    }

    @Test
    fun arrangesTenThousandEntriesInOneCall() {
        val entries = List(10_000) { i -> if (i % 10 == 0) file("carpeta $i", isDir = true) else file("IMG_$i.jpg") }
        arrange(entries.take(10), nameAsc, "", false) // calienta JNA y la carga de la librería
        val (idx, elapsed) = measureTimedValue { arrange(entries, nameAsc, "", showHidden = false) }
        println("arrange 10 000 entradas vía JNA: $elapsed")
        assertEquals(10_000, idx.size)
        assertEquals("IMG_1.jpg", entries[idx[1_000].toInt()].name)
        // Holgado: la build de host es debug.
        assertTrue("arrange tardó $elapsed", elapsed.inWholeMilliseconds < 2_000)
    }

    @Test
    fun ageOfTwoHoursIsHourBucket() {
        val now = 1_800_000_000_000L
        val a = age(now - 2 * 3_600_000L, now)
        assertEquals(AgeBucket.HOUR, a.bucket)
        assertEquals(AgeUnit.HOURS, a.unit)
        assertEquals(2uL, a.amount)
    }
}
