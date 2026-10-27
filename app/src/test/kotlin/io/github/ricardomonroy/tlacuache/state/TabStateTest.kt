package io.github.ricardomonroy.tlacuache.state

import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import io.github.ricardomonroy.tlacuache.ffi.FilterKey
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

    /** Espera a que el listado asíncrono cumpla [cond] (lectura en IO, orden en Default). */
    private fun awaitUi(cond: (TabState.Ui) -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!cond(ui)) {
            check(System.currentTimeMillis() < deadline) { "estado no alcanzado: $ui" }
            Thread.sleep(10)
        }
    }

    private fun names() = ui.visible.map { it.name }

    @Test
    fun quickFilterAndHiddenReapplyWithoutRereading() {
        File(root, "Canción.mp3").writeText("x")
        File(root, "foto.jpg").writeText("x")
        File(root, ".oculto").writeText("x")
        tab.reload()
        awaitUi { !it.loading && names() == listOf("DCIM", "Canción.mp3", "foto.jpg") }

        assertTrue(tab.applyFilterKey(FilterKey.Char("c")))
        tab.setQuery("cancion")
        awaitUi { names() == listOf("Canción.mp3") }

        // Con filtro activo, Backspace lo edita en vez de subir de nivel (ADR-006).
        assertTrue(tab.applyFilterKey(FilterKey.Backspace))
        assertEquals("cancio", ui.query)
        assertTrue(tab.applyFilterKey(FilterKey.Escape))
        assertFalse(tab.applyFilterKey(FilterKey.Backspace))
        awaitUi { names().size == 3 }

        tab.toggleHidden()
        awaitUi { ".oculto" in names() }
    }

    @Test
    fun navigatingClearsFilterButKeepsHidden() {
        tab.toggleHidden()
        tab.setQuery("dc")
        tab.open(dir("DCIM"))
        assertEquals("", ui.query)
        assertTrue(ui.showHidden)
    }

    @Test
    fun openingAFileDoesNotNavigate() {
        tab.open(FileEntry("foto.jpg", isDir = false, isHidden = false, size = 1u, modifiedMs = null))
        assertEquals(root, ui.dir)
        assertFalse(ui.canGoBack)
    }
}
