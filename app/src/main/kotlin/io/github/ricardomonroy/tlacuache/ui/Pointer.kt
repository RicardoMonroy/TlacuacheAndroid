package io.github.ricardomonroy.tlacuache.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme

/** El último puntero de la ventana fue un ratón o touchpad (UI_SPEC §3): filas compactas, hover. */
val LocalMousePointer = compositionLocalOf { false }

/** Anota el tipo del último puntero (también el ratón que solo se mueve) y lo publica en [LocalMousePointer]. */
@Composable
fun TrackPointer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var mouse by remember { mutableStateOf(false) }
    Box(
        modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull()?.let {
                        mouse = it.type == PointerType.Mouse
                    }
                }
            }
        },
    ) { CompositionLocalProvider(LocalMousePointer provides mouse, content) }
}

/** Alto de fila: el del tema como base, 32 dp con ratón y 48 dp (objetivo táctil) con el dedo. */
@Composable
fun rowHeight(): Dp {
    val base =
        TlacuacheTheme.tokens.style.rowHeight
            .toInt()
    return (if (LocalMousePointer.current) maxOf(base, 32) else maxOf(base, 48)).dp
}
