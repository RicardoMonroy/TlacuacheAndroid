package io.github.ricardomonroy.tlacuache.ui.pane

import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.Dimension
import coil3.video.VideoFrameDecoder
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.FileCategory
import io.github.ricardomonroy.tlacuache.ffi.FileEntry
import io.github.ricardomonroy.tlacuache.ffi.classify
import io.github.ricardomonroy.tlacuache.ui.theme.TlacuacheTheme
import io.github.ricardomonroy.tlacuache.ui.theme.color
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** Categoría de una entrada para su ícono y color (`core::filetype`); se calcula por celda visible. */
@Composable
fun rememberCategory(entry: FileEntry): FileCategory =
    remember(entry.name, entry.isDir) {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(entry.name).extension.lowercase())
        classify(entry.name, mime, entry.isDir)
    }

/** Ícono simbólico de la categoría (BRAND.md §5), teñido con su color del tema. */
@Composable
fun FileIcon(
    category: FileCategory,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val f = TlacuacheTheme.tokens.filetypes
    val (icon, tint) =
        when (category) {
            FileCategory.FOLDER -> R.drawable.ic_folder to f.folder
            FileCategory.CODE -> R.drawable.ic_file_code to f.code
            FileCategory.IMAGE -> R.drawable.ic_file_image to f.image
            FileCategory.VIDEO -> R.drawable.ic_file_video to f.video
            FileCategory.AUDIO -> R.drawable.ic_file_audio to f.audio
            FileCategory.ARCHIVE -> R.drawable.ic_file_archive to f.archive
            FileCategory.DOCUMENT -> R.drawable.ic_file_document to f.document
            FileCategory.PDF -> R.drawable.ic_file_pdf to f.pdf
            FileCategory.SPREADSHEET -> R.drawable.ic_file_spreadsheet to f.spreadsheet
            FileCategory.TEXT -> R.drawable.ic_file_text to f.text
            FileCategory.EXECUTABLE -> R.drawable.ic_file_executable to f.executable
            FileCategory.OTHER -> R.drawable.ic_file_other to f.other
        }
    Icon(
        painterResource(icon),
        contentDescription = null,
        tint = tint.color,
        modifier = modifier.then(Modifier.size(size)),
    )
}

/**
 * Miniatura de fotos, videos (un fotograma) y PDF (primera página); el ícono de la categoría mientras
 * carga, si falla o si el tipo no tiene miniatura.
 */
@Composable
fun FileThumbnail(
    dir: File,
    entry: FileEntry,
    category: FileCategory,
    modifier: Modifier = Modifier,
) {
    val model =
        remember(dir, entry.name, entry.modifiedMs, category) {
            val file = File(dir, entry.name)
            when (category) {
                FileCategory.IMAGE, FileCategory.VIDEO -> file
                FileCategory.PDF -> PdfPage(file, entry.modifiedMs ?: 0)
                else -> null
            }
        }
    var loaded by remember(model) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!loaded) FileIcon(category, 48.dp)
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // De una página se ve el encabezado, no el centro.
                alignment = if (category == FileCategory.PDF) Alignment.TopCenter else Alignment.Center,
                onState = { loaded = it is AsyncImagePainter.State.Success },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Cargador de imágenes de la app: fotogramas de video y primera página de PDF además de las imágenes. */
fun thumbnailLoader(context: android.content.Context): ImageLoader =
    ImageLoader
        .Builder(context)
        .components {
            add(VideoFrameDecoder.Factory())
            add(PdfPageFetcher.Factory())
            add(PdfPageKeyer())
        }.build()

/** Primera página de un PDF como modelo de Coil; [modifiedMs] invalida la caché si el archivo cambia. */
data class PdfPage(
    val file: File,
    val modifiedMs: Long,
)

private class PdfPageKeyer : Keyer<PdfPage> {
    override fun key(
        data: PdfPage,
        options: Options,
    ) = "pdf:${data.file.path}:${data.modifiedMs}"
}

/** Renderiza la página 1 con el `PdfRenderer` del sistema al tamaño pedido (Coil lo llama fuera del hilo principal). */
private class PdfPageFetcher(
    private val data: PdfPage,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bitmap =
            ParcelFileDescriptor.open(data.file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    renderer.openPage(0).use { page ->
                        val side = (options.size.width as? Dimension.Pixels)?.px ?: DEFAULT_SIDE
                        val scale = min(side.toFloat() / page.width, side.toFloat() / page.height)
                        val w = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val h = (page.height * scale).roundToInt().coerceAtLeast(1)
                        createBitmap(w, h).also {
                            // El papel del documento, no un color de la interfaz: los PDF suelen no pintar el fondo.
                            it.eraseColor(Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        return ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<PdfPage> {
        override fun create(
            data: PdfPage,
            options: Options,
            imageLoader: ImageLoader,
        ) = PdfPageFetcher(data, options)
    }

    private companion object {
        const val DEFAULT_SIDE = 256
    }
}
