package io.github.ricardomonroy.tlacuache.storage

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ListingTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun emitsFirstBatchEarlyThenEverything() =
        runBlocking {
            val dir = tmp.newFolder("muchos")
            repeat(Listing.FIRST_BATCH + 50) { File(dir, "f$it.txt").writeText("x") }
            val batches = Listing.read(dir, volumeRoot = false).toList()
            assertEquals(listOf(Listing.FIRST_BATCH, Listing.FIRST_BATCH + 50), batches.map { it.size })
        }

    @Test
    fun readsTypeSizeAndDate() =
        runBlocking {
            val dir = tmp.newFolder("tipos")
            File(dir, "nota.txt").writeText("hola")
            File(dir, "sub").mkdir()
            val byName =
                Listing
                    .read(dir, volumeRoot = false)
                    .toList()
                    .last()
                    .associateBy { it.name }
            assertEquals(4uL, byName.getValue("nota.txt").size)
            assertFalse(byName.getValue("nota.txt").isDir)
            assertTrue(byName.getValue("sub").isDir)
            assertEquals(0uL, byName.getValue("sub").size)
            assertTrue((byName.getValue("nota.txt").modifiedMs ?: 0) > 0)
        }

    @Test
    fun androidFolderIsHiddenOnlyAtVolumeRoot() {
        assertTrue(Listing.isHidden("Android", isDir = true, volumeRoot = true))
        assertFalse(Listing.isHidden("Android", isDir = true, volumeRoot = false))
        assertFalse(Listing.isHidden("Android", isDir = false, volumeRoot = true))
        assertTrue(Listing.isHidden(".nomedia", isDir = false, volumeRoot = false))
    }

    @Test(expected = IOException::class)
    fun missingFolderFails() {
        runBlocking { Listing.read(File(tmp.root, "no-existe"), volumeRoot = false).toList() }
    }
}
