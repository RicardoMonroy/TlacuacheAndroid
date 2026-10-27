package io.github.ricardomonroy.tlacuache.storage

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Acceso al almacenamiento compartido según la versión de Android (ADR-A04). */
object StorageAccess {
    /** Android 11+: «todos los archivos» se concede en Ajustes, no con un diálogo. */
    val usesAllFilesAccess: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** Permisos del diálogo de Android 10. */
    val legacyPermissions =
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    fun isGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            legacyPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }

    /**
     * Abre la pantalla de Ajustes donde el usuario concede el acceso: la de «todos los archivos» de
     * Tlacuache (Android 11+) o, si el fabricante no la tiene, la lista general; en Android 10, la
     * ficha de la app.
     */
    fun openSettings(context: Context) {
        val pkg = Uri.fromParts("package", context.packageName, null)
        val intents =
            if (usesAllFilesAccess) {
                listOf(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg),
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                )
            } else {
                listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
            }
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Algunos fabricantes no tienen la pantalla por app: se prueba la siguiente.
            }
        }
    }
}
