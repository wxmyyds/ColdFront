package io.github.wxmyyds.coldfront.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import io.github.wxmyyds.coldfront.ble.BackgroundResume
import io.github.wxmyyds.coldfront.ble.BleManagerHolder
import io.github.wxmyyds.coldfront.ble.BleScanDiagnostic
import io.github.wxmyyds.coldfront.data.AppSettings
import io.github.wxmyyds.coldfront.data.ProfileRepository
import io.github.wxmyyds.coldfront.data.SettingsRepository
import io.github.wxmyyds.coldfront.domain.CoolerDevice
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import io.github.wxmyyds.coldfront.domain.RGBConfig
import io.github.wxmyyds.coldfront.service.CoolerService
import io.github.wxmyyds.coldfront.service.ManualControlTarget
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** UI state bridge. Transport lifetime and successful-connection bookkeeping have explicit owners. */
class CoolerViewModel(app: Application) : AndroidViewModel(app) {
    private val ble = BleManagerHolder.acquire(app, this)
    private val profileRepo = ProfileRepository(app)
    private val settingsRepo = SettingsRepository(app)
    private val storageErrors = Channel<Unit>(Channel.CONFLATED)
    private val profileMutations = ProfileMutationCoordinator(
        scope = viewModelScope,
        record = { profileRepo.recordConnection(it) },
        delete = profileRepo::delete,
    )
    val errors: Flow<Unit> = storageErrors.receiveAsFlow()

    val liveState = ble.state
    val rgbWriteState = ble.rgbWriteState
    val discoveredDevices = ble.discoveredDevices
    val rawDevices = ble.rawDevices
    val scanState = ble.scanState
    private val _bluetoothEnabled = MutableStateFlow(ble.isBluetoothEnabled)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    // A transient read failure keeps the last good value and retries, rather than replacing
    // persisted data with defaults or permanently killing an eagerly collected StateFlow.
    private fun <T> Flow<T>.uiState(initial: T): StateFlow<T> =
        storageStateIn(viewModelScope, initial, ::reportStorageError)

    private val _profilesLoaded = MutableStateFlow(false)
    val profilesLoaded: StateFlow<Boolean> = _profilesLoaded.asStateFlow()
    val profiles = profileRepo.observeProfiles(::reportStorageError)
        .onEach { _profilesLoaded.value = true }
        .uiState(emptyList())
    // null is a real value here ("no startup default"), so readiness is reported separately.
    private val _defaultDeviceLoaded = MutableStateFlow(false)
    val defaultDeviceLoaded: StateFlow<Boolean> = _defaultDeviceLoaded.asStateFlow()
    val defaultDevice: StateFlow<CoolerProfile?> = profileRepo.observeDefaultProfile(::reportStorageError)
        .onEach { _defaultDeviceLoaded.value = true }
        .uiState<CoolerProfile?>(null)
    private val backgroundResume = BackgroundResume(
        lifecycle = ProcessLifecycleOwner.get().lifecycle,
        scope = viewModelScope,
        store = ble,
        hasRunningSession = ble::hasRunningSession,
        connect = ble::connectByAddress,
    )
    // null means no successful disk read yet. Readiness and all settings move atomically.
    val settings: StateFlow<AppSettings?> = settingsRepo.settings.uiState<AppSettings?>(null)

    init {
        backgroundResume.attach()
        viewModelScope.launch {
            // App start dials the configured default device once. A default can only be chosen
            // after a successful connection, so permissions and the adapter were already usable
            // then; a missing one here is reported like any other failed attempt, without retries.
            val profile = try {
                profileRepo.loadDefaultProfile()
            } catch (e: IOException) {
                reportStorageError(e)
                return@launch
            }
            val target = startupConnectTarget(profile, ble.hasRunningSession()) ?: return@launch
            ble.connectByAddress(target.macAddress, target.deviceType)
        }
        viewModelScope.launch {
            liveState.filter { it.isConnected }
                .distinctUntilChangedBy { it.connectionSessionId }
                .collect { state ->
                    // All connection entry points share this commit-on-success path. No profile
                    // is created by a failed attempt, and metadata is retained on reconnect.
                    try {
                        profileMutations.recordConnection(state)
                    } catch (e: IOException) {
                        reportStorageError(e)
                    }
                }
        }
    }

