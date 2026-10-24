package io.github.wxmyyds.coldfront.domain

/**
 * 红魔/努比亚散热器支持的设备类型。
 *
 * 识别方式(逆向自官方 App,见 docs/protocol-6pro.md 与 docs/protocol-8pro.md):
 * - 3 代起:广播厂商数据 MSD(company id 0x08CA)前两字节 [mainType, subType];
 * - 兜底:蓝牙广播名匹配 [matchesBleName]。
 *
 * 全系命名规则:展示名 "RM Magcooler {代数}[ 变体]",别名 "Cryo Cooler {代数}[ 变体]"
 * (历史展示名);广播名识别规则不受影响。
 *
 * @param mainType MSD byte0(全系 0x05)
 * @param subType MSD byte1(型号编号)
 * @param rawMin 风扇 raw 下限(6/8 Pro 为 40)
 * @param rawMax 风扇 raw 上限(6/8 Pro 为 80,旧型号 200)
 */
enum class CoolerDeviceType(
    val deviceName: String,
    val mainType: Int,
    val subType: Int,
    val generation: Int,
    val description: String,
    val rawMin: Int = 40,
    val rawMax: Int = 200,
    val supportsRgb: Boolean = true,
    val supportsAutoMode: Boolean = true,
    /** 6 Pro uses the official 40–80 raw slider; 8 Pro is the only discrete gear UI. */
    val usesRawFanSlider: Boolean = false,
    /**
     * 全系覆盖的其它已知叫法(历史展示名 Cryo 系)。仅作元数据与展示兼容,
     * 广播名识别规则(contains,见 [matchesBleName])不受此列表限制。
     */
    val aliases: List<String> = emptyList(),
) {
    JACKET_1(
        deviceName = "RM Magcooler 1",
        mainType = 0x05, subType = 0x01,
        generation = 1,
        description = "红魔散热器第一代(旧协议)",
        supportsRgb = false,
        supportsAutoMode = false,
        aliases = listOf("Cryo Cooler 1"),
    ),

    JACKET_2(
        deviceName = "RM Magcooler 2",
        mainType = 0x05, subType = 0x02,
        generation = 2,
        description = "红魔散热器第二代(旧协议)",
        supportsRgb = false,
        supportsAutoMode = false,
        aliases = listOf("Cryo Cooler 2"),
    ),

    JACKET_3(
        deviceName = "RM Magcooler 3",
        mainType = 0x05, subType = 0x03,
        generation = 3,
        description = "第三代(官方名称就叫「红魔散热器」)",
        aliases = listOf("Cryo Cooler 3"),
    ),

    JACKET_4(
        deviceName = "RM Magcooler 4 Pro",
        mainType = 0x05, subType = 0x04,
        generation = 4,
        description = "第四代专业版",
        aliases = listOf("Cryo Cooler 4 Pro"),
    ),

    JACKET_5(
        deviceName = "RM Magcooler 5 Pro",
        mainType = 0x05, subType = 0x05,
        generation = 5,
        description = "第五代,RGB 控制",
        aliases = listOf("Cryo Cooler 5 Pro"),
    ),

    JACKET_5_LITE(
        deviceName = "RM Magcooler 5",
        mainType = 0x05, subType = 0x05,
        generation = 5,
        description = "第五代紧凑版(与 5 Pro 同识别码)",
        aliases = listOf("Cryo Cooler 5"),
    ),

    JACKET_6(
        deviceName = "RM Magcooler 6",
        mainType = 0x05, subType = 0x06,
        generation = 6,
        description = "第六代(与 6 Pro 同识别码,官方双管理器)",
        aliases = listOf("Cryo Cooler 6"),
    ),

    JACKET_6_PRO(
        deviceName = "RM Magcooler 6 Pro",
        mainType = 0x05, subType = 0x06,
        generation = 6,
        description = "第六代专业版(官方 40–80 raw 协议)",
        rawMin = 40,
        rawMax = 80,
        usesRawFanSlider = true,
        aliases = listOf("Cryo Cooler 6 Pro"),
    ),

    /**
     * 红魔散热器 6 Pro+(官方广播名 "REDMAGIC Cooler 6 pro+",见 DeviceHelperKt.X)。
     * 与 6/6 Pro 共用 MSD (0x05,0x05)/(0x05,0x06),官方也仅靠名称区分;
     * 协议与 6 Pro 同族(见 [usesJacket6ProFamilyProtocol]),但官方界面是 9 档风扇
     * (Jacket6ProPlusManagerV2$a 档位表 40/46/52/58/64/68/72/76/80),不是连续滑条。
     */
    JACKET_6_PRO_PLUS(
        deviceName = "RM Magcooler 6 Pro+",
        mainType = 0x05, subType = 0x06,
        generation = 6,
        description = "第六代专业版 Plus(官方 9 档风扇)",
        rawMin = 40,
        rawMax = 80,
        aliases = listOf("Cryo Cooler 6 Pro+"),
    ),

    /**
     * 红魔磁吸散热器 8 Pro(第 8 代 COLDLNG,36W,11 叶 6200RPM,16 颗可寻址 RGB,NTC 温控)。
     * MSD(0x08CA) = [0x05, 0x08];风扇 raw 40–80 共 9 档;自动模式写 0x01/0x00。
     */
    JACKET_8_PRO(
        deviceName = "RM Magcooler 8 Pro",
        mainType = 0x05, subType = 0x08,
        generation = 8,
        description = "第八代 COLDLNG 架构,36W 峰值",
        rawMin = 40,
        rawMax = 80,
        aliases = listOf("Cryo Cooler 8 Pro"),
    );

    companion object {
        /** 按 MSD [mainType, subType] 精确匹配 */
        fun fromMsdType(mainType: Int, subType: Int): CoolerDeviceType? =
            entries.firstOrNull { it.mainType == mainType && it.subType == subType }

        /**
         * The official 6 Pro scanner accepts the same MSD family as the plain 6, and the
         * official 6 Pro+ scan filter (Jacket6ProPlusManagerV2.S6) additionally accepts both
         * (0x05,0x05) and (0x05,0x06). A Pro/Pro+ broadcast name is therefore the only static
         * discriminator available to the app; without it we retain the conservative plain-6
         * type rather than inventing a Pro claim.
         */
        fun fromMsdType(mainType: Int, subType: Int, bleName: String?): CoolerDeviceType? {
            val name = bleName.orEmpty()
            return when {
                mainType != 0x05 -> null
                subType in setOf(0x05, 0x06) && name.matchesJacket6ProPlus() -> JACKET_6_PRO_PLUS
                subType in setOf(0x05, 0x06) &&
                    name.contains("6", ignoreCase = true) &&
                    name.contains("Pro", ignoreCase = true) -> JACKET_6_PRO
                else -> fromMsdType(mainType, subType)
            }
        }

        /**
         * 名称兜底识别:优先明确代数，最后匹配通用 Magcooler（第三代）。
         * MSD 不可用时(旧系统/广播被裁剪)的回退。
         */
        fun fromBleName(bleName: String?): CoolerDeviceType? {
            if (bleName.isNullOrBlank()) return null
            val isRedMagic = bleName.contains("Magcooler", true) ||
                bleName.contains("RM ", true) ||
                bleName.contains("RedMagic", true) ||
                bleName.contains("Red Magic", true) ||
                bleName.contains("Nubia", true) ||
                bleName.contains("Cryo", true) ||
                bleName.contains("Heat Sink", true)
            if (!isRedMagic) return null
            return entries.firstOrNull { it != JACKET_3 && it.matchesBleName(bleName) }
                ?: JACKET_3.takeIf { it.matchesBleName(bleName) }
        }

        /** 6 Pro+ 官方名称判别(DeviceHelperKt.X:包含 "REDMAGIC Cooler 6 pro+"，忽略大小写)。 */
        private fun String.matchesJacket6ProPlus(): Boolean =
            contains("6", ignoreCase = true) &&
                contains("Pro", ignoreCase = true) &&
                (contains("Plus", ignoreCase = true) || '+' in this)
    }

    /** 该型号是否与给定 BLE 广播名一致(严格匹配,避免误判) */
    fun matchesBleName(bleName: String?): Boolean {
        if (bleName.isNullOrBlank()) return false
        return when (this) {
            JACKET_1 -> bleName.contains("Jacket", true) || bleName.contains("Dual", true) ||
                bleName.contains("双核", true)
            JACKET_2 -> bleName.contains("Turbo", true) || bleName.contains("涡轮", true)
            JACKET_3 -> (bleName.contains("Magcooler", true) && !bleName.contains("Pro", true)) ||
                bleName.contains("Magcooler 3", true)
            JACKET_4 -> bleName.contains("4", true) && !bleName.contains(Regex("[5678]"))
            JACKET_5 -> bleName.contains("5pro", true) ||
                (bleName.contains("5", true) && bleName.contains("Pro", true) && !bleName.contains("Lite", true))
            JACKET_5_LITE -> bleName.contains("5 Lite", true) || bleName.contains("5Lite", true)
            JACKET_6 -> bleName.contains("6", true) && !bleName.contains("Pro", true)
            JACKET_6_PRO -> bleName.contains("6", true) && bleName.contains("Pro", true) &&
                !bleName.contains("Plus", true) && '+' !in bleName
            JACKET_6_PRO_PLUS -> bleName.matchesJacket6ProPlus()
            JACKET_8_PRO -> (bleName.contains("8", true) && bleName.contains("Pro", true)) ||
                bleName.contains("Cooler 8", true) || bleName.contains("8 Pro", true)
        }
    }

    /** 6 Pro 与 6 Pro+ 共用同一套 jacket6 家族协议路径(同服务/特征/状态包,命令均为单字节)。 */
    val usesJacket6ProFamilyProtocol: Boolean
        get() = this == JACKET_6_PRO || this == JACKET_6_PRO_PLUS

    val suggestedIcon: String
        get() = when (generation) {
            1, 2 -> "🌀"
            3, 4 -> "❄️"
            5, 6 -> "🔷"
            7, 8 -> "🧊"
            else -> "📦"
        }
}
