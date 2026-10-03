package io.github.wxmyyds.coldfront.ui

import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerLiveState

/** Route is deliberately absent: opening scan with an existing connection is not a new success. */
internal data class ConnectionNavigationKey(val connection: ConnectionState, val sessionId: Long)

internal fun connectionNavigationKey(state: CoolerLiveState): ConnectionNavigationKey =
    ConnectionNavigationKey(state.connection, state.connectionSessionId)

internal fun shouldLeaveScanOnConnection(key: ConnectionNavigationKey, isScanDestination: Boolean): Boolean =
    key.connection == ConnectionState.CONNECTED && isScanDestination