    private fun reportStorageError(error: IOException) {
        Log.e("CoolerViewModel", "Storage operation failed", error)
        storageErrors.trySend(Unit)
    }

    private fun saveSetting(action: suspend () -> Unit) = viewModelScope.launch {
        try {
            action()
        } catch (e: IOException) {
            reportStorageError(e)
        }
    }

    fun setDynamicColor(enabled: Boolean) = saveSetting { settingsRepo.setDynamicColor(enabled) }
    fun setDarkMode(mode: String) = saveSetting { settingsRepo.setDarkMode(mode) }
    fun setPalette(palette: String) = saveSetting { settingsRepo.setPalette(palette) }
    fun setPredictiveBack(enabled: Boolean) = saveSetting { settingsRepo.setPredictiveBack(enabled) }
    fun setAppLanguage(language: String) = saveSetting { settingsRepo.setAppLanguage(language) }
    fun setDefaultDevice(profileId: String?) = saveSetting { profileRepo.setDefaultProfile(profileId) }

    fun refreshBluetoothState() {
        _bluetoothEnabled.value = ble.isBluetoothEnabled
        ble.refreshScanConditions()
    }

    fun startScan() = ble.startScan()
    fun stopScan() = ble.stopScan()
    fun connectRaw(entry: BleScanDiagnostic, type: CoolerDeviceType) = ble.connectRaw(entry, type)
    fun connect(device: CoolerDevice) = ble.connect(device)
    fun connectProfile(profile: CoolerProfile) = ble.connectByAddress(profile.macAddress, profile.deviceType)
    fun reconnectTelemetry(requested: CoolerLiveState) {
        val target = telemetryReconnectTarget(requested, liveState.value) ?: return
        ble.connectByAddress(target.address, target.type)
    }
    fun setFanSpeed(percent: Int) = ble.setFanSpeed(percent)
    fun setCooling(on: Boolean) = ble.setCooling(on)
    fun setSmart(on: Boolean) {
        if (on) {
            ble.setSmart(true)
            return
        }
        stopAuto(switchToManual = true)
    }

    /** Cancel same-device background recovery; BLE owns the mutually exclusive mode writes. */
    private fun stopAuto(switchToManual: Boolean) {
        val requested = ManualControlTarget.from(liveState.value) ?: return
        saveSetting {
            val target = profileRepo.loadServiceProfile()
            if (!requested.matches(liveState.value)) return@saveSetting
            if (target != null && target.macAddress.equals(requested.address, ignoreCase = true)) {
                // Service owns background opt-out and validates this session again on delivery.
                if (switchToManual) CoolerService.switchToManual(getApplication(), requested)
                else CoolerService.stopForTarget(getApplication(), requested)
            } else if (switchToManual) {
                ble.setSmart(false)
            }
        }
    }

    fun setBoost(on: Boolean) {
        if (on && !liveState.value.powerLimited) stopAuto(switchToManual = false)
        // Do not enqueue a delayed Smart OFF here: it could supersede the Boost transition.
        ble.setBoost(on)
    }
    fun setOvercoldProtection(on: Boolean) = ble.setOvercoldProtection(on)
    fun setRGB(config: RGBConfig) = ble.setRGB(config)
    fun deleteProfile(id: String) = saveSetting {
        val deletion = profileMutations.deleteProfile(id) { liveState.value }
        val removed = deletion.profile
        val state = deletion.state
        if (removed != null && removed.macAddress.equals(state.deviceAddress, ignoreCase = true)) {
            if (!state.isConnected && liveState.value.connectionSessionId == state.connectionSessionId) {
                ble.disconnect()
            }
        }
    }

    override fun onCleared() {
        profileMutations.close()
        backgroundResume.detach()
        BleManagerHolder.release(this)
        storageErrors.close()
    }
}
