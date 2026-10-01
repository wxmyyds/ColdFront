package io.github.wxmyyds.coldfront.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.wxmyyds.coldfront.ble.BleManagerHolder
import io.github.wxmyyds.coldfront.data.ProfileRepository
import io.github.wxmyyds.coldfront.data.SettingsRepository
import io.github.wxmyyds.coldfront.data.ThermalThresholds
import io.github.wxmyyds.coldfront.domain.CoolerDevice
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.ble.BleScanDiagnostic
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import io.github.wxmyyds.coldfront.domain.FanMode
import io.github.wxmyyds.coldfront.domain.RGBConfig
import io.github.wxmyyds.coldfront.service.CoolerService
import io.github.wxmyyds.coldfront.thermal.ThermalMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI 与业务层的桥梁：持有 [CoolerBleManager]（UI 连接）与各仓库，
 * 暴露状态并转发控制操作。
 */
class CoolerViewModel(app: Application) : AndroidViewModel(app) {

    private val ble = BleManagerHolder.get(app)
    private val profileRepo = ProfileRepository(app)
    private val settingsRepo = SettingsRepository(app)
    @Suppress("unused")
    private val thermal = ThermalMonitor(app)

    val liveState: StateFlow<io.github.wxmyyds.coldfront.domain.CoolerLiveState> = ble.state
    val rgbWriteState = ble.rgbWriteState

    val discoveredDevices: StateFlow<List<CoolerDevice>> = ble.discoveredDevices

    /** 诊断模式:全部未过滤扫描结果 */
    val rawDevices: StateFlow<List<BleScanDiagnostic>> = ble.rawDevices

    /** 扫描器实时状态(权限/定位/蓝牙/失败码) */
    val scanState: StateFlow<io.github.wxmyyds.coldfront.ble.ScanState> = ble.scanState

    val profiles: StateFlow<List<CoolerProfile>> =
        profileRepo.profiles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val thresholds: StateFlow<ThermalThresholds> =
        settingsRepo.thresholds.stateIn(viewModelScope, SharingStarted.Eagerly, ThermalThresholds())

    // —— 主题设置 ——
    val dynamicColor: StateFlow<Boolean> =
        settingsRepo.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val darkMode: StateFlow<String> =
        settingsRepo.darkMode.stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    /** 界面语言：system / zh / en */
    val appLanguage: StateFlow<String> =
        settingsRepo.appLanguage.stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    fun setDynamicColor(enabled: Boolean) =
        viewModelScope.launch { settingsRepo.setDynamicColor(enabled) }

    fun setDarkMode(mode: String) =
        viewModelScope.launch { settingsRepo.setDarkMode(mode) }

    fun setAppLanguage(language: String) =
        viewModelScope.launch { settingsRepo.setAppLanguage(language) }

    private val _bluetoothEnabled = MutableStateFlow(ble.isBluetoothEnabled)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    /** 由 Activity 在 onResume / 权限回调 / 收到广播时调用刷新 */
    fun refreshBluetoothState() {
        _bluetoothEnabled.value = ble.isBluetoothEnabled
        ble.refreshScanConditions()
    }

    fun startScan() = ble.startScan()
    fun stopScan() = ble.stopScan()

    /** 诊断模式:手动指定型号连接 */
    fun connectRaw(entry: BleScanDiagnostic, type: CoolerDeviceType) = ble.connectRaw(entry, type)

    fun connect(device: CoolerDevice) {
        viewModelScope.launch {
            // 连上后落档案
            val profile = CoolerProfile.fromDevice(device)
            profileRepo.upsert(profile)
            profileRepo.setActive(profile.id)
        }
        ble.connect(device)
    }

    fun disconnect() = ble.disconnect()

    fun setFanSpeed(percent: Int) = ble.setFanSpeed(percent)
    fun setFanMode(mode: FanMode) = ble.setFanMode(mode)

    // —— 独立控制:散热开关/智能温控/破坏神/过冷保护 ——
    fun setCooling(on: Boolean) = ble.setCooling(on)
    fun setSmart(on: Boolean) = ble.setSmart(on)
    fun setBoost(on: Boolean) = ble.setBoost(on)
    fun setOvercoldProtection(on: Boolean) = ble.setOvercoldProtection(on)

    fun setRGB(config: RGBConfig) = ble.setRGB(config)

    fun deleteProfile(id: String) = viewModelScope.launch { profileRepo.delete(id) }
    fun setActiveProfile(id: String?) = viewModelScope.launch { profileRepo.setActive(id) }

    fun saveProfile(profile: CoolerProfile) = viewModelScope.launch { profileRepo.upsert(profile) }

    /** 启动智能温控常驻通知(共享连接,不断开) */
    fun startAutoMode(profile: CoolerProfile) {
        viewModelScope.launch { profileRepo.setActive(profile.id) }
        val intent = Intent(getApplication(), CoolerService::class.java).apply {
            action = CoolerService.ACTION_START_AUTO
            putExtra(CoolerService.EXTRA_PROFILE_ID, profile.id)
            putExtra(CoolerService.EXTRA_DEVICE_TYPE, profile.deviceType.name)
            putExtra(CoolerService.EXTRA_DEVICE_MAC, profile.macAddress)
            putExtra(CoolerService.EXTRA_DEVICE_NAME, profile.name)
        }
        val ctx = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    fun stopAutoMode() {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, CoolerService::class.java).apply {
            action = CoolerService.ACTION_STOP
        })
    }

    /** 从已保存设备直连 */
    fun connectProfile(profile: CoolerProfile) {
        ble.connectByAddress(profile.macAddress, profile.deviceType)
    }

    fun updateThresholds(thresholds: ThermalThresholds) =
        viewModelScope.launch { settingsRepo.update(thresholds) }

    override fun onCleared() {
        ble.release()
        super.onCleared()
    }
}
