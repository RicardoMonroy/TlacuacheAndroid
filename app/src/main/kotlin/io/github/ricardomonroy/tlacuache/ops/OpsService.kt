package io.github.ricardomonroy.tlacuache.ops

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.IBinder
import android.os.PowerManager
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.ricardomonroy.tlacuache.MainActivity
import io.github.ricardomonroy.tlacuache.R
import io.github.ricardomonroy.tlacuache.ffi.OpKind
import io.github.ricardomonroy.tlacuache.ffi.OpState
import io.github.ricardomonroy.tlacuache.ffi.Operation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import java.io.File

/**
 * Servicio en primer plano `dataSync` (ADR-A09) que vacía [OpsRepository]: notificación con progreso y
 * Cancelar, `PARTIAL_WAKE_LOCK` mientras corre y aviso a MediaStore de lo que cambió. Se detiene solo al
 * quedar la cola vacía.
 */
class OpsService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var lastStartId = 0
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        val channel =
            NotificationChannel(CHANNEL, getString(R.string.ops_channel), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        wakeLock =
            getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Tlacuache:ops")
    }

    @OptIn(FlowPreview::class)
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        lastStartId = startId
        if (intent?.action == ACTION_CANCEL) {
            OpsRepository.cancel(intent.getLongExtra(EXTRA_ID, 0).toULong())
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(OpsRepository.operations.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        // Todo corre en el hilo principal salvo la E/S del runner: encolar y terminar no se cruzan.
        if (job?.isActive != true) {
            job =
                scope.launch {
                    wakeLock.acquire(WAKE_LOCK_MS)
                    val notifications =
                        launch {
                            val manager = getSystemService(NotificationManager::class.java)
                            OpsRepository.operations
                                .sample(
                                    NOTIFY_MS,
                                ).collect { manager.notify(NOTIFICATION_ID, notification(it)) }
                        }
                    try {
                        OpsRepository.drain(this@OpsService, ::scan)
                    } finally {
                        notifications.cancel()
                        if (wakeLock.isHeld) wakeLock.release()
                        ServiceCompat.stopForeground(this@OpsService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        // Si llegó otra operación mientras terminaba, su arranque es más nuevo y no se detiene.
                        stopSelfResult(lastStartId)
                    }
                }
        }
        return START_NOT_STICKY
    }

    /**
     * Límite diario de `dataSync` (Android 15+): se cancela la operación en curso y se detiene.
     *
     * shortcut: ARCHITECTURE §7 pide pausar la cola y avisar para continuar; por ahora se cancela.
     */
    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        job?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Galería y otras apps ven lo creado y dejan de ver lo quitado (regla 7 de CLAUDE.md). */
    private fun scan(files: List<File>) {
        if (files.isNotEmpty()) MediaScannerConnection.scanFile(this, files.map { it.path }.toTypedArray(), null, null)
    }

    private fun notification(ops: List<Operation>): Notification {
        val running = ops.firstOrNull { it.state == OpState.Running || it.state == OpState.Cancelling }
        val builder =
            NotificationCompat
                .Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification_ops)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
        if (running == null) return builder.setContentTitle(getString(R.string.ops_preparing)).build()
        val count = running.sources.size
        val title =
            resources.getQuantityString(
                if (running.kind == OpKind.MOVE) R.plurals.ops_moving else R.plurals.ops_copying,
                count,
                count,
            )
        val p = running.progress
        val total = p.bytesTotal?.toLong()
        val fraction = running.fraction
        return builder
            .setContentTitle(title)
            .setContentText(
                total?.let {
                    getString(
                        R.string.ops_progress,
                        Formatter.formatShortFileSize(this, p.bytesDone.toLong()),
                        Formatter.formatShortFileSize(this, it),
                    )
                },
            ).setSubText(p.current)
            .setProgress(PROGRESS_MAX, ((fraction ?: 0.0) * PROGRESS_MAX).toInt(), fraction == null)
            .addAction(
                0,
                getString(R.string.ops_cancel),
                PendingIntent.getService(
                    this,
                    running.id.toInt(),
                    Intent(
                        this,
                        OpsService::class.java,
                    ).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, running.id.toLong()),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            ).build()
    }

    companion object {
        private const val CHANNEL = "ops"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_CANCEL = "io.github.ricardomonroy.tlacuache.ops.CANCEL"
        private const val EXTRA_ID = "id"
        private const val NOTIFY_MS = 500L
        private const val PROGRESS_MAX = 1000

        /** Tope del wake lock: el mismo que el límite diario de `dataSync`. */
        private const val WAKE_LOCK_MS = 6 * 60 * 60 * 1000L

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, OpsService::class.java))
    }
}
