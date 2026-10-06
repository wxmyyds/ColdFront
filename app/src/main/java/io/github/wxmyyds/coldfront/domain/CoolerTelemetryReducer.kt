package io.github.wxmyyds.coldfront.domain

import java.util.UUID

/** Null means no authoritative report: malformed packets must not suppress write fallbacks. */
internal object CoolerTelemetryReducer {
    data class Update(val state: CoolerLiveState, val temperatureReported: Boolean = false)

    fun reduce(state: CoolerLiveState, uuid: UUID, value: ByteArray): Update? = when (uuid) {
        CoolerBleConstants.TEMPERATURE_NOTIFICATION_UUID -> temperature(state, value)
        CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID -> state.deviceType?.let { type ->
            CoolerTelemetryParser.fanPercent(value, type)?.let {
                Update(state.copy(fanPercent = it, fanRaw = value[0].toInt() and 0xFF))
            }
        }
        CoolerBleConstants.LIGHT_CONTROL_UUID -> RGBConfig.fromNotification(value, state.rgb)?.let {
            Update(state.copy(rgb = it))
        }
        CoolerBleConstants.COOLING_SWITCH_UUID -> when (value.firstOrNull()) {
            CoolerBleConstants.COOLING_SWITCH_ON -> Update(state.copy(coolingOn = true).withFanMode())
            CoolerBleConstants.COOLING_SWITCH_OFF -> Update(state.copy(coolingOn = false).withFanMode())
            else -> null
        }
        CoolerBleConstants.STATUS_UUID -> when (CoolerTelemetryParser.unsignedByte(value)) {
            // 1015 is always tagged. Unlike 1014, [04] alone is not a temperature sample.
            0x04 -> if (value.size >= 2) temperature(state, value) else null
            0x08 -> CoolerTelemetryParser.bigEndianShort(value.copyOfRange(1, value.size))?.let {
                Update(state.copy(fanRpm = it))
            }
            0x09 -> value.getOrNull(1)?.let { Update(state.copy(powerW = it.toInt() and 0xFF)) }
            else -> null
        }
        CoolerBleConstants.RPM_UUID -> CoolerTelemetryParser.bigEndianShort(value)?.let {
            Update(state.copy(fanRpm = it))
        }
        CoolerBleConstants.POWER_UUID -> CoolerTelemetryParser.unsignedByte(value)?.let {
            Update(state.copy(powerW = it))
        }
        CoolerBleConstants.PROTECTION_UUID -> CoolerTelemetryParser.unsignedByte(value)?.let {
            Update(state.copy(overcoldOn = it and 0x04 != 0))
        }
        CoolerBleConstants.BOOST_CONTROL_UUID -> booleanSwitch(value)?.let {
            Update(state.copy(boostOn = it))
        }
        CoolerBleConstants.AUTO_MODE_CONTROL_UUID -> booleanSwitch(value)?.let {
            Update(state.copy(smartOn = it).withFanMode())
        }
        else -> null
    }

    private fun booleanSwitch(value: ByteArray): Boolean? = when (CoolerTelemetryParser.unsignedByte(value)) {
        0 -> false
        1 -> true
        else -> null
    }

    private fun temperature(state: CoolerLiveState, value: ByteArray): Update? =
        CoolerTelemetryParser.temperature(value)?.let {
            Update(state.copy(temperatureC = it), temperatureReported = true)
        }
}

internal fun CoolerLiveState.withFanMode(): CoolerLiveState = copy(
    fanMode = if (!coolingOn) FanMode.OFF else if (smartOn) FanMode.AUTO else FanMode.MANUAL,
)
