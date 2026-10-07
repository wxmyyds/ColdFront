package io.github.wxmyyds.coldfront.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryScheduleTest {
    private class Sampling(private val now: () -> Long) {
        val controls = MutableStateFlow(0)
        val availability = MutableStateFlow(0L)
        val reports = MutableStateFlow(0L)
        var current = true
        var hasReadTarget = true
        var readAvailable = true
        var readNotBefore = 0L
        var readInterval = 0L // The light lane adds its own 4 s eligibility deadline.
        var readSuccess = true
        var hasRecoveryTarget = false
        var recoveryAvailable = true
        val watchStartedAt = now()
        var lastReport: Long? = null
        var lastAttempt: Long? = null
        val reads = mutableListOf<Long>()
        val recoveries = mutableListOf<Long>()
        val passes = mutableListOf<Long>()
        var onRecovery: () -> Unit = {}

        fun report() {
            lastReport = now()
            reports.value++
        }

        suspend fun run() = runTelemetrySchedule(
            controls = controls, availabilityChanges = availability, reportChanges = reports,
            isCurrent = { current }, nowMs = now,
            nextReadableAt = { readNotBefore.takeIf { hasReadTarget && readAvailable } },
            nextRecoveryAt = {
                if (hasRecoveryTarget && recoveryAvailable) {
                    temperatureRecoveryAt(watchStartedAt, lastReport, lastAttempt)
                } else null
            },
            poll = { readDue, recoveryDue ->
                passes += now()
                if (readDue) {
                    reads += now()
                    readNotBefore = now() + readInterval
                }
                if (recoveryDue) {
                    recoveries += now()
                    onRecovery()
                    lastAttempt = now()
                }
                TelemetryPollResult(if (readDue) 1 else 0, if (readDue && readSuccess) 1 else 0)
            },
        )
    }

    @Test
    fun `busy controls suspend for a minute then completion wakes sampling`() = runTest {
        val sampling = Sampling { testScheduler.currentTime }
        sampling.controls.value = 1
        val job = launch { sampling.run() }
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(sampling.passes.isEmpty())
        sampling.controls.value = 0
        runCurrent()
        assertEquals(listOf(60_000L), sampling.reads)
        advanceTimeBy(499)
        runCurrent()
        assertEquals(1, sampling.reads.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(60_000L, 60_500L), sampling.reads)
        job.cancelAndJoin()
    }

    @Test
    fun `no targets means no timed passes until an eligibility event`() = runTest {
        val sampling = Sampling { testScheduler.currentTime }
        sampling.hasReadTarget = false
        val job = launch { sampling.run() }
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(sampling.passes.isEmpty())
        sampling.hasReadTarget = true
        sampling.availability.value++
        runCurrent()
        assertEquals(listOf(60_000L), sampling.reads)
        job.cancelAndJoin()
    }

    @Test
    fun `blocked lanes park and drain events wake them without guessing from elapsed time`() = runTest {
        val sampling = Sampling { testScheduler.currentTime }
        sampling.readAvailable = false
        val job = launch { sampling.run() }
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(sampling.passes.isEmpty())
        // An unrelated queue event does not make this lane eligible.
        sampling.availability.value++
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(sampling.passes.isEmpty())
        sampling.readAvailable = true
        sampling.availability.value++
        runCurrent()
        assertEquals(listOf(120_000L), sampling.reads)
        job.cancelAndJoin()
    }

    @Test
    fun `availability restored before sample deadline cannot accelerate normal reads`() = runTest {
        val sampling = Sampling { testScheduler.currentTime }
        val job = launch { sampling.run() }
        runCurrent()
        sampling.readAvailable = false
        sampling.availability.value++
        runCurrent()
        advanceTimeBy(100)
        sampling.readAvailable = true
        sampling.availability.value++
        runCurrent()
        assertEquals(listOf(0L), sampling.reads)
        advanceTimeBy(400)
        runCurrent()
        assertEquals(listOf(0L, 500L), sampling.reads)
        job.cancelAndJoin()
    }

    @Test
    fun `an availability change between eligibility check and suspension is not lost`() = runTest {
        val controls = MutableStateFlow(0)
        val availability = MutableStateFlow(0L)
        val reports = MutableStateFlow(0L)
        var available = false
        var samples = 0
        val job = launch {
            runTelemetrySchedule(
                controls = controls, availabilityChanges = availability, reportChanges = reports,
                isCurrent = { true }, nowMs = { testScheduler.currentTime },
                nextReadableAt = {
                    if (available) 0L else {
                        available = true
                        availability.value++
                        null
                    }
                },
                nextRecoveryAt = { null },
                poll = { _, _ -> samples++; TelemetryPollResult(1, 1) },
            )
        }
        runCurrent()
        assertEquals(1, samples)
        job.cancelAndJoin()
    }

    @Test
    fun `normal reads keep 500 ms and only three real failures enter 4 s backoff`() = runTest {
        val sampling = Sampling { testScheduler.currentTime }
        sampling.readSuccess = false
        val job = launch { sampling.run() }
        runCurrent()
        advanceTimeBy(4999)
        runCurrent()
        assertEquals(listOf(0L, 500L, 1000L), sampling.reads)
        // False results remain retryable; they do not imply permanent unavailability.
        sampling.readSuccess = true
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(0L, 500L, 1000L, 5000L), sampling.reads)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(5500L, sampling.reads.last())
        job.cancelAndJoin()
    }

    @Test
    fun `notification and availability events never amplify normal read traffic`() = runTest {
        val sampling = Sampling { testScheduler.currentTime }
        val job = launch { sampling.run() }
        runCurrent()
        repeat(20) {
            advanceTimeBy(100)
            sampling.report()
            sampling.availability.value++
            runCurrent()
        }
        assertEquals(listOf(0L, 500L, 1000L, 1500L, 2000L), sampling.reads)
        job.cancelAndJoin()
    }

    @Test
    fun `light-only work waits directly for its 4 s deadline and stops on confirmation`() = runTest {
        val sampling = Sampling { testScheduler.currentTime }
        sampling.readNotBefore = 4000L
        sampling.readInterval = 4000L
        val job = launch { sampling.run() }
        runCurrent()
        advanceTimeBy(3999)
        runCurrent()
        assertTrue(sampling.passes.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(4000L), sampling.reads)
        advanceTimeBy(3999)
        runCurrent()
        assertEquals(1, sampling.passes.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(4000L, 8000L), sampling.reads)
        sampling.hasReadTarget = false
        sampling.reports.value++
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, sampling.passes.size)
        job.cancelAndJoin()
    }

    @Test
    fun `notify-only recovery has a 6 s report deadline and independent 2 s attempt pacing`() = runTest {
        val sampling = Sampling { testScheduler.currentTime }
        sampling.hasReadTarget = false
        sampling.hasRecoveryTarget = true
        val job = launch { sampling.run() }
        runCurrent()
        advanceTimeBy(5999)
        runCurrent()
        assertTrue(sampling.passes.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(6000L), sampling.recoveries)
        assertNull(sampling.lastReport)
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(listOf(6000L, 8000L), sampling.recoveries)
        assertNull(sampling.lastReport)
        sampling.report()
        runCurrent()
        advanceTimeBy(5999)
        runCurrent()
        assertEquals(2, sampling.recoveries.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(6000L, 8000L, 14_000L), sampling.recoveries)
        assertEquals(8000L, sampling.lastReport)
        assertTrue(sampling.reads.isEmpty())
        job.cancelAndJoin()
    }

    @Test
    fun `identical valid notifications reset the deadline without recovery passes`() = runTest {
        val sampling = Sampling { testScheduler.currentTime }
        sampling.hasReadTarget = false
        sampling.hasRecoveryTarget = true
        val job = launch { sampling.run() }
        runCurrent()
        repeat(6) {
            advanceTimeBy(5000)
            sampling.report() // The payload may be unchanged; receipt still matters.
            runCurrent()
        }
        assertTrue(sampling.passes.isEmpty())
        assertEquals(30_000L, sampling.lastReport)
        job.cancelAndJoin()
    }

    @Test
    fun `a real report during recovery is never overwritten by attempt bookkeeping`() = runTest {
        val sampling = Sampling { testScheduler.currentTime }
        sampling.hasReadTarget = false
        sampling.hasRecoveryTarget = true
        sampling.onRecovery = { sampling.report() }
        val job = launch { sampling.run() }
        runCurrent()
        advanceTimeBy(6000)
        runCurrent()
        assertEquals(6000L, sampling.lastReport)
        assertEquals(6000L, sampling.lastAttempt)
        advanceTimeBy(5999)
        runCurrent()
        assertEquals(listOf(6000L), sampling.recoveries)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(6000L, 12_000L), sampling.recoveries)
        job.cancelAndJoin()
    }

    @Test
    fun `blocked notify-only recovery does not keep its expired timer running`() = runTest {
        val sampling = Sampling { testScheduler.currentTime }
        sampling.hasReadTarget = false
        sampling.hasRecoveryTarget = true
        sampling.recoveryAvailable = false
        val job = launch { sampling.run() }
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(sampling.passes.isEmpty())
        assertNull(sampling.lastAttempt)
        sampling.recoveryAvailable = true
        sampling.availability.value++
        runCurrent()
        assertEquals(listOf(60_000L), sampling.recoveries)
        job.cancelAndJoin()
    }

    @Test
    fun `cancellation exits both indefinite and timed waits without old-session work`() = runTest {
        for (blocked in listOf(false, true)) {
            val sampling = Sampling { testScheduler.currentTime }
            sampling.readAvailable = !blocked
            val job = launch { sampling.run() }
            runCurrent()
            val count = sampling.passes.size
            job.cancelAndJoin()
            sampling.availability.value++
            sampling.reports.value++
            advanceTimeBy(60_000)
            runCurrent()
            assertFalse(job.isActive)
            assertEquals(count, sampling.passes.size)
        }
    }

    @Test
    fun `RSSI retains 2 s sampling but blocked lane waits for availability`() = runTest {
        val availability = MutableStateFlow(0L)
        var available = true
        val reads = mutableListOf<Long>()
        val job = launch {
            runRssiSchedule(
                availabilityChanges = availability, isCurrent = { true },
                isAvailable = { available }, nowMs = { testScheduler.currentTime },
                sample = { reads += testScheduler.currentTime },
            )
        }
        runCurrent()
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(listOf(0L, 2000L), reads)
        available = false
        availability.value++
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, reads.size)
        available = true
        availability.value++
        runCurrent()
        assertEquals(listOf(0L, 2000L, 62_000L), reads)
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(64_000L, reads.last())
        job.cancelAndJoin()
    }
}
