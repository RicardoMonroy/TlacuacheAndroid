package io.github.ricardomonroy.tlacuache.ui

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutsTest {
    @Test
    fun everyBindingBelongsToExactlyOneShortcut() {
        val all = Shortcut.entries.flatMap { s -> s.bindings.map { it to s } }
        val repeated = all.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }
        assertEquals(emptyMap<Binding, List<Shortcut>>(), repeated)
    }

    @Test
    fun modifiersMustMatchExactly() {
        assertEquals(Shortcut.TRASH, Shortcut.of(Binding(Key.Delete)))
        assertEquals(Shortcut.DELETE, Shortcut.of(Binding(Key.Delete, shift = true)))
        assertEquals(Shortcut.NEXT_TAB, Shortcut.of(Binding(Key.Tab, ctrl = true)))
        assertEquals(Shortcut.PREVIOUS_TAB, Shortcut.of(Binding(Key.Tab, ctrl = true, shift = true)))
        assertEquals(Shortcut.NEW_FOLDER, Shortcut.of(Binding(Key.N, ctrl = true, shift = true)))
        assertNull(Shortcut.of(Binding(Key.N, ctrl = true)))
    }
}
