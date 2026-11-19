package io.github.wxmyyds.coldfront.domain

import java.util.UUID

/**
 * 红魔散热器 BLE 协议常量。
 *
 * 特征语义根据第三方 App(com.magcooler.cpucontrol)与官方 App 的逆向结果交叉验证，详见 docs/protocol-8pro.md：
 * - 温度在 0x1014(单字节有符号；固件 8.4.7 为 [0x04, 温度] 包)，无偏移；
 * - 0x1011 是散热总开关(写 0x02 开 / 0x03 关)，不写它风扇不会转；
 * - 0x1012 风扇 raw 40–80(8 Pro/6 Pro+ 均为官方 9 档)，旧型号 40–200；
 * - 特征 UUID 的服务归属会随型号和固件变化。6 Pro/6 Pro+ 只从主服务绑定；其他型号遍历服务以兼容 UUID 变体。
 */
object CoolerBleConstants {

    /** 广播 Service UUID 家族标记(出现在 ServiceUuids 列表;部分型号不广播) */
    val ADVERTISING_SERVICE_UUID: UUID = UUID.fromString("00004a41-0000-1000-8000-00805f9b34fb")

    /**
     * 厂商数据(Manufacturer Specific Data)的公司 ID：0x08CA(2250)。
     * payload 前 2 字节为 [mainType, subType]，用于精确识别型号(8 Pro = 0x05, 0x08)。
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
     * 官方主服务(Jacket8ProProcessor.buildServiceAndCharacter 构造；其 onCharacteristicRead /
     * onCharacteristicChanged 只在 service == 此值时处理)。8 Pro 的全部控制/灯效特征都在该服务下。
     * 其它服务可能暴露相同 UUID 但语义不同的特征，因此绑定时优先使用主服务中的实例。
     */
    val MAIN_SERVICE_UUID: UUID = UUID.fromString("d52082ad-e805-9f97-9d4e-1c682d9c9ce6")

    /**
     * 官方默认灯效字节(n0.a() = [0x01,0x00,0x00,0x00]，炫彩)。读取到非 1/2/3/4/6 的未知灯效字节且数组非空时，
     * 官方 refreshLightModeView 会下发该命令，将设备恢复到已知状态。
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
     * 官方 `Jacket8ProManagerV2$a.b()`(即静态字段 `r0`)的值，表示设备上报的功耗限档“不限”值。
     * 限档等于该值表示供电充足；小于该值表示充电器供电功率不足。
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

    /**
     * Official 8 Pro raw boundaries (`Jacket8ProManagerV2$a.d(I)`), presented as UI gears
     * 1..9: 40–43/43–49/49–55/55–61/61–67/67–71/71–74/74–78/78+（与 6 Pro+ 同边界）。
     */
    fun rawToGear8Pro(raw: Int): Int = when {
        raw <= 43 -> 1
        raw <= 49 -> 2
        raw <= 55 -> 3
        raw <= 61 -> 4
        raw <= 67 -> 5
        raw <= 71 -> 6
        raw <= 74 -> 7
        raw <= 78 -> 8
        else -> 9
    }

    /**
     * Official `Jacket8ProManagerV2$a.e(I)` gear→raw table as UI gears 1..9
     * (40/46/52/58/64/68/72/76/80);与官方 0..8 索引一一对应(+1),含此前被 UI 跳过的 76 档。
     */
    fun gearToRaw8Pro(gear: Int): Int = when (gear.coerceIn(1, 9)) {
        1 -> 40
        2 -> 46
        3 -> 52
        4 -> 58
        5 -> 64
        6 -> 68
        7 -> 72
        8 -> 76
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
     * 功耗限档索引(0–8) → 本应用 UI 档位(1–9)上限。
     *
     * 本应用档位 1–9 与官方索引 0–8 一一对应(+1),因此限档索引 N 的最高可用档位就是 N+1;
     * 仍按"raw 不超过限档 raw"取最高档位实现,与官方 `setMaxSelectableProgress(索引)` 等效。
     */
    fun maxGearForFanLimit8Pro(index: Int): Int {
        val maxRaw = fanLimitToRaw8Pro(index)
        return (9 downTo 1).first { gearToRaw8Pro(it) <= maxRaw }
    }

    // —— 红魔散热器 6 Pro+:官方 Jacket6ProPlusManagerV2$a 的 9 档风扇 ——

    /**
     * 官方 `Jacket6ProPlusManagerV2$a.d(I)`:档位索引 0–8 → 风扇 raw
     * (40/46/52/58/64/68/72/76/80)。本应用 UI 用 1–9 档,与官方索引一一对应(+1)。
     */
    fun gearToRaw6ProPlus(gear: Int): Int = when (gear.coerceIn(1, 9)) {
        1 -> 40
        2 -> 46
        3 -> 52
        4 -> 58
        5 -> 64
        6 -> 68
        7 -> 72
        8 -> 76
        else -> 80
    }

    /**
     * 官方 `Jacket6ProPlusManagerV2$a.c(I)` 的 raw→档位边界
     * (40–43/43–49/49–55/55–61/61–67/67–71/71–74/74–78/78+),换算为本应用 1–9 档。
     */
    fun rawToGear6ProPlus(raw: Int): Int = when {
        raw <= 43 -> 1
        raw <= 49 -> 2
        raw <= 55 -> 3
        raw <= 61 -> 4
        raw <= 67 -> 5
        raw <= 71 -> 6
        raw <= 74 -> 7
        raw <= 78 -> 8
        else -> 9
    }

    fun gearToPercentage6ProPlus(gear: Int): Int =
        rawToPercentage(gearToRaw6ProPlus(gear), CoolerDeviceType.JACKET_6_PRO_PLUS)

    /** 任意 raw 归一到官方档位格点(c(I) 取档 → d(I) 回 raw):官方界面只会写这 9 个字节。 */
    fun snapToGearRaw6ProPlus(raw: Int): Int = gearToRaw6ProPlus(rawToGear6ProPlus(raw))

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
