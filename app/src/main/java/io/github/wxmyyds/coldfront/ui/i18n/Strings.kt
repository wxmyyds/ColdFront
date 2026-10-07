package io.github.wxmyyds.coldfront.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * 100% Kotlin 的双语字符串表（中文 + 英文）。按系统 locale 切换。
 * 不使用 strings.xml，保持工程尽量纯 Kotlin。
 */
interface AppStrings {
    /** 当前文案是否中文(灯效等枚举名本地化用) */
    val langIsZh: Boolean
    val appName: String

    // —— 通用 ——
    val cancel: String
    val delete: String
    val back: String
    val retry: String
    val close: String

    // —— 导航 ——
    val navHome: String
    val navDevices: String
    val navRgb: String
    val navSettings: String

    // —— 首页:独立控制 ——
    val homeCoolingSwitch: String
    val homeCoolingSwitchDesc: String
    val telemetryDegradedTitle: String
    val telemetryDegradedHint: String
    val homePowerLimitedTitle: String
    val homePowerLimitedHint: String
    val homePowerLimitedConfirm: String
    val homeSmart: String
    val homeSmartDesc: String
    val homeBoost: String
    val homeBoostDesc: String
    val homeOvercold: String
    val homeOvercoldDesc: String
    val homeLevel: String
    val homeLevelGear: String
    val homeLevelPercent: String
    val homeSmartActiveLevel: String

    // —— 设备页 ——
    val devicesTitle: String
    val devicesEmpty: String
    val devicesEmptyHint: String
    val devicesAdd: String
    val devicesConnect: String
    val devicesConnected: String
    val devicesDelete: String
    val devicesLastSeen: String

    // —— 设置页 ——
    val settingsTitle: String
    val settingsTheme: String
    val settingsDynamicColor: String
    val settingsDynamicColorDesc: String
    val settingsThemeMode: String
    val settingsPalette: String
    val settingsPaletteTonalSpot: String
    val settingsPaletteNeutral: String
    val settingsPaletteVibrant: String
    val settingsPaletteExpressive: String
    val settingsPaletteRainbow: String
    val settingsPaletteFruitSalad: String
    val settingsPaletteMonochrome: String
    val settingsPaletteFidelity: String
    val settingsPaletteContent: String
    val settingsInterface: String
    val settingsPredictiveBack: String
    val settingsPredictiveBackDesc: String
    val settingsConnection: String
    val settingsDefaultDevice: String
    val settingsDefaultDeviceDesc: String
    val settingsDefaultDeviceOff: String
    val settingsFollowSystem: String
    val settingsDarkModeLight: String
    val settingsDarkModeDark: String
    val settingsLanguage: String
    val settingsAbout: String
    val settingsAboutReport: String
    val settingsAboutProject: String
    val aboutOpenLinkFailed: String
    val storageOperationFailed: String

    // —— 首页 ——
    val homeTitle: String
    val homeNoDeviceHint: String
    val homeConnecting: String
    val homeTemp: String
    val homeConnected: String
    val homeConnectionFailed: String
    val homeRetryHint: String
    val homeGoScan: String
    val homeNotConnected: String

    // —— 扫描/添加设备 ——
    val scanTitle: String
    val scanScanning: String
    val scanNoFound: String
    val scanNoFoundHint: String
    val scanRescan: String
    val scanSelect: String
    val scanMatchedByName: String
    val scanBluetoothOff: String
    val scanBluetoothOffHint: String
    val scanEnableBluetooth: String

    // —— 诊断模式 ——
    val diagToggle: String
    val diagHint: String
    val diagNoName: String
    val diagNoSignal: String
    val diagNoSignalHint: String
    val diagMsd: String
    val diagServiceData: String
    val diagServiceUuids: String
    val diagMoreCount: String
    val diagConnectAs: String
    val diagCancel: String
    val diagRawCount: String

    // —— 诊断状态行 ——
    val diagStatusPermission: String
    val diagStatusScanner: String
    val diagStatusLocation: String
    val diagStatusBluetooth: String
    val diagPermissionGranted: String
    val diagPermissionMissing: String
    val diagScannerRunning: String
    val diagScannerStopped: String
    val diagScannerFailed: String
    val diagServiceOn: String
    val diagServiceOff: String
    val diagGrantAgain: String
    val diagOpenAppSettings: String
    val diagOpenLocation: String
    val diagCoolerBadge: String

