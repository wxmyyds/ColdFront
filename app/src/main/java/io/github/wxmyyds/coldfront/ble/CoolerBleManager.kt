package io.github.wxmyyds.coldfront.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import io.github.wxmyyds.coldfront.domain.CoolerBleConstants
import io.github.wxmyyds.coldfront.domain.CoolerDevice
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.FanMode
import io.github.wxmyyds.coldfront.domain.LightEffect
import io.github.wxmyyds.coldfront.domain.RGBConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

private const val TAG = "CoolerBleManager"
private val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/** 单个 GATT 操作等待回调的超时(ms) */
private const val OP_TIMEOUT_MS = 2500L

/** 读操作超时(冰环看门狗同为 1200ms:坏连接时避免一轮轮询拖太久) */
private const val READ_TIMEOUT_MS = 1200L
/** 两个 GATT 操作之间的最小间隔(ms),给固件喘息 */
private const val OP_SPACING_MS = 60L

/** 扫描器实时状态（诊断可视化用） */
data class ScanState(
    val scanning: Boolean = false,
    val permissionGranted: Boolean = false,
    val locationServiceEnabled: Boolean = true,
    val bluetoothOn: Boolean = false,
    /** onScanFailed 的错误码 */
    val errorCode: Int? = null,
)

/**
 * BLE 控制器:封装扫描、连接、GATT 操作与状态广播。
 *
 * 关键设计——GATT 命令串行队列:
 * Android BLE 栈同一时刻只允许一个在途操作,并发发起会被静默丢弃。
 * 所有读/写/描述符写都经 [gattMutex] 串行化,等待对应回调确认(超时重试一次),
 * 操作间保留间隔。没有这套机制,「连接成功但风扇/温度/灯光全无反应」是必然的。
 *
 * 识别:MSD(公司 0x08CA)的 [mainType, subType](8 Pro = 5,8),回退广播名。
 * 协议详见 docs/protocol-8pro.md。
 */
class CoolerBleManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scanner get() = bluetoothAdapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null
    private var fanChar: BluetoothGattCharacteristic? = null
    private var tempChar: BluetoothGattCharacteristic? = null
    private var statusChar: BluetoothGattCharacteristic? = null
    private var lightChar: BluetoothGattCharacteristic? = null
    private var autoChar: BluetoothGattCharacteristic? = null
    private var boostChar: BluetoothGattCharacteristic? = null
    private var switchChar: BluetoothGattCharacteristic? = null
    private var rpmChar: BluetoothGattCharacteristic? = null
    private var powerChar: BluetoothGattCharacteristic? = null
    private var protectChar: BluetoothGattCharacteristic? = null

    private var scanning = false
    private var pendingRgb: RGBConfig? = null

    /** 最近一次成功解析出温度的时间戳(轮询自愈用) */
    @Volatile
    private var lastTempUpdateMs = 0L

    // —— GATT 串行队列 ——
    private val gattMutex = Mutex()
    private var writeWaiter: CompletableDeferred<Int>? = null
    private var readWaiter: CompletableDeferred<Pair<Int, ByteArray>>? = null
    private var descWaiter: CompletableDeferred<Int>? = null

    // —— 防抖任务 ——
    private var fanJob: Job? = null
    private var lightJob: Job? = null
    private var autoJob: Job? = null

    // —— 状态轮询循环(8 Pro 温度/转速/功率靠主动读,不靠推送) ——
    private var pollJob: Job? = null

    private val _state = MutableStateFlow(CoolerLiveState(connection = ConnectionState.DISCONNECTED))
    val state: StateFlow<CoolerLiveState> = _state.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<CoolerDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<CoolerDevice>> = _discoveredDevices.asStateFlow()

    /** 诊断模式：未过滤的全部原始扫描结果(按地址去重,保留最新) */
    private val _rawDevices = MutableStateFlow<List<BleScanDiagnostic>>(emptyList())
    val rawDevices: StateFlow<List<BleScanDiagnostic>> = _rawDevices.asStateFlow()

    /** 扫描器状态（权限/定位/蓝牙/失败码），供 UI 诊断展示 */
    private val _scanState = MutableStateFlow(
        ScanState(
            permissionGranted = BlePermissionManager.hasScanPermission(context),
            locationServiceEnabled = BlePermissionManager.isLocationServiceEnabled(context),
            bluetoothOn = isBluetoothEnabled,
        )
    )
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    /** 外部在权限/蓝牙/定位变化后调用，重新评估扫描条件 */
    fun refreshScanConditions() {
        _scanState.update {
            it.copy(
                permissionGranted = BlePermissionManager.hasScanPermission(context),
                locationServiceEnabled = BlePermissionManager.isLocationServiceEnabled(context),
                bluetoothOn = isBluetoothEnabled,
            )
        }
    }

    val isBluetoothEnabled: Boolean get() = bluetoothAdapter?.isEnabled == true

    // ──────────────────────────── 扫描 ────────────────────────────

    @SuppressLint("MissingPermission")
    fun startScan() {
        refreshScanConditions()
        val sc = scanner
        if (sc == null || scanning) return
        if (!BlePermissionManager.hasScanPermission(context)) {
            Log.w(TAG, "无扫描权限，跳过 startScan")
            _scanState.update { it.copy(scanning = false, permissionGranted = false) }
            return
        }
        _discoveredDevices.value = emptyList()
        _rawDevices.value = emptyList()
        scanning = true
        _scanState.update { it.copy(scanning = true, errorCode = null) }
        // 注:不覆盖 connection 状态——已连接时进入扫描页不能显示"已断开"
        try {
            sc.startScan(
                /* filters = */ null,
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build(),
                scanCallback,
            )
        } catch (e: Exception) {
            Log.e(TAG, "startScan 失败: ${e.message}")
            scanning = false
            _scanState.update { it.copy(scanning = false) }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanning = false
        _scanState.update { it.copy(scanning = false) }
        val sc = scanner ?: return
        try {
            sc.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "stopScan 失败: ${e.message}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // 诊断:先记录原始广播(不做任何过滤)
            recordRaw(result)
            val device = identify(result) ?: return
            _discoveredDevices.update { list ->
                if (list.any { it.address == device.address }) {
                    list.map { if (it.address == device.address) device else it }
                } else list + device
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "扫描失败 errorCode=$errorCode")
            scanning = false
            _scanState.update { it.copy(scanning = false, errorCode = errorCode) }
        }
    }

    /** 记录原始扫描结果(诊断用,不过滤) */
    @SuppressLint("MissingPermission")
    private fun recordRaw(result: ScanResult) {
        val record = result.scanRecord ?: return
        val name = record.deviceName ?: result.device.safeName()
        val msdSparse = record.manufacturerSpecificData
        val msd = buildList {
            for (i in 0 until msdSparse.size()) {
                val key = msdSparse.keyAt(i)
                add(key to (msdSparse.valueAt(i)?.toHex() ?: ""))
            }
        }
        // MSD 命中已知散热器型号 → 直接标记,诊断卡片可一键连接
        val coolerType = run {
            val payload = msdSparse.get(CoolerBleConstants.MSD_COMPANY_ID) ?: return@run null
            if (payload.size > 1) {
                CoolerDeviceType.fromMsdType(
                    payload[0].toInt() and 0xFF,
                    payload[1].toInt() and 0xFF,
                )
            } else null
        }
        val serviceData = record.serviceData?.entries
            ?.map { (k, v) -> k.uuid.toString() to (v?.toHex() ?: "") }
            ?: emptyList()
        val entry = BleScanDiagnostic(
            address = result.device.address,
            name = name,
            rssi = result.rssi,
            msd = msd,
            serviceUuids = record.serviceUuids?.map { it.toString() } ?: emptyList(),
            serviceData = serviceData,
            bluetoothDevice = result.device,
            coolerType = coolerType,
        )
        if (msd.isNotEmpty() || entry.serviceUuids.isNotEmpty() || entry.serviceData.isNotEmpty()) {
            Log.d(
                "BleDiag",
                "${entry.address} name=${entry.name} rssi=${entry.rssi} " +
                    "msd=${entry.msd} uuids=${entry.serviceUuids} svcData=${entry.serviceData}",
            )
        }
        _rawDevices.update { list ->
            (list.filterNot { it.address == entry.address } + entry)
                .sortedByDescending { it.rssi }
                .take(120)
        }
    }

    /**
     * 识别散热器型号:
     * 1) 厂商数据 MSD(0x08CA) → [mainType, subType] 精确匹配(官方方案);
     * 2) 回退广播名匹配(8 Pro 等 MSD 拿不到时)。
     */
    @SuppressLint("MissingPermission")
    private fun identify(result: ScanResult): CoolerDevice? {
        val btDevice = result.device ?: return null

        val msd = result.scanRecord?.manufacturerSpecificData
            ?.get(CoolerBleConstants.MSD_COMPANY_ID)
        var type: CoolerDeviceType? = null
        var matchedByName = false
        if (msd != null && msd.size > CoolerBleConstants.MSD_INDEX_SUB_TYPE) {
            val main = msd[CoolerBleConstants.MSD_INDEX_MAIN_TYPE].toInt() and 0xFF
            val sub = msd[CoolerBleConstants.MSD_INDEX_SUB_TYPE].toInt() and 0xFF
            type = CoolerDeviceType.fromMsdType(main, sub)
            if (type != null) Log.d(TAG, "MSD 识别: ($main, $sub) → $type")
        }
        if (type == null) {
            val name = result.scanRecord?.deviceName ?: btDevice.safeName()
            type = CoolerDeviceType.fromBleName(name)
            if (type != null) matchedByName = true
        }
        if (type == null) return null

        return CoolerDevice(
            bluetoothDevice = btDevice,
            deviceType = type,
            rssi = result.rssi,
            matchedByName = matchedByName,
        )
    }

    // ──────────────────────────── 连接 ────────────────────────────

    @SuppressLint("MissingPermission")
    fun connect(device: CoolerDevice) {
        if (!BlePermissionManager.hasConnectPermission(context)) {
            Log.w(TAG, "无连接权限")
            _state.update {
                it.copy(connection = ConnectionState.FAILED, deviceType = device.deviceType)
            }
            return
        }
        stopScan()
        disconnectInternal()
        _state.update {
            it.copy(
                connection = ConnectionState.CONNECTING,
                deviceType = device.deviceType,
                deviceName = device.displayName,
                deviceAddress = device.address,
                rssi = device.rssi,
            )
        }
        // TRANSPORT_LE 显式指定:避免双模手机走 BR/EDR 导致连接失败
        gatt = device.bluetoothDevice.connectGatt(
            context, false, gattCallback, BluetoothDevice.TRANSPORT_LE,
        )
    }

    /** 诊断模式:手动指定型号连接(不做识别检查) */
    @SuppressLint("MissingPermission")
    fun connectRaw(entry: BleScanDiagnostic, type: CoolerDeviceType) {
        val btDevice = entry.bluetoothDevice
            ?: bluetoothAdapter?.getRemoteDevice(entry.address)
            ?: return
        connect(
            CoolerDevice(
                bluetoothDevice = btDevice,
                deviceType = type,
                rssi = entry.rssi,
                matchedByName = true,
            )
        )
    }

    /** 按 MAC + 型号直连(用于自动模式恢复连接) */
    @SuppressLint("MissingPermission")
    fun connectByAddress(macAddress: String, type: CoolerDeviceType) {
        val device = bluetoothAdapter?.getRemoteDevice(macAddress) ?: run {
            _state.update { it.copy(connection = ConnectionState.FAILED, deviceType = type) }
            return
        }
        stopScan()
        disconnectInternal()
        _state.update {
            it.copy(
                connection = ConnectionState.CONNECTING,
                deviceType = type,
                deviceAddress = macAddress,
            )
        }
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        disconnectInternal()
        _state.update { CoolerLiveState(connection = ConnectionState.DISCONNECTED) }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectInternal() {
        // 释放等待中的操作,避免协程悬挂
        writeWaiter?.complete(BluetoothGatt.GATT_FAILURE)
        writeWaiter = null
        readWaiter?.complete(BluetoothGatt.GATT_FAILURE to ByteArray(0))
        readWaiter = null
        descWaiter?.complete(BluetoothGatt.GATT_FAILURE)
        descWaiter = null
        fanJob?.cancel()
        lightJob?.cancel()
        autoJob?.cancel()
        pollJob?.cancel()
        gatt?.let {
            try {
                it.disconnect()
                it.close()
            } catch (e: Exception) {
                Log.e(TAG, "断开异常: ${e.message}")
            }
        }
        gatt = null
        fanChar = null
        tempChar = null
        statusChar = null
        lightChar = null
        autoChar = null
        boostChar = null
        switchChar = null
        rpmChar = null
        powerChar = null
        protectChar = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.update { it.copy(connection = ConnectionState.DISCOVERING) }
                    // 等待 300ms 再发现服务:部分固件连接后需要缓冲
                    scope.launch {
                        delay(300)
                        try {
                            if (!g.discoverServices()) {
                                Log.e(TAG, "discoverServices 调用失败")
                                _state.update { it.copy(connection = ConnectionState.FAILED) }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "discoverServices 异常: ${e.message}")
                            _state.update { it.copy(connection = ConnectionState.FAILED) }
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "连接断开 status=$status")
                    _state.update { it.copy(connection = ConnectionState.DISCONNECTED) }
                    disconnectInternal()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.update { it.copy(connection = ConnectionState.FAILED) }
                return
            }
            // 服务无关查找:遍历全部服务的全部特征(实测可用方案——
            // 8 Pro 的服务 UUID 可能与 d52082ad 不同,不能只 getService(主服务))
            for (svc in g.services) {
                for (ch in svc.characteristics) {
                    when (ch.uuid) {
                        CoolerBleConstants.COOLING_SWITCH_UUID -> switchChar = ch
                        CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID -> fanChar = ch
                        CoolerBleConstants.LIGHT_CONTROL_UUID -> lightChar = ch
                        CoolerBleConstants.TEMPERATURE_NOTIFICATION_UUID -> tempChar = ch
                        CoolerBleConstants.STATUS_UUID -> statusChar = ch
                        CoolerBleConstants.BOOST_CONTROL_UUID -> boostChar = ch
                        CoolerBleConstants.AUTO_MODE_CONTROL_UUID -> autoChar = ch
                        CoolerBleConstants.RPM_UUID -> rpmChar = ch
                        CoolerBleConstants.POWER_UUID -> powerChar = ch
                        CoolerBleConstants.PROTECTION_UUID -> protectChar = ch
                    }
                }
            }
            Log.d(
                TAG,
                "特征: switch=${switchChar != null} fan=${fanChar != null} " +
                    "light=${lightChar != null} temp=${tempChar != null} " +
                    "status=${statusChar != null} auto=${autoChar != null} " +
                    "boost=${boostChar != null} rpm=${rpmChar != null} " +
                    "power=${powerChar != null}",
            )
            if (fanChar == null && switchChar == null) {
                Log.e(TAG, "未找到散热器特征值(1011/1012)")
                _state.update { it.copy(connection = ConnectionState.FAILED) }
                return
            }

            // 先上报已连接,初始化命令在队列中依次执行:
            // 订阅(开关/温度/状态/转速/功率/灯光) → 写散热开(0x02!) → 读风扇 → 灯光握手 → 待写 RGB
            _state.update { it.copy(connection = ConnectionState.CONNECTED) }
            scope.launch { initializeAfterConnect(g) }
        }

        /** 串行初始化(全部走队列,一个完成才发起下一个) */
        private suspend fun initializeAfterConnect(g: BluetoothGatt) {
            switchChar?.let { ch -> enableNotification(g, ch) }
            tempChar?.let { ch -> if (!enableNotification(g, ch)) Log.w(TAG, "订阅温度通知失败") }
            statusChar?.let { ch -> enableNotification(g, ch) }
            rpmChar?.let { ch -> enableNotification(g, ch) }
            powerChar?.let { ch -> enableNotification(g, ch) }
            protectChar?.let { ch -> enableNotification(g, ch) }
            lightChar?.let { ch -> enableNotification(g, ch) }
            // 关键:写散热总开关 ON(0x02)——不写它风扇不转!
            switchChar?.let { ch ->
                val ok = enqueueWrite(g, ch, byteArrayOf(CoolerBleConstants.COOLING_SWITCH_ON))
                if (ok) _state.update { it.copy(coolingOn = true) } else Log.w(TAG, "散热开关写入失败")
            }
            fanChar?.let { ch ->
                val v = readIfReadable(g, ch)
                if (v != null) handleData(ch.uuid, v)
            }
            tempChar?.let { ch ->
                val v = readIfReadable(g, ch)
                if (v != null) handleData(ch.uuid, v)
            }
            pendingRgb?.let {
                applyRgbInternal(it)
                pendingRgb = null
            }
            startPollLoop(g)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            Log.d(TAG, "onCharacteristicWrite ${characteristic.uuid} status=$status")
            writeWaiter?.complete(status)
            writeWaiter = null
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "onDescriptorWrite status=$status")
            descWaiter?.complete(status)
            descWaiter = null
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            readWaiter?.complete(status to value)
            readWaiter = null
            if (status == BluetoothGatt.GATT_SUCCESS) handleRead(characteristic.uuid, value)
        }

        /** 旧重载：Android 13 以下调用 */
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            readWaiter?.complete(status to (characteristic.value ?: ByteArray(0)))
            readWaiter = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleRead(characteristic.uuid, characteristic.value ?: ByteArray(0))
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleChanged(characteristic.uuid, value)
        }

        /** 旧重载：Android 13 以下调用 */
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            handleChanged(characteristic.uuid, characteristic.value ?: ByteArray(0))
        }

        private fun handleRead(uuid: UUID, value: ByteArray) {
            this@CoolerBleManager.handleData(uuid, value)
        }

        private fun handleChanged(uuid: UUID, value: ByteArray) {
            this@CoolerBleManager.handleData(uuid, value)
        }
    }

    // ────────────────────── GATT 串行队列原语 ──────────────────────

    /** 读回与通知的统一解析入口 */
    private fun handleData(uuid: UUID, value: ByteArray) {
        when (uuid) {
            CoolerBleConstants.TEMPERATURE_NOTIFICATION_UUID -> {
                val temp = parseTemperature(value)
                if (temp != null) {
                    lastTempUpdateMs = System.currentTimeMillis()
                    _state.update { it.copy(temperatureC = temp) }
                }
            }
            CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID -> handleFanValue(value)
            CoolerBleConstants.LIGHT_CONTROL_UUID -> handleLightValue(value)
            CoolerBleConstants.COOLING_SWITCH_UUID -> {
                // 通知: 2=开 3=关
                val on = value.firstOrNull()?.toInt() == 0x02
                _state.update { it.copy(coolingOn = on) }
            }
            CoolerBleConstants.STATUS_UUID -> handleStatusValue(value)
            CoolerBleConstants.RPM_UUID -> {
                parseBigEndianShort(value)?.let { rpm ->
                    _state.update { it.copy(fanRpm = rpm) }
                }
            }
            CoolerBleConstants.POWER_UUID -> {
                value.firstOrNull()?.let { w -> _state.update { it.copy(powerW = w.toInt()) } }
            }
            CoolerBleConstants.PROTECTION_UUID -> {
                // bit2 = 过冷保护开;[2]=高阈值 [3]=低阈值(有符号)
                if (value.isNotEmpty()) {
                    val on = (value[0].toInt() and 0x04) != 0
                    _state.update { it.copy(overcoldOn = on) }
                }
            }
            CoolerBleConstants.BOOST_CONTROL_UUID -> {
                if (value.isNotEmpty()) _state.update { it.copy(boostOn = value[0] == 1.toByte()) }
            }
            CoolerBleConstants.AUTO_MODE_CONTROL_UUID -> {
                if (value.isNotEmpty()) _state.update { it.copy(smartOn = value[0] == 1.toByte()) }
            }
        }
    }
    private fun handleStatusValue(value: ByteArray) {
        if (value.isEmpty()) return
        when (value[0].toInt() and 0xFF) {
            0x08 -> parseBigEndianShort(value.copyOfRange(1, value.size))?.let { rpm ->
                _state.update { it.copy(fanRpm = rpm) }
            }
            0x09 -> value.getOrNull(1)?.let { w ->
                _state.update { it.copy(powerW = w.toInt() and 0xFF) }
            }
        }
    }

    private fun handleFanValue(value: ByteArray) {
        val type = _state.value.deviceType ?: return
        val raw = value.firstOrNull()?.toInt() ?: return
        _state.update { it.copy(fanPercent = CoolerBleConstants.rawToPercentage(raw, type)) }
    }

    /** 灯光状态上报:byte0 = 当前灯效模式 */
    private fun handleLightValue(value: ByteArray) {
        if (value.isEmpty()) return
        val code = value[0].toInt()
        val effect = LightEffect.entries.firstOrNull { it.code.toInt() == code } ?: return
        _state.update {
            it.copy(rgb = (it.rgb ?: RGBConfig(effect)).copy(effect = effect))
        }
    }

    /** 单次写尝试 */
    @SuppressLint("MissingPermission")
    private suspend fun writeOnce(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray): Boolean {
        @Suppress("DEPRECATION")
        ch.value = value
        // 关键:先登记 waiter 再发起写——否则回调可能在 binder 线程抢先到达,
        // waiter 永远等不到 → 白白超时(初始化被拖慢、订阅被误判失败)
        val waiter = CompletableDeferred<Int>()
        writeWaiter = waiter
        val accepted = try {
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        } catch (e: Exception) {
            Log.e(TAG, "writeCharacteristic 异常: ${e.message}")
            false
        }
        if (!accepted) {
            writeWaiter = null
            return false
        }
        val status = withTimeoutOrNull(OP_TIMEOUT_MS) { waiter.await() }
        writeWaiter = null
        return status == BluetoothGatt.GATT_SUCCESS
    }

    /**
     * 串行写:加锁排队,等 onCharacteristicWrite 确认,失败/超时重试一次。
     * 必须在队列中调用——绝不可直接 gatt.writeCharacteristic。
     */
    @SuppressLint("MissingPermission")
    private suspend fun enqueueWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray): Boolean =
        gattMutex.withLock {
            var ok = writeOnce(g, ch, value)
            if (!ok) {
                delay(120)
                ok = writeOnce(g, ch, value)
            }
            if (!ok) Log.e(TAG, "写入失败(重试后) ${ch.uuid} value=${value.toHex()}")
            delay(OP_SPACING_MS)
            ok
        }

    /** 串行读:等待 onCharacteristicRead 携带数据返回;失败/超时重试一次 */
    @SuppressLint("MissingPermission")
    private suspend fun enqueueRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic): ByteArray? =
        gattMutex.withLock {
            var result = readOnce(g, ch)
            if (result == null) {
                delay(120)
                result = readOnce(g, ch)
            }
            if (result == null) Log.w(TAG, "读取失败(重试后) ${ch.uuid}")
            delay(OP_SPACING_MS)
            result
        }

    private suspend fun readOnce(g: BluetoothGatt, ch: BluetoothGattCharacteristic): ByteArray? {
        val waiter = CompletableDeferred<Pair<Int, ByteArray>>()
        readWaiter = waiter
        val accepted = try {
            g.readCharacteristic(ch)
        } catch (e: Exception) {
            Log.e(TAG, "readCharacteristic 异常: ${e.message}")
            false
        }
        if (!accepted) {
            readWaiter = null
            return null
        }
        val (status, value) = withTimeoutOrNull(READ_TIMEOUT_MS) { waiter.await() }
            ?: (BluetoothGatt.GATT_FAILURE to ByteArray(0))
        readWaiter = null
        return if (status == BluetoothGatt.GATT_SUCCESS) value else null
    }

    /**
     * 串行订阅通知:按特征属性决定 CCCD 值(实测可用方案:冰环对 NOTIFY 用
     * 通知值、INDICATE 用指示值,无两者属性则跳过——乱写会被设备拒收)。
     * 先登记 waiter 再写描述符(防回调竞态),失败/超时重试一次。
     */
    @SuppressLint("MissingPermission")
    private suspend fun enableNotification(g: BluetoothGatt, ch: BluetoothGattCharacteristic): Boolean =
        gattMutex.withLock {
            val props = ch.properties
            val hasNotify = props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            val hasIndicate = props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
            if (!hasNotify && !hasIndicate) {
                Log.d(TAG, "特征 ${ch.uuid} 无通知/指示属性,跳过订阅")
                return@withLock true
            }
            try {
                g.setCharacteristicNotification(ch, true)
            } catch (e: Exception) {
                Log.e(TAG, "setCharacteristicNotification 异常: ${e.message}")
            }
            val descriptor = ch.getDescriptor(CCC_DESCRIPTOR_UUID)
            if (descriptor == null) {
                Log.w(TAG, "特征 ${ch.uuid} 无 CCC 描述符")
                return@withLock false
            }
            var ok = subscribeOnce(g, descriptor, hasIndicate)
            if (!ok) {
                delay(120)
                ok = subscribeOnce(g, descriptor, hasIndicate)
            }
            delay(OP_SPACING_MS)
            if (!ok) Log.e(TAG, "订阅通知失败(重试后) ${ch.uuid}")
            ok
        }

    private suspend fun subscribeOnce(
        g: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        useIndication: Boolean,
    ): Boolean {
        @Suppress("DEPRECATION")
        descriptor.value = if (useIndication) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        val waiter = CompletableDeferred<Int>()
        descWaiter = waiter
        val accepted = try {
            @Suppress("DEPRECATION")
            g.writeDescriptor(descriptor)
        } catch (e: Exception) {
            Log.e(TAG, "writeDescriptor 异常: ${e.message}")
            false
        }
        if (!accepted) {
            descWaiter = null
            return false
        }
        val status = withTimeoutOrNull(OP_TIMEOUT_MS) { waiter.await() }
        descWaiter = null
        return status == BluetoothGatt.GATT_SUCCESS
    }

    // ──────────────────────────── 控制 ────────────────────────────

    /** 仅当特征具备读属性时才读(避免读只通知特征导致队列长时间超时) */
    @SuppressLint("MissingPermission")
    private suspend fun readIfReadable(g: BluetoothGatt, ch: BluetoothGattCharacteristic?): ByteArray? {
        if (ch == null) return null
        val props = ch.properties
        if (props and BluetoothGattCharacteristic.PROPERTY_READ == 0) return null
        return enqueueRead(g, ch)
    }

    /**
     * 状态轮询循环(实测可用方案):8 Pro 的温度/转速/功率不主动推送,
     * 需周期性 readCharacteristic 拉取。每轮串行读各状态特征,间隔 500ms。
     * 有控制写入(档位/灯光/智能)待执行时本轮让位,避免控制命令排队延迟。
     */
    @SuppressLint("MissingPermission")
    private fun startPollLoop(g: BluetoothGatt) {
        pollJob?.cancel()
        lastTempUpdateMs = System.currentTimeMillis() // 连接时重置,给自愈 6s 宽限期
        pollJob = scope.launch {
            while (isActive && gatt === g) {
                try {
                    val controlBusy = fanJob?.isActive == true ||
                        lightJob?.isActive == true ||
                        autoJob?.isActive == true
                    if (!controlBusy) {
                        tempChar?.let { ch -> readIfReadable(g, ch)?.let { handleData(ch.uuid, it) } }
                        statusChar?.let { ch -> readIfReadable(g, ch)?.let { handleData(ch.uuid, it) } }
                        rpmChar?.let { ch -> readIfReadable(g, ch)?.let { handleData(ch.uuid, it) } }
                        powerChar?.let { ch -> readIfReadable(g, ch)?.let { handleData(ch.uuid, it) } }
                        fanChar?.let { ch -> readIfReadable(g, ch)?.let { handleData(ch.uuid, it) } }
                        protectChar?.let { ch -> readIfReadable(g, ch)?.let { handleData(ch.uuid, it) } }

                        // 自愈:温度断流超 6s(读不出+通知不来)→ 重订阅温度特征再补读。
                        // 覆盖「订阅竞态失败后通知哑火」「读返回空」等场景。
                        if (lastTempUpdateMs > 0 &&
                            System.currentTimeMillis() - lastTempUpdateMs > 6000
                        ) {
                            Log.w(TAG, "温度断流超过 6s,尝试重订阅 + 补读")
                            tempChar?.let { ch ->
                                enableNotification(g, ch)
                                readIfReadable(g, ch)?.let { handleData(ch.uuid, it) }
                            }
                            // 即便这次没读到也刷新宽限期,避免每轮都重订阅
                            lastTempUpdateMs = System.currentTimeMillis() - 4000
                        }
                    }
                } catch (e: Exception) {
                    // 任何解析/IO 异常都不能杀死轮询循环
                    Log.e(TAG, "轮询轮次异常: ${e.message}")
                }
                delay(500)
            }
        }
    }


    /**
     * 制冷档位百分比(0–100),按型号 raw 范围换算(8 Pro: 40–80)。防抖 150ms。
     * 滑条拖动高频触发:防抖 150ms,取最终值写一次。
     */
    @SuppressLint("MissingPermission")
    fun setFanSpeed(percent: Int) {
        val type = _state.value.deviceType ?: return
        val clamped = percent.coerceIn(0, 100)
        _state.update { it.copy(fanPercent = clamped) }
        val g = gatt ?: return
        val ch = fanChar ?: return
        fanJob?.cancel()
        fanJob = scope.launch {
            delay(150)
            val raw = CoolerBleConstants.percentageToRaw(clamped, type).toByte()
            val ok = enqueueWrite(g, ch, byteArrayOf(raw))
            if (ok) Log.d(TAG, "制冷档位已写入 raw=$raw ($clamped%)")
        }
    }

    /** 兼容旧接口:按模式语义分发到独立控制 */
    @SuppressLint("MissingPermission")
    fun setFanMode(mode: FanMode) {
        when (mode) {
            FanMode.OFF -> setCooling(false)
            FanMode.MANUAL -> { setCooling(true); setSmart(false) }
            FanMode.AUTO -> { setCooling(true); setSmart(true) }
        }
    }

    /** 散热总开关(1011:0x02 开/0x03 关)——独立于档位/智能 */
    @SuppressLint("MissingPermission")
    fun setCooling(on: Boolean) {
        _state.update {
            it.copy(
                coolingOn = on,
                fanMode = if (!on) FanMode.OFF else if (it.smartOn) FanMode.AUTO else FanMode.MANUAL,
            )
        }
        val g = gatt ?: return
        val ch = switchChar ?: return
        autoJob?.cancel()
        autoJob = scope.launch {
            val cmd = if (on) CoolerBleConstants.COOLING_SWITCH_ON else CoolerBleConstants.COOLING_SWITCH_OFF
            val ok = enqueueWrite(g, ch, byteArrayOf(cmd))
            if (ok) Log.d(TAG, "散热开关已写入 0x%02X".format(cmd))
        }
    }

    /** 智能温控(1018:0x01 开/0x00 关,设备自主控制) */
    @SuppressLint("MissingPermission")
    fun setSmart(on: Boolean) {
        _state.update {
            it.copy(
                smartOn = on,
                fanMode = if (on) FanMode.AUTO else if (it.coolingOn) FanMode.MANUAL else FanMode.OFF,
            )
        }
        val g = gatt ?: return
        val ch = autoChar ?: return
        autoJob?.cancel()
        autoJob = scope.launch {
            val cmd = if (on) CoolerBleConstants.AUTO_MODE_ON else CoolerBleConstants.AUTO_MODE_OFF
            val ok = enqueueWrite(g, ch, byteArrayOf(cmd))
            if (ok) Log.d(TAG, "智能温控已写入 0x%02X".format(cmd))
        }
    }

    /** 破坏神/Boost 超频(1017:0x01 开/0x00 关) */
    @SuppressLint("MissingPermission")
    fun setBoost(on: Boolean) {
        _state.update { it.copy(boostOn = on) }
        val g = gatt ?: return
        val ch = boostChar ?: return
        scope.launch {
            val cmd: Byte = if (on) 0x01 else 0x00
            val ok = enqueueWrite(g, ch, byteArrayOf(cmd))
            if (ok) Log.d(TAG, "破坏神已写入 0x%02X".format(cmd))
        }
    }

    /** 过冷/冷凝保护(101F:[flag,0,高阈值,低阈值],flag = (开?0x04:0)|0x03) */
    @SuppressLint("MissingPermission")
    fun setOvercoldProtection(on: Boolean) {
        _state.update { it.copy(overcoldOn = on) }
        val g = gatt ?: return
        val ch = protectChar ?: return
        scope.launch {
            val payload = byteArrayOf(
                if (on) CoolerBleConstants.PROTECTION_FLAG_ON else CoolerBleConstants.PROTECTION_FLAG_OFF,
                0,
                CoolerBleConstants.PROTECTION_HIGH_DEFAULT,
                CoolerBleConstants.PROTECTION_LOW_DEFAULT,
            )
            val ok = enqueueWrite(g, ch, payload)
            if (ok) Log.d(TAG, "过冷保护已写入 ${payload.toHex()}")
        }
    }

    /** 设置 RGB 灯效(命令格式见 [RGBConfig.toCommand]),防抖 200ms */
    @SuppressLint("MissingPermission")
    fun setRGB(config: RGBConfig) {
        _state.update { it.copy(rgb = config) }
        val g = gatt ?: run { pendingRgb = config; return }
        val ch = lightChar ?: run { pendingRgb = config; return }
        applyRgbInternal(config)
    }

    @SuppressLint("MissingPermission")
    private fun applyRgbInternal(config: RGBConfig) {
        val g = gatt ?: return
        val ch = lightChar ?: return
        lightJob?.cancel()
        lightJob = scope.launch {
            delay(200)
            val cmd = config.toCommand()
            val ok = enqueueWrite(g, ch, cmd)
            if (ok) Log.d(TAG, "灯效已写入 ${cmd.toHex()}")
        }
    }

    // ──────────────────────────── 工具 ────────────────────────────

    /** 大端 16 位无符号解析(payload 长度≥2) */
    private fun parseBigEndianShort(data: ByteArray): Int? {
        if (data.size < 2) return null
        return ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
    }

    /**
     * 解析 1014 温度(多格式兜底,对齐冰环实测方案):
     * - 单字节:直接就是有符号 °C
     * - [0x04, temp]:固件 8.4.7 状态包
     * - [0x08, hi, lo] / 双字节:16 位大端——可能是开尔文也可能是补码 °C,
     *   两种都试,落在物理合理区间者胜
     * 最后统一应用官方 App 的显示校准:显示值 = raw − 6。
     */
    private fun parseTemperature(data: ByteArray): Float? {
        val raw = parseTemperatureRaw(data) ?: return null
        return raw - CoolerBleConstants.TEMPERATURE_OFFSET
    }

    private fun parseTemperatureRaw(data: ByteArray): Float? {
        if (data.isEmpty()) return null
        // 单字节:有符号 °C
        if (data.size == 1) {
            return data[0].toFloat().takeIf { it in -40f..80f }
        }
        val b0 = data[0].toInt() and 0xFF
        // [0x04, temp]:官方 8.4.7 状态温度包
        if (b0 == 0x04) {
            return data.getOrNull(1)?.toInt()?.toFloat()?.takeIf { it in -40f..80f }
        }
        // 16 位大端(tag 0x08 或裸双字节):开尔文 / 补码两种解释
        if (data.size >= 2) {
            val be16 = parseBigEndianShort(data)?.let { raw16 ->
                when {
                    // 开尔文合理区间 233..353K(−40..80°C)
                    raw16 in 233..353 -> (raw16 - 273).toFloat()
                    raw16 in 40..80 -> raw16.toFloat()
                    else -> null
                }
            }
            if (be16 != null) return be16
            // 有符号 16 位补码
            val signed = ((data[0].toInt() shl 8) or (data[1].toInt() and 0xFF)).toShort().toInt()
            if (signed in -40..80) return signed.toFloat()
        }
        // 兜底:首字节按有符号处理
        return data[0].toInt().toFloat().takeIf { it in -40..80 }
    }

    private fun BluetoothDevice.safeName(): String? =
        try { name } catch (_: SecurityException) { null }

    /** 释放资源(Service 销毁时调用) */
    fun release() {
        stopScan()
        disconnectInternal()
    }
}
