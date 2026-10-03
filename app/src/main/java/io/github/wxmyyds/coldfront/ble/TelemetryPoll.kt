package io.github.wxmyyds.coldfront.ble

/**
 * Shared by the periodic BLE pass and JVM regression tests. Only successful readable
 * targets count as progress; setup/control reads keep their separate skip-as-success policy.
 */
internal suspend fun <T> pollTelemetry(
    targets: Iterable<T>,
    temperatureTargets: Iterable<T>,
    isCurrent: () -> Boolean,
    isReadable: (T) -> Boolean,
    read: suspend (T) -> Boolean,
    temperatureStale: () -> Boolean,
    subscribe: suspend (T) -> Unit,
    onTemperatureRecovery: () -> Unit,
): Boolean {
    if (!isCurrent()) return false
    var succeeded = false
    for (target in targets) {
        if (!isCurrent()) return succeeded
        if (isReadable(target) && read(target)) succeeded = true
        if (!isCurrent()) return succeeded
    }
    if (temperatureStale()) {
        for (target in temperatureTargets) {
            if (!isCurrent()) return succeeded
            // Notify-only temperature sources still need resubscription, but not a read.
            subscribe(target)
            if (!isCurrent()) return succeeded
            if (isReadable(target) && read(target)) succeeded = true
            if (!isCurrent()) return succeeded
        }
        onTemperatureRecovery()
    }
    return succeeded
}