    // —— RGB ——
    val rgbTitle: String
    val rgbEffect: String
    val rgbBreathMode: String
    val rgbBreathSingle: String
    val rgbBreathFull: String
    val rgbRed: String
    val rgbGreen: String
    val rgbBlue: String
    val rgbApply: String
    val rgbWriting: String
    val rgbSent: String
    val rgbWriteFailed: String
    val rgbConnectFirst: String
    val rgbGoConnect: String
    val rgbPalette: String
    val rgbCustomColor: String
    val rgbNotSupported: String
    val rgbSyncing: String

    // —— 自动模式/服务 ——
    val serviceChannelName: String
    val serviceChannelDesc: String
    val serviceAutoOn: String
    val serviceSwitchManual: String
    val serviceReconnect: String
    val serviceWaitingConfig: String
    val serviceManual: String
    val serviceUnavailable: String
    val serviceControlFailed: String

}

object ZhStrings : AppStrings {
    override val langIsZh = true
    override val appName = "ColdFront"
    override val cancel = "取消"
    override val delete = "删除"
    override val back = "返回"
    override val retry = "重试"
    override val close = "关闭"

    override val navHome = "首页"
    override val navDevices = "设备"
    override val navRgb = "灯效"
    override val navSettings = "设置"

    override val homeCoolingSwitch = "散热开关"
    override val homeCoolingSwitchDesc = "关闭后风扇全停"
    override val telemetryDegradedTitle = "状态更新受限"
    override val telemetryDegradedHint = "部分状态已停止更新，显示值可能已过期。请重新连接以恢复更新。"
    override val homePowerLimitedTitle = "充电器供电功率不足"
    override val homePowerLimitedHint = "散热器已限制制冷档位上限，并关闭了破坏神。请换用功率更大的充电器或供电口。"
    override val homePowerLimitedConfirm = "知道了"
    override val homeSmart = "智能温控"
    override val homeSmartDesc = "由散热器根据温度自动调节档位"
    override val homeBoost = "破坏神"
    override val homeBoostDesc = "超频增强模式(功耗更高)"
    override val homeOvercold = "过冷保护"
    override val homeOvercoldDesc = "防止冷凝:低温自动降档保护"
    override val homeLevel = "制冷档位"
    override val homeLevelGear = "%d 档"
    override val homeLevelPercent = "%d%%"
    override val homeSmartActiveLevel = "智能温控运行中,档位由设备调节"

    override val devicesTitle = "我的设备"
    override val devicesEmpty = "还没有保存的设备"
    override val devicesEmptyHint = "点击下方按钮扫描并添加散热器"
    override val devicesAdd = "添加设备"
    override val devicesConnect = "连接"
    override val devicesConnected = "已连接"
    override val devicesDelete = "删除"
    override val devicesLastSeen = "上次使用:%s"

    override val settingsTitle = "设置"
    override val settingsTheme = "主题与配色"
    override val settingsDynamicColor = "动态取色"
    override val settingsDynamicColorDesc = "跟随系统壁纸配色"
    override val settingsThemeMode = "主题模式"
    override val settingsPalette = "调色板"
    override val settingsPaletteTonalSpot = "Tonal Spot"
    override val settingsPaletteNeutral = "Neutral"
    override val settingsPaletteVibrant = "Vibrant"
    override val settingsPaletteExpressive = "Expressive"
    override val settingsPaletteRainbow = "Rainbow"
    override val settingsPaletteFruitSalad = "Fruit Salad"
    override val settingsPaletteMonochrome = "Monochrome"
    override val settingsPaletteFidelity = "Fidelity"
    override val settingsPaletteContent = "Content"
    override val settingsInterface = "界面"
    override val settingsPredictiveBack = "预测性返回"
    override val settingsPredictiveBackDesc = "启用导航页面的系统返回过渡"
    override val settingsConnection = "连接"
    override val settingsDefaultDevice = "默认连接的设备"
    override val settingsDefaultDeviceDesc = "应用启动时自动连接该设备"
    override val settingsDefaultDeviceOff = "关闭"
    override val settingsFollowSystem = "跟随系统"
    override val settingsDarkModeLight = "浅色"
    override val settingsDarkModeDark = "深色"
    override val settingsLanguage = "语言"
    override val settingsAbout = "关于"
    override val settingsAboutReport = "提交错误报告"
    override val settingsAboutProject = "项目主页"
    override val aboutOpenLinkFailed = "没有可用于打开此链接的应用。"
    override val storageOperationFailed = "读取或保存配置失败，请稍后重试。"

