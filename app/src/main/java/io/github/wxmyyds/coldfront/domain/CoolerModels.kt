package io.github.wxmyyds.coldfront.domain

/**
 * RGB 灯效(逆向自官方 App,写入 0x1013 的命令格式 [mode][R][G][B]):
 * 1=炫彩 2=全彩呼吸 3=单色呼吸 4=常亮 5=场景 6=关闭。
 */
enum class LightEffect(val code: Byte, val labelZh: String, val labelEn: String) {
    COLORFUL(0x01, "炫彩", "Colorful"),
    BREATH_FULLCOLOR(0x02, "全彩呼吸", "Breathing"),
    BREATH_SINGLE(0x03, "单色呼吸", "Breathing (Single)"),
    ALWAYS_BRIGHT(0x04, "常亮", "Always On"),
    OFF(0x06, "关闭", "Off"),
    ;

    companion object {
        fun fromCode(code: Byte): LightEffect? = entries.firstOrNull { it.code == code }
    }
}

/**
 * RGB 配置。
 * @property effect 灯效
 * @property red/green/blue 颜色分量（0–255）
 */
data class RGBConfig(
    val effect: LightEffect,
    val red: Int,
    val green: Int,
    val blue: Int,
) {
    init {
        require(red in 0..255) { "red 越界：$red" }
        require(green in 0..255) { "green 越界：$green" }
        require(blue in 0..255) { "blue 越界：$blue" }
    }

    /** 序列化为 [effect][R][G][B] 4 字节命令 */
    fun toCommand(): ByteArray = byteArrayOf(effect.code, red.toByte(), green.toByte(), blue.toByte())
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
    val temperatureC: Float = 0f,
    val fanPercent: Int = 0,
    val fanMode: FanMode = FanMode.OFF,
    val rgb: RGBConfig? = null,
    val deviceType: CoolerDeviceType? = null,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val rssi: Int = 0,
) {
    val isConnected: Boolean get() = connection == ConnectionState.CONNECTED
    val temperatureText: String get() = if (temperatureC > 0) "%.1f°C".format(temperatureC) else "--"
}
