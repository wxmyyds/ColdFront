package io.github.wxmyyds.coldfront.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.util.size
import io.github.wxmyyds.coldfront.domain.CoolerBleConstants
import io.github.wxmyyds.coldfront.domain.CoolerCapabilities
import io.github.wxmyyds.coldfront.domain.CoolerDevice
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerTelemetryReducer
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.LightEffect
import io.github.wxmyyds.coldfront.domain.RGBConfig
import io.github.wxmyyds.coldfront.domain.RgbWriteState
import io.github.wxmyyds.coldfront.domain.RgbWriteStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "CoolerBleManager"
private val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
private const val OP_TIMEOUT_MS = 2500L
private const val READ_TIMEOUT_MS = 2500L
private const val CONNECT_TIMEOUT_MS = 15000L
private const val DISCOVERY_TIMEOUT_MS = 10000L
private const val INIT_TIMEOUT_MS = 30000L
/** 灯效未读到前的 read 重试间隔:大于 READ_TIMEOUT_MS,避免慢设备每次 read 都被超时打断 */
private const val LIGHT_READ_RETRY_MS = 4000L
/** 下发默认灯(官方未知模式兑底)的最小间隔,避免设备持续返回未知值时写入风暴 */
private const val LIGHT_DEFAULT_MIN_INTERVAL_MS = 3000L
/** 每次连接下发默认灯的次数上限 */
private const val MAX_LIGHT_DEFAULT_ATTEMPTS = 3

data class ScanState(
    val scanning: Boolean = false,
    val permissionGranted: Boolean = false,
    val locationServiceEnabled: Boolean = true,
    val bluetoothOn: Boolean = false,
    val errorCode: Int? = null,
)

/**
 * Main-confined BLE owner. Public fire-and-forget methods dispatch to Main.immediate;
 * all Android callbacks (including legacy Binder callbacks) are posted to the main Handler.
 * CONNECTED means required startup commands and supported initialization attempts have completed.
 */
