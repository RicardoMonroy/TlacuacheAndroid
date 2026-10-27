package io.github.ricardomonroy.tlacuache

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.ricardomonroy.tlacuache.ffi.builtinTheme
import io.github.ricardomonroy.tlacuache.ffi.coreVersion
import io.github.ricardomonroy.tlacuache.ffi.defaultThemeId
import io.github.ricardomonroy.tlacuache.storage.StorageAccess
import io.github.ricardomonroy.tlacuache.ui.theme.MonoFamily
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import io.github.ricardomonroy.tlacuache.ui.welcome.WelcomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // El tema predeterminado va embebido en el core y sus pruebas lo validan: nulo sería un error de build.
        val tokens = requireNotNull(builtinTheme(defaultThemeId())) { "tema predeterminado ausente" }
        // Barras del sistema transparentes; el contenido pinta debajo (edge-to-edge).
        val bars =
            if (tokens.dark) {
                SystemBarStyle.dark(TRANSPARENT)
            } else {
                SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
            }
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        setContent {
            TlacuacheTheme(tokens) {
                App()
            }
        }
    }
}

/** Sin acceso a los archivos solo se muestra la bienvenida; se vuelve a comprobar al regresar a la app (UI_SPEC §10). */
@Composable
private fun App() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(StorageAccess.isGranted(context)) }
    // Al volver de Ajustes (concedido o revocado) el estado puede haber cambiado.
    LifecycleResumeEffect(Unit) {
        granted = StorageAccess.isGranted(context)
        onPauseOrDispose { }
    }
    if (granted) Placeholder() else WelcomeScreen(onGranted = { granted = true })
}

// shortcut: pantalla de prueba hasta que A1.3 traiga el panel real.
@Composable
private fun Placeholder() {
    val t = TlacuacheTheme.tokens
    Column(Modifier.fillMaxSize().background(t.colors.windowBg.color)) {
        Text(
            stringResource(R.string.app_name),
            color = t.colors.headerFg.color,
            style = MaterialTheme.typography.titleLarge,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(t.colors.headerBg.color)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.placeholder_greeting), color = t.colors.fg.color)
            val rootEntries by produceState<Int?>(null) {
                value =
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION") // ruta del almacenamiento interno; con acceso total es legible.
                        Environment.getExternalStorageDirectory().list()?.size
                    }
            }
            rootEntries?.let {
                Text(
                    pluralStringResource(R.plurals.placeholder_root_entries, it, it),
                    color = t.colors.fg.color,
                )
            }
            Text(
                coreVersion(),
                color = t.colors.fgMuted.color,
                fontFamily = MonoFamily,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                t.name,
                color = t.colors.accentFg.color,
                modifier =
                    Modifier
                        .background(t.colors.accent.color, MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}
