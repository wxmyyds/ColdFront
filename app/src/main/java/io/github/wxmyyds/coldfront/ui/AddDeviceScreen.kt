package io.github.wxmyyds.coldfront.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.ble.BlePermissionManager
import io.github.wxmyyds.coldfront.ble.BleScanDiagnostic
import io.github.wxmyyds.coldfront.domain.CoolerDevice
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.ui.i18n.AppStrings
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings

@Composable
fun AddDeviceScreen(vm: CoolerViewModel) {
    val strings = LocalStrings.current
    val btEnabled by vm.bluetoothEnabled.collectAsStateWithLifecycle()
    val devices by vm.discoveredDevices.collectAsStateWithLifecycle()
    val rawDevices by vm.rawDevices.collectAsStateWithLifecycle()
    val scanState by vm.scanState.collectAsStateWithLifecycle()
    val state by vm.liveState.collectAsStateWithLifecycle()
    var diagMode by remember { mutableStateOf(false) }
    var connectTarget by remember { mutableStateOf<BleScanDiagnostic?>(null) }
    val context = LocalContext.current

    // 重新授权启动器（授权后刷新条件，扫描由下方 LaunchedEffect 重启）
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshBluetoothState() }

    // 进入即扫描；权限/蓝牙状态变化时也重启（修复授权后扫描不启动的问题）
    LaunchedEffect(btEnabled, scanState.permissionGranted) {
        if (btEnabled && scanState.permissionGranted) vm.startScan()
    }
    DisposableEffect(Unit) { onDispose { vm.stopScan() } }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(strings.scanTitle, style = MaterialTheme.typography.headlineMedium)

        when {
            !btEnabled -> BluetoothOffState(strings)
            diagMode -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScanStatusCard(
                    strings = strings,
                    scanState = scanState,
                    onGrant = { permLauncher.launch(BlePermissionManager.scanPermissionsToRequest()) },
                    onAppSettings = {
                        context.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:${context.packageName}"),
                            )
                        )
                    },
                    onLocation = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                )
                DiagnosticList(
                    strings = strings,
                    rawDevices = rawDevices,
                    onSelect = { connectTarget = it },
                )
            }
            devices.isEmpty() -> ScanningEmptyState(strings, scanning = state.connection == io.github.wxmyyds.coldfront.domain.ConnectionState.SCANNING) {
                vm.startScan()
            }
            else -> DeviceList(strings, devices) { vm.connect(it); vm.stopScan() }
        }

        if (!btEnabled) {
            // 蓝牙关闭时不显示切换
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = diagMode,
                    onClick = { diagMode = !diagMode },
                    label = { Text(strings.diagToggle) },
                )
            }
            if (diagMode) {
                Text(
                    strings.diagHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // 手动选型号连接对话框
    connectTarget?.let { entry ->
        ConnectAsDialog(
            strings = strings,
            onDismiss = { connectTarget = null },
            onConfirm = { type ->
                vm.connectRaw(entry, type)
                vm.stopScan()
                connectTarget = null
            },
        )
    }
}

/** 扫描条件状态卡：权限/扫描器/定位/蓝牙，一项异常给对应修复按钮 */
@Composable
private fun ScanStatusCard(
    strings: AppStrings,
    scanState: io.github.wxmyyds.coldfront.ble.ScanState,
    onGrant: () -> Unit,
    onAppSettings: () -> Unit,
    onLocation: () -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                strings.diagStatusPermission + ": " +
                    if (scanState.permissionGranted) strings.diagPermissionGranted
                    else strings.diagPermissionMissing,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (scanState.permissionGranted) FontWeight.Normal else FontWeight.Bold,
                color = if (scanState.permissionGranted) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.error,
            )
            if (!scanState.permissionGranted) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onGrant) { Text(strings.diagGrantAgain) }
                    OutlinedButton(onClick = onAppSettings) { Text(strings.diagOpenAppSettings) }
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
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (scanState.locationServiceEnabled) FontWeight.Normal else FontWeight.Bold,
                color = if (scanState.locationServiceEnabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.error,
            )
            if (!scanState.locationServiceEnabled) {
                OutlinedButton(onClick = onLocation) { Text(strings.diagOpenLocation) }
            }
            Text(
                strings.diagStatusBluetooth + ": " +
                    if (scanState.bluetoothOn) strings.diagServiceOn else strings.diagServiceOff,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DiagnosticList(
    strings: AppStrings,
    rawDevices: List<BleScanDiagnostic>,
    onSelect: (BleScanDiagnostic) -> Unit,
) {
    if (rawDevices.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(Modifier.size(40.dp))
            Text(strings.diagNoSignal, style = MaterialTheme.typography.titleMedium)
            Text(
                strings.diagNoSignalHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        return
    }
    Text(
        strings.diagRawCount.format(rawDevices.size),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rawDevices, key = { it.address }) { entry ->
            DiagnosticCard(strings, entry, onSelect)
        }
    }
}

@Composable
private fun DiagnosticCard(
    strings: AppStrings,
    entry: BleScanDiagnostic,
    onSelect: (BleScanDiagnostic) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(entry) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.name ?: strings.diagNoName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${entry.rssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                entry.address,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
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
    Row {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectAsDialog(
    strings: AppStrings,
    onDismiss: () -> Unit,
    onConfirm: (CoolerDeviceType) -> Unit,
) {
    var selected by remember { mutableStateOf(CoolerDeviceType.JACKET_8_PRO) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.diagConnectAs) },
        text = {
            LazyColumn {
                items(CoolerDeviceType.entries) { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = type },
                    ) {
                        RadioButton(selected = selected == type, onClick = { selected = type })
                        Column {
                            Text(type.deviceName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "MSD (0x%02X, 0x%02X) · raw %d–%d".format(
                                    type.mainType, type.subType, type.rawMin, type.rawMax,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected) }) { Text(strings.scanSelect) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(strings.diagCancel) }
        },
    )
}

@Composable
private fun BluetoothOffState(strings: AppStrings) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }) { Text(strings.scanEnableBluetooth) }
        }
    }
}

@Composable
private fun ScanningEmptyState(strings: AppStrings, scanning: Boolean, onRescan: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (scanning) {
                CircularProgressIndicator(Modifier.size(48.dp))
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
            OutlinedButton(onClick = onRescan) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.scanRescan)
            }
        }
    }
}

@Composable
private fun DeviceList(
    strings: AppStrings,
    devices: List<CoolerDevice>,
    onConnect: (CoolerDevice) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(devices, key = { it.address }) { device ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onConnect(device) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${device.deviceType.suggestedIcon} ${device.displayName}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        device.deviceType.deviceName + " · ${device.rssi} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        if (device.matchedByName) {
                            ElevatedAssistChip(onClick = {}, label = { Text(strings.scanMatchedByName) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EightProNotice(strings: AppStrings) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            strings.eightProUuidNotice,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}
