package io.github.wxmyyds.coldfront.ui

import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import io.github.wxmyyds.coldfront.util.CommandMailbox
import kotlinx.coroutines.CoroutineScope

/**
 * A single mailbox serializes connection bookkeeping and deletion suppression. A DataStore
 * transaction cannot order the in-memory session check against a deletion that is still in progress.
 */
internal class ProfileMutationCoordinator(
    scope: CoroutineScope,
    private val record: suspend (CoolerLiveState) -> Unit,
    private val delete: suspend (String) -> CoolerProfile?,
) {
    data class Deletion(val profile: CoolerProfile?, val state: CoolerLiveState)

    private val mailbox = CommandMailbox(scope)
    // Mailbox actions are the only code that accesses this set, even when storage suspends.
    private val ignoredSessions = mutableSetOf<Long>()

    suspend fun recordConnection(state: CoolerLiveState) {
        mailbox.execute {
            if (state.connectionSessionId !in ignoredSessions) record(state)
        }
    }

    suspend fun deleteProfile(id: String, currentState: () -> CoolerLiveState): Deletion = mailbox.execute {
        // Take this snapshot after earlier mailbox commands finish. A replacement session may have
        // appeared while deletion waited in the queue; keep the snapshot across the suspended transaction.
        val stateAtStart = currentState()
        val removed = delete(id)
        // A reconnect to the same address may finish while DataStore is suspended. The mailbox holds
        // its queued record event until this post-commit snapshot has been captured.
        val state = currentState()
        // Use the committed deletion, not a potentially stale UI snapshot. If deletion fails, this
        // session remains eligible for recording; a later session has a distinct ID.
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
