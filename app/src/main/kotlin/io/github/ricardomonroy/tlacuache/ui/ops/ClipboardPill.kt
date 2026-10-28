package io.github.ricardomonroy.tlacuache.ui.ops

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ops.Clipboard
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color

/** Píldora del portapapeles interno (UI_SPEC §4): «3 elementos para pegar · Pegar · ✕», visible mientras haya algo. */
@Composable
fun ClipboardPill(
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = TlacuacheTheme.tokens
    val clip by Clipboard.clip.collectAsStateWithLifecycle()
    val c = clip ?: return
    Row(
        modifier
            .background(t.colors.headerBg.color, MaterialTheme.shapes.large)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pluralStringResource(
                if (c.cut) R.plurals.clip_to_move else R.plurals.clip_to_copy,
                c.files.size,
                c.files.size,
            ),
            color = t.colors.headerFg.color,
            style = MaterialTheme.typography.labelLarge,
        )
        TextButton(onPaste) { Text(stringResource(R.string.paste)) }
        val clear = stringResource(R.string.clip_clear)
        IconButton(Clipboard::clear, Modifier.semantics { contentDescription = clear }) {
            Text("✕", color = t.colors.headerFg.color)
        }
    }
}
