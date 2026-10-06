package io.github.wxmyyds.coldfront.domain

import java.util.UUID

/**
 * 红魔散热器 BLE 协议常量。
 *
 * 特征语义以实测可用的第三方 App(com.magcooler.cpucontrol)与官方 App 逆向交叉验证,
 * 详见 docs/protocol-8pro.md。关键点:
 * - 温度在 0x1014(单字节有符号;固件 8.4.7 为 [0x04, 温度] 包),无偏移;
 * - 0x1011 是散热总开关(写 0x02 开 / 0x03 关)——不写它风扇不会转;
 * - 0x1012 风扇 raw 40–80(8 Pro,8 档);旧型号 40–200;
 * - 特征值不依赖特定服务 UUID:遍历所有服务查找(兼容服务 UUID 变体)。
 */
object CoolerBleConstants {

    /** 广播 Service UUID 家族标记(出现在 ServiceUuids 列表;部分型号不广播) */
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

    // —— 特征值(16 位 UUID 扩展为标准 128 位) ——

    /** 散热总开关:写 0x02 开 / 0x03 关;通知值 2=开 3=关 */
    val COOLING_SWITCH_UUID: UUID = UUID.fromString("00001011-0000-1000-8000-00805f9b34fb")
    const val COOLING_SWITCH_ON: Byte = 0x02
    const val COOLING_SWITCH_OFF: Byte = 0x03

    /** 风扇调速:写单字节 raw(8 Pro: 40–80);可读回当前值 */
    val FAN_SPEED_CHARACTERISTIC_UUID: UUID = UUID.fromString("00001012-0000-1000-8000-00805f9b34fb")

    /** RGB 灯光:[mode][R][G][B];1炫彩 2全彩呼吸 3单色呼吸 4常亮 5保留 6关 */
    val LIGHT_CONTROL_UUID: UUID = UUID.fromString("00001013-0000-1000-8000-00805f9b34fb")

    /** 灯光查询字节:写 0x11 到 0x1013 后,设备经 notify 回读当前灯效(不可读固件的替代路径) */
    const val LIGHT_QUERY_COMMAND: Byte = 0x11

    /** 背夹温度:通知。单字节有符号 °C;固件 8.4.7 为 [0x04, 温度] 多字节包 */
    val TEMPERATURE_NOTIFICATION_UUID: UUID = UUID.fromString("00001014-0000-1000-8000-00805f9b34fb")

    /** 状态:通知。包格式 [tag, ...]:tag 0x08 → 后 2 字节大端转速;tag 0x09 → 后 1 字节功率 W */
    val STATUS_UUID: UUID = UUID.fromString("00001015-0000-1000-8000-00805f9b34fb")

    /** 智能温控(自动模式):写 0x01 开 / 0x00 关;通知 1=开 */
    val AUTO_MODE_CONTROL_UUID: UUID = UUID.fromString("00001018-0000-1000-8000-00805f9b34fb")

    /** Boost/破坏神(超频):写 0x01 开 / 0x00 关 */
    val BOOST_CONTROL_UUID: UUID = UUID.fromString("00001017-0000-1000-8000-00805f9b34fb")

    /** 风扇转速(新):通知,大端 16 位 RPM */
    val RPM_UUID: UUID = UUID.fromString("0000101c-0000-1000-8000-00805f9b34fb")

    /** 功率(新):通知,byte0 = W */
    val POWER_UUID: UUID = UUID.fromString("0000101d-0000-1000-8000-00805f9b34fb")

    /** 过冷/冷凝保护(101F):写 [flag, 0, 高阈值, 低阈值];flag = (开?0x04:0)|0x03 */
    val PROTECTION_UUID: UUID = UUID.fromString("0000101f-0000-1000-8000-00805f9b34fb")
    const val PROTECTION_FLAG_ON: Byte = 0x07   // 0x04 | 0x03
    const val PROTECTION_FLAG_OFF: Byte = 0x03
    const val PROTECTION_HIGH_DEFAULT: Byte = 100   // 0x64
    const val PROTECTION_LOW_DEFAULT: Byte = -30    // 0xE2

    /** 温度显示校准偏移(官方 App:显示 = raw − 6,以官方 App 显示为准) */
    const val TEMPERATURE_OFFSET: Int = 6

    /** 自动模式开(官方:"8PRO 背夹 writeAutoOn 写1") */
    const val AUTO_MODE_ON: Byte = 0x01

    /** 自动模式关(官方:"writeAutoOff 写0") */
    const val AUTO_MODE_OFF: Byte = 0x00

    // —— 风扇调速换算:百分比 ↔ raw(逐型号范围) ——

    /** Official 8 Pro raw boundaries, presented as 1..8 rather than firmware's 0..7. */
    fun rawToGear8Pro(raw: Int): Int = when {
        raw <= 42 -> 1
        raw <= 48 -> 2
        raw <= 54 -> 3
        raw <= 60 -> 4
        raw <= 66 -> 5
        raw <= 70 -> 6
        raw <= 74 -> 7
        else -> 8
    }

    /** Even representatives survive the integer percentage round-trip; endpoints reach 40/80. */
    fun gearToRaw8Pro(gear: Int): Int = when (gear.coerceIn(1, 8)) {
        1 -> 40
        2 -> 46
        3 -> 52
        4 -> 58
        5 -> 64
        6 -> 68
        7 -> 72
        else -> 80
    }

    fun gearToPercentage8Pro(gear: Int): Int =
        rawToPercentage(gearToRaw8Pro(gear), CoolerDeviceType.JACKET_8_PRO)

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
