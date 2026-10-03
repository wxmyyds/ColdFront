package io.github.wxmyyds.coldfront.ble

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        var onRead: (Target) -> Unit = {}
        var onSubscribe: (Target) -> Unit = {}

        suspend fun once(): Boolean = pollTelemetry(
            targets = targets,
            temperatureTargets = temperatures,
            isCurrent = { current },
            isReadable = { it.readable },
            // Non-readable targets deliberately return true, like setup's readIfReadable.
            read = { reads += it; onRead(it); it.success },
            temperatureStale = { stale },
            subscribe = { subscriptions += it; onSubscribe(it) },
            onTemperatureRecovery = { recoveries++ },
        )
    }

    @Test
    fun `notify and write only targets do not count as successful polling`() = runTest {
        val targets = listOf(Target("notify-only", false), Target("write-only", false))
        val poll = Poll(targets)
        // Every pass remains failed, allowing the production loop's failure backoff to accrue.
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
        assertEquals(0, poll.recoveries)
    }
}
