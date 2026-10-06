package io.github.wxmyyds.coldfront.domain

import java.util.UUID

/** Discovered GATT capabilities, not an optimistic assumption based on the model name. */
data class CoolerCapabilities(
    val hasCoolingSwitch: Boolean = false,
    val coolingControl: Boolean = false,
    val fanControl: Boolean = false,
    val smartControl: Boolean = false,
    val boostControl: Boolean = false,
    val protectionControl: Boolean = false,
) {
    companion object {
        fun fromCharacteristics(available: Set<UUID>, writable: Set<UUID>) = CoolerCapabilities(
            hasCoolingSwitch = CoolerBleConstants.COOLING_SWITCH_UUID in available,
            coolingControl = CoolerBleConstants.COOLING_SWITCH_UUID in writable,
            fanControl = CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID in writable,
            smartControl = CoolerBleConstants.AUTO_MODE_CONTROL_UUID in writable,
            boostControl = CoolerBleConstants.BOOST_CONTROL_UUID in writable,
            protectionControl = CoolerBleConstants.PROTECTION_UUID in writable,
        )
    }
}
