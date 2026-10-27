package io.github.ricardomonroy.tlacuache.state

import androidx.annotation.StringRes
import io.github.ricardomonroy.tlacuache.R
import java.io.File

/** Un lugar de la barra lateral (UI_SPEC §6): una carpeta estándar del almacenamiento interno. */
data class Place(
    @param:StringRes val label: Int,
    val kind: Kind,
    val dir: File,
) {
    enum class Kind { IMAGE, VIDEO, DOWNLOAD, DOCUMENT, AUDIO }

    companion object {
        /** Orden de UI_SPEC §6; cada lugar admite varias carpetas y usa la primera que exista. */
        private val STANDARD =
            listOf(
                Triple(R.string.place_photos, Kind.IMAGE, listOf("DCIM")),
                Triple(R.string.place_pictures, Kind.IMAGE, listOf("Pictures")),
                Triple(R.string.place_videos, Kind.VIDEO, listOf("Movies")),
                Triple(R.string.place_downloads, Kind.DOWNLOAD, listOf("Download")),
                Triple(R.string.place_documents, Kind.DOCUMENT, listOf("Documents")),
                Triple(R.string.place_music, Kind.AUDIO, listOf("Music")),
                // shortcut: «Sonidos» abre la primera de las tres; una vista que las junte, si hace falta.
                Triple(R.string.place_sounds, Kind.AUDIO, listOf("Ringtones", "Notifications", "Alarms")),
                Triple(R.string.place_recordings, Kind.AUDIO, listOf("Recordings")),
            )

        /** Lugares cuya carpeta existe en [root]; se omiten los que no (UI_SPEC §6). */
        fun existing(root: File): List<Place> =
            STANDARD.mapNotNull { (label, kind, names) ->
                names.map { File(root, it) }.firstOrNull(File::isDirectory)?.let { Place(label, kind, it) }
            }
    }
}
