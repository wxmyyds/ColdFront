package io.github.wxmyyds.coldfront.ui

import androidx.compose.runtime.saveable.listSaver
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerProfile

/** Route is deliberately absent: opening scan with an existing connection is not a new success. */
internal data class ConnectionNavigationKey(val connection: ConnectionState, val sessionId: Long)

internal fun connectionNavigationKey(state: CoolerLiveState): ConnectionNavigationKey =
    ConnectionNavigationKey(state.connection, state.connectionSessionId)

internal val ConnectionNavigationKeySaver = listSaver<ConnectionNavigationKey, Any>(
    save = { listOf(it.connection.name, it.sessionId) },
    restore = { ConnectionNavigationKey(ConnectionState.valueOf(it[0] as String), it[1] as Long) },
)

internal fun shouldLeaveScanOnConnection(
    previous: ConnectionNavigationKey,
    current: ConnectionNavigationKey,
    isScanDestination: Boolean,
): Boolean = previous != current && current.connection == ConnectionState.CONNECTED && isScanDestination

/**
 * App start may dial the configured default device, but never steals a running session: the
 * foreground service or a connection the user started earlier already owns that one.
 */
internal fun startupConnectTarget(profile: CoolerProfile?, hasRunningSession: Boolean): CoolerProfile? =
    if (hasRunningSession) null else profile