    override val homeTitle = "散热控制"
    override val homeNoDeviceHint = "点击下方按钮扫描并添加红魔散热器"
    override val homeConnecting = "连接中…"
    override val homeTemp = "温度"
    override val homeConnected = "已连接"
    override val homeConnectionFailed = "连接失败"
    override val homeRetryHint = "请确认散热器已通电并靠近后重试"
    override val homeGoScan = "去扫描设备"
    override val homeNotConnected = "未连接散热器"

    override val scanTitle = "扫描散热器"
    override val scanScanning = "正在扫描…"
    override val scanNoFound = "未发现红魔散热器"
    override val scanNoFoundHint = "请确认散热器已通电并靠近手机"
    override val scanRescan = "重新扫描"
    override val scanSelect = "连接"
    override val scanMatchedByName = "名称识别"
    override val scanBluetoothOff = "蓝牙未开启"
    override val scanBluetoothOffHint = "请先开启蓝牙以扫描设备"
    override val scanEnableBluetooth = "开启蓝牙"

    override val diagToggle = "显示全部设备（诊断）"
    override val diagHint = "不过滤识别，显示周围全部 BLE 原始广播：MSD/UUID。散热器广播的内容可直接读出，也可手动选型号连接。"
    override val diagNoName = "（无名设备）"
    override val diagNoSignal = "一个广播都没收到"
    override val diagNoSignalHint = "检查：①散热器已通电（插电/磁吸亮灯） ②系统定位服务已开启（旧系统扫描依赖定位） ③蓝牙已开启 ④距离足够近"
    override val diagMsd = "厂商数据"
    override val diagServiceData = "服务数据"
    override val diagServiceUuids = "服务 UUID"
    override val diagMoreCount = "另有 %d 项"
    override val diagConnectAs = "选择型号连接"
    override val diagCancel = "取消"
    override val diagRawCount = "共 %d 个设备"

    override val diagStatusPermission = "扫描权限"
    override val diagStatusScanner = "扫描器"
    override val diagStatusLocation = "定位服务"
    override val diagStatusBluetooth = "蓝牙"
    override val diagPermissionGranted = "已授予"
    override val diagPermissionMissing = "未授予"
    override val diagScannerRunning = "运行中"
    override val diagScannerStopped = "已停止"
    override val diagScannerFailed = "失败（码 %d）"
    override val diagServiceOn = "开启"
    override val diagServiceOff = "关闭"
    override val diagGrantAgain = "重新授权"
    override val diagOpenAppSettings = "去应用设置"
    override val diagOpenLocation = "去开定位"
    override val diagCoolerBadge = "已识别:%s"

    override val rgbTitle = "RGB 灯效"
    override val rgbEffect = "灯效模式"
    override val rgbBreathMode = "呼吸颜色"
    override val rgbBreathSingle = "单色"
    override val rgbBreathFull = "全彩"
    override val rgbRed = "红"
    override val rgbGreen = "绿"
    override val rgbBlue = "蓝"
    override val rgbConnectFirst = "连接散热器后即可调节灯效"
    override val rgbGoConnect = "去连接"
    override val rgbPalette = "预设颜色"
    override val rgbCustomColor = "自定义颜色"
    override val rgbApply = "应用"
    override val rgbWriting = "正在发送…"
    override val rgbSent = "已发送到设备"
    override val rgbWriteFailed = "发送失败，点击重试"
    override val rgbNotSupported = "该设备不支持 RGB"
    override val rgbSyncing = "正在读取设备当前灯效…"

    override val serviceChannelName = "散热器服务"
    override val serviceChannelDesc = "散热器自动模式与状态通知"
    override val serviceAutoOn = "自动模式：开"
    override val serviceSwitchManual = "切回手动"
    override val serviceReconnect = "重新连接"
    override val serviceWaitingConfig = "等待配置…"
    override val serviceManual = "自动模式：关"
    override val serviceControlFailed = "未能确认温控命令已发送，请检查连接后重试。"
    override val serviceUnavailable = "无法启动自动模式，请打开应用检查蓝牙权限并连接支持智能温控的设备。"
}

