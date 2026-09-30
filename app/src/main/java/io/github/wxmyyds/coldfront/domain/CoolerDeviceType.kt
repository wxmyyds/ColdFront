package io.github.wxmyyds.coldfront.domain

import java.util.UUID

/**
 * 红魔/努比亚散热器支持的设备类型。
 *
 * 每个型号有：
 * - [advertisingUUID]：广播 ServiceData（0x4A41）payload 中的设备专属 UUID，用于精确识别；
 * - [deviceName]：商用名称；
 * - [generation]：代数；
 * - 名称兜底匹配 [matchesBleName]：当拿不到 ServiceData UUID（如 8 Pro 尚未抓取）时，
 *   靠广播名识别。
 *
 * @param advertisingUUID 设备专属广播 UUID。8 Pro 暂为占位 TODO，待用 nRF Connect 抓取真实值。
 * @param supportsRgb 是否支持 RGB 灯效控制
 * @param supportsAutoMode 是否支持自动温控调速
 * @param maxFanPercent 该型号允许的风扇百分比上限（默认 100）
 */
enum class CoolerDeviceType(
    val deviceName: String,
    val advertisingUUID: UUID,
    val generation: Int,
    val description: String,
    val supportsRgb: Boolean = true,
    val supportsAutoMode: Boolean = true,
    val maxFanPercent: Int = 100,
) {
    JACKET_1(
        deviceName = "双核散热背夹",
        advertisingUUID = UUID.fromString("70e03463-0478-4596-a826-e0002d27618a"),
        generation = 1,
        description = "红魔散热器第一代",
        supportsRgb = false,
        supportsAutoMode = false,
    ),

    JACKET_2(
        deviceName = "涡轮散热背夹",
        advertisingUUID = UUID.fromString("3823dac1-f7be-4a11-a145-83b2b2f35e5e"),
        generation = 2,
        description = "红魔散热器第二代，性能提升",
        supportsRgb = false,
        supportsAutoMode = false,
    ),

    JACKET_3(
        deviceName = "磁吸散热器",
        advertisingUUID = UUID.fromString("85e1fd9d-7ca1-4be3-8131-1edde011a47d"),
        generation = 3,
        description = "第三代，设计优化",
    ),

    JACKET_4(
        deviceName = "Heat Sink 4 Pro",
        advertisingUUID = UUID.fromString("1e3f2686-e85f-4c25-8dd3-ff6164d46a16"),
        generation = 4,
        description = "第四代专业版",
    ),

    JACKET_5(
        deviceName = "Heat Sink 5 Pro",
        advertisingUUID = UUID.fromString("b739be84-9cca-44ed-8653-4e4c7f16a9a4"),
        generation = 5,
        description = "第五代，RGB 高级控制",
    ),

    JACKET_5_LITE(
        deviceName = "Heat Sink 5 Lite",
        advertisingUUID = UUID.fromString("a8f345b9-d726-4707-910a-fd637c2b00a7"),
        generation = 5,
        description = "第五代紧凑版",
    ),

    JACKET_6(
        deviceName = "Heat Sink 6",
        advertisingUUID = UUID.fromString("e39bc7af-ca69-4977-9fe7-ae3ad560f063"),
        generation = 6,
        description = "第六代，能效提升",
    ),

    JACKET_6_PRO(
        deviceName = "Heat Sink 6 Pro",
        advertisingUUID = UUID.fromString("9c24a801-7666-40c1-9589-26a89425a0b0"),
        generation = 6,
        description = "第六代专业版",
    ),

    JACKET_7(
        deviceName = "Heat Sink 7",
        advertisingUUID = UUID.fromString("c0ffee12-0000-0000-0000-000000000007"),
        generation = 7,
        description = "第七代（待确认 UUID）",
    ),

    JACKET_7_PRO(
        deviceName = "Heat Sink 7 Pro",
        advertisingUUID = UUID.fromString("c0ffee17-0000-0000-0000-000000000770"),
        generation = 7,
        description = "第七代专业版（待确认 UUID）",
    ),

    /**
     * 红魔磁吸散热器 8 Pro（第 8 代 COLDLNG 架构，36W，11 叶 6200RPM，16 颗可寻址 RGB，NTC 温控）。
     *
     * ⚠️ [advertisingUUID] 为占位值，待用 nRF Connect 抓取 8 Pro 实机广播的
     * ServiceData(0x4A41) payload 后填入真实 UUID。当前靠 [matchesBleName] 兜底识别。
     */
    JACKET_8_PRO(
        deviceName = "Cryo Cooler 8 Pro",
        advertisingUUID = UUID.fromString("00000000-0000-0000-0000-000000000088"),
        generation = 8,
        description = "第八代 COLDLNG 架构，36W 峰值，待确认广播 UUID",
        maxFanPercent = 100,
    );

    companion object {
        /** 按 ServiceData 广播 UUID 精确匹配 */
        fun fromAdvertisingUUID(uuid: UUID): CoolerDeviceType? =
            entries.firstOrNull { it.advertisingUUID == uuid }

        /** 按商用名匹配 */
        fun fromDeviceName(name: String): CoolerDeviceType? =
            entries.firstOrNull { it.deviceName.equals(name, ignoreCase = true) }

        /** 所有广播 UUID，用于扫描过滤 */
        fun getAllAdvertisingUUIDs(): List<UUID> = entries.map { it.advertisingUUID }

        /**
         * 名称兜底识别：遍历所有型号，返回第一个 [matchesBleName] 命中的。
         * 用于拿不到 ServiceData UUID 时的回退。
         */
        fun fromBleName(bleName: String?): CoolerDeviceType? {
            if (bleName.isNullOrBlank()) return null
            // 必须先确认是红魔/努比亚家族，避免误判
            val isRedMagic = bleName.contains("Magcooler", true) ||
                bleName.contains("MagCooler", true) ||
                bleName.contains("RM ", true) ||
                bleName.contains("RedMagic", true) ||
                bleName.contains("Red Magic", true) ||
                bleName.contains("Nubia", true) ||
                bleName.contains("Cryo", true) ||
                bleName.contains("Heat Sink", true)
            if (!isRedMagic) return null
            return entries.firstOrNull { it.matchesBleName(bleName) }
        }
    }

    /**
     * 校验该型号是否与给定的 BLE 广播名一致（严格匹配，避免假阳性）。
     */
    fun matchesBleName(bleName: String?): Boolean {
        if (bleName.isNullOrBlank()) return false
        val isRedMagic = bleName.contains("Magcooler", true) ||
            bleName.contains("MagCooler", true) ||
            bleName.contains("RM ", true) ||
            bleName.contains("RedMagic", true) ||
            bleName.contains("Red Magic", true) ||
            bleName.contains("Nubia", true) ||
            bleName.contains("Cryo", true) ||
            bleName.contains("Heat Sink", true)
        if (!isRedMagic) return false
        return when (this) {
            JACKET_1 -> bleName.contains("Jacket", true) || bleName.contains("Dual", true) ||
                bleName.contains("双核", true)
            JACKET_2 -> bleName.contains("Turbo", true) || bleName.contains("涡轮", true)
            JACKET_3 -> bleName.contains("Magcooler 3", true) || bleName.contains("MagCooler3", true) ||
                bleName.contains("Magcooler", true) && !bleName.contains("Pro", true)
            JACKET_4 -> bleName.contains("4", true) && !bleName.contains("5", true) &&
                !bleName.contains("6", true) && !bleName.contains("7", true) && !bleName.contains("8", true)
            JACKET_5 -> (bleName.contains("5pro", true)) ||
                (bleName.contains("5", true) && bleName.contains("Pro", true) && !bleName.contains("Lite", true))
            JACKET_5_LITE -> bleName.contains("5 Lite", true) || bleName.contains("5Lite", true)
            JACKET_6 -> bleName.contains("6", true) && !bleName.contains("Pro", true)
            JACKET_6_PRO -> bleName.contains("6", true) && bleName.contains("Pro", true)
            JACKET_7 -> bleName.contains("7", true) && !bleName.contains("Pro", true) &&
                !bleName.contains("8", true)
            JACKET_7_PRO -> bleName.contains("7", true) && bleName.contains("Pro", true) &&
                !bleName.contains("8", true)
            JACKET_8_PRO -> (bleName.contains("8", true) && bleName.contains("Pro", true)) ||
                bleName.contains("Cooler 8", true) || bleName.contains("8 Pro", true)
        }
    }

    /** UI 建议图标（emoji，过渡用，最终用 Compose 矢量） */
    val suggestedIcon: String
        get() = when (generation) {
            1, 2 -> "🌀"
            3, 4 -> "❄️"
            5, 6 -> "🔷"
            7, 8 -> "🧊"
            else -> "📦"
        }

    /** 该型号的广播 UUID 是否已确认（未确认则只能靠名称兜底） */
    val uuidConfirmed: Boolean
        get() = !advertisingUUID.toString().contains("00000000-0000-0000-0000-0000000000") &&
            !advertisingUUID.toString().contains("c0ffee")
}
