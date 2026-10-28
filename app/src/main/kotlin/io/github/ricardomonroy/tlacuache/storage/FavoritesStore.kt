package io.github.ricardomonroy.tlacuache.storage

import io.github.ricardomonroy.tlacuache.ffi.FavoriteGroup
import io.github.ricardomonroy.tlacuache.ffi.Favorites
import io.github.ricardomonroy.tlacuache.ffi.FavoritesException
import io.github.ricardomonroy.tlacuache.ffi.configTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Favoritos guardados en `[[favorites]]` de `config.toml` (ADR-A11). Las reglas (grupos, duplicados,
 * orden) son del core; aquí solo la E/S, en IO y con escritura atómica. Un `config.toml` inválido
 * nunca se sobrescribe: sin favoritos hasta que se arregle.
 */
class FavoritesStore(
    private val file: File,
    /** Raíz del almacenamiento interno: se guarda como `~`, igual que en Linux. */
    private val home: File,
) {
    enum class Result { OK, EMPTY_NAME, DUPLICATE_NAME, FAILED }

    private val mutex = ConfigLock.mutex
    private var favorites: Favorites? = null
    private val _groups = MutableStateFlow(emptyList<FavoriteGroup>())
    val groups: StateFlow<List<FavoriteGroup>> = _groups.asStateFlow()

    /** Lee el archivo; si no existe lo crea con la plantilla del core. */
    suspend fun load() =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                favorites =
                    try {
                        val text = if (file.exists()) file.readText() else configTemplate().also(::write)
                        Favorites.fromToml(text, home.path)
                    } catch (_: IOException) {
                        null
                    } catch (_: FavoritesException) {
                        null
                    }
                _groups.value = favorites?.groups().orEmpty()
            }
        }

    /** Aplica [change] y guarda; sin guardar si el core rechaza el cambio. */
    suspend fun edit(change: (Favorites) -> Unit): Result =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val favs = favorites ?: return@withLock Result.FAILED
                try {
                    change(favs)
                    write(favs.updateToml(file.readText()))
                    Result.OK
                } catch (_: FavoritesException.EmptyName) {
                    Result.EMPTY_NAME
                } catch (_: FavoritesException.DuplicateGroup) {
                    Result.DUPLICATE_NAME
                } catch (_: FavoritesException) {
                    Result.FAILED
                } catch (_: IOException) {
                    Result.FAILED
                } finally {
                    _groups.value = favs.groups()
                }
            }
        }

    private fun write(text: String) = writeAtomically(file, text)

    companion object {
        @Volatile private var shared: FavoritesStore? = null

        /** Uno por proceso: con varias ventanas (A4.5) todas ven y guardan los mismos favoritos. */
        fun shared(
            file: File,
            home: File,
        ): FavoritesStore = shared ?: synchronized(this) { shared ?: FavoritesStore(file, home).also { shared = it } }
    }
}
