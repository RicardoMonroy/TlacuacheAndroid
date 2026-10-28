package io.github.ricardomonroy.tlacuache.storage

import io.github.ricardomonroy.tlacuache.ffi.CatalogTheme
import io.github.ricardomonroy.tlacuache.ffi.ThemeSetting
import io.github.ricardomonroy.tlacuache.ffi.ThemeTokens
import io.github.ricardomonroy.tlacuache.ffi.configTemplate
import io.github.ricardomonroy.tlacuache.ffi.defaultThemeId
import io.github.ricardomonroy.tlacuache.ffi.resolveTheme
import io.github.ricardomonroy.tlacuache.ffi.themeCatalog
import io.github.ricardomonroy.tlacuache.ffi.themeSetting
import io.github.ricardomonroy.tlacuache.ffi.updateThemeSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Temas (ADR-A07, THEMES.md): los incluidos y los de [themesDir], y `[theme]` de `config.toml`. Uno por
 * proceso: todas las ventanas cambian a la vez. Elegir un tema solo cambia estado observable, así que la
 * actividad no se recrea.
 */
class ThemeStore(
    private val configFile: File,
    private val themesDir: File,
) {
    /** Algo que avisar una vez: un archivo de tema roto o un tema de usuario con poco contraste. */
    sealed interface Warning {
        data class Broken(
            val message: String,
        ) : Warning

        data class LowContrast(
            val theme: String,
            val pairs: List<String>,
        ) : Warning
    }

    private val _themes = MutableStateFlow<List<CatalogTheme>?>(null)

    /** `null` hasta leer el catálogo (en IO). */
    val themes: StateFlow<List<CatalogTheme>?> = _themes.asStateFlow()

    private val _setting = MutableStateFlow(defaultSetting())
    val setting: StateFlow<ThemeSetting> = _setting.asStateFlow()

    private val _warnings = MutableStateFlow<List<Warning>>(emptyList())
    val warnings: StateFlow<List<Warning>> = _warnings.asStateFlow()

    private val warned = mutableSetOf<String>()

    suspend fun load() =
        withContext(Dispatchers.IO) {
            if (_themes.value != null) return@withContext
            themesDir.mkdirs()
            val list = themeCatalog(themesDir.path)
            ConfigLock.mutex.withLock {
                val text = runCatching { configFile.readText() }.getOrNull()
                // Inválido o ausente: los valores por omisión, sin tocar el archivo (ADR-A11).
                text?.let(::themeSetting)?.let { _setting.value = it }
            }
            _warnings.update { it + list.errors.map(Warning::Broken) }
            _themes.value = list.themes
        }

    /** El tema a pintar según la preferencia del sistema; Nord si el elegido no existe (THEMES.md §6). */
    fun tokens(systemDark: Boolean): ThemeTokens? {
        val themes = _themes.value ?: return null
        val id = resolveTheme(_setting.value, systemDark)
        return (themes.firstOrNull { it.tokens.id == id } ?: themes.firstOrNull { it.tokens.id == defaultThemeId() })
            ?.tokens
    }

    /** Un tema de usuario que no pasa el contraste se aplica igual, con un aviso la primera vez (THEMES.md §6). */
    fun applied(id: String) {
        val theme = _themes.value?.firstOrNull { it.tokens.id == id } ?: return
        if (theme.contrast.isNotEmpty() && warned.add(id)) {
            _warnings.update { it + Warning.LowContrast(theme.tokens.name, theme.contrast) }
        }
    }

    /**
     * Elige [theme]. Siguiendo al sistema, pasa a ser el tema claro o el oscuro según su variante; si no,
     * el tema fijo.
     */
    suspend fun choose(theme: ThemeTokens) =
        save { s ->
            when {
                !s.followSystem -> s.copy(name = theme.id)
                theme.dark -> s.copy(dark = theme.id)
                else -> s.copy(light = theme.id)
            }
        }

    /** Al dejar de seguir al sistema se queda el tema que se ve ahora, no el fijo de antes. */
    suspend fun followSystem(
        on: Boolean,
        systemDark: Boolean,
    ) = save { s ->
        if (on) s.copy(followSystem = true) else s.copy(followSystem = false, name = resolveTheme(s, systemDark))
    }

    fun consumeWarnings() = _warnings.update { emptyList() }

    /** Aplica al instante y guarda; si `config.toml` no es TOML válido, el cambio dura hasta cerrar la app. */
    private suspend fun save(change: (ThemeSetting) -> ThemeSetting) {
        val new = change(_setting.value)
        _setting.value = new
        withContext(Dispatchers.IO) {
            ConfigLock.mutex.withLock {
                try {
                    val text = if (configFile.exists()) configFile.readText() else configTemplate()
                    updateThemeSetting(text, new)?.let { writeAtomically(configFile, it) }
                } catch (_: IOException) {
                    // Sin guardar: el tema ya está aplicado en esta sesión.
                }
            }
        }
    }

    companion object {
        @Volatile private var shared: ThemeStore? = null

        fun shared(filesDir: File): ThemeStore =
            shared ?: synchronized(this) {
                shared ?: ThemeStore(File(filesDir, "config.toml"), File(filesDir, "themes")).also { shared = it }
            }

        /** `[theme]` por omisión, el mismo de la plantilla del core. */
        private fun defaultSetting(): ThemeSetting =
            themeSetting(configTemplate()) ?: ThemeSetting(defaultThemeId(), false, "claro", defaultThemeId())
    }
}
