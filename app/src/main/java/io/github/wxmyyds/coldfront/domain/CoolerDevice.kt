package io.github.wxmyyds.coldfront.domain

import android.bluetooth.BluetoothDevice

/**
 * 扫描到的散热器设备。
 *
 * @param bluetoothDevice 系统 BLE 设备句柄
 * @param deviceType 识别出的型号（优先 ServiceData UUID，回退广播名）
 * @param rssi 信号强度（dBm）
 * @param scanTimeNanos 扫描时间戳
 * @param matchedByName 为 true 表示本次识别走的是名称兜底（UUID 未确认）
 */
data class CoolerDevice(
    val bluetoothDevice: BluetoothDevice,
    val deviceType: CoolerDeviceType,
    val rssi: Int,
    val scanTimeNanos: Long = System.nanoTime(),
    val matchedByName: Boolean = false,
) {
    /** MAC 地址 */
    val address: String get() = bluetoothDevice.address

    /** BLE 广播名（可能为 null，SecurityException 时安全返回 null） */
    val bleName: String?
        get() = try {
            bluetoothDevice.name
        } catch (_: SecurityException) {
            null
        }

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CoolerDevice) return false
        return address == other.address
    }

    override fun hashCode(): Int = address.hashCode()

    override fun toString(): String =
        "CoolerDevice(type=${deviceType.deviceName}, name=$bleName, address=$address, rssi=$rssi dBm)"
}
