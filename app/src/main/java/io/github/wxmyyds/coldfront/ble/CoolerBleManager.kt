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
import io.github.wxmyyds.coldfront.domain.RGBConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "CoolerBleManager"
private val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/**
 * BLE 控制器:封装扫描、连接、GATT 操作与状态广播。
 *
 * 识别:优先解析厂商数据 MSD(公司 ID 0x08CA)的 [mainType, subType](8 Pro = 5,8),
 * 回退广播名匹配。协议细节见 docs/protocol-8pro.md。
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
    private var lightChar: BluetoothGattCharacteristic? = null
    private var autoChar: BluetoothGattCharacteristic? = null

    private var scanning = false
    private var pendingRgb: RGBConfig? = null

    private val _state = MutableStateFlow(CoolerLiveState(connection = ConnectionState.DISCONNECTED))
    val state: StateFlow<CoolerLiveState> = _state.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<CoolerDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<CoolerDevice>> = _discoveredDevices.asStateFlow()

    val isBluetoothEnabled: Boolean get() = bluetoothAdapter?.isEnabled == true

    // ──────────────────────────── 扫描 ────────────────────────────

    @SuppressLint("MissingPermission")
    fun startScan() {
        val sc = scanner
        if (sc == null || scanning) return
        if (!BlePermissionManager.hasScanPermission(context)) {
            Log.w(TAG, "无扫描权限，跳过 startScan")
            return
        }
        _discoveredDevices.value = emptyList()
        scanning = true
        _state.update { it.copy(connection = ConnectionState.SCANNING) }
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
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanning = false
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
        gatt = device.bluetoothDevice.connectGatt(context, false, gattCallback)
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
        gatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        disconnectInternal()
        _state.update { CoolerLiveState(connection = ConnectionState.DISCONNECTED) }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectInternal() {
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
        lightChar = null
        autoChar = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.update { it.copy(connection = ConnectionState.DISCOVERING) }
                    try {
                        g.discoverServices()
                    } catch (e: Exception) {
                        Log.e(TAG, "discoverServices 异常: ${e.message}")
                        _state.update { it.copy(connection = ConnectionState.FAILED) }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _state.update { it.copy(connection = ConnectionState.DISCONNECTED) }
                    disconnectInternal()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.update { it.copy(connection = ConnectionState.FAILED) }
                return
            }
            val service = g.getService(CoolerBleConstants.FAN_SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "未找到散热器主服务 ${CoolerBleConstants.FAN_SERVICE_UUID}")
                _state.update { it.copy(connection = ConnectionState.FAILED) }
                return
            }
            fanChar = service.getCharacteristic(CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID)
            tempChar = service.getCharacteristic(CoolerBleConstants.TEMPERATURE_NOTIFICATION_UUID)
            lightChar = service.getCharacteristic(CoolerBleConstants.LIGHT_CONTROL_UUID)
            autoChar = service.getCharacteristic(CoolerBleConstants.AUTO_MODE_CONTROL_UUID)

            // 订阅温度通知
            tempChar?.let { enableNotification(g, it) }

            _state.update { it.copy(connection = ConnectionState.CONNECTED) }

            // 读取当前转速
            fanChar?.let { runCatching { g.readCharacteristic(it) } }

            // 灯光握手(写 0x11 触发状态上报)
            lightChar?.let {
                scope.launch { writeCharacteristic(g, it, byteArrayOf(CoolerBleConstants.LIGHT_QUERY_COMMAND)) }
            }

            // 应用待写入的 RGB
            pendingRgb?.let { applyRgbInternal(it); pendingRgb = null }
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            handleRead(characteristic.uuid, value)
        }

        /** 旧重载：Android 13 以下调用 */
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            handleRead(characteristic.uuid, characteristic.value ?: ByteArray(0))
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
            when (uuid) {
                CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID -> handleFanValue(value)
            }
        }

        private fun handleChanged(uuid: UUID, value: ByteArray) {
            when (uuid) {
                CoolerBleConstants.TEMPERATURE_NOTIFICATION_UUID -> {
                    val temp = parseTemperature(value)
                    if (temp != null) _state.update { it.copy(temperatureC = temp) }
                }
                CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID -> handleFanValue(value)
            }
        }

        private fun handleFanValue(value: ByteArray) {
            val type = _state.value.deviceType ?: return
            val raw = value.firstOrNull()?.toInt() ?: return
            _state.update { it.copy(fanPercent = CoolerBleConstants.rawToPercentage(raw, type)) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        runCatching {
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID) ?: return
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    // ──────────────────────────── 控制 ────────────────────────────

    /** 设置风扇转速百分比(0–100),按该型号 raw 范围换算(8 Pro: 40–80) */
    @SuppressLint("MissingPermission")
    fun setFanSpeed(percent: Int) {
        val type = _state.value.deviceType ?: return
        val clamped = percent.coerceIn(0, 100)
        _state.update { it.copy(fanPercent = clamped, fanMode = FanMode.MANUAL) }
        val g = gatt ?: return
        val ch = fanChar ?: return
        scope.launch {
            val raw = CoolerBleConstants.percentageToRaw(clamped, type).toByte()
            writeCharacteristic(g, ch, byteArrayOf(raw))
        }
    }

    /** 设置风扇模式(自动=写 0x01,离开自动=写 0x00) */
    @SuppressLint("MissingPermission")
    fun setFanMode(mode: FanMode) {
        val wasAuto = _state.value.fanMode == FanMode.AUTO
        _state.update { it.copy(fanMode = mode) }
        val g = gatt ?: return
        when (mode) {
            FanMode.OFF -> setFanSpeed(0)
            FanMode.MANUAL -> {
                // 从自动切回手动:写自动关
                if (wasAuto) {
                    val ch = autoChar ?: return
                    scope.launch {
                        writeCharacteristic(g, ch, byteArrayOf(CoolerBleConstants.AUTO_MODE_OFF))
                    }
                }
            }
            FanMode.AUTO -> {
                val ch = autoChar ?: return
                scope.launch {
                    writeCharacteristic(g, ch, byteArrayOf(CoolerBleConstants.AUTO_MODE_ON))
                }
            }
        }
    }

    /** 设置 RGB 灯效(命令格式见 [RGBConfig.toCommand]) */
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
        scope.launch { writeCharacteristic(g, ch, config.toCommand()) }
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        ch: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        try {
            // 兼容 API 24–37:旧 setValue 路径全版本可用(高版本仅告警)
            @Suppress("DEPRECATION")
            ch.value = value
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(ch)
        } catch (e: Exception) {
            Log.e(TAG, "写入特征失败 ${ch.uuid}: ${e.message}")
        }
    }

    // ──────────────────────────── 工具 ────────────────────────────

    /**
     * 解析温度通知 payload:单字节有符号 °C,显示值 = raw − 6(官方校准)。
     */
    private fun parseTemperature(data: ByteArray): Float? {
        if (data.isEmpty()) return null
        val raw = data[0].toInt()
        val signed = if (raw > 127) raw - 256 else raw
        return (signed - CoolerBleConstants.TEMPERATURE_OFFSET).toFloat()
    }

    private fun BluetoothDevice.safeName(): String? =
        try { name } catch (_: SecurityException) { null }

    /** 释放资源(Service 销毁时调用) */
    fun release() {
        stopScan()
        disconnectInternal()
    }
}
