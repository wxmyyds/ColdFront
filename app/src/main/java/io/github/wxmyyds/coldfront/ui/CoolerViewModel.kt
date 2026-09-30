package io.github.wxmyyds.coldfront.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.wxmyyds.coldfront.ble.CoolerBleManager
import io.github.wxmyyds.coldfront.data.ProfileRepository
import io.github.wxmyyds.coldfront.data.SettingsRepository
import io.github.wxmyyds.coldfront.data.ThermalThresholds
import io.github.wxmyyds.coldfront.domain.CoolerDevice
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

    private val ble = CoolerBleManager(app)
    private val profileRepo = ProfileRepository(app)
    private val settingsRepo = SettingsRepository(app)
    @Suppress("unused")
    private val thermal = ThermalMonitor(app)

    val liveState: StateFlow<io.github.wxmyyds.coldfront.domain.CoolerLiveState> = ble.state

    val discoveredDevices: StateFlow<List<CoolerDevice>> = ble.discoveredDevices

    /** 诊断模式:全部未过滤扫描结果 */
    val rawDevices: StateFlow<List<BleScanDiagnostic>> = ble.rawDevices

    val profiles: StateFlow<List<CoolerProfile>> =
        profileRepo.profiles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val thresholds: StateFlow<ThermalThresholds> =
        settingsRepo.thresholds.stateIn(viewModelScope, SharingStarted.Eagerly, ThermalThresholds())

    private val _bluetoothEnabled = MutableStateFlow(ble.isBluetoothEnabled)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    /** 由 Activity 在 onResume / 收到广播时调用刷新 */
    fun refreshBluetoothState() {
        _bluetoothEnabled.value = ble.isBluetoothEnabled
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

    fun setRGB(config: RGBConfig) {
        // 自动模式运行时由服务端写入；否则用 UI 连接写入
        val svc = CoolerService.getInstance()
        if (svc != null) svc.setRGB(config) else ble.setRGB(config)
    }

    fun deleteProfile(id: String) = viewModelScope.launch { profileRepo.delete(id) }
    fun setActiveProfile(id: String?) = viewModelScope.launch { profileRepo.setActive(id) }

    fun saveProfile(profile: CoolerProfile) = viewModelScope.launch { profileRepo.upsert(profile) }

    /** 启动自动模式：UI 让出连接，由前台服务接管 */
    fun startAutoMode(profile: CoolerProfile) {
        ble.disconnect()
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
        viewModelScope.launch { profileRepo.setActive(null) }
    }

    /** 自动模式停止后，UI 重新接管连接 */
    fun reconnectProfile(profile: CoolerProfile) {
        ble.connectByAddress(profile.macAddress, profile.deviceType)
    }

    fun updateThresholds(thresholds: ThermalThresholds) =
        viewModelScope.launch { settingsRepo.update(thresholds) }

    override fun onCleared() {
        ble.release()
        super.onCleared()
    }
}