object EnStrings : AppStrings {
    override val langIsZh = false
    override val appName = "ColdFront"
    override val cancel = "Cancel"
    override val delete = "Delete"
    override val back = "Back"
    override val retry = "Retry"
    override val close = "Close"

    override val navHome = "Home"
    override val navDevices = "Devices"
    override val navRgb = "RGB"
    override val navSettings = "Settings"

    override val homeCoolingSwitch = "Cooling switch"
    override val homeCoolingSwitchDesc = "Off stops the fan completely"
    override val telemetryDegradedTitle = "Telemetry updates interrupted"
    override val telemetryDegradedHint = "Some readings have stopped updating and may be out of date. Reconnect to restore updates."
    override val homePowerLimitedTitle = "Charger power is insufficient"
    override val homePowerLimitedHint = "The cooler capped the cooling level and turned Boost off. Use a higher-wattage charger or port."
    override val homePowerLimitedConfirm = "Got it"
    override val homeSmart = "Smart temp control"
    override val homeSmartDesc = "The cooler adjusts its level by itself"
    override val homeBoost = "Boost (Destruction God)"
    override val homeBoostDesc = "Overclocking mode (higher power draw)"
    override val homeOvercold = "Over-cold protection"
    override val homeOvercoldDesc = "Anti-condensation: auto throttling at low temps"
    override val homeLevel = "Cooling level"
    override val homeLevelGear = "Level %d"
    override val homeLevelPercent = "%d%%"
    override val homeSmartActiveLevel = "Smart control active - level managed by device"

    override val devicesTitle = "My devices"
    override val devicesEmpty = "No saved devices yet"
    override val devicesEmptyHint = "Tap the button below to scan and add a cooler"
    override val devicesAdd = "Add device"
    override val devicesConnect = "Connect"
    override val devicesConnected = "Connected"
    override val devicesDelete = "Delete"
    override val devicesLastSeen = "Last used: %s"

    override val settingsTitle = "Settings"
    override val settingsTheme = "Theme & colors"
    override val settingsDynamicColor = "Dynamic color"
    override val settingsDynamicColorDesc = "Follow system wallpaper"
    override val settingsThemeMode = "Theme mode"
    override val settingsPalette = "Palette"
    override val settingsPaletteTonalSpot = "Tonal Spot"
    override val settingsPaletteNeutral = "Neutral"
    override val settingsPaletteVibrant = "Vibrant"
    override val settingsPaletteExpressive = "Expressive"
    override val settingsPaletteRainbow = "Rainbow"
    override val settingsPaletteFruitSalad = "Fruit Salad"
    override val settingsPaletteMonochrome = "Monochrome"
    override val settingsPaletteFidelity = "Fidelity"
    override val settingsPaletteContent = "Content"
    override val settingsInterface = "Interface"
    override val settingsPredictiveBack = "Predictive back"
    override val settingsPredictiveBackDesc = "Enable system back transitions for app navigation"
    override val settingsConnection = "Connection"
    override val settingsDefaultDevice = "Default device"
    override val settingsDefaultDeviceDesc = "Connects automatically when the app starts"
    override val settingsDefaultDeviceOff = "Off"
    override val settingsFollowSystem = "Follow system"
    override val settingsDarkModeLight = "Light"
    override val settingsDarkModeDark = "Dark"
    override val settingsLanguage = "Language"
    override val settingsAbout = "About"
    override val settingsAboutReport = "Report an issue"
    override val settingsAboutProject = "Project homepage"
    override val aboutOpenLinkFailed = "No app is available to open this link."
    override val storageOperationFailed = "Couldn't read or save settings. Please try again."

    override val homeTitle = "Cooler Control"
    override val homeNoDeviceHint = "Tap below to scan and add a Redmagic cooler"
    override val homeConnecting = "Connecting…"
    override val homeTemp = "Temperature"
    override val homeConnected = "Connected"
    override val homeConnectionFailed = "Connection failed"
    override val homeRetryHint = "Make sure the cooler is powered on and nearby, then retry"
    override val homeGoScan = "Scan for devices"
    override val homeNotConnected = "Not connected"

