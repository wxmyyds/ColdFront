package io.github.wxmyyds.coldfront.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
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
    val ok: String
    val cancel: String
    val save: String
    val delete: String
    val edit: String
    val back: String
    val retry: String
    val close: String
    val next: String
    val done: String
    val loading: String
    val error: String
    val settings: String

    // —— 导航 ——
    val navHome: String
    val navDevices: String
    val navRgb: String
    val navSettings: String

    // —— 首页:独立控制 ——
    val homeCoolingSwitch: String
    val homeCoolingSwitchDesc: String
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
    val settingsDarkModeSystem: String
    val settingsDarkModeLight: String
    val settingsDarkModeDark: String
    val settingsAbout: String
    val settingsAboutDesc: String

    // —— 首页 ——
    val homeTitle: String
    val homeNoDevice: String
    val homeNoDeviceHint: String
    val homeAddDevice: String
    val homeConnecting: String
    val homeTemp: String
    val homeModeManual: String
    val homeAutoRunning: String
    val homeConnected: String
    val homeDisconnected: String
    val homeConnectionFailed: String
    val homeRetryHint: String
    val homeGoScan: String
    val homeNotConnected: String
    val homeStartService: String
    val homeStopService: String

    // —— 扫描/添加设备 ——
    val scanTitle: String
    val scanScanning: String
    val scanNoFound: String
    val scanNoFoundHint: String
    val scanRescan: String
    val scanSelect: String
    val scanMatchedByName: String
    val scanUuidUnconfirmed: String
    val scanBluetoothOff: String
    val scanBluetoothOffHint: String
    val scanEnableBluetooth: String
    val scanPermissionNeeded: String
    val scanGrantPermission: String

    // —— 诊断模式 ——
    val diagToggle: String
    val diagHint: String
    val diagNoName: String
    val diagNoSignal: String
    val diagNoSignalHint: String
    val diagMsd: String
    val diagServiceData: String
    val diagServiceUuids: String
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
    val rgbColor: String
    val rgbRed: String
    val rgbGreen: String
    val rgbBlue: String
    val rgbPreview: String
    val rgbApply: String
    val rgbSynced: String
    val rgbConnectFirst: String
    val rgbGoConnect: String
    val rgbPalette: String
    val rgbCustomColor: String
    val rgbOff: String
    val rgbNotSupported: String

    // —— 档案 ——
    val profileTitle: String
    val profileName: String
    val profileNameHint: String
    val profileDeviceType: String
    val profileMac: String
    val profileFanSpeed: String
    val profileMode: String
    val profileCreatedAt: String
    val profileLastConnected: String
    val profileDeleteConfirm: String
    val profileEmpty: String
    val profileEmptyHint: String
    val profileNew: String

    // —— 自动模式/服务 ——
    val serviceChannelName: String
    val serviceChannelDesc: String
    val serviceNotificationTitle: String
    val serviceAutoOn: String
    val serviceAutoOff: String
    val serviceSwitchManual: String
    val serviceReconnect: String
    val serviceWaitingConfig: String
    val serviceTempLow: String
    val serviceTempMid: String
    val serviceTempHigh: String

    // —— 温控阈值 ——
    val thresholdTitle: String
    val thresholdLow: String
    val thresholdLowSpeed: String
    val thresholdMid: String
    val thresholdMidSpeed: String
    val thresholdHigh: String
    val thresholdHighSpeed: String
    val thresholdCondensationGuard: String

    // —— 权限 ——
    val permBluetoothTitle: String
    val permBluetoothMsg: String
    val permLocationTitle: String
    val permLocationMsg: String
    val permNotificationTitle: String
    val permNotificationMsg: String
}

object ZhStrings : AppStrings {
    override val langIsZh = true
    override val appName = "ColdFront"
    override val ok = "确定"
    override val cancel = "取消"
    override val save = "保存"
    override val delete = "删除"
    override val edit = "编辑"
    override val back = "返回"
    override val retry = "重试"
    override val close = "关闭"
    override val next = "下一步"
    override val done = "完成"
    override val loading = "加载中…"
    override val error = "出错了"
    override val settings = "设置"

