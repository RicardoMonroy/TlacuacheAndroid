package io.github.ricardomonroy.tlacuache.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/** Un panel: sus pestañas y cuál está activa (ARCHITECTURE §5). Siempre hay al menos una. */
class PaneState(
    startDir: File,
    private val newTab: (File) -> TabState,
) {
    data class Tabs(
        val list: List<TabState>,
        val active: Int,
    ) {
        val current: TabState get() = list[active]
    }

    private val _tabs = MutableStateFlow(Tabs(listOf(newTab(startDir)), 0))
    val tabs: StateFlow<Tabs> = _tabs.asStateFlow()

    /** Ctrl+T: pestaña nueva en la carpeta actual, justo después de la activa (como en Linux). */
    fun openTab() {
        _tabs.update { t ->
            val tab = newTab(t.current.ui.value.dir)
            val at = t.active + 1
            Tabs(t.list.toMutableList().apply { add(at, tab) }, at)
        }
    }

    /** Ctrl+W o ✕: cierra la pestaña [index]; la última no se cierra. */
    fun closeTab(index: Int = _tabs.value.active) {
        _tabs.update { t ->
            if (t.list.size == 1 || index !in t.list.indices) return
            t.list[index].stopWatching()
            val list = t.list.toMutableList().apply { removeAt(index) }
            // La activa sigue siendo la misma pestaña si queda; si se cerró, pasa a la de al lado.
            val active =
                when {
                    index < t.active -> t.active - 1
                    index == t.active -> index.coerceAtMost(list.lastIndex)
                    else -> t.active
                }
            Tabs(list, active)
        }
    }

    fun select(index: Int) {
        _tabs.update { t -> if (index in t.list.indices) t.copy(active = index) else t }
    }

    /** Ctrl+Tab / Ctrl+Shift+Tab, con vuelta al principio o al final. */
    fun cycle(step: Int) {
        _tabs.update { t -> t.copy(active = Math.floorMod(t.active + step, t.list.size)) }
    }
}
