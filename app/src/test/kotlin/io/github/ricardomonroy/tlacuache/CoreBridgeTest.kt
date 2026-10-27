package io.github.ricardomonroy.tlacuache

import io.github.ricardomonroy.tlacuache.ffi.builtinTheme
import io.github.ricardomonroy.tlacuache.ffi.coreVersion
import io.github.ricardomonroy.tlacuache.ffi.defaultThemeId
import io.github.ricardomonroy.tlacuache.ui.theme.color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Llama al core real (libtlacuache_ffi del host vía JNA) desde la JVM. */
class CoreBridgeTest {
    @Test
    fun coreVersionNamesFfiAndAppId() {
        val version = coreVersion()
        assertTrue(version, version.startsWith("tlacuache-ffi "))
        assertTrue(version, version.endsWith("io.github.RicardoMonroy.Tlacuache"))
    }

    @Test
    fun defaultThemeIsDarkNord() {
        val nord = checkNotNull(builtinTheme(defaultThemeId()))
        assertEquals("nord", nord.id)
        assertTrue(nord.dark)
        assertEquals(0xFF2E3440u, nord.colors.windowBg)
        assertEquals(26u, nord.style.rowHeight)
    }

    @Test
    fun unknownThemeIsNull() {
        assertNull(builtinTheme("no-existe"))
    }

    @Test
    fun argbConvertsToOpaqueComposeColor() {
        val c = 0xFF2E3440u.color
        assertEquals(1f, c.alpha)
        assertEquals(0x2E / 255f, c.red, 1e-3f)
        assertEquals(0x40 / 255f, c.blue, 1e-3f)
    }
}
