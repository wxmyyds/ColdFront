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
 * Main-confined GATT queue. Owners and targets are matched by identity, not UUID or equality.
 * An accepted operation owns its lane until its callback or session teardown. By default, a
 * timeout poisons the session because retrying could consume the old callback. Telemetry can opt
 * out of poisoning; each timed-out owner/target/kind tuple then keeps a tombstone. The tombstone
 * remains permanent until teardown by default. With `quarantineOnTimeout = false`, only consuming
 * the old callback clears it, without applying that callback's value. [onQuarantined] reports
 * recovery needs for drainable lanes, repeats while they remain blocked, and reports promotion to
 * permanent quarantine.
 */
internal class GattOperationQueue(
    private val onTimeout: (owner: Any) -> Unit,
    // Retry once after an explicit platform rejection or failed callback. Acceptance still requires its callback.
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

    // Android allows one callback-bearing GATT operation at a time. This mutex is the transport
    // lane; cancellation cannot release work already accepted by the platform. Command mailboxes
    // serialize higher-level transactions.
    private val mutex = Mutex()
    private var pending: Pending? = null
    private val timedOut =
        java.util.IdentityHashMap<Any, java.util.IdentityHashMap<Any, MutableMap<Kind, TimeoutDisposition>>>()
    private val _availabilityChanges = MutableStateFlow(0L)
    val availabilityChanges = _availabilityChanges.asStateFlow()

    /** Check eligibility; the mutex still serializes platform dispatch. */
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
                // A stricter caller cannot reuse a lane while its old callback is pending.
                if (poisonOnTimeout) onTimeout(owner)
                else {
                    if (quarantineOnTimeout) {
                        markTimedOut(owner, target, kind, TimeoutDisposition.PERMANENT)
                    }
                    // A drainable retry can report again if setup has not exposed the degradation.
                    onQuarantined(owner)
                }
            }
            return@withLock Result(false)
        }
        // Cancellation cannot release an operation Android has accepted. Disconnect can abort
        // the owner, and the timeout remains bounded.
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
                        // Do not retry after timeout. Android callbacks carry no generation, so the
                        // lane stays blocked until teardown or the old callback drains.
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
            // The callback releases the operation; no post-callback delay is needed.
            if (isCurrent()) result else Result(false)
        }
    }
}
