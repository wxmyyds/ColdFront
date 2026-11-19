package io.github.wxmyyds.coldfront.ui

import androidx.compose.runtime.saveable.listSaver
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerProfile

/** 扫描页已有连接时不视为新的连接成功，因此不会自动离开。 */
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

// 启动时可以连接默认设备，但不会抢占已有的服务或用户连接。
internal fun startupConnectTarget(profile: CoolerProfile?, hasRunningSession: Boolean): CoolerProfile? =
    if (hasRunningSession) null else profile
