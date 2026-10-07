package io.github.wxmyyds.coldfront.ble

/** Counts relevant read outcomes, not unavailable/throttled targets or CCCD writes. */
internal data class TelemetryPollResult(val attemptedReads: Int = 0, val successfulReads: Int = 0)

/**
 * A single due pass. Null results mean skipped/superseded work, not failed samples.
 * Accepted operations finish normally; [isCurrent] stops adding work after control starts.
 * Recovery pacing is recorded after real work completes, never merely for an eligible target.
 */
internal suspend fun <T> pollTelemetry(
    targets: Iterable<T>,
    temperatureTargets: Iterable<T>,
    isCurrent: () -> Boolean,
    isReadable: (T) -> Boolean,
    read: suspend (target: T, recovery: Boolean) -> Boolean?,
    temperatureStale: () -> Boolean,
    isSubscribable: (T) -> Boolean,
    subscribe: suspend (T) -> Boolean?,
    onTemperatureRecoveryAttempt: () -> Unit,
): TelemetryPollResult {
    var attempted = 0
    var succeeded = 0
    var recoveryAttempted = false
    fun result(): TelemetryPollResult {
        if (recoveryAttempted) onTemperatureRecoveryAttempt()
        return TelemetryPollResult(attempted, succeeded)
    }
    suspend fun sample(target: T, recovery: Boolean = false) {
        val success = read(target, recovery) ?: return
        if (recovery) recoveryAttempted = true
        attempted++
        if (success) succeeded++
    }
    if (!isCurrent()) return result()
    for (target in targets) {
        if (!isCurrent()) return result()
        if (isReadable(target)) sample(target)
        if (!isCurrent()) return result()
    }
    for (target in temperatureTargets) {
        if (!isCurrent() || !temperatureStale()) return result()
        // A notify-only source can be recovered without pretending it supports READ.
        if (isSubscribable(target) && subscribe(target) != null) recoveryAttempted = true
        // A valid report during subscription ends this recovery, including queued reads.
        if (!isCurrent() || !temperatureStale()) return result()
        if (isReadable(target)) sample(target, recovery = true)
    }
    return result()
}
