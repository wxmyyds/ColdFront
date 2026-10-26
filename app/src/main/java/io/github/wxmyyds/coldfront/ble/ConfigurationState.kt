package io.github.wxmyyds.coldfront.ble

import io.github.wxmyyds.coldfront.domain.CoolerBleConstants
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerTelemetryReducer
import java.util.UUID

internal val configurationUuids = listOf(
    CoolerBleConstants.COOLING_SWITCH_UUID,
    CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID,
    CoolerBleConstants.LIGHT_CONTROL_UUID,
    CoolerBleConstants.AUTO_MODE_CONTROL_UUID,
    CoolerBleConstants.BOOST_CONTROL_UUID,
    CoolerBleConstants.PROTECTION_UUID,
)

/** Invalid switch or fan reports must not confirm a default OFF value. */
internal fun confirmedConfigurationState(
    previous: CoolerLiveState,
    uuid: UUID,
    update: CoolerTelemetryReducer.Update?,
): CoolerLiveState {
    val next = if (uuid == CoolerBleConstants.LIGHT_CONTROL_UUID && update != null) {
        update.state.copy(rgbRevision = previous.rgbRevision + 1)
    } else update?.state ?: previous
    // Light mode recovery may replace an unknown device mode, but it does not invent RGB data
    // or confirm the other controls.
    if (uuid !in configurationUuids || (update == null && uuid != CoolerBleConstants.LIGHT_CONTROL_UUID)) {
        return next
    }
    return next.copy(confirmedConfiguration = next.confirmedConfiguration + uuid)
}

private val powerLimitConfiguration = setOf(
    CoolerBleConstants.COOLING_SWITCH_UUID,
    CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID,
    CoolerBleConstants.AUTO_MODE_CONTROL_UUID,
    CoolerBleConstants.BOOST_CONTROL_UUID,
)

/** Reapply a limit received during discovery once the connection can enforce it. */
internal fun powerLimitNeedsEnforcement(previous: CoolerLiveState, current: CoolerLiveState): Boolean =
    current.isConnected && current.powerLimited && (
        previous.fanLimit != current.fanLimit || !previous.isConnected ||
            (!previous.boostOn && current.boostOn) ||
            (current.confirmedConfiguration - previous.confirmedConfiguration).any { it in powerLimitConfiguration }
        )
