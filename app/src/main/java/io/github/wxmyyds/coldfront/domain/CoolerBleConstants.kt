package io.github.wxmyyds.coldfront.domain

import java.util.UUID

/**
 * 红魔散热器 BLE 协议常量
 *
 * 逆向自努比亚官方 App（cn.nubia.externdevice）。全系列（1 代至 8 Pro）共用同一套
 * GATT 服务/特征 UUID，仅“用于设备识别的广播 UUID（ServiceData 0x4A41 的 payload）”
 * 因型号而异。
 */
object CoolerBleConstants {

    /** 广播 ServiceData 键（0x4A41 = "JA"），用于识别红魔散热器家族 */
    val ADVERTISING_SERVICE_UUID: UUID = UUID.fromString("00004a41-0000-1000-8000-00805f9b34fb")

    /** 主服务：风扇/温度/灯光/自动模式都在此服务下 */
    val FAN_SERVICE_UUID: UUID = UUID.fromString("d52082ad-e805-9f97-9d4e-1c682d9c9ce6")

    /** 风扇转速特征（写入 raw 值调速，读取当前转速） */
    val FAN_SPEED_CHARACTERISTIC_UUID: UUID = UUID.fromString("00001012-0000-1000-8000-00805f9b34fb")

    /** 温度通知特征（启用 notify 后持续推送散热器温度） */
    val TEMPERATURE_NOTIFICATION_UUID: UUID = UUID.fromString("00001015-0000-1000-8000-00805f9b34fb")

    /** RGB 灯光控制特征，写入 [effect][R][G][B] 4 字节 */
    val LIGHT_CONTROL_UUID: UUID = UUID.fromString("00001013-0000-1000-8000-00805f9b34fb")

    /** 自动模式控制特征 */
    val AUTO_MODE_CONTROL_UUID: UUID = UUID.fromString("00001018-0000-1000-8000-00805f9b34fb")

    // —— 风扇调速换算：百分比 ↔ BLE raw ——
    // 0–100% 映射到 40–200 raw
    const val MIN_RAW_VALUE = 40
    const val MAX_RAW_VALUE = 200

    /** 百分比（0–100）→ BLE raw（40–200） */
    fun percentageToRaw(percentage: Int): Int {
        val clamped = percentage.coerceIn(0, 100)
        return MIN_RAW_VALUE + (MAX_RAW_VALUE - MIN_RAW_VALUE) * clamped / 100
    }

    /** BLE raw（40–200）→ 百分比（0–100） */
    fun rawToPercentage(raw: Int): Int {
        val clamped = raw.coerceIn(MIN_RAW_VALUE, MAX_RAW_VALUE)
        return (clamped - MIN_RAW_VALUE) * 100 / (MAX_RAW_VALUE - MIN_RAW_VALUE)
    }

    /** 自动模式命令：logcat 抓取显示 writeAutoOn / writeAutoOff 均写 0x00 */
    const val AUTO_MODE_COMMAND: Byte = 0x00
}
