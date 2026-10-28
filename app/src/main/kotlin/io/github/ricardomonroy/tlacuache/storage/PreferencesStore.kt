package io.github.ricardomonroy.tlacuache.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import io.github.ricardomonroy.tlacuache.ffi.GeneralSetting
import io.github.ricardomonroy.tlacuache.ffi.configError
import io.github.ricardomonroy.tlacuache.ffi.configTemplate
import io.github.ricardomonroy.tlacuache.ffi.generalSetting
import io.github.ricardomonroy.tlacuache.ffi.updateGeneralSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Preferencias (ARCHITECTURE §12): lo que comparte con Linux va en `[general]` de `config.toml` (con el
 * core); lo propio de Android (densidad, mostrar `Android/`) en `SharedPreferences`. Una por proceso.
 */
class PreferencesStore private constructor(
    context: Context,
) {
    /** Alto de fila (UI_SPEC §3): Auto según el último puntero, o fijo. */
    enum class Density { AUTO, COMFORTABLE, COMPACT }

    /** Resultado de importar un `config.toml`. */
    sealed interface ImportResult {
        data object Ok : ImportResult

        /** No es un `config.toml` válido: el actual no se toca. */
        data class Invalid(
            val message: String,
        ) : ImportResult

        data object Failed : ImportResult
    }

    // Sin guardar el Context: vive todo el proceso.
    private val resolver = context.contentResolver
    private val configFile = File(context.filesDir, "config.toml")
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    private val _general = MutableStateFlow(defaultGeneral())
    val general: StateFlow<GeneralSetting> = _general.asStateFlow()

    private val _density =
        MutableStateFlow(
            runCatching { Density.valueOf(prefs.getString(KEY_DENSITY, null).orEmpty()) }.getOrDefault(Density.AUTO),
        )
    val density: StateFlow<Density> = _density.asStateFlow()

    private val _showAndroid = MutableStateFlow(prefs.getBoolean(KEY_SHOW_ANDROID, false))

    /** Listar `Android/` en la raíz de los volúmenes (de solo lectura, ADR-A06). */
    val showAndroid: StateFlow<Boolean> = _showAndroid.asStateFlow()

    /** Lee `[general]`; inválido o ausente = valores por omisión, sin tocar el archivo (ADR-A11). */
    suspend fun load() =
        withContext(Dispatchers.IO) {
            ConfigLock.mutex.withLock {
                val text = runCatching { configFile.readText() }.getOrNull()
                _general.value = text?.let(::generalSetting) ?: defaultGeneral()
            }
        }

    /** Aplica al instante y guarda; si `config.toml` no es TOML válido, el cambio dura hasta cerrar la app. */
    suspend fun setGeneral(change: (GeneralSetting) -> GeneralSetting) {
        val new = change(_general.value)
        _general.value = new
        withContext(Dispatchers.IO) {
            ConfigLock.mutex.withLock {
                try {
                    val text = if (configFile.exists()) configFile.readText() else configTemplate()
                    updateGeneralSetting(text, new)?.let { writeAtomically(configFile, it) }
                } catch (_: IOException) {
                    // Sin guardar: ya está aplicado en esta sesión.
                }
            }
        }
    }

    fun setDensity(density: Density) {
        _density.value = density
        prefs.edit { putString(KEY_DENSITY, density.name) }
    }

    fun setShowAndroid(show: Boolean) {
        _showAndroid.value = show
        prefs.edit { putBoolean(KEY_SHOW_ANDROID, show) }
    }

    /** Copia `config.toml` (o la plantilla, si aún no existe) a [uri], elegido con el selector del sistema. */
    suspend fun export(uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            ConfigLock.mutex.withLock {
                try {
                    val text = if (configFile.exists()) configFile.readText() else configTemplate()
                    resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
                } catch (_: IOException) {
                    false
                } catch (_: SecurityException) {
                    false
                }
            }
        }

    /**
     * Reemplaza `config.toml` por [uri] si el core lo acepta (p. ej. el de Linux); quien llama recarga
     * favoritos y tema.
     */
    suspend fun import(uri: Uri): ImportResult =
        withContext(Dispatchers.IO) {
            val text =
                try {
                    resolver.openInputStream(uri)?.use { input ->
                        // Sin `readNBytes` (API 33): se lee hasta pasar el límite.
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (out.size() <= MAX_CONFIG_BYTES) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                        }
                        if (out.size() > MAX_CONFIG_BYTES) null else out.toString(Charsets.UTF_8.name())
                    }
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                } ?: return@withContext ImportResult.Failed
            configError(text)?.let { return@withContext ImportResult.Invalid(it) }
            ConfigLock.mutex.withLock {
                try {
                    writeAtomically(configFile, text)
                } catch (_: IOException) {
                    return@withContext ImportResult.Failed
                }
            }
            load()
            ImportResult.Ok
        }

    companion object {
        private const val KEY_DENSITY = "density"
        private const val KEY_SHOW_ANDROID = "show_android"

        /** Un `config.toml` real pesa unos KB; esto evita leer a memoria un archivo equivocado enorme. */
        private const val MAX_CONFIG_BYTES = 1 shl 20

        @Volatile private var shared: PreferencesStore? = null

        fun shared(context: Context): PreferencesStore =
            shared ?: synchronized(this) { shared ?: PreferencesStore(context).also { shared = it } }

        private fun defaultGeneral(): GeneralSetting =
            requireNotNull(generalSetting(configTemplate())) { "la plantilla del core es TOML válido" }
    }
}
