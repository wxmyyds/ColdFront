package io.github.wxmyyds.coldfront.domain

import java.util.UUID

/**
 * RedMagic Cooler 6 Pro protocol extracted from the official application's smali.
 *
 * This type is deliberately separate from the 8 Pro power-limit protocol. The official 6 Pro
 * uses the same GATT register UUID family, but its 0x1015 status envelope and fan range differ.
 * No checksum or extra framing is added: the official write paths pass these byte arrays directly
 * to BluetoothGattCharacteristic.writeCharacteristic().
 */
object Jacket6ProProtocol {
    val MAIN_SERVICE_UUID: UUID = CoolerBleConstants.MAIN_SERVICE_UUID
    val HALL_UUID: UUID = CoolerBleConstants.COOLING_SWITCH_UUID
    val FAN_UUID: UUID = CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID
    val LIGHT_UUID: UUID = CoolerBleConstants.LIGHT_CONTROL_UUID
    val TEMPERATURE_UUID: UUID = CoolerBleConstants.TEMPERATURE_NOTIFICATION_UUID
    val STATUS_UUID: UUID = CoolerBleConstants.STATUS_UUID
    val AUTO_UUID: UUID = CoolerBleConstants.AUTO_MODE_CONTROL_UUID
    val OVER_CLOCKING_UUID: UUID = CoolerBleConstants.BOOST_CONTROL_UUID
    val REQUIRED_CHARACTERISTICS: Set<UUID> = setOf(
        HALL_UUID,
        FAN_UUID,
        LIGHT_UUID,
        STATUS_UUID,
    )

    const val FAN_MIN_RAW: Int = 0x28
    const val FAN_MAX_RAW: Int = 0x50

    const val HALL_AUTO: Byte = 0x01
    const val HALL_ON: Byte = 0x02
    const val HALL_OFF: Byte = 0x03
    const val AUTO_OFF: Byte = 0x00
    const val AUTO_ON: Byte = 0x01
    const val OVER_CLOCKING_OFF: Byte = 0x00
    const val OVER_CLOCKING_ON: Byte = 0x01

    /** The official 6 Pro scanner accepts the 4a41 family service plus MSD type 05/05 or 05/06. */
    fun isAdvertisement(
        mainType: Int,
        subType: Int,
        advertisedServices: Collection<UUID>,
    ): Boolean = mainType == 0x05 && subType in setOf(0x05, 0x06) &&
        CoolerBleConstants.ADVERTISING_SERVICE_UUID in advertisedServices

    fun fanRaw(raw: Int): Int = raw.coerceIn(FAN_MIN_RAW, FAN_MAX_RAW)

    fun fanCommand(raw: Int): ByteArray = byteArrayOf(fanRaw(raw).toByte())

    fun hallCommand(mode: HallMode): ByteArray = byteArrayOf(
        when (mode) {
            HallMode.AUTO -> HALL_AUTO
            HallMode.ON -> HALL_ON
            HallMode.OFF -> HALL_OFF
        },
    )

    fun autoCommand(on: Boolean): ByteArray = byteArrayOf(if (on) AUTO_ON else AUTO_OFF)

    fun overClockingCommand(on: Boolean): ByteArray =
        byteArrayOf(if (on) OVER_CLOCKING_ON else OVER_CLOCKING_OFF)

    /**
     * The official status characteristic uses [kind, payload...], not the 8 Pro 0x04/0x07/0x08
     * tag table. Unknown kinds and incomplete payloads are intentionally ignored.
     */
    fun statusUpdate(state: CoolerLiveState, value: ByteArray): CoolerTelemetryReducer.Update? {
        if (value.size < 2) return null
        val payload = value.copyOfRange(1, value.size)
        return when (value[0].toInt() and 0xFF) {
            0x01 -> payload.firstOrNull()?.let { hallUpdate(state, it) }
            0x02 -> payload.firstOrNull()?.let { fanUpdate(state, it) }
            0x03 -> RGBConfig.fromNotification(payload, state.rgb)?.let {
                CoolerTelemetryReducer.Update(state.copy(rgb = it))
            }
            0x04 -> CoolerTelemetryParser.temperatureWithoutCalibration(payload)?.let {
                CoolerTelemetryReducer.Update(
                    state.copy(temperatureC = it),
                    temperatureReported = true,
                )
            }
            // The official app exposes an OutputHasLoad callback here, but its value mapping is
            // not proven by the static code. Do not turn an unknown byte into a Boolean state.
            0x05 -> null
            else -> null
        }
    }

    fun hallUpdate(state: CoolerLiveState, value: Byte): CoolerTelemetryReducer.Update? =
        when (value.toInt() and 0xFF) {
            HALL_AUTO.toInt() -> CoolerTelemetryReducer.Update(
                state.copy(coolingOn = true, hallMode = HallMode.AUTO).withFanMode(),
            )
            HALL_ON.toInt() -> CoolerTelemetryReducer.Update(
                state.copy(coolingOn = true, hallMode = HallMode.ON).withFanMode(),
            )
            HALL_OFF.toInt() -> CoolerTelemetryReducer.Update(
                state.copy(coolingOn = false, hallMode = HallMode.OFF).withFanMode(),
            )
            else -> null
        }

    internal fun fanUpdate(state: CoolerLiveState, value: ByteArray): CoolerTelemetryReducer.Update? {
        val first = value.firstOrNull() ?: return null
        return fanUpdate(state, first)
    }

    private fun fanUpdate(state: CoolerLiveState, value: Byte): CoolerTelemetryReducer.Update? {
        val raw = value.toInt() and 0xFF
        if (raw !in FAN_MIN_RAW..FAN_MAX_RAW) return null
        return CoolerTelemetryReducer.Update(
            state.copy(
                fanRaw = raw,
                fanPercent = CoolerBleConstants.rawToPercentage(raw, CoolerDeviceType.JACKET_6_PRO),
            ),
        )
    }
}
