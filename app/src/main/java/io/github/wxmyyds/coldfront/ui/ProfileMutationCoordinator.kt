package io.github.wxmyyds.coldfront.ui

import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import io.github.wxmyyds.coldfront.util.CommandMailbox
import kotlinx.coroutines.CoroutineScope

/**
 * One event lane owns connection bookkeeping and deletion suppression. DataStore transactions
 * alone cannot order the in-memory session check with a deletion that is still committing.
 */
internal class ProfileMutationCoordinator(
    scope: CoroutineScope,
    private val record: suspend (CoolerLiveState) -> Unit,
    private val delete: suspend (String) -> CoolerProfile?,
) {
    data class Deletion(val profile: CoolerProfile?, val state: CoolerLiveState)

    private val mailbox = CommandMailbox(scope)
    // Only mailbox actions access this set, including after a suspended storage operation.
    private val ignoredSessions = mutableSetOf<Long>()

    suspend fun recordConnection(state: CoolerLiveState) {
        mailbox.execute {
            if (state.connectionSessionId !in ignoredSessions) record(state)
        }
    }

    suspend fun deleteProfile(id: String, currentState: () -> CoolerLiveState): Deletion = mailbox.execute {
        // Capture only after earlier commands finish: a replacement session may have appeared
        // while this deletion was queued. Keep that snapshot across the suspended transaction.
        val stateAtStart = currentState()
        val removed = delete(id)
        // A same-address reconnect may complete while DataStore is suspended. The mailbox
        // prevents its queued record event from running until this post-commit snapshot is owned.
        val state = currentState()
        // Trust the committed deletion, not a possibly stale UI snapshot. Failed deletions
        // must leave this session eligible for recording; a later session is always distinct.
        if (removed != null && removed.macAddress.equals(state.deviceAddress, ignoreCase = true)) {
            ignoredSessions.add(state.connectionSessionId)
        }
        if (removed != null && removed.macAddress.equals(stateAtStart.deviceAddress, ignoreCase = true)) {
            ignoredSessions.add(stateAtStart.connectionSessionId)
        }
        Deletion(removed, state)
    }

    fun close() = mailbox.close()
}