    override val navHome = "首页"
    override val navDevices = "设备"
    override val navRgb = "灯效"
    override val navSettings = "设置"

    override val homeCoolingSwitch = "散热开关"
    override val homeCoolingSwitchDesc = "关闭后风扇全停"
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
    override val settingsDynamicColorDesc = "跟随系统壁纸配色(Material You)"
    override val settingsThemeMode = "主题模式"
    override val settingsDarkModeSystem = "跟随系统"
    override val settingsDarkModeLight = "浅色"
    override val settingsDarkModeDark = "深色"
    override val settingsAbout = "关于"
    override val settingsAboutDesc = "ColdFront · 红魔散热器控制 · v1.0"

    override val homeTitle = "散热控制"
    override val homeNoDevice = "尚未连接散热器"
    override val homeNoDeviceHint = "点击下方按钮扫描并添加红魔散热器"
    override val homeAddDevice = "添加设备"
    override val homeConnecting = "连接中…"
    override val homeTemp = "温度"
    override val homeModeManual = "手动"
    override val homeAutoRunning = "自动模式运行中"
    override val homeConnected = "已连接"
    override val homeDisconnected = "已断开"
    override val homeConnectionFailed = "连接失败"
    override val homeRetryHint = "请确认散热器已通电并靠近后重试"
    override val homeGoScan = "去扫描设备"
    override val homeNotConnected = "未连接散热器"
    override val homeStartService = "开启自动模式"
    override val homeStopService = "停止自动模式"

    override val scanTitle = "扫描散热器"
    override val scanScanning = "正在扫描…"
    override val scanNoFound = "未发现红魔散热器"
    override val scanNoFoundHint = "请确认散热器已通电并靠近手机"
    override val scanRescan = "重新扫描"
    override val scanSelect = "连接"
    override val scanMatchedByName = "名称识别"
    override val scanUuidUnconfirmed = "UUID 待确认"
    override val scanBluetoothOff = "蓝牙未开启"
    override val scanBluetoothOffHint = "请先开启蓝牙以扫描设备"
    override val scanEnableBluetooth = "开启蓝牙"
    override val scanPermissionNeeded = "需要权限"
    override val scanGrantPermission = "授予权限"

    override val diagToggle = "显示全部设备（诊断）"
    override val diagHint = "不过滤识别，显示周围全部 BLE 原始广播：MSD/UUID。散热器广播的内容可直接读出，也可手动选型号连接。"
    override val diagNoName = "（无名设备）"
    override val diagNoSignal = "一个广播都没收到"
    override val diagNoSignalHint = "检查：①散热器已通电（插电/磁吸亮灯） ②系统定位服务已开启（旧系统扫描依赖定位） ③蓝牙已开启 ④距离足够近"
    override val diagMsd = "厂商数据"
    override val diagServiceData = "服务数据"
    override val diagServiceUuids = "服务 UUID"
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
    override val rgbColor = "颜色"
    override val rgbRed = "红"
    override val rgbGreen = "绿"
    override val rgbBlue = "蓝"
    override val rgbPreview = "预览"
    override val rgbSynced = "已同步到设备"
    override val rgbConnectFirst = "连接散热器后即可调节灯效"
    override val rgbGoConnect = "去连接"
    override val rgbPalette = "预设颜色"
    override val rgbCustomColor = "自定义颜色"
    override val rgbApply = "应用"
    override val rgbOff = "关闭灯光"
    override val rgbNotSupported = "该设备不支持 RGB"

    override val profileTitle = "设备档案"
    override val profileName = "名称"
    override val profileNameHint = "给这个散热器起个名字"
    override val profileDeviceType = "型号"
    override val profileMac = "MAC 地址"
    override val profileFanSpeed = "默认转速"
    override val profileMode = "默认模式"
    override val profileCreatedAt = "创建于"
    override val profileLastConnected = "上次连接"
    override val profileDeleteConfirm = "删除该档案？"
    override val profileEmpty = "还没有档案"
    override val profileEmptyHint = "连接设备后会自动创建档案"
    override val profileNew = "新建档案"

