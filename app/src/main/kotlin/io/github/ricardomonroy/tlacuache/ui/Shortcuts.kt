package io.github.ricardomonroy.tlacuache.ui

import android.content.Context
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.res.stringResource
import io.github.ricardomonroy.tlacuache.R
import android.view.KeyEvent as AndroidKeyEvent

/** Una combinación exacta: tecla y modificadores. */
data class Binding(
    val key: Key,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
)

/**
 * Atajos de teclado (UI_SPEC §8). Una sola tabla: el panel y la ventana despachan con [of] y la lista del
 * sistema (Meta+/) sale de [groups], así nunca se contradicen. Sin Meta: Android y DeX la reservan.
 *
 * shortcut: no se remapean con `[keys]` de config.toml: `core::keymap` solo tiene las acciones de la terminal
 * de Linux (ADR-A15).
 */
enum class Shortcut(
    @StringRes val label: Int,
    val group: Group,
    vararg val bindings: Binding,
) {
    BACK(R.string.nav_back, Group.NAVIGATION, Binding(Key.DirectionLeft, alt = true)),
    FORWARD(R.string.nav_forward, Group.NAVIGATION, Binding(Key.DirectionRight, alt = true)),
    UP(R.string.nav_up, Group.NAVIGATION, Binding(Key.Backspace), Binding(Key.DirectionUp, alt = true)),
    EDIT_PATH(R.string.shortcut_edit_path, Group.NAVIGATION, Binding(Key.L, ctrl = true)),
    RELOAD(R.string.shortcut_reload, Group.NAVIGATION, Binding(Key.R, ctrl = true)),
    TOGGLE_HIDDEN(R.string.menu_show_hidden, Group.NAVIGATION, Binding(Key.H, ctrl = true)),
    LIST_VIEW(R.string.shortcut_list_view, Group.NAVIGATION, Binding(Key.One, ctrl = true)),
    ICON_VIEW(R.string.menu_icon_view, Group.NAVIGATION, Binding(Key.Two, ctrl = true)),

    SELECT_ALL(R.string.shortcut_select_all, Group.FILES, Binding(Key.A, ctrl = true)),
    COPY(R.string.action_copy, Group.FILES, Binding(Key.C, ctrl = true)),
    CUT(R.string.action_cut, Group.FILES, Binding(Key.X, ctrl = true)),
    PASTE(R.string.paste, Group.FILES, Binding(Key.V, ctrl = true)),
    RENAME(R.string.action_rename, Group.FILES, Binding(Key.F2)),
    NEW_FOLDER(R.string.new_folder, Group.FILES, Binding(Key.F7), Binding(Key.N, ctrl = true, shift = true)),
    NEW_FILE(R.string.new_file, Group.FILES, Binding(Key.N, ctrl = true, alt = true)),
    TRASH(R.string.action_trash, Group.FILES, Binding(Key.Delete)),
    DELETE(R.string.action_delete, Group.FILES, Binding(Key.Delete, shift = true)),
    UNDO(R.string.ops_undo, Group.FILES, Binding(Key.Z, ctrl = true)),

    COPY_TO_OTHER(R.string.action_copy_to_other, Group.WINDOW, Binding(Key.F5)),
    MOVE_TO_OTHER(R.string.action_move_to_other, Group.WINDOW, Binding(Key.F6)),
    OTHER_PANE(R.string.shortcut_other_pane, Group.WINDOW, Binding(Key.Tab)),
    TOGGLE_DUAL(R.string.menu_dual_pane, Group.WINDOW, Binding(Key.F3)),
    SIDEBAR(R.string.shortcut_sidebar, Group.WINDOW, Binding(Key.F9)),
    NEW_WINDOW(R.string.new_window, Group.WINDOW, Binding(Key.N, ctrl = true)),
    PREFERENCES(R.string.prefs_menu, Group.WINDOW, Binding(Key.Comma, ctrl = true)),
    NEW_TAB(R.string.tab_new, Group.WINDOW, Binding(Key.T, ctrl = true)),
    CLOSE_TAB(R.string.tab_close, Group.WINDOW, Binding(Key.W, ctrl = true)),
    NEXT_TAB(R.string.shortcut_next_tab, Group.WINDOW, Binding(Key.Tab, ctrl = true)),
    PREVIOUS_TAB(R.string.shortcut_previous_tab, Group.WINDOW, Binding(Key.Tab, ctrl = true, shift = true)),
    ;

    enum class Group(
        @StringRes val title: Int,
    ) {
        NAVIGATION(R.string.shortcuts_navigation),
        FILES(R.string.shortcuts_files),
        WINDOW(R.string.shortcuts_window),
    }

    companion object {
        /** El atajo de esta combinación, o `null`. */
        fun of(binding: Binding): Shortcut? = entries.firstOrNull { binding in it.bindings }

        fun of(event: KeyEvent): Shortcut? =
            if (event.isMetaPressed) {
                null
            } else {
                of(Binding(event.key, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed))
            }

        /** La lista de atajos que Android muestra con Meta+/ (`onProvideKeyboardShortcuts`). */
        fun groups(context: Context): List<KeyboardShortcutGroup> =
            Group.entries.map { group ->
                KeyboardShortcutGroup(
                    context.getString(group.title),
                    entries.filter { it.group == group }.flatMap { s ->
                        s.bindings.map { b ->
                            KeyboardShortcutInfo(context.getString(s.label), b.key.nativeKeyCode, b.metaState)
                        }
                    },
                )
            }

        private val Binding.metaState: Int
            get() =
                (if (ctrl) AndroidKeyEvent.META_CTRL_ON else 0) or
                    (if (shift) AndroidKeyEvent.META_SHIFT_ON else 0) or
                    (if (alt) AndroidKeyEvent.META_ALT_ON else 0)
    }
}

/** «Ctrl+Shift+N» de la primera combinación del atajo, para los tooltips. */
@Composable
fun Shortcut.keysLabel(): String {
    val b = bindings.first()
    val key =
        when (b.key) {
            Key.DirectionLeft -> "←"
            Key.DirectionRight -> "→"
            Key.DirectionUp -> "↑"
            Key.Backspace -> stringResource(R.string.key_backspace)
            Key.Delete -> stringResource(R.string.key_delete)
            Key.Tab -> "Tab"
            Key.Comma -> ","
            else -> AndroidKeyEvent.keyCodeToString(b.key.nativeKeyCode).removePrefix("KEYCODE_")
        }
    return listOfNotNull(
        "Ctrl".takeIf {
            b.ctrl
        },
        "Shift".takeIf { b.shift },
        "Alt".takeIf { b.alt },
        key,
    ).joinToString("+")
}

/** Tooltip al pasar el ratón (o pulsación larga con el dedo): «Atrás (Alt+←)» (UI_SPEC §3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithTooltip(
    text: String,
    shortcut: Shortcut?,
    content: @Composable () -> Unit,
) {
    val full = shortcut?.let { "$text (${it.keysLabel()})" } ?: text
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(full) } },
        state = rememberTooltipState(),
        content = content,
    )
}
