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
}
