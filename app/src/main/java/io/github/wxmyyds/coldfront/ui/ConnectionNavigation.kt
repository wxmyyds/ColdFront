package io.github.wxmyyds.coldfront.ui

import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerProfile

/** Route is deliberately absent: opening scan with an existing connection is not a new success. */
internal data class ConnectionNavigationKey(val connection: ConnectionState, val sessionId: Long)

internal fun connectionNavigationKey(state: CoolerLiveState): ConnectionNavigationKey =
    ConnectionNavigationKey(state.connection, state.connectionSessionId)

internal fun shouldLeaveScanOnConnection(key: ConnectionNavigationKey, isScanDestination: Boolean): Boolean =
    key.connection == ConnectionState.CONNECTED && isScanDestination

/**
 * App start may dial the configured default device, but never steals a running session: the
 * foreground service or a connection the user started earlier already owns that one.
 */
internal fun startupConnectTarget(profile: CoolerProfile?, hasRunningSession: Boolean): CoolerProfile? =
    if (hasRunningSession) null else profile
