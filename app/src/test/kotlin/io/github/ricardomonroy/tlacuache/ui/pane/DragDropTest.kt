package io.github.ricardomonroy.tlacuache.ui.pane

import io.github.ricardomonroy.tlacuache.ffi.OpKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DragDropTest {
    private val interno = File("/storage/emulated/0")
    private val sd = File("/storage/1234-ABCD")
    private val roots = listOf(interno, sd)
    private val photo = File(interno, "DCIM/Camera/a.jpg")

    @Test
    fun movesWithinAVolumeCopiesAcrossAndModifiersForce() {
        assertEquals(
            OpKind.MOVE,
            dropKind(listOf(photo), File(interno, "Pictures"), roots, ctrl = false, shift = false),
        )
        assertEquals(OpKind.COPY, dropKind(listOf(photo), File(sd, "Fotos"), roots, ctrl = false, shift = false))
        assertEquals(OpKind.COPY, dropKind(listOf(photo), File(interno, "Pictures"), roots, ctrl = true, shift = false))
        assertEquals(OpKind.MOVE, dropKind(listOf(photo), File(sd, "Fotos"), roots, ctrl = false, shift = true))
    }

    @Test
    fun droppingWhereItAlreadyIsDoesNothing() {
        assertTrue(pointlessDrop(listOf(photo), File(interno, "DCIM/Camera")))
        val album = File(interno, "Album")
        assertTrue(pointlessDrop(listOf(album), album))
        assertFalse(pointlessDrop(listOf(photo), File(interno, "Pictures")))
    }
}
