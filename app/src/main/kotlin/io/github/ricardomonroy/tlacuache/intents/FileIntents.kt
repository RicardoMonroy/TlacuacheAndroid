package io.github.ricardomonroy.tlacuache.intents

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/** Abrir y compartir con otras apps mediante URIs `content://` de nuestro FileProvider (ARCHITECTURE §9). */
object FileIntents {
    private const val ANY = "*/*"

    /** Tipo MIME por extensión; comodín si Android no la conoce. */
    fun mimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: ANY

    private fun uri(
        context: Context,
        file: File,
    ): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /**
     * Abre con la app predeterminada. Con un tipo desconocido muestra el selector: cualquier app que
     * acepte todo (un reproductor, por ejemplo) se quedaría con el archivo. `false` si ninguna puede.
     */
    fun open(
        context: Context,
        file: File,
        chooserTitle: String,
    ): Boolean =
        if (mimeType(file) ==
            ANY
        ) {
            openWith(context, file, chooserTitle)
        } else {
            start(context, view(context, file))
        }

    /** Muestra siempre el selector de apps, aunque haya una predeterminada. */
    fun openWith(
        context: Context,
        file: File,
        title: String,
    ): Boolean = start(context, Intent.createChooser(view(context, file), title))

    fun share(
        context: Context,
        files: List<File>,
        title: String,
    ): Boolean {
        val uris = files.map { uri(context, it) }
        val types = files.map(::mimeType).distinct()
        val send =
            if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        send.type = types.singleOrNull() ?: ANY
        // ClipData con todas las URIs: así el permiso de lectura llega a la app elegida.
        send.clipData =
            ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(context, Intent.createChooser(send, title))
    }

    private fun view(
        context: Context,
        file: File,
    ) = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri(context, file), mimeType(file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun start(
        context: Context,
        intent: Intent,
    ): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
}