    override val scanTitle = "Scan for cooler"
    override val scanScanning = "Scanning…"
    override val scanNoFound = "No Redmagic cooler found"
    override val scanNoFoundHint = "Make sure the cooler is powered on and near the phone"
    override val scanRescan = "Rescan"
    override val scanSelect = "Connect"
    override val scanMatchedByName = "by name"
    override val scanBluetoothOff = "Bluetooth is off"
    override val scanBluetoothOffHint = "Enable Bluetooth to scan for devices"
    override val scanEnableBluetooth = "Enable Bluetooth"

    override val diagToggle = "Show all devices (diagnostics)"
    override val diagHint = "No identification filtering — shows every raw BLE advertisement around you: MSD/UUID. Read what your cooler actually broadcasts, or connect by manually picking its model."
    override val diagNoName = "(unnamed)"
    override val diagNoSignal = "Not a single advertisement received"
    override val diagNoSignalHint = "Check: ① cooler is powered (plugged in / magnetically attached with LED on) ② system Location service is ON (scanning depends on it on older Android) ③ Bluetooth is ON ④ stay close"
    override val diagMsd = "Manufacturer data"
    override val diagServiceData = "Service data"
    override val diagServiceUuids = "Service UUIDs"
    override val diagMoreCount = "+%d more"
    override val diagConnectAs = "Connect as model…"
    override val diagCancel = "Cancel"
    override val diagRawCount = "%d devices total"

    override val diagStatusPermission = "Scan permission"
    override val diagStatusScanner = "Scanner"
    override val diagStatusLocation = "Location service"
    override val diagStatusBluetooth = "Bluetooth"
    override val diagPermissionGranted = "granted"
    override val diagPermissionMissing = "missing"
    override val diagScannerRunning = "running"
    override val diagScannerStopped = "stopped"
    override val diagScannerFailed = "failed (code %d)"
    override val diagServiceOn = "on"
    override val diagServiceOff = "off"
    override val diagGrantAgain = "Grant again"
    override val diagOpenAppSettings = "App settings"
    override val diagOpenLocation = "Enable location"
    override val diagCoolerBadge = "Identified: %s"

    override val rgbTitle = "RGB Lighting"
    override val rgbEffect = "Effect"
    override val rgbBreathMode = "Breath color"
    override val rgbBreathSingle = "Single color"
    override val rgbBreathFull = "Full color"
    override val rgbRed = "R"
    override val rgbGreen = "G"
    override val rgbBlue = "B"
    override val rgbConnectFirst = "Connect your cooler to control lighting"
    override val rgbGoConnect = "Connect"
    override val rgbPalette = "Preset colors"
    override val rgbCustomColor = "Custom color"
    override val rgbApply = "Apply"
    override val rgbWriting = "Sending…"
    override val rgbSent = "Sent to cooler"
    override val rgbWriteFailed = "Send failed — tap to retry"
    override val rgbNotSupported = "This device does not support RGB"
    override val rgbSyncing = "Reading the cooler's current lighting…"

    override val serviceChannelName = "Cooler service"
    override val serviceChannelDesc = "Cooler auto-mode and status notifications"
    override val serviceAutoOn = "Auto mode: on"
    override val serviceSwitchManual = "Switch to manual"
    override val serviceReconnect = "Reconnect"
    override val serviceWaitingConfig = "Waiting for config…"
    override val serviceManual = "Auto mode: off"
    override val serviceControlFailed = "Couldn't confirm the smart-control command. Check the connection and retry."
    override val serviceUnavailable = "Can't start auto mode. Open the app to check Bluetooth permissions and connect a cooler with smart control."
}

/**
 * 当前生效的字符串表。
 *
 * @param override 用户在设置里选的语言："zh" / "en" 强制，其余（包括 "system"）跟随系统 locale。
 * 工程文案全在这张 Kotlin 表里、没有 strings.xml，所以覆盖语言不需要 per-app locale，
 * 换表即可。（副作用：系统级文案如权限弹框仍跟随系统语言。）
 */
fun stringsFor(locale: Locale, override: String? = null): AppStrings = when (override) {
    "zh" -> ZhStrings
    "en" -> EnStrings
    else -> if (locale.language.equals("zh", ignoreCase = true)) ZhStrings else EnStrings
}

val LocalStrings = staticCompositionLocalOf<AppStrings> { error("AppStrings not provided") }

@Composable
fun rememberStrings(override: String? = null): AppStrings =
    stringsFor(LocalConfiguration.current.locales[0] ?: Locale.ROOT, override)
