package io.github.ricardomonroy.tlacuache.state

import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import io.github.ricardomonroy.tlacuache.storage.FolderWatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PaneStateTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var root: File
    private lateinit var pane: PaneState
    private val watching = mutableSetOf<File>()

    @Before
    fun setUp() {
        root = tmp.newFolder("volumen")
        File(root, "DCIM").mkdirs()
        pane =
            PaneState(root) { dir ->
                TabState(scope, dir, volumeName = { if (it == root) "Interno" else null }, watcher = { d, _ ->
                    watching += d
                    FolderWatch { watching -= d }
                })
            }
    }

    @After
    fun tearDown() = scope.cancel()

    private val tabs get() = pane.tabs.value

    private fun dirs() = tabs.list.map { it.ui.value.dir }

    @Test
    fun newTabOpensAtCurrentFolderAfterActive() {
        tabs.current.open(FileEntry("DCIM", isDir = true, isHidden = false, size = 0u, modifiedMs = null))
        pane.openTab()
        assertEquals(listOf(File(root, "DCIM"), File(root, "DCIM")), dirs())
        assertEquals(1, tabs.active)
        pane.select(0)
        pane.openTab()
        assertEquals(1, tabs.active)
        assertEquals(3, tabs.list.size)
    }

    @Test
    fun lastTabCannotBeClosed() {
        val only = tabs.current
        pane.closeTab()
        assertEquals(1, tabs.list.size)
        assertSame(only, tabs.current)
    }

    @Test
    fun closingKeepsSensibleActiveTabAndStopsWatching() {
        repeat(3) { pane.openTab() } // 4 pestañas, activa la última (3)
        val third = tabs.list[2]
        pane.closeTab(0) // antes de la activa: la activa sigue siendo la misma pestaña
        assertEquals(2, tabs.active)
        pane.select(1)
        assertSame(third, tabs.current)
        third.startWatching()
        assertEquals(setOf(root), watching)
        pane.closeTab() // la activa: pasa a la de al lado y deja de vigilar
        assertEquals(1, tabs.active)
        assertEquals(emptySet<File>(), watching)
    }

    @Test
    fun cycleWrapsAround() {
        repeat(2) { pane.openTab() }
        pane.cycle(1)
        assertEquals(0, tabs.active)
        pane.cycle(-1)
        assertEquals(2, tabs.active)
    }

    @Test
    fun duplicateAndCloseOthers() {
        tabs.current.open(FileEntry("DCIM", isDir = true, isHidden = false, size = 0u, modifiedMs = null))
        pane.openTab()
        tabs.list[1].up()
        pane.openTab(from = 0) // Duplicar la primera: queda junto a ella, en su carpeta
        assertEquals(listOf(File(root, "DCIM"), File(root, "DCIM"), root), dirs())
        assertEquals(1, tabs.active)
        val kept = tabs.list[2]
        kept.startWatching()
        tabs.list[0].startWatching()
        pane.closeOthers(2)
        assertEquals(listOf(kept), tabs.list)
        assertEquals(0, tabs.active)
        assertEquals(setOf(root), watching)
    }

    @Test
    fun moveKeepsActiveAndDetachSkipsLastTab() {
        repeat(2) { pane.openTab() } // 3 pestañas, activa la última
        val (a, b, c) = tabs.list
        pane.move(2, 0)
        assertEquals(listOf(c, a, b), tabs.list)
        assertSame(c, tabs.current)
        c.startWatching()
        assertSame(c, pane.detach(0)) // sale sin dejar de vigilar: sigue viva en el otro panel
        assertEquals(listOf(a, b), tabs.list)
        assertEquals(setOf(root), watching)
        pane.adopt(c)
        assertSame(c, tabs.current)
        pane.closeOthers(0)
        assertEquals(null, pane.detach(0))
    }
}
