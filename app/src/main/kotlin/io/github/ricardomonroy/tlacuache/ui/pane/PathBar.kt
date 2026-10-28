package io.github.ricardomonroy.tlacuache.ui.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.splitForCompletion
import io.github.ricardomonroy.tlacuache.state.PathInput
import io.github.ricardomonroy.tlacuache.state.TabState
import io.github.ricardomonroy.tlacuache.ui.shiftWheelScroll
import io.github.ricardomonroy.tlacuache.ui.theme.MonoFamily
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import io.github.ricardomonroy.tlacuache.ui.theme.headerMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Máximo de sugerencias visibles en la lista desplegable. */
private const val MAX_SUGGESTIONS = 8

/**
 * Barra de ruta (UI_SPEC §2): migas desde el volumen; tocar la zona vacía o Ctrl+L la vuelve
 * editable, con autocompletado del core. [editing] lo guarda el panel (el gesto atrás lo cierra).
 */
@Composable
fun PathBar(
    ui: TabState.Ui,
    tab: TabState,
    pathInput: PathInput,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (editing) {
        PathEditor(ui.dir, tab, pathInput, onDone = { onEditingChange(false) }, modifier)
    } else {
        Breadcrumb(ui, onCrumb = { tab.goTo(it) }, onEmptyTap = { onEditingChange(true) }, modifier)
    }
}

@Composable
private fun Breadcrumb(
    ui: TabState.Ui,
    onCrumb: (File) -> Unit,
    onEmptyTap: () -> Unit,
    modifier: Modifier,
) {
    val t = TlacuacheTheme.tokens
    val scroll = rememberScrollState()
    // Siempre se ve el final de la ruta, la carpeta actual, también al cambiar el ancho del panel.
    LaunchedEffect(ui.crumbs, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }

    @Composable
    fun Crumb(i: Int) {
        val crumb = ui.crumbs[i]
        val label = ui.rootLabel?.takeIf { i == 0 } ?: crumb.name
        if (i == ui.crumbs.lastIndex) {
            // La actual no se toca: texto sin el relleno del botón, que en un panel estrecho no cabe.
            Text(
                label,
                color = t.colors.headerFg.color,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else {
            TextButton(onClick = { onCrumb(crumb) }) {
                Text(label, color = t.colors.headerMuted, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            }
        }
    }
    BoxWithConstraints(
        modifier.heightIn(min = 48.dp).clickable(onClick = onEmptyTap),
        contentAlignment = Alignment.CenterStart,
    ) {
        // La carpeta actual se mide primero y, si no cabe ni sola, se acorta con «…»; los padres se
        // desplazan en lo que sobra. Dentro del desplazamiento el panel estrecho cortaba su inicio.
        val limit = maxWidth
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f, fill = false).shiftWheelScroll(scroll).horizontalScroll(scroll),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until ui.crumbs.lastIndex) {
                    Crumb(i)
                    Text("›", color = t.colors.headerMuted)
                }
            }
            Box(Modifier.widthIn(max = limit)) { Crumb(ui.crumbs.lastIndex) }
        }
    }
}

@Composable
private fun PathEditor(
    dir: File,
    tab: TabState,
    pathInput: PathInput,
    onDone: () -> Unit,
    modifier: Modifier,
) {
    val t = TlacuacheTheme.tokens
    var value by remember(
        dir,
    ) { pathInput.display(dir).let { mutableStateOf(TextFieldValue(it, TextRange(it.length))) } }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var error by remember { mutableStateOf<TabState.GoTo?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Sugerencias tras una pausa corta al escribir; leer carpetas va fuera del hilo principal.
    LaunchedEffect(value.text) {
        delay(120)
        suggestions = withContext(Dispatchers.IO) { pathInput.suggestions(value.text, dir) }.take(MAX_SUGGESTIONS)
    }

    fun setText(text: String) {
        value = TextFieldValue(text, TextRange(text.length))
        error = null
    }

    fun submit() {
        val result = tab.goTo(pathInput.resolve(value.text, dir))
        if (result == TabState.GoTo.OK) onDone() else error = result
    }

    Column(modifier) {
        Box {
            BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = t.colors.fg.color, fontFamily = MonoFamily),
                cursorBrush = SolidColor(t.colors.accent.color),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                        autoCorrectEnabled = false,
                    ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(t.colors.paneBg.color, MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .focusRequester(focus)
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                // Tab completa como en la terminal; Esc cancela.
                                Key.Tab -> {
                                    pathInput.complete(value.text, dir)?.let(::setText)
                                    true
                                }

                                Key.Escape -> {
                                    onDone()
                                    true
                                }

                                Key.Enter, Key.NumPadEnter -> {
                                    submit()
                                    true
                                }

                                else -> {
                                    false
                                }
                            }
                        },
            )
            val typedDir = splitForCompletion(value.text).dir
            DropdownMenu(
                expanded = suggestions.isNotEmpty() && error == null,
                onDismissRequest = { suggestions = emptyList() },
                // La lista no roba el foco: se sigue escribiendo.
                properties = PopupProperties(focusable = false),
            ) {
                suggestions.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name, fontFamily = MonoFamily) },
                        onClick = { setText("$typedDir$name/") },
                    )
                }
            }
        }
        error?.let {
            Text(
                stringResource(
                    if (it ==
                        TabState.GoTo.OUTSIDE_STORAGE
                    ) {
                        R.string.path_outside_storage
                    } else {
                        R.string.path_not_a_folder
                    },
                ),
                color = t.colors.warning.color,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
            )
        }
    }
}
