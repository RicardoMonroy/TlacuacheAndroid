package io.github.ricardomonroy.tlacuache.ops

import android.content.Context
import android.os.SystemClock
import io.github.ricardomonroy.tlacuache.ffi.OpQueue
import io.github.ricardomonroy.tlacuache.ffi.OpRequest
import io.github.ricardomonroy.tlacuache.ffi.OpState
import io.github.ricardomonroy.tlacuache.ffi.Operation
import io.github.ricardomonroy.tlacuache.ffi.OpsException
import io.github.ricardomonroy.tlacuache.storage.Volumes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import java.io.File
import java.io.IOException

/**
 * Cola de operaciones de todo el proceso (`core::ops::OpQueue`, una activa a la vez), compartida por las
 * ventanas; `OpsService` la vacía (ARCHITECTURE §7). Se usa desde el hilo principal.
 */
object OpsRepository {
    private val queue = OpQueue(1u)

    private val _operations = MutableStateFlow(emptyList<Operation>())

    /** Instantánea de la cola, para la notificación y el indicador. */
    val operations: StateFlow<List<Operation>> = _operations.asStateFlow()

    /** Un conflicto esperando respuesta de la usuaria (diálogo en la ventana). */
    class PendingConflict(
        val opId: ULong,
        val conflict: OpsRunner.Conflict,
        private val answer: CompletableDeferred<OpsRunner.Decision>,
    ) {
        fun answer(decision: OpsRunner.Decision) = answer.complete(decision)
    }

    private val _conflict = MutableStateFlow<PendingConflict?>(null)
    val conflict: StateFlow<PendingConflict?> = _conflict.asStateFlow()

    private val _finished = MutableSharedFlow<Operation>(extraBufferCapacity = 8)

    /** Cada operación al terminar (bien, mal o cancelada), para avisar en la ventana. */
    val finished: SharedFlow<Operation> = _finished.asSharedFlow()

    fun enqueue(
        context: Context,
        request: OpRequest,
    ) {
        queue.enqueue(request)
        publish()
        OpsService.start(context)
    }

    /** Cancelar: en cola, al instante; en curso, el runner se detiene en el siguiente bloque. */
    fun cancel(id: ULong) {
        try {
            queue.cancel(id)
        } catch (_: OpsException) {
            // Ya terminó: no hay nada que cancelar.
        }
        publish()
    }

    fun isIdle() = queue.isIdle()

    private fun publish() {
        _operations.value = queue.operations()
    }

    /** Ejecuta la cola hasta vaciarla; [scan] recibe las rutas cambiadas de cada operación. */
    suspend fun drain(
        context: Context,
        scan: (List<File>) -> Unit,
    ) {
        val runner = OpsRunner(volumeRoots = { Volumes.roots(context) }, askConflict = ::ask)
        while (true) {
            val ids = queue.startNext().ifEmpty { return }
            publish()
            ids.forEach { runOne(it, runner, scan) }
        }
    }

    private suspend fun runOne(
        id: ULong,
        runner: OpsRunner,
        scan: (List<File>) -> Unit,
    ) {
        val op = queue.get(id) ?: return
        val changed = mutableListOf<File>()
        var lastPublish = 0L
        val context = currentCoroutineContext()
        try {
            val journal =
                runner.run(
                    op,
                    // Cancelar desde la notificación o detener el servicio: el runner para en el siguiente bloque.
                    cancelled = { queue.get(id)?.state == OpState.Cancelling || !context.isActive },
                    progress = {
                        queue.updateProgress(id, it)
                        val now = SystemClock.uptimeMillis()
                        if (now - lastPublish >= PUBLISH_MS) {
                            lastPublish = now
                            publish()
                        }
                    },
                    changed = { changed += it },
                )
            queue.complete(id, journal)
        } catch (_: OpsRunner.CancelledException) {
            markCancelled(id)
        } catch (e: OpsRunner.FailedException) {
            queue.fail(id, e.failure.name)
        } catch (_: IOException) {
            queue.fail(id, OpsRunner.Failure.IO.name)
        } catch (e: CancellationException) {
            // El servicio se detuvo (p. ej. límite de tiempo de dataSync): la operación queda cancelada.
            markCancelled(id)
            throw e
        } finally {
            scan(changed)
            publish()
        }
        queue.get(id)?.let(_finished::tryEmit)
    }

    /** Cancelada aunque nadie lo pidiera por la cola (el servicio se detuvo). */
    private fun markCancelled(id: ULong) {
        // En curso pasa a «cancelando» y luego a cancelada; si ya estaba cancelando, el primer paso no hace nada.
        runCatching { queue.cancel(id) }
        runCatching { queue.cancelled(id) }
    }

    private suspend fun ask(conflict: OpsRunner.Conflict): OpsRunner.Decision {
        val answer = CompletableDeferred<OpsRunner.Decision>()
        val id =
            _operations.value.firstOrNull { it.state == OpState.Running || it.state == OpState.Cancelling }?.id ?: 0u
        _conflict.value = PendingConflict(id, conflict, answer)
        return try {
            answer.await()
        } finally {
            _conflict.value = null
        }
    }

    /** La lista y la notificación se repintan como mucho cada tanto, no por cada bloque. */
    private const val PUBLISH_MS = 250L
}
