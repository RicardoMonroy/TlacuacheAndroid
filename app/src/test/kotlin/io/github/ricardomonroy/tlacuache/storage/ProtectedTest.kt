package io.github.ricardomonroy.tlacuache.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProtectedTest {
    private val interno = File("/storage/emulated/0")
    private val sd = File("/storage/1234-ABCD")
    private val roots = listOf(interno, sd)

    @Test
    fun rootsStandardFoldersAndAndroidCannotBeRemoved() {
        assertTrue(Protected.cannotRemove(interno, roots))
        assertTrue(Protected.cannotRemove(File(interno, "DCIM"), roots))
        assertTrue(Protected.cannotRemove(File(sd, "Download"), roots))
        assertTrue(Protected.cannotRemove(File(interno, ".Tlacuache"), roots))
        assertTrue(Protected.cannotRemove(File(interno, "Android/data/x"), roots))
        // Su contenido sí se gestiona, y una carpeta «DCIM» que no está en la raíz es una carpeta más.
        assertFalse(Protected.cannotRemove(File(interno, "DCIM/Camera"), roots))
        assertFalse(Protected.cannotRemove(File(interno, "Documents/DCIM"), roots))
        assertFalse(Protected.cannotRemove(File(interno, "Fotos"), roots))
    }

    @Test
    fun nothingIsWrittenInsideAndroid() {
        assertTrue(Protected.cannotWriteInto(File(interno, "Android/media"), roots))
        assertFalse(Protected.cannotWriteInto(File(interno, "DCIM"), roots))
    }
}
