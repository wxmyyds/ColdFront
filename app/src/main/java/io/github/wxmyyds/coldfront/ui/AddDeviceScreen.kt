package io.github.wxmyyds.coldfront.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.ble.BlePermissionManager
import io.github.wxmyyds.coldfront.ble.BleScanDiagnostic
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerDevice
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.ui.component.SegmentedRow
import io.github.wxmyyds.coldfront.ui.component.SegmentedSwitchRow
import io.github.wxmyyds.coldfront.ui.component.segmentedRowShapes
import io.github.wxmyyds.coldfront.ui.i18n.AppStrings
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings
import io.github.wxmyyds.coldfront.ui.theme.EmphasizedTypography

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(vm: CoolerViewModel, onBack: () -> Unit = {}) {
    val strings = LocalStrings.current
    val btEnabled by vm.bluetoothEnabled.collectAsStateWithLifecycle()
    val devices by vm.discoveredDevices.collectAsStateWithLifecycle()
    val rawDevices by vm.rawDevices.collectAsStateWithLifecycle()
    val scanState by vm.scanState.collectAsStateWithLifecycle()
    val state by vm.liveState.collectAsStateWithLifecycle()
    var diagMode by rememberSaveable { mutableStateOf(false) }
    var connectTarget by remember { mutableStateOf<BleScanDiagnostic?>(null) }
    var connectionRequest by remember(vm) { mutableStateOf<ScanConnectionRequest?>(null) }
    var resumed by remember(vm) { mutableStateOf(false) }
    var scanRequested by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val locationRequired = Build.VERSION.SDK_INT <= Build.VERSION_CODES.R
    val locationReady = !locationRequired || scanState.locationServiceEnabled
    val scanReady = scanState.permissionGranted && btEnabled && scanState.bluetoothOn && locationReady
    val connecting = state.connection == ConnectionState.CONNECTING ||
        state.connection == ConnectionState.DISCOVERING
    val canConnect = resumed && scanReady && !connecting

    fun connect(request: ScanConnectionRequest) {
        // Check the source too: a second tap can arrive before the StateFlow recomposes this screen.
        val current = vm.liveState.value.connection
        val conditions = vm.scanState.value
        if (!resumed || !conditions.permissionGranted || !vm.bluetoothEnabled.value ||
            !conditions.bluetoothOn || (locationRequired && !conditions.locationServiceEnabled) ||
            current == ConnectionState.CONNECTING || current == ConnectionState.DISCOVERING
        ) return
        connectionRequest = request
        scanRequested = false
        connectTarget = null
        vm.stopScan()
        request.connect()
    }

    fun rescan() {
        vm.refreshBluetoothState()
        val conditions = vm.scanState.value
        val current = vm.liveState.value.connection
        if (resumed && conditions.permissionGranted && vm.bluetoothEnabled.value &&
            conditions.bluetoothOn && (!locationRequired || conditions.locationServiceEnabled) &&
            current != ConnectionState.CONNECTING && current != ConnectionState.DISCOVERING
        ) {
            scanRequested = true
            vm.stopScan()
            vm.startScan()
        }
    }

    LaunchedEffect(connectionRequest) {
        if (connectionRequest != null) listState.animateScrollToItem(0)
    }

    // 重新授权启动器（授权后刷新条件，扫描由下方 LaunchedEffect 重启）
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshBluetoothState() }

    // Settings/permission screens can pause us without removing this composition.
    LifecycleResumeEffect(vm) {
        vm.refreshBluetoothState()
        resumed = true
        onPauseOrDispose {
            resumed = false
            vm.stopScan()
        }
    }
    LaunchedEffect(
        vm, resumed, btEnabled, scanState.bluetoothOn, scanState.permissionGranted,
        scanState.locationServiceEnabled, connecting, scanRequested,
    ) {
        // Re-read after refresh: lifecycle-collected UI values may still be one frame behind.
        val conditions = vm.scanState.value
        val current = vm.liveState.value.connection
        if (resumed && scanRequested && scanReady && conditions.permissionGranted &&
            vm.bluetoothEnabled.value && conditions.bluetoothOn &&
            (!locationRequired || conditions.locationServiceEnabled) &&
            current != ConnectionState.CONNECTING && current != ConnectionState.DISCOVERING
        ) {
            vm.startScan()
        } else {
            vm.stopScan()
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // MD3E 小顶栏 + 返回导航:标题走默认 TitleLarge,滚动后容器转 surfaceContainer
            TopAppBar(
                title = { Text(strings.scanTitle) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Recovery is always first, including permission denial with Bluetooth also off.
            if (diagMode || !scanReady || scanState.errorCode != null) {
                item(key = "scanStatus") {
                    ScanStatusCard(
                        strings = strings,
                        scanState = scanState,
                        locationRequired = locationRequired,
                        onGrant = { permLauncher.launch(BlePermissionManager.scanPermissionsToRequest()) },
                        onAppSettings = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.parse("package:${context.packageName}"),
                                )
                            )
                        },
                        onLocation = {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        },
                    )
                }
            }
            if (connectionRequest != null || connecting || state.connection == ConnectionState.FAILED) {
                item(key = "connectionStatus") {
                    ConnectionStatusCard(
                        strings = strings,
                        connection = state.connection,
                        deviceName = connectionRequest?.name ?: state.deviceName,
                        retryEnabled = canConnect && connectionRequest != null,
                        onRetry = { connectionRequest?.let { connect(it) } },
                    )
                }
            }
            when {
                !scanState.permissionGranted -> Unit // The status card owns permission recovery.
                !btEnabled || !scanState.bluetoothOn -> item(key = "bluetoothOff") {
                    BluetoothOffState(strings)
                }
                !locationReady -> Unit // Android <= 30 must enable location before scanning.
                diagMode -> {
                    if (rawDevices.isEmpty()) {
                        item(key = "diagnosticEmpty") {
                            DiagnosticEmptyState(strings, scanning = scanState.scanning)
                        }
                    } else {
                        item(key = "diagnosticCount") {
                            Text(
                                strings.diagRawCount.format(rawDevices.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(rawDevices, key = { "raw:${it.address}" }) { entry ->
                            DiagnosticCard(strings, entry, enabled = canConnect) {
                                val known = entry.coolerType
                                if (known != null) {
                                    connect(ScanConnectionRequest(entry.name ?: entry.address) {
                                        vm.connectRaw(entry, known)
                                    })
                                } else if (canConnect) {
                                    connectTarget = entry
                                }
                            }
                        }
                    }
                }
                devices.isEmpty() -> item(key = "scanEmpty") {
                    ScanningEmptyState(strings, scanning = scanState.scanning)
                }
                else -> itemsIndexed(devices, key = { _, device -> "device:${device.address}" }) { index, device ->
                    DeviceRow(strings, device, index, devices.size, enabled = canConnect) {
                        connect(ScanConnectionRequest(device.displayName) { vm.connect(device) })
                    }
                }
            }
            if (scanReady) {
                item(key = "rescan") {
                    OutlinedButton(
                        onClick = { rescan() },
                        enabled = resumed && !connecting,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.scanRescan)
                    }
                }
            }
            item(key = "diagMode") {
                SegmentedSwitchRow(
                    title = strings.diagToggle,
                    summary = if (diagMode) strings.diagHint else null,
                    checked = diagMode,
                    onCheckedChange = { diagMode = it },
                    shapes = segmentedRowShapes(index = 0, count = 1),
                )
            }
        }
    }

    // 手动选型号连接对话框
    connectTarget?.let { entry ->
        ConnectAsDialog(
            strings = strings,
            onDismiss = { connectTarget = null },
            enabled = canConnect,
            onConfirm = { type ->
                connect(ScanConnectionRequest(entry.name ?: entry.address) { vm.connectRaw(entry, type) })
            },
        )
    }
}

