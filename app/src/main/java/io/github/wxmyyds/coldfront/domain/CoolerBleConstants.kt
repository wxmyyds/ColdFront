package io.github.wxmyyds.coldfront.domain

import java.util.UUID

/**
 * 红魔散热器 BLE 协议常量。
 *
 * 3–8 代共用同一套 GATT 服务/特征(逆向自 cn.nubia.externdevice,
 * 详见 docs/protocol-8pro.md)。8 Pro 差异:风扇 raw 40–80、自动模式写 0x01/0x00、温度显示 -6。
 */
object CoolerBleConstants {

    /** 广播 Service UUID 家族标记(须出现在 ServiceUuids 列表) */
    val ADVERTISING_SERVICE_UUID: UUID = UUID.fromString("00004a41-0000-1000-8000-00805f9b34fb")

    /**
     * 厂商数据(Manufacturer Specific Data)公司 ID:0x08CA(2250)。
     * payload 前 2 字节 = [mainType, subType],用于精确识别型号(8 Pro = 0x05, 0x08)。
     */
    const val MSD_COMPANY_ID: Int = 0x08CA

    /** MSD payload 中 mainType 的下标 */
    const val MSD_INDEX_MAIN_TYPE: Int = 0
    /** MSD payload 中 subType 的下标 */
    const val MSD_INDEX_SUB_TYPE: Int = 1

    /** 主服务:风扇/温度/灯光/自动模式都在此服务下 */
    val FAN_SERVICE_UUID: UUID = UUID.fromString("d52082ad-e805-9f97-9d4e-1c682d9c9ce6")

    /** 霍尔(磁吸检测) */
    val HALL_CHARACTERISTIC_UUID: UUID = UUID.fromString("00001011-0000-1000-8000-00805f9b34fb")

    /** 风扇转速特征(写单字节 raw 调速,读取当前转速) */
    val FAN_SPEED_CHARACTERISTIC_UUID: UUID = UUID.fromString("00001012-0000-1000-8000-00805f9b34fb")

    /** RGB 灯光控制特征(模式 4 字节 / 自定义 [R,G,B];查询写 0x11) */
    val LIGHT_CONTROL_UUID: UUID = UUID.fromString("00001013-0000-1000-8000-00805f9b34fb")

    /** 温度通知特征(单字节有符号 °C,显示值 = raw − 6) */
    val TEMPERATURE_NOTIFICATION_UUID: UUID = UUID.fromString("00001015-0000-1000-8000-00805f9b34fb")

    /** 自动模式控制特征(写 0x01 开 / 0x00 关) */
    val AUTO_MODE_CONTROL_UUID: UUID = UUID.fromString("00001018-0000-1000-8000-00805f9b34fb")

    /** 温度告警/保护阈值特征(4 字节配置) */
    val TEMPERATURE_WARNING_UUID: UUID = UUID.fromString("0000101f-0000-1000-8000-00805f9b34fb")

    /** 灯光查询/握手命令(写单字节 0x11 到灯光特征) */
    const val LIGHT_QUERY_COMMAND: Byte = 0x11

    /** 自动模式开(官方:"8PRO 背夹 writeAutoOn 写1") */
    const val AUTO_MODE_ON: Byte = 0x01

    /** 自动模式关(官方:"writeAutoOff 写0") */
    const val AUTO_MODE_OFF: Byte = 0x00

    /** 温度显示校准偏移(官方显示值 = raw − 6) */
    const val TEMPERATURE_OFFSET: Int = 6

    // —— 风扇调速换算:百分比 ↔ raw(逐型号范围) ——

    /** 百分比(0–100)→ 该型号 raw */
    fun percentageToRaw(percentage: Int, type: CoolerDeviceType): Int {
        val clamped = percentage.coerceIn(0, 100)
        return type.rawMin + (type.rawMax - type.rawMin) * clamped / 100
    }

    /** 该型号 raw → 百分比(0–100) */
    fun rawToPercentage(raw: Int, type: CoolerDeviceType): Int {
        val clamped = raw.coerceIn(type.rawMin, type.rawMax)
        return (clamped - type.rawMin) * 100 / (type.rawMax - type.rawMin)
    }
}
