package io.github.ricardomonroy.tlacuache.ui.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.state.TabState
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color

/** Un panel con su carpeta actual. Relee al volver a primer plano (ARCHITECTURE §6). */
@Composable
fun PaneScreen(tab: TabState) {
    val ui by tab.ui.collectAsStateWithLifecycle()
    val t = TlacuacheTheme.tokens
    // La primera reanudación no relee: la pestaña ya leyó al crearse (o sobrevivió a una rotación).
    val resumed = remember { booleanArrayOf(false) }
    LifecycleResumeEffect(tab) {
        if (resumed[0]) tab.reload()
        resumed[0] = true
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize().background(t.colors.paneBg.color)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(t.colors.headerBg.color)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // shortcut: nombre de la carpeta y conteo; breadcrumb en A1.5, barra de estado en A2.5.
            Text(
                if (ui.isVolumeRoot) stringResource(R.string.volume_internal) else ui.dir.name,
                color = t.colors.headerFg.color,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pluralStringResource(R.plurals.pane_item_count, ui.visible.size, ui.visible.size),
                color = t.colors.fgMuted.color,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box(Modifier.fillMaxSize()) {
            when {
                ui.failed -> {
                    Message(stringResource(R.string.pane_read_error)) { tab.reload() }
                }

                !ui.loading && ui.visible.isEmpty() -> {
                    Message(stringResource(R.string.pane_empty), null)
                }

                else -> {
                    FileList(
                        ui.visible,
                        ui.nowMs,
                        Modifier.fillMaxSize(),
                        WindowInsets.navigationBars.asPaddingValues(),
                    )
                }
            }
            if (ui.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun Message(
    text: String,
    onRetry: (() -> Unit)?,
) {
    val t = TlacuacheTheme.tokens
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = t.colors.fgMuted.color, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Button(onClick = onRetry) { Text(stringResource(R.string.pane_retry)) }
        }
    }
}