    override val serviceChannelName = "散热器服务"
    override val serviceChannelDesc = "散热器自动模式与状态通知"
    override val serviceNotificationTitle = "散热器运行中"
    override val serviceAutoOn = "自动模式：开"
    override val serviceAutoOff = "自动模式：关"
    override val serviceSwitchManual = "切回手动"
    override val serviceReconnect = "重新连接"
    override val serviceWaitingConfig = "等待配置…"
    override val serviceTempLow = "低温"
    override val serviceTempMid = "中温"
    override val serviceTempHigh = "高温"

    override val thresholdTitle = "温控阈值"
    override val thresholdLow = "低温阈值"
    override val thresholdLowSpeed = "低温转速"
    override val thresholdMid = "中温阈值"
    override val thresholdMidSpeed = "中温转速"
    override val thresholdHigh = "高温阈值"
    override val thresholdHighSpeed = "高温转速"
    override val thresholdCondensationGuard = "防凝露保护（低于此温度降速）"

    override val permBluetoothTitle = "需要蓝牙权限"
    override val permBluetoothMsg = "控制红魔散热器需要蓝牙连接权限。"
    override val permLocationTitle = "需要位置权限"
    override val permLocationMsg = "Android 11 及以下扫描蓝牙设备需要位置权限。"
    override val permNotificationTitle = "需要通知权限"
    override val permNotificationMsg = "前台服务需要通知权限以保持持续运行。"
}

object EnStrings : AppStrings {
    override val langIsZh = false
    override val appName = "ColdFront"
    override val ok = "OK"
    override val cancel = "Cancel"
    override val save = "Save"
    override val delete = "Delete"
    override val edit = "Edit"
    override val back = "Back"
    override val retry = "Retry"
    override val close = "Close"
    override val next = "Next"
    override val done = "Done"
    override val loading = "Loading…"
    override val error = "Error"
    override val settings = "Settings"

    override val navHome = "Home"
    override val navDevices = "Devices"
    override val navRgb = "RGB"
    override val navSettings = "Settings"

    override val homeCoolingSwitch = "Cooling switch"
    override val homeCoolingSwitchDesc = "Off stops the fan completely"
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
    override val settingsDynamicColorDesc = "Follow system wallpaper (Material You)"
    override val settingsThemeMode = "Theme mode"
    override val settingsDarkModeSystem = "Follow system"
    override val settingsDarkModeLight = "Light"
    override val settingsDarkModeDark = "Dark"
    override val settingsAbout = "About"
    override val settingsAboutDesc = "ColdFront · Redmagic cooler control · v1.0"

    override val homeTitle = "Cooler Control"
    override val homeNoDevice = "No cooler connected"
    override val homeNoDeviceHint = "Tap below to scan and add a Redmagic cooler"
    override val homeAddDevice = "Add device"
    override val homeConnecting = "Connecting…"
    override val homeTemp = "Temperature"
    override val homeModeManual = "Manual"
    override val homeAutoRunning = "Auto mode running"
    override val homeConnected = "Connected"
    override val homeDisconnected = "Disconnected"
    override val homeConnectionFailed = "Connection failed"
    override val homeRetryHint = "Make sure the cooler is powered on and nearby, then retry"
    override val homeGoScan = "Scan for devices"
    override val homeNotConnected = "Not connected"
    override val homeStartService = "Start auto mode"
    override val homeStopService = "Stop auto mode"

    override val scanTitle = "Scan for cooler"
    override val scanScanning = "Scanning…"
    override val scanNoFound = "No Redmagic cooler found"
    override val scanNoFoundHint = "Make sure the cooler is powered on and near the phone"
    override val scanRescan = "Rescan"
    override val scanSelect = "Connect"
    override val scanMatchedByName = "by name"
    override val scanUuidUnconfirmed = "UUID unconfirmed"
    override val scanBluetoothOff = "Bluetooth is off"
    override val scanBluetoothOffHint = "Enable Bluetooth to scan for devices"
    override val scanEnableBluetooth = "Enable Bluetooth"
    override val scanPermissionNeeded = "Permissions required"
    override val scanGrantPermission = "Grant permission"

