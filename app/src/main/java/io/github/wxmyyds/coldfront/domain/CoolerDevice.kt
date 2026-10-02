package io.github.wxmyyds.coldfront.domain

/**
 * 扫描到的散热器设备。
 *
 * @param address BLE 设备 MAC 地址
 * @param bleName BLE 广播名，可能为空
 * @param deviceType 识别出的型号（优先厂商 MSD，回退广播名）
 * @param rssi 信号强度（dBm）
 * @param scanTimeNanos 扫描时间戳
 * @param matchedByName 为 true 表示本次识别走的是名称兜底（MSD 未确认）
 */
data class CoolerDevice(
    val address: String,
    val bleName: String?,
    val deviceType: CoolerDeviceType,
    val rssi: Int,
    val scanTimeNanos: Long = System.nanoTime(),
    val matchedByName: Boolean = false,
) {
    /** UI 显示名 */
    val displayName: String get() = bleName ?: deviceType.deviceName

    /** 信号是否足够强（> -70 dBm） */
    val hasStrongSignal: Boolean get() = rssi > -70

    /** 信号质量百分比（0–100） */
    val signalQuality: Int
        get() = when {
            rssi >= -50 -> 100
            rssi >= -60 -> 80
            rssi >= -70 -> 60
            rssi >= -80 -> 40
            rssi >= -90 -> 20
            else -> 10
        }

    override fun toString(): String =
        "CoolerDevice(type=${deviceType.deviceName}, name=$bleName, address=$address, rssi=$rssi dBm)"
}
