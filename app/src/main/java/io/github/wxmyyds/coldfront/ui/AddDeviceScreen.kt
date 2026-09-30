package io.github.wxmyyds.coldfront.ui

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.domain.CoolerDevice
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.ui.i18n.AppStrings
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings

@Composable
fun AddDeviceScreen(vm: CoolerViewModel) {
    val strings = LocalStrings.current
    val btEnabled by vm.bluetoothEnabled.collectAsStateWithLifecycle()
    val devices by vm.discoveredDevices.collectAsStateWithLifecycle()
    val state by vm.liveState.collectAsStateWithLifecycle()

    // 进入即扫描，离开即停
    LaunchedEffect(btEnabled) { if (btEnabled) vm.startScan() }
    DisposableEffect(Unit) { onDispose { vm.stopScan() } }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(strings.scanTitle, style = MaterialTheme.typography.headlineMedium)

        when {
            !btEnabled -> BluetoothOffState(strings)
            devices.isEmpty() -> ScanningEmptyState(strings, scanning = state.connection == io.github.wxmyyds.coldfront.domain.ConnectionState.SCANNING) {
                vm.startScan()
            }
            else -> DeviceList(strings, devices) { vm.connect(it); vm.stopScan() }
        }

        // 8 Pro 提示横幅
        if (devices.any { it.deviceType == CoolerDeviceType.JACKET_8_PRO }) {
            EightProNotice(strings)
        }
    }
}

@Composable
private fun BluetoothOffState(strings: AppStrings) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.BluetoothDisabled, contentDescription = null, modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error)
            Text(strings.scanBluetoothOff, style = MaterialTheme.typography.titleLarge)
            Text(strings.scanBluetoothOffHint, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            }) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.scanEnableBluetooth)
            }
        }
    }
}

@Composable
private fun ScanningEmptyState(strings: AppStrings, scanning: Boolean, onRescan: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (scanning) {
                CircularProgressIndicator()
                Text(strings.scanScanning, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(strings.scanNoFound, style = MaterialTheme.typography.titleMedium)
                Text(strings.scanNoFoundHint, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onRescan) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.scanRescan)
                }
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(device.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(device.deviceType.deviceName, style = MaterialTheme.typography.bodySmall)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            if (device.matchedByName) {
                                ElevatedAssistChip(onClick = {}, label = { Text(strings.scanMatchedByName) })
                            }
                            if (!device.deviceType.uuidConfirmed) {
                                ElevatedAssistChip(onClick = {}, label = { Text(strings.scanUuidUnconfirmed) })
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${device.rssi} dBm", style = MaterialTheme.typography.labelMedium)
                        Text("${device.signalQuality}%", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.size(8.dp))
                        Button(onClick = { onConnect(device) }) { Text(strings.scanSelect) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EightProNotice(strings: AppStrings) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            strings.eightProUuidNotice,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}
