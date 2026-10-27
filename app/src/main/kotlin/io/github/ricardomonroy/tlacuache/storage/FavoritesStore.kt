package io.github.ricardomonroy.tlacuache.storage

import io.github.ricardomonroy.tlacuache.ffi.FavoriteGroup
import io.github.ricardomonroy.tlacuache.ffi.Favorites
import io.github.ricardomonroy.tlacuache.ffi.FavoritesException
import io.github.ricardomonroy.tlacuache.ffi.configTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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

    private val mutex = Mutex()
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

    /** Escribe a un temporal y lo renombra: un corte a mitad no deja el archivo a medias. */
    private fun write(text: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
