package io.github.wxmyyds.coldfront.ble

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Main-confined GATT queue. Owner and target are compared by identity, not UUID/equality.
 * An accepted operation owns the lane until its callback or session teardown. A timeout
 * poisons the session by default: retrying on that GATT could consume the previous
 * operation's callback. Optional telemetry callers pass poisonOnTimeout = false;
 * their owner/target/kind combination retains a timeout tombstone. By default it is
 * permanently quarantined until session teardown. With quarantineOnTimeout = false,
 * only consuming the old callback (without committing it) makes a later retry safe.
 * Optional timeouts report recovery needs through onQuarantined, including drainable
 * lanes. A still-blocked drainable retry reports again, since setup may not be ready
 * to expose degradation yet; promotion to permanent quarantine also reports it.
 */
internal class GattOperationQueue(
    private val onTimeout: (owner: Any) -> Unit,
    // A single bounded retry backs off only after explicit platform rejection/failure.
    // It is not evidence of completion; accepted requests still require their callback.
    private val retryDelayMs: Long = 120,
    private val onQuarantined: (owner: Any) -> Unit = {},
) {
    enum class Kind { READ, WRITE, DESCRIPTOR, RSSI }

    data class Result(
        val success: Boolean,
        val value: ByteArray = byteArrayOf(),
        val rssi: Int? = null,
    )

    private class Pending(val owner: Any, val target: Any, val kind: Kind) {
        val completion = CompletableDeferred<Result>()
    }

    private enum class TimeoutDisposition { DRAINABLE, PERMANENT }

    // Android permits only one callback-bearing GATT operation in flight. This transport
    // lane is the remaining coroutine mutex: cancellation cannot release accepted work.
    // Business-level transactions use single-consumer mailboxes instead.
    private val mutex = Mutex()
    private var pending: Pending? = null
    private val timedOut =
        java.util.IdentityHashMap<Any, java.util.IdentityHashMap<Any, MutableMap<Kind, TimeoutDisposition>>>()
    private val _availabilityChanges = MutableStateFlow(0L)
    val availabilityChanges = _availabilityChanges.asStateFlow()

    /** Eligibility only: the transport lane still serializes actual platform dispatch. */
    fun isAvailable(owner: Any, target: Any, kind: Kind): Boolean =
        timeoutDisposition(owner, target, kind) == null

    fun hasTimedOut(owner: Any): Boolean = timedOut[owner]?.isNotEmpty() == true

    private fun timeoutDisposition(owner: Any, target: Any, kind: Kind): TimeoutDisposition? =
        timedOut[owner]?.get(target)?.get(kind)

    private fun markTimedOut(owner: Any, target: Any, kind: Kind, disposition: TimeoutDisposition) {
        val targets = timedOut.getOrPut(owner) { java.util.IdentityHashMap() }
        targets.getOrPut(target) { mutableMapOf() }[kind] = disposition
        _availabilityChanges.value++
    }

    private fun removeTimedOut(owner: Any, target: Any, kind: Kind) {
        val targets = timedOut[owner] ?: return
        val kinds = targets[target] ?: return
        kinds.remove(kind)
        if (kinds.isEmpty()) targets.remove(target)
        if (targets.isEmpty()) timedOut.remove(owner)
        _availabilityChanges.value++
    }

    /**
     * Returns false for unsolicited, stale-owner, wrong-kind/target, or timed-out callbacks.
     * A drainable timeout consumes its old callback but still returns false: its value is stale.
     */
    fun complete(owner: Any, target: Any, kind: Kind, result: Result): Boolean {
        val disposition = timeoutDisposition(owner, target, kind)
        if (disposition != null) {
            if (disposition == TimeoutDisposition.DRAINABLE) removeTimedOut(owner, target, kind)
            return false
        }
        val operation = pending ?: return false
        if (operation.owner !== owner || operation.target !== target || operation.kind != kind) return false
        clear(operation)
        operation.completion.complete(result)
        return true
    }

    /** Call only after invalidating the owner, before closing its transport. */
    fun abort(owner: Any) {
        if (timedOut.remove(owner) != null) _availabilityChanges.value++
        val operation = pending ?: return
        if (operation.owner !== owner) return
        clear(operation)
        operation.completion.complete(Result(false))
    }

    private fun clear(operation: Pending) {
        if (pending === operation) pending = null
    }

    suspend fun execute(
        owner: Any,
        target: Any,
        kind: Kind,
        timeoutMs: Long,
        isCurrent: () -> Boolean,
        poisonOnTimeout: Boolean = true,
        quarantineOnTimeout: Boolean = true,
        start: () -> Boolean,
    ): Result = mutex.withLock {
        if (!isCurrent()) return@withLock Result(false)
        val disposition = timeoutDisposition(owner, target, kind)
        if (disposition != null) {
            if (disposition == TimeoutDisposition.DRAINABLE) {
                // A stricter caller cannot silently inherit an undrained setup timeout.
                // No new operation may start, regardless of how long ago it timed out.
                if (poisonOnTimeout) onTimeout(owner)
                else {
                    if (quarantineOnTimeout) {
                        markTimedOut(owner, target, kind, TimeoutDisposition.PERMANENT)
                    }
                    // Repeat for drainable retries: the initial report may precede readiness.
                    onQuarantined(owner)
                }
            }
            return@withLock Result(false)
        }
        // Cancellation must not free a lane that Android has already accepted. The owner
        // can still abort it immediately on disconnect; timeout remains bounded.
        withContext(NonCancellable) {
            var result = Result(false)
            for (attempt in 0..1) {
                if (!isCurrent()) return@withContext Result(false)
                val operation = Pending(owner, target, kind)
                check(pending == null)
                pending = operation
                val accepted = try {
                    start()
                } catch (_: Exception) {
                    false
                }
                if (accepted) {
                    val callback = withTimeoutOrNull(timeoutMs) { operation.completion.await() }
                    clear(operation)
                    if (callback == null) {
                        // No retry, even if isCurrent has become false due to a newer intent.
                        // Android callbacks have no generation. Keep this exact lane blocked
                        // until teardown, or (only for a drainable timeout) its old callback.
                        // Other owners/targets/kinds remain usable.
                        if (poisonOnTimeout) onTimeout(owner)
                        else {
                            markTimedOut(
                                owner, target, kind,
                                if (quarantineOnTimeout) TimeoutDisposition.PERMANENT else TimeoutDisposition.DRAINABLE,
                            )
                            onQuarantined(owner)
                        }
                        return@withContext Result(false)
                    }
                    result = callback
                } else {
                    clear(operation)
                    result = Result(false)
                }
                if (!isCurrent()) return@withContext Result(false)
                if (result.success) break
                if (attempt == 0) delay(retryDelayMs)
            }
            // The callback, not an arbitrary post-callback sleep, releases this operation.
            if (isCurrent()) result else Result(false)
        }
    }
}
