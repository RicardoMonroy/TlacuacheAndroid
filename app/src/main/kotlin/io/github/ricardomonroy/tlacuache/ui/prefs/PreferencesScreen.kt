package io.github.ricardomonroy.tlacuache.ui.prefs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.ViewMode
import io.github.ricardomonroy.tlacuache.state.WindowViewModel
import io.github.ricardomonroy.tlacuache.storage.PreferencesStore
import io.github.ricardomonroy.tlacuache.ui.theme.DialogSystemBars
import io.github.ricardomonroy.tlacuache.ui.theme.ThemeDialog
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import kotlinx.coroutines.launch

/**
 * Preferencias (UI_SPEC §11, ARCHITECTURE §12). Lo de `[general]` es el mismo `config.toml` de Linux;
 * densidad y `Android/` son de este dispositivo. Todo se aplica al tocarlo.
 */
@Composable
fun PreferencesScreen(
    window: WindowViewModel,
    onClose: () -> Unit,
) {
    val t = TlacuacheTheme.tokens
    val prefs = PreferencesStore.shared(LocalContext.current)
    val general by prefs.general.collectAsStateWithLifecycle()
    val density by prefs.density.collectAsStateWithLifecycle()
    val showAndroid by prefs.showAndroid.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var themeDialog by remember { mutableStateOf(false) }
    var confirmAndroid by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val notice: (Int) -> Unit = { scope.launch { snackbar.showSnackbar(resources.getString(it)) } }
    // Selector de archivos del sistema: el usuario elige dónde guardar o qué importar (sin permisos extra).
    val exporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/toml")) { uri ->
            uri?.let { window.exportConfig(it, notice) }
        }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { window.importConfig(it, notice) { message -> importError = message } }
        }

    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogSystemBars()
        Column(Modifier.fillMaxSize().background(t.colors.paneBg.color).windowInsetsPadding(WindowInsets.systemBars)) {
            Row(
                Modifier.fillMaxWidth().background(t.colors.headerBg.color).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val close = stringResource(R.string.prefs_close)
                IconButton(onClose, Modifier.semantics { contentDescription = close }) {
                    Text("←", color = t.colors.headerFg.color, style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    stringResource(R.string.prefs_title),
                    color = t.colors.headerFg.color,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            // En pantallas anchas, una columna de lectura centrada: el interruptor no queda lejos de su texto.
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                    Section(R.string.prefs_section_files)
                    SwitchRow(R.string.prefs_show_hidden, R.string.prefs_show_hidden_hint, general.showHidden) { on ->
                        scope.launch { prefs.setGeneral { it.copy(showHidden = on) } }
                    }
                    SwitchRow(R.string.prefs_dual_pane, R.string.prefs_dual_pane_hint, general.dualPane) { on ->
                        scope.launch { prefs.setGeneral { it.copy(dualPane = on) } }
                    }
                    SwitchRow(R.string.prefs_confirm_trash, null, general.confirmTrash) { on ->
                        scope.launch { prefs.setGeneral { it.copy(confirmTrash = on) } }
                    }
                    Label(R.string.prefs_default_view)
                    // `columns` (Miller) llega en A6.2: hasta entonces se muestra y se usa como lista.
                    val icons = general.defaultView == ViewMode.ICONS
                    ChoiceRow(R.string.prefs_view_list, !icons) {
                        scope.launch { prefs.setGeneral { it.copy(defaultView = ViewMode.DETAILS) } }
                    }
                    ChoiceRow(R.string.prefs_view_icons, icons) {
                        scope.launch { prefs.setGeneral { it.copy(defaultView = ViewMode.ICONS) } }
                    }

                    Section(R.string.prefs_section_look)
                    ActionRow(R.string.theme_menu) { themeDialog = true }
                    Label(R.string.prefs_density)
                    PreferencesStore.Density.entries.forEach { d ->
                        val label =
                            when (d) {
                                PreferencesStore.Density.AUTO -> R.string.prefs_density_auto
                                PreferencesStore.Density.COMFORTABLE -> R.string.prefs_density_comfortable
                                PreferencesStore.Density.COMPACT -> R.string.prefs_density_compact
                            }
                        ChoiceRow(label, density == d) { prefs.setDensity(d) }
                    }

                    Section(R.string.prefs_section_device)
                    SwitchRow(R.string.prefs_show_android, R.string.prefs_show_android_hint, showAndroid) { on ->
                        if (on) confirmAndroid = true else prefs.setShowAndroid(false)
                    }

                    Section(R.string.prefs_section_config)
                    Text(
                        stringResource(R.string.prefs_config_hint),
                        color = t.colors.fgMuted.color,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ActionRow(R.string.prefs_export) { exporter.launch("config.toml") }
                    ActionRow(R.string.prefs_import) { importer.launch(arrayOf("*/*")) }
                }
            }
        }
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
            contentAlignment = Alignment.BottomCenter,
        ) {
            SnackbarHost(snackbar)
        }
        if (themeDialog) ThemeDialog { themeDialog = false }
        if (confirmAndroid) {
            AlertDialog(
                onDismissRequest = { confirmAndroid = false },
                title = { Text(stringResource(R.string.prefs_show_android)) },
                text = { Text(stringResource(R.string.prefs_show_android_warning)) },
                confirmButton = {
                    TextButton({
                        prefs.setShowAndroid(true)
                        confirmAndroid = false
                    }) { Text(stringResource(R.string.prefs_show_android_confirm)) }
                },
                dismissButton = {
                    TextButton(
                        { confirmAndroid = false },
                    ) { Text(stringResource(R.string.ops_cancel)) }
                },
            )
        }
        importError?.let { message ->
            AlertDialog(
                onDismissRequest = { importError = null },
                title = { Text(stringResource(R.string.prefs_import_invalid)) },
                text = { Text(message) },
                confirmButton = { TextButton({ importError = null }) { Text(stringResource(R.string.theme_close)) } },
            )
        }
    }
}

@Composable
private fun Section(
    @StringRes title: Int,
) {
    Text(
        stringResource(title).uppercase(),
        color = TlacuacheTheme.tokens.colors.accent.color,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Label(
    @StringRes text: Int,
) {
    Text(
        stringResource(text),
        color = TlacuacheTheme.tokens.colors.fg.color,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    @StringRes label: Int,
    @StringRes hint: Int?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val t = TlacuacheTheme.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(stringResource(label), color = t.colors.fg.color, style = MaterialTheme.typography.bodyLarge)
            hint?.let {
                Text(stringResource(it), color = t.colors.fgMuted.color, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun ChoiceRow(
    @StringRes label: Int,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null)
        Text(
            stringResource(label),
            color = TlacuacheTheme.tokens.colors.fg.color,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun ActionRow(
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    TextButton(onClick, Modifier.heightIn(min = 48.dp)) { Text(stringResource(label)) }
}
