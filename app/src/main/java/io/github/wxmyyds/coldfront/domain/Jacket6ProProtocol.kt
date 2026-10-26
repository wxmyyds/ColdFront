package io.github.wxmyyds.coldfront.domain

import java.util.UUID

/**
 * RedMagic Cooler 6 Pro / 6 Pro+ ("Jacket6Pro" 家族)协议，依据官方应用的 smali 提取。
 * 两个型号共用 6-family V2 协议栈(Jacket6ProProcessor)：GATT 寄存器 UUID 系列、0x1015 状态包格式
 * 和 0x28..0x50 的风扇 raw 范围都相同；Pro+ 只有风扇 UI 使用 9 档(见 CoolerBleConstants 中的 6 Pro+ 档位函数)。
 *
 * 此类型与 8 Pro 的功耗限档协议分开处理。官方写入路径会直接将这些字节数组传给
 * BluetoothGattCharacteristic.writeCharacteristic()，因此这里不添加校验和或额外封装。
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
    /** 这些寄存器不在官方 6 Pro 处理器的特征映射中。 */
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

    /** 官方 6 Pro 扫描器要求 4a41 系列服务，以及 MSD 类型 05/05 或 05/06。 */
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
     * 官方状态特征使用 [kind, payload...] 格式，不采用 8 Pro 的 0x04/0x07/0x08 标签表。
     * 未知 kind 和不完整 payload 会被忽略。
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
                // 官方 Pro+ 的 onOutputHasLoad 会识别 0x7f，但对应协程为空，因此不改变负载状态。
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
