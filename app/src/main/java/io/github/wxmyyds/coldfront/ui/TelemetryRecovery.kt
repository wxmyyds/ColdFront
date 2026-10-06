package io.github.wxmyyds.coldfront.ui

import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.CoolerLiveState

internal data class TelemetryReconnectTarget(val address: String, val type: CoolerDeviceType)

/** A delayed click on the old warning must never disconnect a newer device/session. */
internal fun telemetryReconnectTarget(
    requested: CoolerLiveState,
    current: CoolerLiveState,
): TelemetryReconnectTarget? {
    if (!requested.isConnected || !requested.telemetryDegraded ||
        !current.isConnected || !current.telemetryDegraded ||
        requested.connectionSessionId != current.connectionSessionId ||
        requested.deviceAddress != current.deviceAddress || requested.deviceType != current.deviceType
    ) return null
    return TelemetryReconnectTarget(
        current.deviceAddress ?: return null,
        current.deviceType ?: return null,
    )
}
