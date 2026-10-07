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

    /**
     * 官方主服务(Jacket8ProProcessor.buildServiceAndCharacter 构造;其 onCharacteristicRead /
     * onCharacteristicChanged 只在 service == 此值时处理)。8 Pro 的全部控制/灯效特征都在该服务下。
     * 其它服务里可能暴露同 UUID 但语义不同的特征,因此绑定特征时优先取主服务实例。
     */
    val MAIN_SERVICE_UUID: UUID = UUID.fromString("d52082ad-e805-9f97-9d4e-1c682d9c9ce6")

    /**
     * 官方默认灯效字节(n0.a() = [0x01,0x00,0x00,0x00],炫彩)。官方 refreshLightModeView 在读到
     * 非 1/2/3/4/6 的未知灯效字节且数组非空时,会下发该命令把设备纠正到已知状态。
     */
    fun defaultLightCommand(): ByteArray = byteArrayOf(0x01, 0x00, 0x00, 0x00)

    /** 背夹温度:通知。单字节有符号 °C;固件 8.4.7 为 [0x04, 温度] 多字节包 */
    val TEMPERATURE_NOTIFICATION_UUID: UUID = UUID.fromString("00001014-0000-1000-8000-00805f9b34fb")

    /**
     * 状态:通知。包格式 [tag, byte0, byte1, byte2](byte1 起为 3 字节数据,不足补 0):
     * tag 0x04 → byte0 温度;tag 0x05 → 3 字节负载状态;tag 0x07 → byte0 功耗限档索引;
     * tag 0x08 → byte0..1 大端转速;tag 0x09 → byte0 功率 W。
     * 官方 `cn/nubia/device/bluetooth/jacket8pro/b.smali` 的 `j(...)` 按此分发。
     */
    val STATUS_UUID: UUID = UUID.fromString("00001015-0000-1000-8000-00805f9b34fb")

    /** 0x1015 状态包标签:温度(byte0 有符号 °C) */
    const val STATUS_TAG_TEMPERATURE: Int = 0x04

    /** 0x1015 状态包标签:负载状态(后 3 字节) */
    const val STATUS_TAG_OUTPUT_LOAD: Int = 0x05

    /** 0x1015 状态包标签:供电功率限档(byte0 = 官方档位索引,见 [fanLimitToRaw8Pro]) */
    const val STATUS_TAG_FAN_LIMIT: Int = 0x07

    /** 0x1015 状态包标签:风扇转速(byte0..1 大端 RPM) */
    const val STATUS_TAG_FAN_RPM: Int = 0x08

    /** 0x1015 状态包标签:功率(byte0 = W) */
    const val STATUS_TAG_FAN_POWER: Int = 0x09

    /**
     * 官方 `Jacket8ProManagerV2$a.b()`(即静态字段 `r0`)的值:设备上报的功耗限档"不限"值。
     * 限档等于该值表示供电充足;小于该值表示充电器供电功率不足。
     */
    const val FAN_LIMIT_UNLIMITED_8_PRO: Int = 8

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

    /**
     * 官方 `Jacket8ProManagerV2$a.e(I)`:功耗限档索引(0–8)→ 该限档允许的最高风扇 raw。
     * 索引 8 是"不限档"(raw 80);索引越界走官方 packed-switch 的默认分支,同样返回 80。
     */
    fun fanLimitToRaw8Pro(index: Int): Int = when (index) {
        0 -> 40
        1 -> 46
        2 -> 52
        3 -> 58
        4 -> 64
        5 -> 68
        6 -> 72
        7 -> 76
        else -> 80
    }

    /**
     * 功耗限档索引 → 本应用 UI 档位(1–8)上限。
     *
     * 官方的滑条本身就是 0–8 的档位索引,所以直接 `setMaxSelectableProgress(索引)` 即可;
     * 本应用的档位是 1–8 且第 8 档代表 raw 80,与官方索引并非一一对应
     * (官方索引 7 = raw 76 在 UI 上没有对应档位),因此按"raw 不超过限档 raw"取最高档位:
     * 索引 7 → 档位 7(raw 72),索引 6 → 档位 7,索引 5 → 档位 6,……,索引 0 → 档位 1。
     */
    fun maxGearForFanLimit8Pro(index: Int): Int {
        val maxRaw = fanLimitToRaw8Pro(index)
        return (8 downTo 1).first { gearToRaw8Pro(it) <= maxRaw }
    }

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
