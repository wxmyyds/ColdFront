package io.github.wxmyyds.coldfront.ble

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryPollTest {
    private data class Target(val name: String, val readable: Boolean, val success: Boolean = true)

    private class Poll(
        val targets: List<Target>,
        val temperatures: List<Target> = emptyList(),
    ) {
        var current = true
        var stale = false
        var recoveries = 0
        val reads = mutableListOf<Target>()
        val subscriptions = mutableListOf<Target>()
        var subscribable = true
        var onRead: (Target) -> Unit = {}
        var onSubscribe: (Target) -> Unit = {}

        suspend fun once(): Boolean = pollTelemetry(
            targets = targets,
            temperatureTargets = temperatures,
            isCurrent = { current },
            isReadable = { it.readable },
            // Non-readable targets deliberately return true, like setup's readIfReadable.
            read = { target, _ -> reads += target; onRead(target); target.success },
            temperatureStale = { stale },
            isSubscribable = { subscribable },
            subscribe = { subscriptions += it; onSubscribe(it); true },
            onTemperatureRecoveryAttempt = { recoveries++ },
        ).successfulReads > 0
    }

    @Test
    fun `notify and write only targets do not count as successful polling`() = runTest {
        val targets = listOf(Target("notify-only", false), Target("write-only", false))
        val poll = Poll(targets)
        // Empty passes are distinguishable from genuine failed read attempts.
        repeat(4) { assertFalse(poll.once()) }
        assertTrue(poll.reads.isEmpty())
        assertTrue(poll.subscriptions.isEmpty())
        assertFalse(Poll(emptyList()).once())
    }

    @Test
    fun `skipped targets cannot mask failures and real readable success counts`() = runTest {
        val skipped = Target("notify-only", false)
        val failed = Target("read-failed", true, false)
        val succeeded = Target("read-ok", true)
        val failure = Poll(listOf(skipped, failed))
        assertFalse(failure.once())
        assertEquals(listOf(failed), failure.reads)
        val mixed = Poll(listOf(skipped, failed, succeeded))
        assertTrue(mixed.once())
        assertEquals(listOf(failed, succeeded), mixed.reads)
    }

    @Test
    fun `temperature recovery resubscribes notify only sources without counting skipped reads`() = runTest {
        val temperature = Target("1014", false)
        val status = Target("1015", false)
        val poll = Poll(listOf(temperature, status), listOf(temperature, status))
        poll.stale = true
        assertFalse(poll.once())
        assertTrue(poll.reads.isEmpty())
        assertEquals(listOf(temperature, status), poll.subscriptions)
        assertEquals(1, poll.recoveries)
    }

    @Test
    fun `temperature recovery counts only successful readable samples`() = runTest {
        val notifyOnly = Target("1014", false)
        for (success in listOf(false, true)) {
            val status = Target("1015", true, success)
            val poll = Poll(emptyList(), listOf(notifyOnly, status))
            poll.stale = true
            assertEquals(success, poll.once())
            assertEquals(listOf(status), poll.reads)
            assertEquals(listOf(notifyOnly, status), poll.subscriptions)
            assertEquals(1, poll.recoveries)
        }
    }

    @Test
    fun `fresh temperature does not trigger recovery`() = runTest {
        val poll = Poll(emptyList(), listOf(Target("1014", true)))
        assertFalse(poll.once())
        assertTrue(poll.reads.isEmpty())
        assertTrue(poll.subscriptions.isEmpty())
        assertEquals(0, poll.recoveries)
    }

    @Test
    fun `ordinary reads can make temperature fresh before recovery is evaluated`() = runTest {
        val temperature = Target("1014", true)
        val poll = Poll(listOf(temperature), listOf(temperature))
        poll.stale = true
        poll.onRead = { poll.stale = false }
        assertTrue(poll.once())
        assertEquals(listOf(temperature), poll.reads)
        assertTrue(poll.subscriptions.isEmpty())
        assertEquals(0, poll.recoveries)
    }

    @Test
    fun `empty invalidated session cannot schedule recovery`() = runTest {
        val poll = Poll(emptyList())
        poll.stale = true
        poll.current = false
        assertFalse(poll.once())
        assertEquals(0, poll.recoveries)
    }

    @Test
    fun `invalidated session stops before next read or recovery`() = runTest {
        val first = Target("1011", true)
        val second = Target("1012", true)
        val poll = Poll(listOf(first, second), listOf(Target("1014", true)))
        poll.stale = true
        poll.current = false
        assertFalse(poll.once())
        assertTrue(poll.reads.isEmpty())
        poll.current = true
        poll.onRead = { poll.current = false }
        assertTrue(poll.once())
        assertEquals(listOf(first), poll.reads)
        assertTrue(poll.subscriptions.isEmpty())
        assertEquals(0, poll.recoveries)
    }

    @Test
    fun `session invalidation during resubscription prevents recovery reads`() = runTest {
        val temperature = Target("1014", true)
        val poll = Poll(emptyList(), listOf(temperature, Target("1015", true)))
        poll.stale = true
        poll.onSubscribe = { poll.current = false }
        assertFalse(poll.once())
        assertEquals(listOf(temperature), poll.subscriptions)
        assertTrue(poll.reads.isEmpty())
        assertEquals(1, poll.recoveries) // The subscription was attempted, not a recovered sample.
    }

    @Test
    fun `no executable recovery target does not record an attempt`() = runTest {
        val poll = Poll(emptyList(), listOf(Target("write-only", false)))
        poll.stale = true
        poll.subscribable = false
        assertFalse(poll.once())
        assertEquals(0, poll.recoveries)
        assertTrue(poll.subscriptions.isEmpty())
        val empty = Poll(emptyList())
        empty.stale = true
        assertFalse(empty.once())
        assertEquals(0, empty.recoveries)
    }

    @Test
    fun `a report during resubscription stops remaining recovery work`() = runTest {
        val first = Target("1014", true)
        val poll = Poll(emptyList(), listOf(first, Target("1015", true)))
        poll.stale = true
        poll.onSubscribe = { poll.stale = false }
        assertFalse(poll.once())
        assertEquals(listOf(first), poll.subscriptions)
        assertTrue(poll.reads.isEmpty())
        assertEquals(1, poll.recoveries)
    }

    @Test
    fun `skipped dispatches do not count as failed reads`() = runTest {
        val result = pollTelemetry(
            targets = listOf("blocked-before-dispatch"), temperatureTargets = emptyList(),
            isCurrent = { true }, isReadable = { true }, read = { _, _ -> null },
            temperatureStale = { false }, isSubscribable = { false }, subscribe = { null },
            onTemperatureRecoveryAttempt = { error("Not a recovery") },
        )
        assertEquals(TelemetryPollResult(), result)
    }

    @Test
    fun `recovery dispatch skipped after eligibility check does not record an attempt`() = runTest {
        var attempts = 0
        val result = pollTelemetry(
            targets = emptyList(), temperatureTargets = listOf("became-unavailable"),
            isCurrent = { true }, isReadable = { true }, read = { _, _ -> null },
            temperatureStale = { true }, isSubscribable = { true }, subscribe = { null },
            onTemperatureRecoveryAttempt = { attempts++ },
        )
        assertEquals(TelemetryPollResult(), result)
        assertEquals(0, attempts)
    }

    @Test
    fun `control starts during accepted read without cancelling it or adding another read`() = runTest {
        var idle = true
        val finishRead = CompletableDeferred<Unit>()
        val reads = mutableListOf<String>()
        var result: TelemetryPollResult? = null
        val job = launch {
            result = pollTelemetry(
                targets = listOf("first", "second"), temperatureTargets = listOf("temperature"),
                isCurrent = { idle }, isReadable = { true },
                read = { target, _ -> reads += target; finishRead.await(); true },
                temperatureStale = { true }, isSubscribable = { true },
                subscribe = { error("Control must stop recovery") },
                onTemperatureRecoveryAttempt = { error("Control must stop recovery") },
            )
        }
        runCurrent()
        idle = false
        runCurrent()
        assertTrue(job.isActive)
        assertEquals(listOf("first"), reads)
        finishRead.complete(Unit)
        job.join()
        assertEquals(listOf("first"), reads)
        assertEquals(TelemetryPollResult(1, 1), result)
    }
}
