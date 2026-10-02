package io.github.wxmyyds.coldfront.domain

import java.util.UUID

/**
 * 持久化的散热器档案。
 */
data class CoolerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val deviceType: CoolerDeviceType,
    val macAddress: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastConnectedAtMs: Long = System.currentTimeMillis(),
    val fanPercent: Int = 50,
    val fanMode: FanMode = FanMode.MANUAL,
    val rgb: RGBConfig? = null,
) {
    val displayName: String get() = name.ifBlank { deviceType.deviceName }

    val icon: String get() = deviceType.suggestedIcon

    companion object {
        fun fromDevice(
            device: CoolerDevice,
            name: String? = null,
        ): CoolerProfile = CoolerProfile(
            name = name ?: device.displayName,
            deviceType = device.deviceType,
            macAddress = device.address,
            fanPercent = 50,
            fanMode = FanMode.MANUAL,
        )
    }
}
