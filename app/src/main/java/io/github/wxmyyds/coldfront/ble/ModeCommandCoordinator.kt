package io.github.wxmyyds.coldfront.ble

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Session-local, caller-confined mode intents (Main in the BLE manager). Register requests
 * before launching their work: SMART and BOOST share a latest-intent identity, unlike other controls.
 * The mutex serializes complete OFF/ON sequences, not registration of a newer intent.
 */
internal class ModeCommandCoordinator {
    enum class Mode {
        SMART, BOOST;

        val other: Mode get() = if (this == SMART) BOOST else SMART
    }

    class Request internal constructor(
        val mode: Mode,
        val on: Boolean,
    )

    private val mutex = Mutex()
    private var latest: Request? = null
    // A queued/accepted ON may reach the device before any confirming read-back. Never
    // clear this on supersession, failed writes, or an OFF request that has not succeeded.
    private val possiblyOn = mutableSetOf<Mode>()

    fun request(mode: Mode, on: Boolean): Request =
        Request(mode, on).also {
            latest = it
            if (on) possiblyOn += mode
        }

    fun isFresh(request: Request): Boolean = latest === request

    /** Safety enforcement is not a new user intent and must not supersede a Smart transition. */
    suspend fun enforceOff(
        mode: Mode,
        isCurrent: () -> Boolean,
        reportedOn: () -> Boolean,
        writeOff: suspend (fresh: () -> Boolean) -> Boolean?,
    ): Boolean = mutex.withLock {
        if (!isCurrent()) return@withLock false
        if (mode !in possiblyOn && !reportedOn()) return@withLock true
        val ok = writeOff(isCurrent) == true
        if (!ok || !isCurrent()) return@withLock false
        possiblyOn -= mode
        true
    }

    /**
     * [isCurrent] includes session ownership and the main command's control generation.
     * [write] must pass its supplied freshness check into both queued writes and reads;
     * null means the characteristic/command is unavailable. An attached OFF uses this
     * request's ticket, never registers a new request, and must succeed before target ON.
     */
    suspend fun execute(
        request: Request,
        isCurrent: () -> Boolean,
        reportedOn: (Mode) -> Boolean,
        write: suspend (mode: Mode, on: Boolean, fresh: () -> Boolean) -> Boolean?,
    ): Boolean = mutex.withLock {
        val compositeFresh = { isCurrent() && isFresh(request) }
        if (!compositeFresh()) return@withLock false
        val other = request.mode.other
        if (request.on && (other in possiblyOn || reportedOn(other))) {
            if (write(other, false, compositeFresh) != true || !compositeFresh()) {
                return@withLock false
            }
            possiblyOn -= other
        }
        if (!compositeFresh()) return@withLock false
        val ok = write(request.mode, request.on, compositeFresh) == true
        if (!ok || !compositeFresh()) return@withLock false
        if (!request.on) possiblyOn -= request.mode
        true
    }
}

/**
 * Shared command completion path. Read-back is best-effort confirmation, not write
 * success, but a command superseded/disconnected while awaiting it must return false.
 * Transport callbacks must recheck [compositeFresh] after waiting for their queue lane.
 */
internal suspend fun executeFreshCommand(
    compositeFresh: () -> Boolean,
    write: suspend (fresh: () -> Boolean) -> Boolean,
    readBack: suspend (fresh: () -> Boolean) -> Unit,
): Boolean {
    if (!compositeFresh()) return false
    val ok = write(compositeFresh)
    if (!compositeFresh()) return false
    readBack(compositeFresh)
    return ok && compositeFresh()
}
