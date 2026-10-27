package io.github.ricardomonroy.tlacuache.state

import org.junit.Assert.assertEquals
import org.junit.Test

class BackStepTest {
    @Test
    fun followsUiSpecOrder() {
        assertEquals(
            BackStep.CLOSE_MENU,
            BackStep.of(menuOpen = true, filterActive = true, selectionActive = true, canGoBack = true),
        )
        assertEquals(
            BackStep.CLEAR_FILTER,
            BackStep.of(menuOpen = false, filterActive = true, selectionActive = true, canGoBack = true),
        )
        assertEquals(
            BackStep.CLEAR_SELECTION,
            BackStep.of(menuOpen = false, filterActive = false, selectionActive = true, canGoBack = true),
        )
        assertEquals(
            BackStep.HISTORY_BACK,
            BackStep.of(menuOpen = false, filterActive = false, selectionActive = false, canGoBack = true),
        )
        assertEquals(
            BackStep.EXIT,
            BackStep.of(menuOpen = false, filterActive = false, selectionActive = false, canGoBack = false),
        )
    }
}