    override val diagToggle = "Show all devices (diagnostics)"
    override val diagHint = "No identification filtering — shows every raw BLE advertisement around you: MSD/UUID. Read what your cooler actually broadcasts, or connect by manually picking its model."
    override val diagNoName = "(unnamed)"
    override val diagNoSignal = "Not a single advertisement received"
    override val diagNoSignalHint = "Check: ① cooler is powered (plugged in / magnetically attached with LED on) ② system Location service is ON (scanning depends on it on older Android) ③ Bluetooth is ON ④ stay close"
    override val diagMsd = "Manufacturer data"
    override val diagServiceData = "Service data"
    override val diagServiceUuids = "Service UUIDs"
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
    override val rgbColor = "Color"
    override val rgbRed = "R"
    override val rgbGreen = "G"
    override val rgbBlue = "B"
    override val rgbPreview = "Preview"
    override val rgbSynced = "Synced to cooler"
    override val rgbConnectFirst = "Connect your cooler to control lighting"
    override val rgbGoConnect = "Connect"
    override val rgbPalette = "Preset colors"
    override val rgbCustomColor = "Custom color"
    override val rgbApply = "Apply"
    override val rgbOff = "Lights off"
    override val rgbNotSupported = "This device does not support RGB"

    override val profileTitle = "Device profiles"
    override val profileName = "Name"
    override val profileNameHint = "Name this cooler"
    override val profileDeviceType = "Model"
    override val profileMac = "MAC address"
    override val profileFanSpeed = "Default speed"
    override val profileMode = "Default mode"
    override val profileCreatedAt = "Created"
    override val profileLastConnected = "Last connected"
    override val profileDeleteConfirm = "Delete this profile?"
    override val profileEmpty = "No profiles yet"
    override val profileEmptyHint = "A profile is created automatically when you connect a device"
    override val profileNew = "New profile"

    override val serviceChannelName = "Cooler service"
    override val serviceChannelDesc = "Cooler auto-mode and status notifications"
    override val serviceNotificationTitle = "Cooler running"
    override val serviceAutoOn = "Auto mode: on"
    override val serviceAutoOff = "Auto mode: off"
    override val serviceSwitchManual = "Switch to manual"
    override val serviceReconnect = "Reconnect"
    override val serviceWaitingConfig = "Waiting for config…"
    override val serviceTempLow = "Low"
    override val serviceTempMid = "Mid"
    override val serviceTempHigh = "High"

    override val thresholdTitle = "Thermal thresholds"
    override val thresholdLow = "Low threshold"
    override val thresholdLowSpeed = "Low speed"
    override val thresholdMid = "Mid threshold"
    override val thresholdMidSpeed = "Mid speed"
    override val thresholdHigh = "High threshold"
    override val thresholdHighSpeed = "High speed"
    override val thresholdCondensationGuard = "Anti-condensation guard (slow down below)"

    override val permBluetoothTitle = "Bluetooth permission required"
    override val permBluetoothMsg = "Bluetooth access is needed to control the Redmagic cooler."
    override val permLocationTitle = "Location permission required"
    override val permLocationMsg = "Android 11 and below require location access to scan for BLE devices."
    override val permNotificationTitle = "Notification permission required"
    override val permNotificationMsg = "Notifications are required to keep the foreground service running."
}

/** 当前 locale 对应的字符串表 */
fun stringsFor(locale: Locale): AppStrings =
    if (locale.language.equals("zh", ignoreCase = true)) ZhStrings else EnStrings

val LocalStrings = staticCompositionLocalOf<AppStrings> { error("AppStrings not provided") }

@Composable
fun rememberStrings(): AppStrings = stringsFor(Locale.getDefault())
