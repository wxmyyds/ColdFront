package io.github.wxmyyds.coldfront.ble

import io.github.wxmyyds.coldfront.ble.ModeCommandCoordinator.Mode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModeCommandCoordinatorTest {
    private data class Write(val mode: Mode, val on: Boolean)

    /**
     * No Android state or optimistic read-back. Gates model queued/accepted transport
     * operations; both transport callbacks use the production command completion helper.
     */
    private fun TestScope.Commands() = Commands(backgroundScope)

    private class Commands(scope: CoroutineScope) {
        val coordinator = ModeCommandCoordinator(scope)
        var current = true
        var controlFresh = true
        var powerLimited = false
        val reported = mutableSetOf<Mode>()
        val unavailable = mutableSetOf<Mode>()
        val attempts = mutableListOf<Write>()
        val writes = mutableListOf<Write>()
        val reads = mutableListOf<Write>()
        var beforeWrite: suspend (Write) -> Unit = {}
        var afterWrite: suspend (Write) -> Boolean = { true }
        var beforeRead: suspend (Write) -> Unit = {}
        var duringRead: suspend (Write) -> Unit = {}

        fun request(mode: Mode, on: Boolean) = coordinator.request(mode, on)

        suspend fun execute(request: ModeCommandCoordinator.Request): Boolean = coordinator.execute(
            request = request,
            isCurrent = {
                current && controlFresh &&
                    (request.mode != Mode.BOOST || !request.on || !powerLimited)
            },
            reportedOn = { it in reported },
            write = command@{ mode, on, ticketFresh ->
                val step = Write(mode, on)
                attempts += step
                if (mode in unavailable) return@command null
                executeFreshCommand(
                    compositeFresh = ticketFresh,
                    write = { fresh ->
                        beforeWrite(step)
                        if (!fresh()) false else {
                            writes += step
                            afterWrite(step)
                        }
                    },
                    readBack = { fresh ->
                        beforeRead(step)
                        if (fresh()) {
                            reads += step
                            duringRead(step)
                        }
                    },
                )
            },
        )
    }

    @Test
    fun `failed or unavailable opposite OFF never permits target ON in either direction`() = runTest {
        for (target in Mode.entries) for (missing in listOf(false, true)) {
            val commands = Commands()
            commands.reported += target.other
            if (missing) commands.unavailable += target.other
            commands.afterWrite = { false }

            assertFalse(commands.execute(commands.request(target, true)))
            assertEquals(listOf(Write(target.other, false)), commands.attempts)
            assertEquals(if (missing) emptyList() else commands.attempts, commands.writes)
        }
    }

    @Test
    fun `reported opposite ON is successfully turned OFF before target ON`() = runTest {
        for (target in Mode.entries) {
            val commands = Commands()
            commands.reported += target.other
            val request = commands.request(target, true)
            assertTrue(commands.execute(request))
            assertEquals(listOf(Write(target.other, false), Write(target, true)), commands.writes)
            // The attached OFF must not invalidate the original ticket.
            assertTrue(commands.coordinator.isFresh(request))
        }
    }

    @Test
    fun `pending Boost ON is turned OFF before Smart ON despite reported Boost OFF`() = runTest {
        assertPendingSwitch(Mode.BOOST)
    }

    @Test
    fun `pending Smart ON is turned OFF before Boost ON despite reported Smart OFF`() = runTest {
        assertPendingSwitch(Mode.SMART)
    }

    private suspend fun TestScope.assertPendingSwitch(from: Mode) {
        val commands = Commands()
        val callback = CompletableDeferred<Boolean>()
        commands.afterWrite = { if (it == Write(from, true)) callback.await() else true }
        val old = commands.request(from, true)
        val oldResult = async { commands.execute(old) }
        runCurrent()
        assertEquals(listOf(Write(from, true)), commands.writes)
        assertTrue(commands.reported.isEmpty())

        val latest = commands.request(from.other, true)
        val latestResult = async { commands.execute(latest) }
        runCurrent()
        assertFalse(commands.coordinator.isFresh(old))
        // The consumer retains the sequence while the old ON is accepted and pending.
        assertEquals(listOf(Write(from, true)), commands.writes)
        callback.complete(true)
        runCurrent()
        assertFalse(oldResult.await())
        assertTrue(latestResult.await())
        assertEquals(
            listOf(Write(from, true), Write(from, false), Write(from.other, true)),
            commands.writes,
        )
        assertFalse(Write(from, true) in commands.reads)
    }

    @Test
    fun `registration supersedes unsent opposite ON but retains its conservative flag`() = runTest {
        for (from in Mode.entries) {
            val commands = Commands()
            val old = commands.request(from, true)
            val latest = commands.request(from.other, true)
            assertFalse(commands.execute(old))
            assertTrue(commands.execute(latest))
            assertEquals(listOf(Write(from, false), Write(from.other, true)), commands.writes)
        }
    }

    @Test
    fun `opposite OFF request also invalidates older ON without clearing its possible ON`() = runTest {
        val commands = Commands()
        val old = commands.request(Mode.SMART, true)
        val off = commands.request(Mode.BOOST, false)
        assertFalse(commands.execute(old))
        assertTrue(commands.execute(off))
        assertTrue(commands.execute(commands.request(Mode.BOOST, true)))
        assertEquals(
            listOf(Write(Mode.BOOST, false), Write(Mode.SMART, false), Write(Mode.BOOST, true)),
            commands.writes,
        )
    }

    @Test
    fun `only successful OFF clears possible ON including when original ON failed`() = runTest {
        for (offSucceeded in listOf(false, true)) {
            val commands = Commands()
            commands.afterWrite = { false }
            assertFalse(commands.execute(commands.request(Mode.BOOST, true)))
            commands.afterWrite = { offSucceeded }
            assertEquals(offSucceeded, commands.execute(commands.request(Mode.BOOST, false)))
            commands.afterWrite = { true }
            commands.writes.clear()
            assertTrue(commands.execute(commands.request(Mode.SMART, true)))
            val expected = if (offSucceeded) listOf(Write(Mode.SMART, true)) else {
                listOf(Write(Mode.BOOST, false), Write(Mode.SMART, true))
            }
            assertEquals(expected, commands.writes)
        }
    }

    @Test
    fun `intent change or disconnect during attached OFF prevents target ON`() = runTest {
        for (duringRead in listOf(false, true)) for (disconnect in listOf(false, true)) {
            val commands = Commands()
            commands.reported += Mode.BOOST
            val completion = CompletableDeferred<Unit>()
            if (duringRead) commands.duringRead = { completion.await() }
            else commands.afterWrite = { completion.await(); true }
            val request = commands.request(Mode.SMART, true)
            val result = async { commands.execute(request) }
            runCurrent()
            assertEquals(listOf(Write(Mode.BOOST, false)), commands.writes)
            if (disconnect) commands.current = false else commands.request(Mode.SMART, false)
            completion.complete(Unit)
            runCurrent()
            assertFalse(result.await())
            assertEquals(listOf(Write(Mode.BOOST, false)), commands.writes)
        }
    }

    @Test
    fun `successful but stale OFF cannot clear a newer possible ON`() = runTest {
        val commands = Commands()
        commands.request(Mode.BOOST, true)
        val completion = CompletableDeferred<Unit>()
        commands.duringRead = { completion.await() }
        val smart = commands.request(Mode.SMART, true)
        val result = async { commands.execute(smart) }
        runCurrent()
        commands.request(Mode.BOOST, true)
        completion.complete(Unit)
        runCurrent()
        assertFalse(result.await())
        // Even though the first OFF got a successful callback, a new Boost ON was
        // registered during its read-back. Only a fresh successful OFF can clear it.
        assertTrue(commands.execute(commands.request(Mode.SMART, true)))
        assertEquals(
            listOf(Write(Mode.BOOST, false), Write(Mode.BOOST, false), Write(Mode.SMART, true)),
            commands.writes,
        )
    }

    @Test
    fun `stale or disconnected queued event performs no followup writes`() = runTest {
        for (disconnect in listOf(false, true)) {
            val commands = Commands()
            val completion = CompletableDeferred<Unit>()
            commands.afterWrite = { completion.await(); true }
            val boost = commands.request(Mode.BOOST, true)
            val first = async { commands.execute(boost) }
            runCurrent()
            val smart = commands.request(Mode.SMART, true)
            val waiting = async { commands.execute(smart) }
            runCurrent()
            if (disconnect) commands.current = false else commands.request(Mode.BOOST, false)
            completion.complete(Unit)
            runCurrent()
            assertFalse(first.await())
            assertFalse(waiting.await())
            assertEquals(listOf(Write(Mode.BOOST, true)), commands.writes)
        }
    }

    @Test
    fun `ticket freshness reaches queued target and attached OFF writes`() = runTest {
        for (attachedOff in listOf(false, true)) {
            val commands = Commands()
            if (attachedOff) commands.reported += Mode.BOOST
            val lane = CompletableDeferred<Unit>()
            commands.beforeWrite = { lane.await() }
            val smart = commands.request(Mode.SMART, true)
            val result = async { commands.execute(smart) }
            runCurrent()
            assertEquals(listOf(Write(if (attachedOff) Mode.BOOST else Mode.SMART, !attachedOff)), commands.attempts)
            commands.request(Mode.BOOST, false)
            lane.complete(Unit)
            runCurrent()
            assertFalse(result.await())
            assertTrue(commands.writes.isEmpty())
        }
    }

    @Test
    fun `ticket freshness reaches queued target and attached OFF reads`() = runTest {
        for (attachedOff in listOf(false, true)) {
            val commands = Commands()
            if (attachedOff) commands.reported += Mode.BOOST
            val lane = CompletableDeferred<Unit>()
            commands.beforeRead = { lane.await() }
            val smart = commands.request(Mode.SMART, true)
            val result = async { commands.execute(smart) }
            runCurrent()
            commands.request(Mode.BOOST, false)
            lane.complete(Unit)
            runCurrent()
            assertFalse(result.await())
            assertTrue(commands.reads.isEmpty())
            assertEquals(listOf(Write(if (attachedOff) Mode.BOOST else Mode.SMART, !attachedOff)), commands.writes)
        }
    }

    @Test
    fun `power limit arriving while Boost write is queued prevents platform ON`() = runTest {
        val commands = Commands()
        val lane = CompletableDeferred<Unit>()
        commands.beforeWrite = { lane.await() }
        val boost = commands.request(Mode.BOOST, true)
        val result = async { commands.execute(boost) }
        runCurrent()
        assertEquals(listOf(Write(Mode.BOOST, true)), commands.attempts)
        commands.powerLimited = true
        lane.complete(Unit)
        runCurrent()
        assertFalse(result.await())
        assertTrue(commands.writes.isEmpty())
        // Power limiting only forbids ON, not a safety OFF.
        assertTrue(commands.execute(commands.request(Mode.BOOST, false)))
    }

    @Test
    fun `power limit arriving during Smart OFF prevents following Boost ON`() = runTest {
        val commands = Commands()
        commands.reported += Mode.SMART
        val callback = CompletableDeferred<Unit>()
        commands.afterWrite = { callback.await(); true }
        val boost = commands.request(Mode.BOOST, true)
        val result = async { commands.execute(boost) }
        runCurrent()
        commands.powerLimited = true
        callback.complete(Unit)
        runCurrent()
        assertFalse(result.await())
        assertEquals(listOf(Write(Mode.SMART, false)), commands.writes)
    }

    @Test
    fun `invalid main control generation prevents even attached OFF`() = runTest {
        val commands = Commands()
        commands.reported += Mode.BOOST
        val smart = commands.request(Mode.SMART, true)
        commands.controlFresh = false
        assertFalse(commands.execute(smart))
        assertTrue(commands.attempts.isEmpty())
    }

    @Test
    fun `cancelled awaiter does not cancel accepted manager owned mode sequence`() = runTest {
        val commands = Commands()
        commands.reported += Mode.BOOST
        val callback = CompletableDeferred<Unit>()
        commands.afterWrite = { callback.await(); true }
        val smart = commands.request(Mode.SMART, true)
        // Same ownership as startMode / setSmartAndAwait: only the manager owns async.
        val managerCommand = backgroundScope.async { commands.execute(smart) }
        val waiter = launch { managerCommand.await() }
        runCurrent()
        assertEquals(listOf(Write(Mode.BOOST, false)), commands.writes)
        waiter.cancel()
        runCurrent()
        assertTrue(managerCommand.isActive)
        callback.complete(Unit)
        runCurrent()
        assertTrue(managerCommand.await())
        assertEquals(listOf(Write(Mode.BOOST, false), Write(Mode.SMART, true)), commands.writes)
    }

    @Test
    fun `production completion returns false when replaced or disconnected during readback`() = runTest {
        for (disconnect in listOf(false, true)) {
            val coordinator = ModeCommandCoordinator(backgroundScope)
            val ticket = coordinator.request(Mode.SMART, true)
            var connected = true
            val compositeFresh = { connected && coordinator.isFresh(ticket) }
            val read = CompletableDeferred<Unit>()
            var reading = false
            // Exercise the helper directly: coordinator's final check must not mask a
            // regression to executeCommand returning the old write result after a read.
            val result = async {
                executeFreshCommand(
                    compositeFresh = compositeFresh,
                    write = { assertSame(compositeFresh, it); true },
                    readBack = { assertSame(compositeFresh, it); reading = true; read.await() },
                )
            }
            runCurrent()
            assertTrue(reading)
            if (disconnect) connected = false else coordinator.request(Mode.BOOST, true)
            read.complete(Unit)
            runCurrent()
            assertFalse(result.await())
        }
    }

    @Test
    fun `nonmode controls keep independent generation and ignore mode requests`() = runTest {
        for (supersedeOwnControl in listOf(false, true)) {
            val coordinator = ModeCommandCoordinator(backgroundScope)
            var controlGeneration = 1L
            val generation = controlGeneration
            val read = CompletableDeferred<Unit>()
            val result = async {
                executeFreshCommand(
                    compositeFresh = { generation == controlGeneration },
                    write = { true },
                    readBack = { read.await() },
                )
            }
            runCurrent()
            coordinator.request(Mode.SMART, true)
            coordinator.request(Mode.BOOST, true)
            if (supersedeOwnControl) controlGeneration++
            read.complete(Unit)
            runCurrent()
            assertEquals(!supersedeOwnControl, result.await())
        }
    }

    @Test
    fun `safety OFF handles accepted Boost ON even before the device reports ON`() = runTest {
        val coordinator = ModeCommandCoordinator(backgroundScope)
        var limited = false
        val callback = CompletableDeferred<Unit>()
        val writes = mutableListOf<Write>()
        val boost = coordinator.request(Mode.BOOST, true)
        val enabling = async {
            coordinator.execute(boost, { !limited }, { false }) { mode, on, fresh ->
                executeFreshCommand(fresh, write = {
                    writes += Write(mode, on)
                    callback.await()
                    true
                }, readBack = {})
            }
        }
        runCurrent()
        limited = true
        val safety = async {
            coordinator.enforceOff(Mode.BOOST, { limited }, { false }) {
                writes += Write(Mode.BOOST, false)
                true
            }
        }
        runCurrent()
        callback.complete(Unit)
        assertFalse(enabling.await())
        assertTrue(safety.await())
        assertEquals(listOf(Write(Mode.BOOST, true), Write(Mode.BOOST, false)), writes)
    }

    @Test
    fun `safety enforcement preserves Smart intent while its attached Boost OFF is pending`() = runTest {
        val coordinator = ModeCommandCoordinator(backgroundScope)
        var boostOn = true
        val callback = CompletableDeferred<Unit>()
        val writes = mutableListOf<Write>()
        val smart = coordinator.request(Mode.SMART, true)
        val enabling = async {
            coordinator.execute(smart, { true }, { it == Mode.BOOST && boostOn }) { mode, on, fresh ->
                executeFreshCommand(fresh, write = {
                    writes += Write(mode, on)
                    if (mode == Mode.BOOST) {
                        callback.await()
                        boostOn = false
                    }
                    true
                }, readBack = {})
            }
        }
        runCurrent()
        val safety = async {
            coordinator.enforceOff(Mode.BOOST, { true }, { boostOn }) {
                writes += Write(Mode.BOOST, false)
                true
            }
        }
        runCurrent()
        assertTrue(coordinator.isFresh(smart))
        callback.complete(Unit)
        assertTrue(enabling.await())
        assertTrue(safety.await())
        assertEquals(listOf(Write(Mode.BOOST, false), Write(Mode.SMART, true)), writes)
    }

    @Test
    fun `closing session invalidates active and queued events without starting followup writes`() = runTest {
        val commands = Commands()
        val callback = CompletableDeferred<Unit>()
        commands.afterWrite = { callback.await(); true }
        val boost = commands.request(Mode.BOOST, true)
        val active = async { commands.execute(boost) }
        runCurrent()
        val smart = commands.request(Mode.SMART, true)
        val queued = async { commands.execute(smart) }
        runCurrent()
        commands.coordinator.close()
        assertFalse(commands.coordinator.isFresh(smart))
        assertFalse(commands.execute(commands.request(Mode.SMART, false)))
        callback.complete(Unit)
        assertFalse(active.await())
        assertFalse(queued.await())
        assertEquals(listOf(Write(Mode.BOOST, true)), commands.writes)
    }

    @Test
    fun `fresh commands retain write result and best effort read semantics`() = runTest {
        for (ok in listOf(false, true)) {
            var readAttempted = false
            val result = executeFreshCommand(
                compositeFresh = { true },
                write = { ok },
                readBack = { readAttempted = true },
            )
            assertEquals(ok, result)
            assertTrue(readAttempted)
        }
    }
}
