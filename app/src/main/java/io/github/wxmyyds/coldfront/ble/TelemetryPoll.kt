package io.github.wxmyyds.coldfront.ble

/** Count relevant read outcomes, excluding unavailable targets, throttled reads, and CCCD writes. */
internal data class TelemetryPollResult(val attemptedReads: Int = 0, val successfulReads: Int = 0)

/**
 * A null result means skipped or superseded work, not a failed sample. Accepted operations still
 * complete; [isCurrent] prevents adding work after a control starts. Record recovery pacing only
 * after real work finishes, never merely because a target is eligible.
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
        // Subscription can recover a notify-only source without treating it as readable.
        if (isSubscribable(target) && subscribe(target) != null) recoveryAttempted = true
        // A valid report ends recovery, including one produced by a queued read.
        if (!isCurrent() || !temperatureStale()) return result()
        if (isReadable(target)) sample(target, recovery = true)
    }
    return result()
}
