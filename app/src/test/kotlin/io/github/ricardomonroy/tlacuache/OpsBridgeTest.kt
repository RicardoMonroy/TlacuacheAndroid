package io.github.ricardomonroy.tlacuache

import io.github.ricardomonroy.tlacuache.ffi.ConflictAction
import io.github.ricardomonroy.tlacuache.ffi.ConflictPolicy
import io.github.ricardomonroy.tlacuache.ffi.Journal
import io.github.ricardomonroy.tlacuache.ffi.MovedItem
import io.github.ricardomonroy.tlacuache.ffi.NameError
import io.github.ricardomonroy.tlacuache.ffi.OpKind
import io.github.ricardomonroy.tlacuache.ffi.OpQueue
import io.github.ricardomonroy.tlacuache.ffi.OpRequest
import io.github.ricardomonroy.tlacuache.ffi.OpState
import io.github.ricardomonroy.tlacuache.ffi.OpsException
import io.github.ricardomonroy.tlacuache.ffi.nextFreeName
import io.github.ricardomonroy.tlacuache.ffi.validateName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cola de operaciones, conflictos y nombres del core a través de JNA, como los usará el runner (A3.2). */
class OpsBridgeTest {
    @Test
    fun queueStatesAndUndo() {
        val queue = OpQueue(1u)
        val id = queue.enqueue(OpRequest(OpKind.COPY, listOf("/i/a.jpg"), "/sd", emptyList()))
        assertEquals(listOf(id), queue.startNext())
        queue.complete(id, Journal(listOf(MovedItem("/i/a.jpg", "/sd/a.jpg")), false, emptyList(), emptyList()))
        assertEquals(OpState.Done, queue.get(id)?.state)
        assertEquals(OpRequest(OpKind.TRASH, listOf("/sd/a.jpg"), null, emptyList()), queue.takeUndo(id))
        val error = runCatching { queue.fail(42u, "x") }.exceptionOrNull()
        assertTrue(error is OpsException.NotFound)
    }

    @Test
    fun conflictsAndNames() {
        val policy = ConflictPolicy()
        policy.remember(ConflictAction.KEEP_BOTH)
        assertEquals(ConflictAction.KEEP_BOTH, policy.remembered(true))
        assertEquals("foto (2).jpg", nextFreeName("foto.jpg", false, listOf("foto.jpg")))
        assertNull(validateName("nota.txt"))
        assertEquals(NameError.CONTAINS_SLASH, validateName("a/b"))
    }
}
