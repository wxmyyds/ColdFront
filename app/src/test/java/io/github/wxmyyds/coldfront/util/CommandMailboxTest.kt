package io.github.wxmyyds.coldfront.util

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommandMailboxTest {
    @Test
    fun `whole suspending commands execute in submission order without time advancement`() = runTest {
        val mailbox = CommandMailbox(backgroundScope)
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val first = async { mailbox.execute { events += "first-start"; release.await(); events += "first-end"; 1 } }
        runCurrent()
        val second = async { mailbox.execute { events += "second"; 2 } }
        runCurrent()
        assertEquals(listOf("first-start"), events)
        release.complete(Unit)
        assertEquals(1, first.await())
        assertEquals(2, second.await())
        assertEquals(listOf("first-start", "first-end", "second"), events)
    }

    @Test
    fun `cancelled queued reply is skipped but cancelled active reply cannot release the sequence`() = runTest {
        val mailbox = CommandMailbox(backgroundScope)
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<Int>()
        val active = launch { mailbox.execute { events += 1; release.await(); events += 2 } }
        runCurrent()
        val skipped = launch { mailbox.execute { events += 99 } }
        val next = async { mailbox.execute { events += 3 } }
        runCurrent()
        active.cancel()
        skipped.cancel()
        runCurrent()
        assertEquals(listOf(1), events)
        assertFalse(next.isCompleted)
        release.complete(Unit)
        next.await()
        assertEquals(listOf(1, 2, 3), events)
    }

    @Test
    fun `command exceptions and local cancellation do not kill the consumer`() = runTest {
        val mailbox = CommandMailbox(backgroundScope)
        val failure = IOException("disk write")
        val result = runCatching { mailbox.execute<Int> { throw failure } }
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(failure.message, result.exceptionOrNull()?.message)
        val cancelled = runCatching { mailbox.execute<Int> { throw CancellationException("command only") } }
        assertTrue(cancelled.exceptionOrNull() is CancellationException)
        assertEquals(3, mailbox.execute { 3 })
    }

    @Test
    fun `close rejects new commands but drains work already admitted`() = runTest {
        val mailbox = CommandMailbox(backgroundScope)
        val release = CompletableDeferred<Unit>()
        val first = async { mailbox.execute { release.await(); 1 } }
        runCurrent()
        val next = async { mailbox.execute { 2 } }
        runCurrent()
        mailbox.close()
        assertTrue(runCatching { mailbox.execute { 99 } }.exceptionOrNull() is CancellationException)
        release.complete(Unit)
        assertEquals(1, first.await())
        assertEquals(2, next.await())
    }

    @Test
    fun `lifecycle cancellation releases active and queued replies and closes submissions`() = runTest {
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val mailbox = CommandMailbox(scope)
        val first = async { mailbox.execute { CompletableDeferred<Unit>().await() } }
        runCurrent()
        val queued = async { mailbox.execute { error("Must not start") } }
        runCurrent()
        scope.cancel()
        runCurrent()
        assertTrue(first.isCancelled)
        assertTrue(queued.isCancelled)
        assertTrue(runCatching { mailbox.execute { 1 } }.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `already cancelled lifecycle cannot strand a submitted reply`() = runTest {
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job().also { it.cancel() })
        val mailbox = CommandMailbox(scope)
        val reply = async { mailbox.execute { error("Must not start") } }
        runCurrent()
        assertTrue(reply.isCancelled)
    }
}
