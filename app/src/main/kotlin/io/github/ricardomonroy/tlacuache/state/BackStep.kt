package io.github.ricardomonroy.tlacuache.state

/** Qué hace el gesto atrás del sistema (y Esc), en el orden de UI_SPEC §3. */
enum class BackStep {
    CLOSE_MENU,
    CLEAR_FILTER,
    CLEAR_SELECTION,
    HISTORY_BACK,

    /** Nada que deshacer en la app: el sistema la cierra. */
    EXIT,
    ;

    companion object {
        fun of(
            menuOpen: Boolean,
            filterActive: Boolean,
            selectionActive: Boolean,
            canGoBack: Boolean,
        ): BackStep =
            when {
                menuOpen -> CLOSE_MENU
                filterActive -> CLEAR_FILTER
                selectionActive -> CLEAR_SELECTION
                canGoBack -> HISTORY_BACK
                else -> EXIT
            }
    }
}
