package io.github.ricardomonroy.tlacuache

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import android.view.KeyboardShortcutGroup
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.setSingletonImageLoaderFactory
import io.github.ricardomonroy.tlacuache.ffi.builtinTheme
import io.github.ricardomonroy.tlacuache.ffi.defaultThemeId
import io.github.ricardomonroy.tlacuache.ops.OpsRepository
import io.github.ricardomonroy.tlacuache.state.WindowViewModel
import io.github.ricardomonroy.tlacuache.storage.StorageAccess
import io.github.ricardomonroy.tlacuache.ui.Shortcut
import io.github.ricardomonroy.tlacuache.ui.pane.PaneLayout
import io.github.ricardomonroy.tlacuache.ui.pane.thumbnailLoader
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.welcome.WelcomeScreen

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        OpsRepository.windowShown()
    }

    /** Meta+/ (y el menú de atajos del sistema): la misma tabla con la que se despachan. */
    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>,
        menu: Menu?,
        deviceId: Int,
    ) {
        data += Shortcut.groups(this)
    }

    override fun onStop() {
        OpsRepository.windowHidden()
        super.onStop()
    }

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
    setSingletonImageLoaderFactory(::thumbnailLoader)
    var granted by remember { mutableStateOf(StorageAccess.isGranted(context)) }
    // Al volver de Ajustes (concedido o revocado) el estado puede haber cambiado.
    LifecycleResumeEffect(Unit) {
        granted = StorageAccess.isGranted(context)
        onPauseOrDispose { }
    }
    if (granted) {
        val window = viewModel<WindowViewModel>()
        val ready by window.ready.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { window.saveSession() }
        if (ready) PaneLayout(window)
    } else {
        WelcomeScreen(onGranted = { granted = true })
    }
}