private class ScanConnectionRequest(val name: String, val connect: () -> Unit)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectionStatusCard(
    strings: AppStrings,
    connection: ConnectionState,
    deviceName: String?,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
) {
    val connecting = connection == ConnectionState.CONNECTING || connection == ConnectionState.DISCOVERING
    val failed = !connecting && connection != ConnectionState.CONNECTED
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            deviceName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            if (connecting) LoadingIndicator(Modifier.size(32.dp))
            Text(
                when {
                    connecting -> strings.homeConnecting
                    failed -> strings.homeConnectionFailed
                    else -> strings.homeConnected
                },
                style = EmphasizedTypography.titleSmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (failed) {
                Text(strings.homeRetryHint, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onRetry, enabled = retryEnabled, shapes = ButtonDefaults.shapes()) {
                    Text(strings.retry)
                }
            }
        }
    }
}

/** 扫描条件状态卡：权限/扫描器/定位/蓝牙，一项异常给对应修复按钮 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScanStatusCard(
    strings: AppStrings,
    scanState: io.github.wxmyyds.coldfront.ble.ScanState,
    locationRequired: Boolean,
    onGrant: () -> Unit,
    onAppSettings: () -> Unit,
    onLocation: () -> Unit,
) {
    val locationBlocked = locationRequired && !scanState.locationServiceEnabled
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                strings.diagStatusPermission + ": " +
                    if (scanState.permissionGranted) strings.diagPermissionGranted
                    else strings.diagPermissionMissing,
                style = if (scanState.permissionGranted) {
                    MaterialTheme.typography.bodySmall
                } else {
                    // 异常项用 emphasized 加重，而不是在调用点散写字重
                    EmphasizedTypography.bodySmall
                },
                color = if (scanState.permissionGranted) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.error,
            )
            if (!scanState.permissionGranted) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onGrant, shapes = ButtonDefaults.shapes()) { Text(strings.diagGrantAgain) }
                    OutlinedButton(onClick = onAppSettings, shapes = ButtonDefaults.shapes()) { Text(strings.diagOpenAppSettings) }
                }
            }
            Text(
                strings.diagStatusScanner + ": " + when {
                    scanState.errorCode != null -> strings.diagScannerFailed.format(scanState.errorCode)
                    scanState.scanning -> strings.diagScannerRunning
                    else -> strings.diagScannerStopped
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                strings.diagStatusLocation + ": " +
                    if (scanState.locationServiceEnabled) strings.diagServiceOn
                    else strings.diagServiceOff,
                style = if (!locationBlocked) {
                    MaterialTheme.typography.bodySmall
                } else {
                    EmphasizedTypography.bodySmall
                },
                color = if (!locationBlocked) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.error,
            )
            if (locationBlocked) {
                OutlinedButton(onClick = onLocation, shapes = ButtonDefaults.shapes()) { Text(strings.diagOpenLocation) }
            }
            Text(
                strings.diagStatusBluetooth + ": " +
                    if (scanState.bluetoothOn) strings.diagServiceOn else strings.diagServiceOff,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiagnosticEmptyState(strings: AppStrings, scanning: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (scanning) LoadingIndicator(Modifier.size(40.dp))
        Text(strings.diagNoSignal, style = MaterialTheme.typography.titleMedium)
        Text(
            strings.diagNoSignalHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun DiagnosticCard(
    strings: AppStrings,
    entry: BleScanDiagnostic,
    enabled: Boolean,
    onSelect: (BleScanDiagnostic) -> Unit,
) {
    val coolerType = entry.coolerType
    val isCooler = coolerType != null
    Card(
        // Card 官方 onClick 重载：整卡涟漪 + 按钮语义 + 状态层，不再 Modifier.clickable 手工拼
        onClick = { onSelect(entry) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isCooler) {
                // 已识别的散热器是扫描结果的「重点目标」，用对比强调角色；
                // secondaryContainer 语义是次要/低强调，与此意图相反。
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = entry.name ?: strings.diagNoName,
                style = EmphasizedTypography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (isCooler) {
                    // 静态徽标保持不可交互，避免在可选择的设备卡内产生假按钮语义。
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(
                            text = "${coolerType.suggestedIcon} " +
                                strings.diagCoolerBadge.format(coolerType.deviceName),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(
                    "${entry.rssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                entry.address,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.msd.isNotEmpty()) {
                entry.msd.forEach { (companyId, hex) ->
                    DetailLine("${strings.diagMsd} 0x%04X".format(companyId), hex)
                }
            }
            if (entry.serviceData.isNotEmpty()) {
                entry.serviceData.forEach { (uuid, hex) ->
                    DetailLine(strings.diagServiceData, "$uuid = $hex")
                }
            }
            if (entry.serviceUuids.isNotEmpty()) {
                entry.serviceUuids.take(3).forEach {
                    DetailLine(strings.diagServiceUuids, it)
                }
                if (entry.serviceUuids.size > 3) {
                    DetailLine(strings.diagServiceUuids, "+${entry.serviceUuids.size - 3} more")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            softWrap = true,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectAsDialog(
    strings: AppStrings,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CoolerDeviceType) -> Unit,
) {
    var selected by remember { mutableStateOf(CoolerDeviceType.JACKET_8_PRO) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.diagConnectAs) },
        text = {
            LazyColumn(Modifier.selectableGroup()) {
                items(CoolerDeviceType.entries) { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == type,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { selected = type },
                            )
                            .sizeIn(minHeight = 48.dp)
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(selected = selected == type, onClick = null, enabled = enabled)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(type.deviceName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "MSD (0x%02X, 0x%02X) · raw %d–%d".format(
                                    type.mainType, type.subType, type.rawMin, type.rawMax,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected) }, enabled = enabled, shapes = ButtonDefaults.shapes()) { Text(strings.scanSelect) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(strings.diagCancel) }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BluetoothOffState(strings: AppStrings) {
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Filled.BluetoothDisabled,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(strings.scanBluetoothOff, style = MaterialTheme.typography.titleMedium)
            Text(
                strings.scanBluetoothOffHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                shapes = ButtonDefaults.shapes(),
            ) { Text(strings.scanEnableBluetooth) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScanningEmptyState(strings: AppStrings, scanning: Boolean) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (scanning) {
                LoadingIndicator(Modifier.size(48.dp))
                Text(strings.scanScanning, style = MaterialTheme.typography.titleMedium)
            } else {
                Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(strings.scanNoFound, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                strings.scanNoFoundHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeviceRow(
    strings: AppStrings,
    device: CoolerDevice,
    index: Int,
    count: Int,
    enabled: Boolean,
    onConnect: () -> Unit,
) {
    SegmentedRow(
        title = device.displayName,
        summary = device.deviceType.deviceName + " · ${device.rssi} dBm",
        overline = if (device.matchedByName) strings.scanMatchedByName else null,
        enabled = enabled,
        onClick = onConnect,
        shapes = segmentedRowShapes(index = index, count = count),
        leadingContent = {
            CoolerArt(
                device.deviceType,
                modifier = Modifier.size(40.dp),
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
