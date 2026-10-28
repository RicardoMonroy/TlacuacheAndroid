package io.github.ricardomonroy.tlacuache.ops

import io.github.ricardomonroy.tlacuache.ffi.OpKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ClipboardTest {
    @Test
    fun copyPastesManyTimesAndCutOnlyOnce() {
        val a = File("/storage/emulated/0/a.txt")
        val dest = File("/storage/emulated/0/Documents")
        Clipboard.set(listOf(a), cut = false)
        assertEquals(OpKind.COPY, Clipboard.paste(dest)?.kind)
        assertEquals(listOf(a.path), Clipboard.paste(dest)?.sources)

        Clipboard.set(listOf(a), cut = true)
        val move = Clipboard.paste(dest)
        assertEquals(OpKind.MOVE, move?.kind)
        assertEquals(dest.path, move?.dest)
        assertNull(Clipboard.paste(dest))
    }
}
