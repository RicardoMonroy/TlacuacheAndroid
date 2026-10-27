package io.github.ricardomonroy.tlacuache.storage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FavoritesStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun favoritesSurviveReloadAndKeepTheRestOfTheFile() =
        runBlocking {
            val home = tmp.newFolder("0")
            val file = File(tmp.root, "config.toml")
            val store = FavoritesStore(file, home)
            store.load()
            assertTrue(file.readText().contains("[general]"))
            store.edit { it.add(File(home, "DCIM").path, "Favoritos") }
            store.edit { it.add(File(home, "Download").path, "Favoritos") }
            store.edit { it.moveFavorite(0u, 1u, 0u, 0u) }
            assertTrue(file.readText().contains("\"~/Download\""))
            assertTrue(file.readText().contains("[general]"))

            val again = FavoritesStore(file, home).apply { load() }
            assertEquals(
                listOf(File(home, "Download").path, File(home, "DCIM").path),
                again.groups.value
                    .single()
                    .paths,
            )
        }

    @Test
    fun rejectedNamesAndInvalidConfigDoNotWrite() =
        runBlocking {
            val home = tmp.newFolder("0")
            val file = File(tmp.root, "config.toml")
            val store = FavoritesStore(file, home).apply { load() }
            assertEquals(FavoritesStore.Result.OK, store.edit { it.addGroup("Clases") })
            assertEquals(FavoritesStore.Result.DUPLICATE_NAME, store.edit { it.addGroup("Clases") })
            assertEquals(FavoritesStore.Result.EMPTY_NAME, store.edit { it.renameGroup(0u, " ") })

            file.writeText("[[roto")
            val broken = FavoritesStore(file, home).apply { load() }
            assertEquals(FavoritesStore.Result.FAILED, broken.edit { it.addGroup("Otra") })
            assertEquals("[[roto", file.readText())
        }
}