@SuppressLint("MissingPermission")
class CoolerBleManager(private val context: Context) : BackgroundLinkLossStore {
    // Process-reusable: release resets transports/state, never cancels this scope.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private class Session(val id: Long) {
        var gatt: BluetoothGatt? = null
        val characteristics = mutableMapOf<UUID, BluetoothGattCharacteristic>()
        val generations = mutableMapOf<Control, Long>()
        var discoveryJob: Job? = null
        var initJob: Job? = null
        var timeoutJob: Job? = null
        var pollJob: Job? = null
        var rssiJob: Job? = null
        var discoveryRequested = false
        var initializing = false
        var controls = 0
        val configurationRead = mutableSetOf<UUID>()
        // Readable configuration characteristics to read back after connecting.
        // Best-effort: a failed or unparsed read must not block CONNECTED.
        var requiredConfiguration = emptySet<UUID>()
        var lastTempUpdateMs = 0L
        var lastLightReadMs = 0L
        var lastLightDefaultMs = 0L
        var lightDefaultAttempts = 0
    }

    private enum class Control { FAN, COOLING, SMART, BOOST, PROTECTION, RGB }
    private class ScanSession(val scanner: BluetoothLeScanner, val callback: ScanCallback)
    private var session: Session? = null
    private var scanSession: ScanSession? = null
    private var nextSessionId = SystemClock.elapsedRealtimeNanos()
    private var nextRgbRequestId = 0L
    private var pendingRgb: RgbWriteState? = null

    private val operations = GattOperationQueue(
        onTimeout = { owner ->
            session?.takeIf { it.gatt === owner }?.let { fail(it, "GATT callback timeout") }
        },
        onQuarantined = { owner ->
            // Keep isolation intact; expose the loss instead of silently showing frozen telemetry.
            session?.takeIf { it.gatt === owner && ready(it) }?.let {
                _state.update { state -> state.copy(telemetryDegraded = true) }
            }
        },
    )

    private val _state = MutableStateFlow(CoolerLiveState())
    val state: StateFlow<CoolerLiveState> = _state.asStateFlow()
    private val _rgbWriteState = MutableStateFlow<RgbWriteState?>(null)
    val rgbWriteState: StateFlow<RgbWriteState?> = _rgbWriteState.asStateFlow()
    private val _discoveredDevices = MutableStateFlow<List<CoolerDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<CoolerDevice>> = _discoveredDevices.asStateFlow()
    private val _rawDevices = MutableStateFlow<List<BleScanDiagnostic>>(emptyList())
    val rawDevices: StateFlow<List<BleScanDiagnostic>> = _rawDevices.asStateFlow()
    private val _scanState = MutableStateFlow(
        ScanState(
            permissionGranted = BlePermissionManager.hasScanPermission(context),
            locationServiceEnabled = BlePermissionManager.isLocationServiceEnabled(context),
            bluetoothOn = isBluetoothEnabled,
        )
    )
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private fun onMain(block: () -> Unit) { scope.launch { block() } }
    private fun owns(s: Session): Boolean = session === s && s.gatt != null
    private fun ready(s: Session): Boolean = owns(s) && _state.value.isConnected
    val isBluetoothEnabled: Boolean
        get() = runCatching { bluetoothAdapter?.isEnabled == true }.getOrDefault(false)

    fun refreshScanConditions() = onMain { refreshScanConditionsInternal() }

    private fun refreshScanConditionsInternal() {
        _scanState.update {
            it.copy(
                permissionGranted = BlePermissionManager.hasScanPermission(context),
                locationServiceEnabled = BlePermissionManager.isLocationServiceEnabled(context),
                bluetoothOn = isBluetoothEnabled,
            )
        }
    }

    fun startScan() = onMain {
        refreshScanConditionsInternal()
        if (scanSession != null) return@onMain
        if (!_scanState.value.permissionGranted || !isBluetoothEnabled) return@onMain
        val scanner = runCatching { bluetoothAdapter?.bluetoothLeScanner }.getOrNull() ?: return@onMain
        lateinit var scan: ScanSession
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handler.post { if (scanSession === scan) acceptScanResult(result) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                val snapshot = results.toList()
                handler.post { if (scanSession === scan) snapshot.forEach(::acceptScanResult) }
            }

            override fun onScanFailed(errorCode: Int) {
                handler.post {
                    if (scanSession !== scan) return@post
                    stopScanInternal()
                    _scanState.update { it.copy(errorCode = errorCode) }
                }
            }
        }
        scan = ScanSession(scanner, callback)
        scanSession = scan
        _discoveredDevices.value = emptyList()
        _rawDevices.value = emptyList()
        _scanState.update { it.copy(scanning = true, errorCode = null) }
        try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        } catch (e: Exception) {
            Log.e(TAG, "startScan failed", e)
            stopScanInternal()
        }
    }

    fun stopScan() = onMain { stopScanInternal() }

    private fun stopScanInternal() {
        val old = scanSession
        scanSession = null // Invalidate queued callbacks before touching the platform.
        _scanState.update { it.copy(scanning = false) }
        if (old != null) runCatching { old.scanner.stopScan(old.callback) }
            .onFailure { Log.w(TAG, "stopScan failed", it) }
    }

    private fun acceptScanResult(result: ScanResult) {
        // Permission may have been revoked after scan start.
        try {
            recordRaw(result)
            val device = identify(result) ?: return
            _discoveredDevices.update { list ->
                if (list.any { it.address == device.address }) {
                    list.map { if (it.address == device.address) device else it }
                } else list + device
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Scan permission revoked", e)
            stopScanInternal()
            refreshScanConditionsInternal()
        }
    }

    private fun recordRaw(result: ScanResult) {
        val record = result.scanRecord ?: return
        val msdSparse = record.manufacturerSpecificData
        val msd = buildList {
            for (i in 0 until msdSparse.size) {
                add(msdSparse.keyAt(i) to (msdSparse.valueAt(i)?.toHex() ?: ""))
            }
        }
        val payload = msdSparse.get(CoolerBleConstants.MSD_COMPANY_ID)
        val coolerType = if (payload != null && payload.size > 1) {
            CoolerDeviceType.fromMsdType(payload[0].toInt() and 0xFF, payload[1].toInt() and 0xFF)
        } else null
        val entry = BleScanDiagnostic(
            address = result.device.address,
            name = record.deviceName ?: result.device.safeName(),
            rssi = result.rssi,
            msd = msd,
            serviceUuids = record.serviceUuids?.map { it.toString() } ?: emptyList(),
            serviceData = record.serviceData?.entries?.map { (k, v) -> k.uuid.toString() to (v?.toHex() ?: "") }
                ?: emptyList(),
            bluetoothDevice = result.device,
            coolerType = coolerType,
        )
        if (msd.isNotEmpty() || entry.serviceUuids.isNotEmpty() || entry.serviceData.isNotEmpty()) {
            Log.d("BleDiag", "${entry.address} name=${entry.name} rssi=${entry.rssi} " +
                "msd=${entry.msd} uuids=${entry.serviceUuids} svcData=${entry.serviceData}")
        }
        _rawDevices.update { list ->
            (list.filterNot { it.address == entry.address } + entry).sortedByDescending { it.rssi }.take(120)
        }
    }

    private fun identify(result: ScanResult): CoolerDevice? {
        val device = result.device
        val name = result.scanRecord?.deviceName ?: device.safeName()
        val msd = result.scanRecord?.manufacturerSpecificData?.get(CoolerBleConstants.MSD_COMPANY_ID)
        val msdType = if (msd != null && msd.size > CoolerBleConstants.MSD_INDEX_SUB_TYPE) {
            CoolerDeviceType.fromMsdType(
                msd[CoolerBleConstants.MSD_INDEX_MAIN_TYPE].toInt() and 0xFF,
                msd[CoolerBleConstants.MSD_INDEX_SUB_TYPE].toInt() and 0xFF,
            )
        } else null
        val type = msdType ?: CoolerDeviceType.fromBleName(name) ?: return null
        return CoolerDevice(device.address, name, type, result.rssi, matchedByName = msdType == null)
    }

    // All entry points share permission, address, adapter and exception handling.
    fun connect(device: CoolerDevice) = onMain {
        // connectInternal is always Main-confined, so clearing here cannot race resume.
        lastLinkLoss = null
        connectInternal(device.address, device.deviceType, device.displayName)
    }

    fun connectRaw(entry: BleScanDiagnostic, type: CoolerDeviceType) = onMain {
        // connectInternal is always Main-confined, so clearing here cannot race resume.
        lastLinkLoss = null
        connectInternal(entry.address, type, entry.name)
    }

    fun connectByAddress(macAddress: String, type: CoolerDeviceType) = onMain {
        // connectInternal is always Main-confined, so clearing here cannot race resume.
        lastLinkLoss = null
        connectInternal(macAddress, type, null)
    }

    private fun connectInternal(address: String, type: CoolerDeviceType, name: String?) {
        // connectInternal owns any transition out of DISCONNECTED/FAILED, and it always owns
        // the fresh session field. The single internal resume path consumes its own record
        // before calling here; explicit entries clear theirs on the Main entry above.
        stopScanInternal()
        disconnectInternal()
        val s = Session(++nextSessionId)
        session = s
        val normalizedAddress = address.trim().uppercase(java.util.Locale.ROOT)
        _state.value = CoolerLiveState(
            connection = ConnectionState.CONNECTING,
            connectionSessionId = s.id,
            deviceType = type,
            deviceName = name ?: type.deviceName,
            deviceAddress = normalizedAddress,
        )
        if (!BlePermissionManager.hasConnectPermission(context) || !isBluetoothEnabled ||
            !BluetoothAdapter.checkBluetoothAddress(normalizedAddress)
        ) {
            fail(s, "Missing permission, disabled adapter or invalid address")
            return
        }
        try {
            val device = bluetoothAdapter?.getRemoteDevice(normalizedAddress)
            if (device == null) {
                fail(s, "Bluetooth adapter unavailable")
                return
            }
            if (name == null) _state.update { it.copy(deviceName = device.safeName() ?: type.deviceName) }
            s.gatt = openGatt(device, callbackFor(s))
            if (s.gatt == null) {
                fail(s, "connectGatt returned null")
                return
            }
            armTimeout(s, CONNECT_TIMEOUT_MS, "Connection timed out")
        } catch (e: Exception) {
            Log.e(TAG, "connectGatt failed", e)
            fail(s, "Could not open GATT")
        }
    }

    private fun openGatt(device: BluetoothDevice, callback: BluetoothGattCallback): BluetoothGatt? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            openGattWithSettings(device, callback)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        }

    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun openGattWithSettings(device: BluetoothDevice, callback: BluetoothGattCallback): BluetoothGatt? {
        val settings = BluetoothGattConnectionSettings.Builder()
            .setTransport(BluetoothDevice.TRANSPORT_LE).setAutoConnectEnabled(false).build()
        return device.connectGatt(settings, context.mainExecutor, callback)
    }

    private class LinkLoss(val address: String?, val type: CoolerDeviceType?)
    private var lastLinkLoss: LinkLoss? = null
    override var foregroundStarted: Boolean = false

    fun hasRunningSession(): Boolean = session != null

    override fun consumeLinkLoss(): BackgroundLinkLoss? {
        val loss = lastLinkLoss ?: return null
        lastLinkLoss = null
        return BackgroundLinkLoss(loss.address, loss.type)
    }

    fun disconnect() = onMain {
        lastLinkLoss = null
        disconnectInternal()
        _state.value = CoolerLiveState(connection = ConnectionState.DISCONNECTED)
    }

    /** Consume without dialing: used when a newer explicit intent supersedes a stale loss. */
    override fun clearLinkLoss() {
        lastLinkLoss = null
    }

    private fun disconnectInternal() {
        val old = session
        session = null // Must precede waiter completion, cancellation, disconnect and close.
        pendingRgb = null
        _rgbWriteState.value = null
        if (old == null) return
        val oldGatt = old.gatt
        old.gatt = null
        if (oldGatt != null) operations.abort(oldGatt)
        old.discoveryJob?.cancel()
        old.initJob?.cancel()
        old.timeoutJob?.cancel()
        old.pollJob?.cancel()
        old.rssiJob?.cancel()
        if (oldGatt != null) {
            runCatching { oldGatt.disconnect() }.onFailure { Log.w(TAG, "disconnect failed", it) }
            runCatching { oldGatt.close() }.onFailure { Log.w(TAG, "close failed", it) }
        }
    }

    private fun fail(s: Session, reason: String) {
        if (session !== s) return
        Log.w(TAG, reason)
        val previous = _state.value
        disconnectInternal()
        _state.value = CoolerLiveState(
            connection = ConnectionState.FAILED,
            connectionSessionId = previous.connectionSessionId,
            deviceType = previous.deviceType,
            deviceName = previous.deviceName,
            deviceAddress = previous.deviceAddress,
        )
    }

    private fun armTimeout(s: Session, timeoutMs: Long, reason: String) {
        s.timeoutJob?.cancel()
        s.timeoutJob = scope.launch {
            delay(timeoutMs)
            if (owns(s)) fail(s, reason)
        }
    }

    // Always post, even on Main: connectGatt may callback before returning its GATT.
    private fun dispatch(s: Session, g: BluetoothGatt, block: () -> Unit) {
        handler.post { if (owns(s) && s.gatt === g) block() }
    }

    private fun callbackFor(s: Session) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) = dispatch(s, g) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val previous = _state.value
                if (previous.isConnected) {
                    val address = previous.deviceAddress
                    val type = previous.deviceType
                    disconnectInternal()
                    _state.value = CoolerLiveState(connection = ConnectionState.DISCONNECTED)
                    lastLinkLoss = LinkLoss(address, type)
                } else if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail(s, "Connection callback failed: $status")
                } else {
                    fail(s, "Disconnected before initialization completed")
                }
                return@dispatch
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail(s, "Connection callback failed: $status")
                return@dispatch
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (_state.value.connection != ConnectionState.CONNECTING) return@dispatch
                    _state.update { it.copy(connection = ConnectionState.DISCOVERING) }
                    armTimeout(s, DISCOVERY_TIMEOUT_MS, "Service discovery timed out")
                    s.discoveryJob = scope.launch {
                        delay(300)
                        if (!owns(s)) return@launch
                        s.discoveryRequested = true
                        if (!runCatching { g.discoverServices() }.getOrDefault(false)) {
                            fail(s, "Service discovery rejected")
                        }
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = dispatch(s, g) {
            if (!s.discoveryRequested || s.initializing || _state.value.connection != ConnectionState.DISCOVERING) return@dispatch
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail(s, "Service discovery failed: $status")
                return@dispatch
            }
            try {
                // Official 8 Pro binds its characteristics from the main service
                // (d52082ad-...) and only processes reads/notifications from it. Another
                // service may expose a same-UUID characteristic that is not the cooler's
                // register, so visit the main service first and keep that instance.
                val orderedServices = g.services.sortedBy {
                    if (it.uuid == CoolerBleConstants.MAIN_SERVICE_UUID) 0 else 1
                }
                for (service in orderedServices) for (ch in service.characteristics) {
                    s.characteristics.putIfAbsent(ch.uuid, ch)
                }
                if (s.characteristics[CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID] == null &&
                    s.characteristics[CoolerBleConstants.COOLING_SWITCH_UUID] == null
                ) {
                    fail(s, "Missing cooler characteristics (1011/1012)")
                    return@dispatch
                }
                val capabilities = CoolerCapabilities.fromCharacteristics(
                    available = s.characteristics.keys,
                    writable = s.characteristics.values.filter(::writable).map { it.uuid }.toSet(),
                )
                _state.update { it.copy(capabilities = capabilities) }
                s.requiredConfiguration = configurationUuids
                    .filter { s.characteristics[it]?.let(::readable) == true }
                    .toSet()
                s.initializing = true
                armTimeout(s, INIT_TIMEOUT_MS, "Initialization timed out")
                s.initJob = scope.launch {
                    try {
                        initialize(s)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Initialization failed", e)
                        fail(s, "Initialization exception")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not inspect services", e)
                fail(s, "Invalid services")
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) = dispatch(s, g) {
            operations.complete(g, characteristic, GattOperationQueue.Kind.WRITE, GattOperationQueue.Result(status == BluetoothGatt.GATT_SUCCESS))
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = dispatch(s, g) {
            operations.complete(g, descriptor, GattOperationQueue.Kind.DESCRIPTOR, GattOperationQueue.Result(status == BluetoothGatt.GATT_SUCCESS))
        }

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) = dispatch(s, g) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            if (operations.complete(g, g, GattOperationQueue.Kind.RSSI, GattOperationQueue.Result(success, rssi = rssi))) {
                _state.update { it.copy(rssi = rssi.takeIf { success }) }
            }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            readCallback(s, g, characteristic, value.copyOf(), status)
        }

        @Deprecated("Legacy Android callback")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // Snapshot Binder-owned mutable bytes before posting.
            readCallback(s, g, characteristic, characteristic.value?.copyOf() ?: byteArrayOf(), status)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            notificationCallback(s, g, characteristic, value.copyOf())
        }

        @Deprecated("Legacy Android callback")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            notificationCallback(s, g, characteristic, characteristic.value?.copyOf() ?: byteArrayOf())
        }
    }

    private fun readCallback(s: Session, g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray, status: Int) = dispatch(s, g) {
        val success = status == BluetoothGatt.GATT_SUCCESS
        if (operations.complete(g, ch, GattOperationQueue.Kind.READ, GattOperationQueue.Result(success, value)) && success) {
            handleData(s, ch.uuid, value)
        }
    }

    private fun notificationCallback(s: Session, g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) = dispatch(s, g) {
        if (s.characteristics[ch.uuid] === ch) handleData(s, ch.uuid, value)
    }

    private val telemetryUuids = listOf(
        CoolerBleConstants.COOLING_SWITCH_UUID,
        CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID,
        CoolerBleConstants.TEMPERATURE_NOTIFICATION_UUID,
        CoolerBleConstants.STATUS_UUID,
        CoolerBleConstants.RPM_UUID,
        CoolerBleConstants.POWER_UUID,
        CoolerBleConstants.PROTECTION_UUID,
        CoolerBleConstants.AUTO_MODE_CONTROL_UUID,
        CoolerBleConstants.BOOST_CONTROL_UUID,
        CoolerBleConstants.LIGHT_CONTROL_UUID,
    )

    private val configurationUuids = listOf(
        CoolerBleConstants.COOLING_SWITCH_UUID,
        CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID,
        CoolerBleConstants.LIGHT_CONTROL_UUID,
        CoolerBleConstants.AUTO_MODE_CONTROL_UUID,
        CoolerBleConstants.BOOST_CONTROL_UUID,
        CoolerBleConstants.PROTECTION_UUID,
    )

    private suspend fun initialize(s: Session) {
        for (uuid in telemetryUuids) {
            if (!owns(s)) return
            val ch = s.characteristics[uuid] ?: continue
            val subscribed = enableNotification(s, ch)
            if (!owns(s)) return
            if (!subscribed) Log.w(TAG, "Optional subscription unavailable: $uuid")
        }
        // Read back every discovered readable configuration once after connecting. A
        // failed or unparsed reply never blocks CONNECTED: the corresponding control
        // stays unknown/unavailable until a later read or notification confirms it.
        for (uuid in s.requiredConfiguration) {
            if (!owns(s)) return
            val ch = s.characteristics[uuid] ?: continue
            // A one-off slow reply right after connecting must not consume the lane: the
            // periodic pass is the retry path that confirms this configuration and unlocks
            // its control, so quarantining here would hide the control until a reconnect.
            // A lane that stays silent is still quarantined by the periodic pass, which
            // surfaces the loss as degraded telemetry instead of retrying forever.
            val ok = readIfReadable(s, ch, poisonOnTimeout = false, quarantineOnTimeout = false)
            if (!owns(s)) return
            if (!ok) Log.w(TAG, "Initial configuration read unavailable: $uuid")
        }
        if (!owns(s)) return
        s.timeoutJob?.cancel()
        _state.update { it.copy(connection = ConnectionState.CONNECTED) }
        if (!ready(s)) return
        val rgb = pendingRgb
        pendingRgb = null
        if (rgb != null) applyRgb(s, rgb)
        if (!ready(s)) return
        startPollLoop(s)
    }

    private fun readable(ch: BluetoothGattCharacteristic): Boolean =
        ch.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0

    private fun writable(ch: BluetoothGattCharacteristic): Boolean =
        ch.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

    private suspend fun enqueueWrite(
        s: Session,
        ch: BluetoothGattCharacteristic,
        value: ByteArray,
        onStart: () -> Unit = {},
        fresh: () -> Boolean = { true },
        poisonOnTimeout: Boolean = true,
        quarantineOnTimeout: Boolean = true,
    ): Boolean {
        val g = s.gatt ?: return false
        if (!writable(ch)) return false
        return operations.execute(g, ch, GattOperationQueue.Kind.WRITE, OP_TIMEOUT_MS,
            isCurrent = { owns(s) && s.characteristics[ch.uuid] === ch && fresh() },
            start = {
                onStart()
                @Suppress("DEPRECATION")
                ch.value = value
                ch.writeType = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                } else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            },
            poisonOnTimeout = poisonOnTimeout,
            quarantineOnTimeout = quarantineOnTimeout,
        ).success
    }

    /**
     * 对齐官方灯光兑底:设备回报的灯效字节无法解析(非 1/2/3/4/6)且数组非空时,下发官方默认灯
     * (`n0.a()`)。节流并限制每次连接的次数,且写入不 poison/不 quarantine 灯光通道。
     */
    private fun maybeSendDefaultLight(s: Session) {
        if (!ready(s)) return
        val ch = s.characteristics[CoolerBleConstants.LIGHT_CONTROL_UUID] ?: return
        if (!writable(ch)) return
        if (s.lightDefaultAttempts >= MAX_LIGHT_DEFAULT_ATTEMPTS) return
        val now = SystemClock.elapsedRealtime()
        if (now - s.lastLightDefaultMs < LIGHT_DEFAULT_MIN_INTERVAL_MS) return
        s.lastLightDefaultMs = now
        s.lightDefaultAttempts++
        scope.launch {
            if (!owns(s)) return@launch
            enqueueWrite(s, ch, CoolerBleConstants.defaultLightCommand(),
                poisonOnTimeout = false, quarantineOnTimeout = false)
        }
    }

    /** Missing read property is a deliberate skip, not a failed read. Data commits only in matching callbacks.
     * [poisonOnTimeout] defaults to true for setup/control reads: a missing callback there means
     * the session cannot complete initialization, so the timeout fails it explicitly.
     */
    private suspend fun readIfReadable(
        s: Session,
        ch: BluetoothGattCharacteristic,
        fresh: () -> Boolean = { true },
        poisonOnTimeout: Boolean = true,
        quarantineOnTimeout: Boolean = true,
    ): Boolean {
        val g = s.gatt ?: return false
        if (!owns(s) || !fresh()) return false
        // A missing READ property is a deliberate skip, not a failed read: the caller
        // only asked because the characteristic might be readable.
        if (ch.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) return true
        return operations.execute(g, ch, GattOperationQueue.Kind.READ, READ_TIMEOUT_MS,
            isCurrent = { owns(s) && s.characteristics[ch.uuid] === ch && fresh() },
            start = { g.readCharacteristic(ch) },
            poisonOnTimeout = poisonOnTimeout,
            quarantineOnTimeout = quarantineOnTimeout,
        ).success
    }

    private suspend fun enableNotification(
        s: Session,
        ch: BluetoothGattCharacteristic,
        poisonOnTimeout: Boolean = true,
    ): Boolean {
        val g = s.gatt ?: return false
        if (!owns(s)) return false
        val notify = ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        val indicate = ch.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
        if (!notify && !indicate) return true
        val descriptor = ch.getDescriptor(CCC_DESCRIPTOR_UUID) ?: return false
        return operations.execute(g, descriptor, GattOperationQueue.Kind.DESCRIPTOR, OP_TIMEOUT_MS,
            isCurrent = { owns(s) && s.characteristics[ch.uuid] === ch },
            start = {
                if (!g.setCharacteristicNotification(ch, true)) false else {
                    @Suppress("DEPRECATION")
                    descriptor.value = if (indicate) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(descriptor)
                }
            },
            poisonOnTimeout = poisonOnTimeout,
        ).success
    }

    private fun startPollLoop(s: Session) {
        if (!ready(s)) return
        s.lastTempUpdateMs = SystemClock.elapsedRealtime()
        // Background polling cadence: frequent state costs a wake plus radio time on every
        // accepted read. Notification-capable telemetry already arrives on its own; reduce
        // the steady-state read volume so Doze/background scheduling cannot starve — and
        // then poison — the session with backlog timeouts.
        var degradedPollCycles = 0
        s.pollJob = scope.launch {
            while (isActive && ready(s)) {
                if (s.controls == 0) {
                    val polled = pollTelemetryOnce(s)
                    if (!ready(s)) return@launch
                    // Back-to-back fully skipped passes mean every read was rejected or
                    // non-readable: do not spin at foreground cadence while in background.
                    degradedPollCycles = if (polled) 0 else degradedPollCycles + 1
                } else {
                    degradedPollCycles = 0
                }
                delay(if (degradedPollCycles >= 3) 4000 else 500)
            }
        }
        s.rssiJob = scope.launch {
            // RSSI is best-effort UI telemetry: its loss never invalidates the session and
            // its success clears only a UI null, never protocol state.
            while (isActive && ready(s)) {
                val g = s.gatt ?: return@launch
                val result = operations.execute(g, g, GattOperationQueue.Kind.RSSI, READ_TIMEOUT_MS,
                    isCurrent = { ready(s) }, start = { g.readRemoteRssi() }, poisonOnTimeout = false)
                if (!ready(s)) return@launch
                if (!result.success) _state.update { it.copy(rssi = null) }
                delay(2000)
            }
        }
    }

    /**
     * One background-tolerant telemetry pass. Returns true when at least one readable
     * characteristic completed successfully. Setup/control paths retain skip-as-success
     * and poisoning defaults; periodic reads cannot invalidate an otherwise healthy session.
     */
    private suspend fun pollTelemetryOnce(s: Session): Boolean {
        // RGB light state is not telemetry: unless a parseable mode has already arrived,
        // keep the light characteristic in the periodic pass so an early failed or timed
        // out read can be retried until the device reports a mode. Its timeout must not
        // quarantine the lane, or the one retry that could succeed would never fire.
        // The retry is throttled above READ_TIMEOUT_MS so a slow firmware's read can
        // complete instead of being starved by the 500ms poll cadence.
        val lightPending = _state.value.rgb == null
        val light = s.characteristics[CoolerBleConstants.LIGHT_CONTROL_UUID]
        val targets = buildList {
            addAll(
                telemetryUuids.filterNot { it == CoolerBleConstants.LIGHT_CONTROL_UUID }
                    .mapNotNull { s.characteristics[it] }
            )
            if (lightPending && light != null && readable(light)) add(light)
        }
        val polled = pollTelemetry(
            targets = targets,
            temperatureTargets = listOf(CoolerBleConstants.TEMPERATURE_NOTIFICATION_UUID, CoolerBleConstants.STATUS_UUID)
                .mapNotNull { s.characteristics[it] },
            isCurrent = { ready(s) },
            isReadable = { it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0 },
            read = { ch ->
                if (ch.uuid == CoolerBleConstants.LIGHT_CONTROL_UUID) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - s.lastLightReadMs < LIGHT_READ_RETRY_MS) {
                        return@pollTelemetry false
                    }
                    s.lastLightReadMs = now
                    readIfReadable(s, ch, poisonOnTimeout = false, quarantineOnTimeout = false)
                } else {
                    readIfReadable(s, ch, poisonOnTimeout = false)
                }
            },
            temperatureStale = { SystemClock.elapsedRealtime() - s.lastTempUpdateMs > 6000 },
            subscribe = { enableNotification(s, it, poisonOnTimeout = false) },
            onTemperatureRecovery = { s.lastTempUpdateMs = SystemClock.elapsedRealtime() - 4000 },
        )
        return polled
    }

    private fun handleData(s: Session, uuid: UUID, value: ByteArray) {
        if (!owns(s)) return
        // A device reply, read-back or notification, is authoritative confirmation that
        // the characteristic was read — even when the payload's semantics are not yet
        // parsed: the read itself is confirmed, only the displayed value stays unknown.
        s.configurationRead += uuid
        val previous = _state.value
        val update = CoolerTelemetryReducer.reduce(previous, uuid, value)
        // Official refreshLightModeView else-branch: a non-empty 0x1013 reply whose
        // effect byte is not a known mode (1/2/3/4/6) cannot be displayed, so the
        // official app pushes the default effect to bring the device to a known state.
        // A known mode whose colour bytes are missing is parsed as null by
        // [RGBConfig.fromNotification] (so no colour is invented) but it is still a known
        // mode: the official page displays it without a fallback, so neither do we.
        if (uuid == CoolerBleConstants.LIGHT_CONTROL_UUID && update == null &&
            value.isNotEmpty() && LightEffect.fromCode(value[0]) == null
        ) {
            maybeSendDefaultLight(s)
        }
        val next = if (update != null) {
            if (update.temperatureReported) s.lastTempUpdateMs = SystemClock.elapsedRealtime()
            update.state
        } else {
            previous
        }
        val committed = next.copy(confirmedConfiguration = s.configurationRead.toSet())
        _state.value = committed
        // 官方 Jacket8ProActivityV3.N4 同样只在限档值发生变化时动作:重复上报不会重复下发。
        if (previous.fanLimit != committed.fanLimit) enforcePowerLimit()
    }

    /**
     * 官方 Jacket8ProActivityV3.N4:设备上报的供电功率限档降到"不限档"以下时,把当前档位压回
     * 限档并下发(官方 `LimitedSeekBar.setProgress(限档)` → 回调 → `viewModel.y1(e(限档))`,即真正
     * 写 0x1012),同时自动关闭破坏神(`CustomSwitchView.c()` → 0x1017=0)。
     *
     * 与官方的一处差异:智能温控开启时只关破坏神,不写风扇转速——此时档位由设备固件自己调节,
     * 再写 0x1012 只会和固件抢控制权(官方也没有在 UI 上展示这条档位限制)。
     */
    private fun enforcePowerLimit() {
        val state = _state.value
        val maxRaw = state.fanRawLimit ?: return
        if (state.boostOn) setBoost(false)
        val type = state.deviceType ?: return
        // 待确认的拖动目标优先于回读值(与 fanGear 同一口径):刚拖到超限档位、限档随后才上报时,
        // 只比回读值会漏掉本次拖动。
        val intendedRaw = state.pendingFanPercent
            ?.let { CoolerBleConstants.percentageToRaw(it, type) }
            ?: state.fanRaw ?: return
        if (intendedRaw <= maxRaw) return
        // 破坏神已在上面关掉(状态随后回读确认),所以按破坏神关闭后的口径判断是否有手动档位可写;
        // 否则限档上报那一刻正开着破坏神时会直接 return,边缘触发的这次压档就永远丢失了。
        if (!state.copy(boostOn = false).manualLevelEnabled) return
        Log.i(TAG, "Power limit ${state.fanLimit} clamps fan raw $intendedRaw -> $maxRaw")
        // 不能调公开的 setFanSpeed:它按当前快照再查一次 manualLevelEnabled,而此时
        // 破坏神关闭的回读还没回来(boostOn 仍为 true),压档会被直接吞掉且边沿不再补发。
        // 手动闸上面已经评估过,这里只做钳制和发写;onMain 则是因为 enforce 可能跑在 binder 线程。
        val percent = CoolerBleConstants.rawToPercentage(maxRaw, type)
        onMain { writeFanSpeed(percent, enforceManual = false) }
    }

    private class Command(
        val session: Session,
        val characteristic: BluetoothGattCharacteristic,
        val control: Control,
        val generation: Long,
    )

    private fun command(control: Control, uuid: UUID): Command? {
        val s = session ?: return null
        if (!ready(s)) return null
        if (uuid in configurationUuids && !_state.value.hasConfirmedConfiguration(uuid)) return null
        val ch = s.characteristics[uuid]?.takeIf(::writable) ?: return null
        val generation = (s.generations[control] ?: 0) + 1
        s.generations[control] = generation
        return Command(s, ch, control, generation)
    }

    private fun fresh(command: Command): Boolean = ready(command.session) &&
        command.session.generations[command.control] == command.generation

    /** No optimistic state: a failed write leaves the last reported/acknowledged value intact. */
    private suspend fun executeCommand(
        command: Command,
        value: ByteArray,
        debounceMs: Long = 0,
    ): Boolean {
        val s = command.session
        s.controls++
        try {
            if (debounceMs > 0) delay(debounceMs)
            if (!fresh(command)) return false
            val ok = enqueueWrite(s, command.characteristic, value,
                fresh = { fresh(command) },
            )
            if (!fresh(command)) return false
            // A GATT write callback confirms delivery only. The device read-back remains the
            // sole source of the published configuration value; its timeout must quarantine
            // the lane (except light, whose lane must stay retryable), never kill the session.
            readIfReadable(s, command.characteristic, fresh = { fresh(command) }, poisonOnTimeout = false,
                quarantineOnTimeout = command.characteristic.uuid != CoolerBleConstants.LIGHT_CONTROL_UUID)
            return ok
        } finally {
            s.controls--
        }
    }

    fun setFanSpeed(percent: Int) = onMain { writeFanSpeed(percent, enforceManual = true) }

    /**
     * 发写风扇百分比(已按限档钳制)。[enforceManual] 为 true 时沿用手动档位闸门(UI/调用方入口);
     * 为 false 时仅限 enforcePowerLimit 调用——它在调用前已用"破坏神关闭后"的口径评估过闸门,
     * 这里再查快照会因回读未到而误杀那次一次性的压档。
     */
    private fun writeFanSpeed(percent: Int, enforceManual: Boolean) {
        if (enforceManual && !_state.value.manualLevelEnabled) return
        val command = command(Control.FAN, CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID) ?: return
        val type = _state.value.deviceType ?: return
        // 供电功率不足时把请求钳到限档 raw 对应的百分比:整数换算只能往下取整,
        // 所以钳后重新算出的 raw 不会超过限档值。
        val maxPercent = _state.value.fanRawLimit
            ?.let { CoolerBleConstants.rawToPercentage(it, type) } ?: 100
        val clamped = percent.coerceIn(0, maxPercent)
        val raw = CoolerBleConstants.percentageToRaw(clamped, type).toByte()
        _state.update { it.copy(pendingFanPercent = clamped) }
        scope.launch {
            try {
                executeCommand(command, byteArrayOf(raw), 150)
            } finally {
                if (fresh(command)) _state.update { it.copy(pendingFanPercent = null) }
            }
        }
    }

    fun setCooling(on: Boolean) = onMain {
        val command = command(Control.COOLING, CoolerBleConstants.COOLING_SWITCH_UUID) ?: return@onMain
        val value = if (on) CoolerBleConstants.COOLING_SWITCH_ON else CoolerBleConstants.COOLING_SWITCH_OFF
        scope.launch { executeCommand(command, byteArrayOf(value)) }
    }

    fun setSmart(on: Boolean) = onMain { startSmart(on) }

    /**
     * True only for a successful GATT callback in the same ready session and latest smart intent.
     * False when unavailable, superseded or failed. This does not assert firmware readback.
     * Caller cancellation stops waiting, never cancels the manager-owned command/accepted write.
     */
    suspend fun setSmartAndAwait(on: Boolean): Boolean = withContext(Dispatchers.Main.immediate) {
        startSmart(on)?.await() ?: false
    }

    private fun startSmart(on: Boolean) = command(Control.SMART, CoolerBleConstants.AUTO_MODE_CONTROL_UUID)?.let { smart ->
        scope.async {
            // 官方 Jacket8ProActivityV3.o6/b6:智能温控与破坏神互斥——开启温控会先关掉破坏神
            // (写入前先落 0x1017=0,顺序与官方一致)。放在这里而不是 UI 层,是为了让磁贴/后台
            // 服务开启温控时也走同一条规则。
            if (on && _state.value.boostOn) {
                command(Control.BOOST, CoolerBleConstants.BOOST_CONTROL_UUID)
                    ?.let { executeCommand(it, byteArrayOf(0)) }
            }
            val value = if (on) CoolerBleConstants.AUTO_MODE_ON else CoolerBleConstants.AUTO_MODE_OFF
            executeCommand(smart, byteArrayOf(value))
        }
    }

    fun setBoost(on: Boolean) = onMain {
        // 官方 Jacket8ProActivityV3.u5:功耗限档低于"不限档"时,开启破坏神的请求被否决。
        if (on && _state.value.powerLimited) {
            Log.i(TAG, "Boost on refused: charger power limited (limit=${_state.value.fanLimit})")
            return@onMain
        }
        val command = command(Control.BOOST, CoolerBleConstants.BOOST_CONTROL_UUID) ?: return@onMain
        scope.launch { executeCommand(command, byteArrayOf(if (on) 1 else 0)) }
    }

    fun setOvercoldProtection(on: Boolean) = onMain {
        val command = command(Control.PROTECTION, CoolerBleConstants.PROTECTION_UUID) ?: return@onMain
        val value = byteArrayOf(
            if (on) CoolerBleConstants.PROTECTION_FLAG_ON else CoolerBleConstants.PROTECTION_FLAG_OFF,
            0, CoolerBleConstants.PROTECTION_HIGH_DEFAULT, CoolerBleConstants.PROTECTION_LOW_DEFAULT,
        )
        scope.launch { executeCommand(command, value) }
    }

    fun setRGB(config: RGBConfig) = onMain {
        val request = RgbWriteState(++nextRgbRequestId, config, RgbWriteStatus.WRITING)
        _rgbWriteState.value = request
        when (_state.value.connection) {
            ConnectionState.CONNECTING, ConnectionState.DISCOVERING -> pendingRgb = request
            ConnectionState.CONNECTED -> session?.let { applyRgb(it, request) }
            else -> _rgbWriteState.update { it?.completed(request.requestId, false) }
        }
    }

    private fun applyRgb(s: Session, request: RgbWriteState) {
        if (!ready(s) || _rgbWriteState.value?.requestId != request.requestId) return
        val command = command(Control.RGB, CoolerBleConstants.LIGHT_CONTROL_UUID)
        if (command == null) {
            _rgbWriteState.update { it?.completed(request.requestId, false) }
            return
        }
        scope.launch {
            val ok = executeCommand(command, request.config.toCommand(), 200)
            if (!fresh(command) || _rgbWriteState.value?.requestId != request.requestId) return@launch
            _rgbWriteState.update { it?.completed(request.requestId, ok) }
        }
    }

    private fun BluetoothDevice.safeName(): String? = try { name } catch (_: SecurityException) { null }

    /** Reset transport and published state without destroying the reusable process scope. */
    fun release() = onMain {
        stopScanInternal()
        disconnectInternal()
        _state.value = CoolerLiveState(connection = ConnectionState.DISCONNECTED)
    }
}
