package io.github.ricardomonroy.tlacuache.storage

import android.app.usage.StorageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.ContextCompat
import io.github.ricardomonroy.tlacuache.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.File

/** Un volumen montado: almacenamiento interno, tarjeta SD o memoria USB (ARCHITECTURE §6). */
data class Volume(
    val root: File,
    /** «Interno» o la descripción del sistema («Tarjeta SD SanDisk», «USB»…). */
    val name: String,
    val kind: Kind,
    /** Bytes libres y totales; 0 si `StatFs` falló (volumen recién retirado). */
    val free: Long,
    val size: Long,
) {
    enum class Kind { INTERNAL, SD, USB }
}

object Volumes {
    /** Volúmenes montados, el interno primero; emite de nuevo al montar o retirar uno. Lee en IO. */
    fun observe(context: Context): Flow<List<Volume>> =
        mountChanges(context.applicationContext)
            .conflate()
            .map { read(context.applicationContext) }
            .flowOn(Dispatchers.IO)

    private fun mountChanges(context: Context): Flow<Unit> =
        callbackFlow {
            trySend(Unit)
            val storage = context.getSystemService(StorageManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val callback =
                    object : StorageManager.StorageVolumeCallback() {
                        override fun onStateChanged(volume: StorageVolume) {
                            trySend(Unit)
                        }
                    }
                storage.registerStorageVolumeCallback(context.mainExecutor, callback)
                awaitClose { storage.unregisterStorageVolumeCallback(callback) }
            } else {
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            context: Context,
                            intent: Intent,
                        ) {
                            trySend(Unit)
                        }
                    }
                val filter =
                    IntentFilter().apply {
                        MOUNT_ACTIONS.forEach(::addAction)
                        addDataScheme("file")
                    }
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                awaitClose { context.unregisterReceiver(receiver) }
            }
        }

    private val MOUNT_ACTIONS =
        listOf(
            Intent.ACTION_MEDIA_MOUNTED,
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_EJECT,
            Intent.ACTION_MEDIA_REMOVED,
            Intent.ACTION_MEDIA_BAD_REMOVAL,
        )

    private fun mounted(storage: StorageManager) =
        storage.storageVolumes.filter {
            it.state == Environment.MEDIA_MOUNTED || it.state == Environment.MEDIA_MOUNTED_READ_ONLY
        }

    /** Raíces de los volúmenes montados, sin uso de disco (carpetas protegidas, ADR-A06). */
    fun roots(context: Context): List<File> {
        val storage = context.getSystemService(StorageManager::class.java)
        return mounted(storage).mapNotNull { rootOf(context, storage, it) }
    }

    private fun read(context: Context): List<Volume> {
        val storage = context.getSystemService(StorageManager::class.java)
        return mounted(storage)
            .mapNotNull { volume ->
                val root = rootOf(context, storage, volume) ?: return@mapNotNull null
                val space = space(context, root)
                val description = volume.getDescription(context)
                Volume(
                    root = root,
                    name = if (volume.isPrimary) context.getString(R.string.volume_internal_short) else description,
                    kind =
                        when {
                            volume.isPrimary -> Volume.Kind.INTERNAL

                            // shortcut: el sistema no dice si es SD o USB; la descripción lo nombra en AOSP y Samsung.
                            description.contains("USB", ignoreCase = true) -> Volume.Kind.USB

                            else -> Volume.Kind.SD
                        },
                    free = space?.first ?: 0,
                    size = space?.second ?: 0,
                )
            }.sortedByDescending { it.kind == Volume.Kind.INTERNAL }
    }

    /** Bytes libres y totales del volumen de [root], o `null` si no se pudo leer. Llamar en IO. */
    fun space(
        context: Context,
        root: File,
    ): Pair<Long, Long>? {
        val stat = runCatching { StatFs(root.path) }.getOrNull() ?: return null

        @Suppress("DEPRECATION")
        val internal = root == Environment.getExternalStorageDirectory().absoluteFile
        // Del interno, Ajustes muestra el tamaño nominal (256 GB), no el del sistema de archivos (245 GB).
        val total =
            if (internal) {
                runCatching {
                    context.getSystemService(StorageStatsManager::class.java).getTotalBytes(StorageManager.UUID_DEFAULT)
                }.getOrNull()
            } else {
                null
            }
        return stat.availableBytes to (total ?: stat.totalBytes)
    }

    /** Raíz del volumen: `directory` en API 30+; en API 29, su carpeta de la app sin `/Android/data/…`. */
    private fun rootOf(
        context: Context,
        storage: StorageManager,
        volume: StorageVolume,
    ): File? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume.directory
        } else {
            context
                .getExternalFilesDirs(null)
                .filterNotNull()
                .firstOrNull { storage.getStorageVolume(it) == volume }
                ?.let { File(it.path.substringBefore("/Android/data/")) }
        }
}
