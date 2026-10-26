package io.github.wxmyyds.coldfront.domain

import java.util.UUID

/**
 * RedMagic Cooler 6 Pro / 6 Pro+ ("Jacket6Pro" 家族) protocol extracted from the official
 * application's smali. The 6-family V2 stack (Jacket6ProProcessor) is shared by both models:
 * same GATT register UUID family, same 0x1015 status envelope and fan raw bounds 0x28..0x50;
 * the Pro+ only differs in its 9-gear fan UI (see CoolerBleConstants' 6 Pro+ gear helpers).
 *
 * This type is deliberately separate from the 8 Pro power-limit protocol.
 * No checksum or extra framing is added: the official write paths pass these byte arrays
 * directly to BluetoothGattCharacteristic.writeCharacteristic().
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
    /** These registers are not part of the official 6 Pro processor's characteristic map. */
    val UNSUPPORTED_CHARACTERISTICS: Set<UUID> = setOf(
        CoolerBleConstants.RPM_UUID,
        CoolerBleConstants.POWER_UUID,
        CoolerBleConstants.PROTECTION_UUID,
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
    internal fun statusUpdate(state: CoolerLiveState, value: ByteArray): CoolerTelemetryReducer.Update? {
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
            0x05 -> when (payload.firstOrNull()?.toInt()?.and(0xFF)) {
                0x80, 0x81 -> CoolerTelemetryReducer.Update(state.copy(outputHasLoad = true))
                0x00, 0x01 -> CoolerTelemetryReducer.Update(state.copy(outputHasLoad = false))
                // 官方 Pro+ onOutputHasLoad 识别 0x7f 但对应协程是空实现:不改变负载状态。
                0x7f -> null
                else -> null
            }
            else -> null
        }
    }

    internal fun hallUpdate(state: CoolerLiveState, value: Byte): CoolerTelemetryReducer.Update? =
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
                fanPercent = CoolerBleConstants.rawToPercentage(
                    raw,
                    state.deviceType?.takeIf { it.usesJacket6ProFamilyProtocol }
                        ?: CoolerDeviceType.JACKET_6_PRO,
                ),
            ),
        )
    }
}
