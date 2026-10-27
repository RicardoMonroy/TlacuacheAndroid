package io.github.ricardomonroy.tlacuache.state

import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Navegación de una pestaña: entrar, subir, atrás y adelante, sin pasar de la raíz del volumen. */
class TabStateTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var root: File
    private lateinit var tab: TabState

    @Before
    fun setUp() {
        root = tmp.newFolder("volumen")
        File(root, "DCIM/Camera").mkdirs()
        tab = TabState(scope, root, isVolumeRoot = { it == root })
    }

    @After
    fun tearDown() = scope.cancel()

    private fun dir(name: String) = FileEntry(name, isDir = true, isHidden = false, size = 0u, modifiedMs = null)

    private val ui get() = tab.ui.value

    @Test
    fun startsAtVolumeRootWithNowhereToGo() {
        assertEquals(root, ui.dir)
        assertFalse(ui.canGoUp)
        assertFalse(ui.canGoBack)
        tab.up()
        assertEquals(root, ui.dir)
    }

    @Test
    fun openUpBackForward() {
        tab.open(dir("DCIM"))
        tab.open(dir("Camera"))
        assertEquals(File(root, "DCIM/Camera"), ui.dir)
        assertTrue(ui.canGoUp)

        tab.up()
        assertEquals(File(root, "DCIM"), ui.dir)

        tab.back()
        assertEquals(File(root, "DCIM/Camera"), ui.dir)
        assertTrue(ui.canGoForward)

        tab.forward()
        assertEquals(File(root, "DCIM"), ui.dir)
        assertFalse(ui.canGoForward)
    }

    @Test
    fun crumbsStartAtVolumeRoot() {
        tab.open(dir("DCIM"))
        tab.open(dir("Camera"))
        assertEquals(listOf(root, File(root, "DCIM"), File(root, "DCIM/Camera")), ui.crumbs)
    }

    @Test
    fun goToRejectsFilesAndOutsideStorage() {
        File(root, "nota.txt").writeText("x")
        assertEquals(TabState.GoTo.NOT_A_FOLDER, tab.goTo(File(root, "nota.txt")))
        assertEquals(TabState.GoTo.OUTSIDE_STORAGE, tab.goTo(tmp.root))
        assertEquals(root, ui.dir)
        assertEquals(TabState.GoTo.OK, tab.goTo(File(root, "DCIM/Camera")))
        assertEquals(File(root, "DCIM/Camera"), ui.dir)
        assertTrue(ui.canGoBack)
    }

    @Test
    fun openingAFileDoesNotNavigate() {
        tab.open(FileEntry("foto.jpg", isDir = false, isHidden = false, size = 1u, modifiedMs = null))
        assertEquals(root, ui.dir)
        assertFalse(ui.canGoBack)
    }
}
