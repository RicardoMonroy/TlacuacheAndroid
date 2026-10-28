package io.github.ricardomonroy.tlacuache.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneOffset

class TrashStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private var clock = 1_800_000_000_000L

    private fun store(root: File) = TrashStore({ listOf(root) }, ZoneOffset.UTC) { clock }

    @Test
    fun trashWritesFreedesktopInfoAndRestoresToTheOriginalPlace() {
        val root = tmp.newFolder("0")
        val photo = File(root, "DCIM/Camera/mi foto+1.jpg").apply { parentFile?.mkdirs() }
        photo.writeText("jpg")
        val trash = store(root)

        val item = trash.trash(photo)
        assertFalse(photo.exists())
        assertEquals(File(root, ".Tlacuache/Papelera/files/mi foto+1.jpg"), item.file)
        assertTrue(File(root, ".Tlacuache/.nomedia").exists())
        val info = item.info.readText()
        assertTrue(info, info.contains("Path=${root.path.replace(" ", "%20")}/DCIM/Camera/mi%20foto%2B1.jpg"))
        assertTrue(info, info.contains("DeletionDate=2027-01-15T08:00:00"))

        val listed = trash.items().single()
        assertEquals(photo.absoluteFile, listed.originalPath)
        assertEquals(clock, listed.deletedAtMs)

        // La carpeta original ya no existe: se recrea.
        File(root, "DCIM/Camera").delete()
        trash.restore(listed)
        assertEquals("jpg", photo.readText())
        assertTrue(trash.items().isEmpty())
    }

    @Test
    fun repeatedNamesGetANumberAndFindPrefersTheLatest() {
        val root = tmp.newFolder("0")
        val a = File(root, "a.txt")
        a.writeText("1")
        val trash = store(root)
        trash.trash(a)
        clock += 1000
        a.writeText("2")
        val second = trash.trash(a)
        assertEquals("a (2).txt", second.file.name)
        assertEquals(second.file, trash.find(a)?.file)
        assertEquals(second.file, trash.find(second.file)?.file)
    }

    @Test
    fun purgeRemovesOnlyWhatIsOlderThanThirtyDays() {
        val root = tmp.newFolder("0")
        val trash = store(root)
        File(root, "viejo.txt").writeText("x")
        trash.trash(File(root, "viejo.txt"))
        clock += TrashStore.PURGE_AFTER_MS - 1000
        File(root, "nuevo.txt").writeText("x")
        val fresh = trash.trash(File(root, "nuevo.txt"))
        clock += 2000
        assertEquals(1, trash.purge())
        assertEquals(listOf(fresh.file), trash.items().map { it.file })

        trash.delete(fresh)
        assertTrue(trash.items().isEmpty())
        assertFalse(fresh.info.exists())
    }
}
