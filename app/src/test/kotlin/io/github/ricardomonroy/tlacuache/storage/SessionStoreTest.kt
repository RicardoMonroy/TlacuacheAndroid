package io.github.ricardomonroy.tlacuache.storage

import io.github.ricardomonroy.tlacuache.ffi.SessionPane
import io.github.ricardomonroy.tlacuache.ffi.SessionState
import io.github.ricardomonroy.tlacuache.ffi.SessionTab
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun restoresWhatWasSavedAndDropsMissingFolders() =
        runBlocking {
            val dcim = tmp.newFolder("DCIM")
            val gone = File(tmp.root, "SD")
            val store = SessionStore(File(tmp.root, "session.toml"))
            assertNull(store.load())
            val tabs = listOf(SessionTab(gone.path, false, false), SessionTab(dcim.path, true, true))
            val saved = SessionState(true, 0.4, 1u, false, listOf(SessionPane(1u, tabs), SessionPane(0u, tabs)))
            assertTrue(store.save(saved))

            val loaded = checkNotNull(store.load())
            assertEquals(0.4, loaded.panesRatio)
            assertEquals(1u, loaded.activePane)
            // La pestaña de la SD retirada desaparece y la elegida (DCIM) sigue elegida.
            assertEquals(SessionPane(0u, listOf(SessionTab(dcim.path, true, true))), loaded.panes[0])
        }

    @Test
    fun damagedSessionIsIgnored() =
        runBlocking {
            val file = File(tmp.root, "session.toml").apply { writeText("[[roto") }
            assertNull(SessionStore(file).load())
        }
}
