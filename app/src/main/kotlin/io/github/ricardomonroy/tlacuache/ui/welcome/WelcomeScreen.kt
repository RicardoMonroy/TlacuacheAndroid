package io.github.ricardomonroy.tlacuache.ui.welcome

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.storage.StorageAccess
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color

/**
 * Bienvenida y petición de acceso a los archivos (UI_SPEC §10). [onGranted] se llama cuando
 * Android 10 concede el permiso en su diálogo; en 11+ el acceso se concede en Ajustes y quien
 * llama vuelve a comprobarlo al regresar a la app.
 */
@Composable
fun WelcomeScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    val colors = TlacuacheTheme.tokens.colors
    var denied by rememberSaveable { mutableStateOf(false) }
    val request =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) onGranted() else denied = true
        }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.windowBg.color)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.mipmap.ic_launcher), contentDescription = null, Modifier.size(96.dp))
        Text(
            stringResource(R.string.welcome_title),
            color = colors.fg.color,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.welcome_body),
            color = colors.fgMuted.color,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
        Button(onClick = {
            if (StorageAccess.usesAllFilesAccess) {
                StorageAccess.openSettings(context)
            } else {
                request.launch(StorageAccess.legacyPermissions)
            }
        }) {
            val label =
                if (StorageAccess.usesAllFilesAccess) {
                    R.string.welcome_grant_all_files
                } else {
                    R.string.welcome_grant_storage
                }
            Text(stringResource(label))
        }
        if (denied) {
            Text(
                stringResource(R.string.welcome_denied),
                color = colors.warning.color,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = { StorageAccess.openSettings(context) }) {
                Text(stringResource(R.string.welcome_open_settings))
            }
        }
    }
}
