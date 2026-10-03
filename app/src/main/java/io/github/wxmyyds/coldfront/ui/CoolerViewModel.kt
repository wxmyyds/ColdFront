package io.github.wxmyyds.coldfront.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import io.github.wxmyyds.coldfront.ble.BackgroundResume
import io.github.wxmyyds.coldfront.ble.BleManagerHolder
import io.github.wxmyyds.coldfront.ble.BleScanDiagnostic
import io.github.wxmyyds.coldfront.data.ProfileRepository
import io.github.wxmyyds.coldfront.data.SettingsRepository
import io.github.wxmyyds.coldfront.domain.CoolerDevice
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import io.github.wxmyyds.coldfront.domain.RGBConfig
import io.github.wxmyyds.coldfront.ui.theme.PaletteStyles
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** UI state bridge. Transport lifetime and successful-connection bookkeeping have explicit owners. */
class CoolerViewModel(app: Application) : AndroidViewModel(app) {
    private val ble = BleManagerHolder.acquire(app, this)
    private val profileRepo = ProfileRepository(app)
    private val settingsRepo = SettingsRepository(app)
    private val storageErrors = Channel<Unit>(Channel.CONFLATED)
    private val profileMutations = Mutex()
    private val ignoredSessions = mutableSetOf<Long>()
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
    private fun <T> Flow<T>.uiState(initial: T): StateFlow<T> = retryWhen { cause, attempt ->
        if (cause !is IOException) return@retryWhen false
        if (attempt == 0L) reportStorageError(cause)
        delay(2_000)
        true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    private val _profilesLoaded = MutableStateFlow(false)
    val profilesLoaded: StateFlow<Boolean> = _profilesLoaded.asStateFlow()
    val profiles = profileRepo.profiles
        .onEach { _profilesLoaded.value = true }
        .uiState(emptyList())
    private val backgroundResume = BackgroundResume(
        lifecycle = ProcessLifecycleOwner.get().lifecycle,
        scope = viewModelScope,
        store = ble,
        hasRunningSession = ble::hasRunningSession,
        connect = ble::connectByAddress,
    )
    // 首帧不渲染，等 DataStore 首个快照全部读完再置位：dynamicColor / darkMode / palette /
    // appLanguage 都存在同一个 store 里，只等其中一项仍会先画出默认深浅模式 + 品牌色的错帧。
    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded.asStateFlow()
    val dynamicColor = settingsRepo.dynamicColor.uiState(false)
    val darkMode = settingsRepo.darkMode.uiState("system")
    val palette = settingsRepo.palette.uiState(PaletteStyles.DEFAULT)
    val predictiveBack = settingsRepo.predictiveBack.uiState(true)
    val appLanguage = settingsRepo.appLanguage.uiState("system")

    init {
        // Any visible setting emitting means the shared DataStore snapshot arrived, so the
        // theme can be painted with final values instead of the brand fallback.
        viewModelScope.launch {
            merge(dynamicColor, darkMode, palette, appLanguage, predictiveBack).first()
            _settingsLoaded.value = true
        }
        backgroundResume.attach()
        viewModelScope.launch {
            liveState.filter { it.isConnected }
                .distinctUntilChangedBy { it.connectionSessionId }
                .collect { state ->
                    // All connection entry points share this commit-on-success path. No profile
                    // is created by a failed attempt, and metadata is retained on reconnect.
                    try {
                        profileMutations.withLock {
                            if (state.connectionSessionId !in ignoredSessions) profileRepo.recordConnection(state)
                        }
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

    fun refreshBluetoothState() {
        _bluetoothEnabled.value = ble.isBluetoothEnabled
        ble.refreshScanConditions()
    }

    fun startScan() = ble.startScan()
    fun stopScan() = ble.stopScan()
    fun connectRaw(entry: BleScanDiagnostic, type: CoolerDeviceType) = ble.connectRaw(entry, type)
    fun connect(device: CoolerDevice) = ble.connect(device)
    fun connectProfile(profile: CoolerProfile) = ble.connectByAddress(profile.macAddress, profile.deviceType)
    fun setFanSpeed(percent: Int) = ble.setFanSpeed(percent)
    fun setCooling(on: Boolean) = ble.setCooling(on)
    fun setSmart(on: Boolean) {
        if (on) {
            ble.setSmart(true)
            return
        }
        val requested = liveState.value
        saveSetting {
            val target = profileRepo.loadServiceProfile()
            if (target != null && target.macAddress.equals(requested.deviceAddress, ignoreCase = true)) {
                // Cancel background recovery before turning this same device OFF.
                io.github.wxmyyds.coldfront.service.CoolerService.start(
                    getApplication(), android.content.Intent(getApplication(), io.github.wxmyyds.coldfront.service.CoolerService::class.java).apply {
                        action = io.github.wxmyyds.coldfront.service.CoolerService.ACTION_SWITCH_TO_MANUAL
                        putExtra(io.github.wxmyyds.coldfront.service.CoolerService.EXTRA_CONTROL_ADDRESS, requested.deviceAddress)
                    },
                )
            } else if (liveState.value.connectionSessionId == requested.connectionSessionId) {
                ble.setSmart(false)
            }
        }
    }
    fun setBoost(on: Boolean) = ble.setBoost(on)
    fun setOvercoldProtection(on: Boolean) = ble.setOvercoldProtection(on)
    fun setRGB(config: RGBConfig) = ble.setRGB(config)
    fun deleteProfile(id: String) = saveSetting {
        profileMutations.withLock {
            val removed = profiles.value.firstOrNull { it.id == id }
            val state = liveState.value
            profileRepo.delete(id)
            if (removed != null && removed.macAddress.equals(state.deviceAddress, ignoreCase = true)) {
                ignoredSessions.add(state.connectionSessionId)
                if (!state.isConnected && liveState.value.connectionSessionId == state.connectionSessionId) {
                    ble.disconnect()
                }
            }
        }
    }

    override fun onCleared() {
        backgroundResume.detach()
        BleManagerHolder.release(this)
        storageErrors.close()
    }
}
