package io.github.ricardomonroy.tlacuache.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PathInputTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var home: File
    private lateinit var input: PathInput

    @Before
    fun setUp() {
        home = tmp.newFolder("0")
        listOf("DCIM/Camera", "Documents", "Download", ".thumbnails").forEach { File(home, it).mkdirs() }
        File(home, "Doc.txt").writeText("no es carpeta")
        input = PathInput(home)
    }

    @Test
    fun displayUsesTildeInsideHome() {
        assertEquals("~", input.display(home))
        assertEquals("~/DCIM/Camera", input.display(File(home, "DCIM/Camera")))
        assertEquals(tmp.root.absolutePath, input.display(tmp.root))
    }

    @Test
    fun resolvesTildeRelativeAndDots() {
        assertEquals(File(home, "DCIM"), input.resolve("~/DCIM/", home))
        assertEquals(File(home, "DCIM/Camera"), input.resolve("Camera", File(home, "DCIM")))
        assertEquals(home, input.resolve("~/DCIM/..", home))
    }

    @Test
    fun suggestsOnlyVisibleSubfolders() {
        assertEquals(listOf("Documents", "Download"), input.suggestions("~/do", home))
        assertEquals(listOf("Camera"), input.suggestions("~/DCIM/", home))
        assertEquals(listOf(".thumbnails"), input.suggestions("~/.", home))
    }

    @Test
    fun tabCompletesUniqueMatch() {
        assertEquals("~/Documents/", input.complete("~/Doc", home))
        assertEquals("~/DCIM/Camera/", input.complete("~/DCIM/C", home))
        assertNull(input.complete("~/zzz", home))
    }
}
