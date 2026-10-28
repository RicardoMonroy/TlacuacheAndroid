package io.github.ricardomonroy.tlacuache.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.ThemeTokens
import io.github.ricardomonroy.tlacuache.storage.ThemeStore
import kotlinx.coroutines.launch

/**
 * Elegir tema (UI_SPEC §11): se aplica al tocarlo, sin cerrar el diálogo. Con «Seguir al sistema», cada
 * tema ocupa el lugar de su variante: hay dos marcados, uno «se usa en modo claro» y otro en oscuro.
 */
@Composable
fun ThemeDialog(onDismiss: () -> Unit) {
    val store = ThemeStore.shared(LocalContext.current.filesDir)
    val themes by store.themes.collectAsStateWithLifecycle()
    val setting by store.setting.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val t = TlacuacheTheme.tokens
    val systemDark = isSystemInDarkTheme()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(setting.followSystem, role = Role.Switch) {
                            scope.launch { store.followSystem(it, systemDark) }
                        }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.theme_follow_system), Modifier.weight(1f), color = t.colors.fg.color)
                    Switch(setting.followSystem, onCheckedChange = null)
                }
                if (setting.followSystem) {
                    Text(
                        stringResource(R.string.theme_follow_hint),
                        color = t.colors.fgMuted.color,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                themes.orEmpty().forEach { theme ->
                    val tokens = theme.tokens
                    val chosen =
                        if (setting.followSystem) {
                            tokens.id == if (tokens.dark) setting.dark else setting.light
                        } else {
                            tokens.id == setting.name
                        }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(chosen, role = Role.RadioButton) { scope.launch { store.choose(tokens) } }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(chosen, onClick = null)
                        Swatch(tokens)
                        Column(Modifier.weight(1f)) {
                            Text(tokens.name, color = t.colors.fg.color, style = MaterialTheme.typography.bodyLarge)
                            // Siguiendo al sistema hay dos marcados: dice en qué modo se usa cada uno.
                            val variant =
                                stringResource(
                                    when {
                                        chosen && setting.followSystem && tokens.dark -> R.string.theme_used_dark
                                        chosen && setting.followSystem -> R.string.theme_used_light
                                        tokens.dark -> R.string.theme_dark
                                        else -> R.string.theme_light
                                    },
                                )
                            val user = if (theme.user) " · " + stringResource(R.string.theme_user) else ""
                            Text(
                                variant + user,
                                color = t.colors.fgMuted.color,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.theme_close)) } },
    )
}

/** Muestra del tema: su fondo de panel con el acento, para reconocerlo sin aplicarlo. */
@Composable
private fun Swatch(tokens: ThemeTokens) {
    val c = tokens.colors
    Box(
        Modifier
            .size(32.dp)
            .background(c.paneBg.color, MaterialTheme.shapes.small)
            .border(1.dp, c.borderStrong.color, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(14.dp).background(c.accent.color, MaterialTheme.shapes.extraSmall))
    }
}
