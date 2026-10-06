package io.github.wxmyyds.coldfront.ble

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Main-confined GATT queue. Owner and target are compared by identity, not UUID/equality.
 * An accepted operation owns the lane until its callback or session teardown. A timeout
 * poisons the session by default: retrying on that GATT could consume the previous
 * operation's callback. Optional telemetry callers pass [poisonOnTimeout] = false;
 * their owner/target/kind combination is quarantined until session teardown instead.
 */
internal class GattOperationQueue(
    private val onTimeout: (owner: Any) -> Unit,
    private val spacingMs: Long = 60,
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

    private val mutex = Mutex()
    private var pending: Pending? = null
    private val timedOut = java.util.IdentityHashMap<Any, java.util.IdentityHashMap<Any, MutableSet<Kind>>>()

    private fun hasTimedOut(owner: Any, target: Any, kind: Kind): Boolean =
        timedOut[owner]?.get(target)?.contains(kind) == true

    private fun markTimedOut(owner: Any, target: Any, kind: Kind) {
        val targets = timedOut.getOrPut(owner) { java.util.IdentityHashMap() }
        targets.getOrPut(target) { mutableSetOf() }.add(kind)
    }

    /** Returns false for unsolicited, stale-owner, wrong-kind/target, or quarantined callbacks. */
    fun complete(owner: Any, target: Any, kind: Kind, result: Result): Boolean {
        if (hasTimedOut(owner, target, kind)) return false
        val operation = pending ?: return false
        if (operation.owner !== owner || operation.target !== target || operation.kind != kind) return false
        clear(operation)
        operation.completion.complete(result)
        return true
    }

    /** Call only after invalidating the owner, before closing its transport. */
    fun abort(owner: Any) {
        timedOut.remove(owner)
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
        start: () -> Boolean,
    ): Result = mutex.withLock {
        if (!isCurrent() || hasTimedOut(owner, target, kind)) {
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
                        // Android callbacks have no generation: quarantine this exact lane
                        // even after a late callback arrives. Other targets/kinds remain usable.
                        if (poisonOnTimeout) onTimeout(owner) else {
                            markTimedOut(owner, target, kind)
                            onQuarantined(owner)
                        }
                        return@withContext Result(false)
                    }
                    result = callback
                } else {
                    clear(operation)
                    result = Result(false)
                }
                if (!isCurrent()) {
                    // Even superseded commands retain the firmware's inter-operation gap.
                    delay(spacingMs)
                    return@withContext Result(false)
                }
                if (result.success) break
                if (attempt == 0) delay(retryDelayMs)
            }
            delay(spacingMs)
            if (isCurrent()) result else Result(false)
        }
    }
}
