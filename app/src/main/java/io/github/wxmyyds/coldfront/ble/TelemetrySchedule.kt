package io.github.wxmyyds.coldfront.ble

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

private data class TelemetryWake(val controls: Int, val availability: Long, val reports: Long)

/**
 * Keep the existing sampling cadence, but never manufacture a pass just to check busy,
 * blocked or not-yet-due targets. Events only recalculate deadlines, not bypass them.
 * All predicates and event mutations share the caller's confinement (Main in production).
 */
internal suspend fun runTelemetrySchedule(
    controls: StateFlow<Int>,
    availabilityChanges: StateFlow<Long>,
    reportChanges: StateFlow<Long>,
    isCurrent: () -> Boolean,
    nowMs: () -> Long,
    nextReadableAt: () -> Long?,
    nextRecoveryAt: () -> Long?,
    poll: suspend (readDue: Boolean, recoveryDue: Boolean) -> TelemetryPollResult,
) {
    var nextSampleAt = nowMs()
    var failedPasses = 0
    while (currentCoroutineContext().isActive && isCurrent()) {
        // Snapshot before inspecting eligibility so a change before suspension is not lost.
        val observed = TelemetryWake(controls.value, availabilityChanges.value, reportChanges.value)
        val readAt = if (observed.controls == 0) nextReadableAt()?.coerceAtLeast(nextSampleAt) else null
        val recoveryAt = if (observed.controls == 0) nextRecoveryAt() else null
        val deadline = listOfNotNull(readAt, recoveryAt).minOrNull()
        val now = nowMs()
        if (deadline == null || deadline > now) {
            val changed: suspend () -> Unit = {
                combine(controls, availabilityChanges, reportChanges, ::TelemetryWake)
                    .first { it != observed }
            }
            if (deadline == null) changed() else withTimeoutOrNull(deadline - now) { changed() }
            continue
        }
        val result = poll(readAt != null && readAt <= now, recoveryAt != null && recoveryAt <= now)
        if (result.attemptedReads > 0) {
            failedPasses = if (result.successfulReads > 0) 0 else (failedPasses + 1).coerceAtMost(3)
            nextSampleAt = nowMs() + if (failedPasses >= 3) 4000L else 500L
        }
        // An empty pass is not a failure. Its changed eligibility/control event will make
        // the next iteration suspend; recovery attempts advance their own deadline.
    }
}

/** Report freshness and recovery pacing are independent: attempts never invent samples. */
internal fun temperatureRecoveryAt(
    watchStartedAtMs: Long,
    lastReportAtMs: Long?,
    lastAttemptAtMs: Long?,
): Long = maxOf(
    (lastReportAtMs ?: watchStartedAtMs) + 6000L,
    lastAttemptAtMs?.plus(2000L) ?: Long.MIN_VALUE,
)

/** RSSI has no unsolicited update event. Keep its 2 s sampling, but park blocked lanes. */
internal suspend fun runRssiSchedule(
    availabilityChanges: StateFlow<Long>,
    isCurrent: () -> Boolean,
    isAvailable: () -> Boolean,
    nowMs: () -> Long,
    sample: suspend () -> Unit,
) {
    var nextSampleAt = nowMs()
    while (currentCoroutineContext().isActive && isCurrent()) {
        val observed = availabilityChanges.value
        val now = nowMs()
        if (!isAvailable()) {
            availabilityChanges.first { it != observed }
        } else if (nextSampleAt > now) {
            withTimeoutOrNull(nextSampleAt - now) {
                availabilityChanges.first { it != observed }
            }
        } else {
            sample()
            nextSampleAt = nowMs() + 2000L
        }
    }
}
