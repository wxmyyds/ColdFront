package io.github.wxmyyds.coldfront.domain

/**
 * RGB 灯效(逆向自官方 App,写入 0x1013 的命令格式 [mode][R][G][B]):
 * 1=炫彩 2=全彩呼吸 3=单色呼吸 4=常亮 5=场景 6=关闭。
 */
enum class LightEffect(val code: Byte, val labelZh: String, val labelEn: String) {
    COLORFUL(0x01, "炫彩", "Colorful"),
    /** UI 上叫「呼吸」。协议里是不带颜色字节的全彩呼吸。 */
    BREATH_FULLCOLOR(0x02, "呼吸", "Breathing"),
    /**
     * 官方协议的单色呼吸。UI 已将它合并进 [BREATH_FULLCOLOR]（两者都叫「呼吸」），
     * 保留枚举项只为解析设备回报的 0x03 与旧档案里按名字存的 "BREATH_SINGLE"——
     * 删掉会让这两种来源静默回退成常亮。呈现时统一走 [uiEffect]。
     */
    BREATH_SINGLE(0x03, "单色呼吸", "Breathing (Single)"),
    ALWAYS_BRIGHT(0x04, "常亮", "Always On"),
    OFF(0x06, "关闭", "Off"),
    ;

    /** 设备回报/旧档案里的单色呼吸，统一按「呼吸」呈现 */
    val uiEffect: LightEffect
        get() = if (this == BREATH_SINGLE) BREATH_FULLCOLOR else this

    companion object {
        fun fromCode(code: Byte): LightEffect? = entries.firstOrNull { it.code == code }

        /** UI 可选项：[BREATH_SINGLE] 已并入 [BREATH_FULLCOLOR]，不单独出现 */
        val selectable: List<LightEffect> =
            listOf(ALWAYS_BRIGHT, BREATH_FULLCOLOR, COLORFUL, OFF)
    }
}

/**
 * RGB 配置。
 * @property effect 灯效
 * @property red/green/blue 颜色分量（0–255）
 */
data class RGBConfig(
    val effect: LightEffect,
    val red: Int = 255,
    val green: Int = 0,
    val blue: Int = 0,
) {
    init {
        require(red in 0..255) { "red 越界：$red" }
        require(green in 0..255) { "green 越界：$green" }
        require(blue in 0..255) { "blue 越界：$blue" }
    }

    /** 序列化为 [effect][R][G][B] 4 字节命令;炫彩/全彩呼吸不带颜色字节(实测 App 同样置零) */
    fun toCommand(): ByteArray = when (effect) {
        LightEffect.COLORFUL, LightEffect.BREATH_FULLCOLOR, LightEffect.OFF ->
            byteArrayOf(effect.code, 0, 0, 0)
        else ->
            byteArrayOf(effect.code, red.toByte(), green.toByte(), blue.toByte())
    }
}

/** 风扇模式 */
enum class FanMode(val labelZh: String, val labelEn: String) {
    OFF("关闭", "Off"),
    MANUAL("手动", "Manual"),
    AUTO("自动", "Auto"),
}

/** BLE 连接状态 */
enum class ConnectionState(val labelZh: String, val labelEn: String) {
    DISCONNECTED("已断开", "Disconnected"),
    SCANNING("扫描中", "Scanning"),
    CONNECTING("连接中", "Connecting"),
    DISCOVERING("发现服务中", "Discovering Services"),
    CONNECTED("已连接", "Connected"),
    FAILED("连接失败", "Failed"),
}

/**
 * 散热器实时状态（UI 与 Service 共享）。
 */
data class CoolerLiveState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val temperatureC: Float? = null,
    val fanPercent: Int = 0,
    val fanMode: FanMode = FanMode.OFF,
    val rgb: RGBConfig? = null,
    val deviceType: CoolerDeviceType? = null,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val rssi: Int = 0,
    /** 风扇转速(101C/1015上报, RPM) */
    val fanRpm: Int? = null,
    /** 功率(101D/1015上报, W) */
    val powerW: Int? = null,
    /** 散热总开关(1011): 2=开 3=关 */
    val coolingOn: Boolean = false,
    /** 智能温控(1018): 设备自主温控 */
    val smartOn: Boolean = false,
    /** 破坏神/Boost 超频(1017) */
    val boostOn: Boolean = false,
    /** 过冷/冷凝保护(101F bit2) */
    val overcoldOn: Boolean = false,
) {
    val isConnected: Boolean get() = connection == ConnectionState.CONNECTED
    val temperatureText: String get() = temperatureC?.let { "%.1f°C".format(it) } ?: "--"

    /** 智能温控或散热关时不可手动调档 */
    val manualLevelEnabled: Boolean get() = isConnected && coolingOn && !smartOn
}
