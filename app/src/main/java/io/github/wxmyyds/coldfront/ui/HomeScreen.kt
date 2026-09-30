package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.FanMode
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings

@Composable
fun HomeScreen(vm: CoolerViewModel, onAddDevice: () -> Unit) {
    val strings = LocalStrings.current
    val state by vm.liveState.collectAsStateWithLifecycle()
    val btEnabled by vm.bluetoothEnabled.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(strings.homeTitle, style = MaterialTheme.typography.headlineMedium)

        if (!state.isConnected) {
            EmptyState(strings, onAddDevice)
        } else {
            ConnectedState(
                state = state,
                onSetSpeed = vm::setFanSpeed,
                onSetMode = vm::setFanMode,
                onStartAuto = {
                    val p = profiles.firstOrNull { it.macAddress == state.deviceAddress }
                        ?: profiles.firstOrNull()
                    if (p != null) vm.startAutoMode(p)
                },
                onStopAuto = vm::stopAutoMode,
            )
        }

        if (!btEnabled && !state.isConnected) {
            Spacer(Modifier.height(8.dp))
            Text(strings.scanBluetoothOffHint, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun EmptyState(strings: io.github.wxmyyds.coldfront.ui.i18n.AppStrings, onAddDevice: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Filled.Thermostat,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(strings.homeNoDevice, style = MaterialTheme.typography.titleLarge)
            Text(strings.homeNoDeviceHint, style = MaterialTheme.typography.bodyMedium)
            ExtendedFloatingActionButton(
                onClick = onAddDevice,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(strings.homeAddDevice) },
            )
        }
    }
}

@Composable
private fun ConnectedState(
    state: CoolerLiveState,
    onSetSpeed: (Int) -> Unit,
    onSetMode: (FanMode) -> Unit,
    onStartAuto: () -> Unit,
    onStopAuto: () -> Unit,
) {
    val strings = LocalStrings.current

    // 状态卡：温度 + 连接状态
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Thermostat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(strings.homeTemp, style = MaterialTheme.typography.labelMedium)
                Text(state.temperatureText, style = MaterialTheme.typography.displaySmall)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(strings.homeSignal, style = MaterialTheme.typography.labelMedium)
                Text("${state.rssi} dBm", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    // 模式选择：关闭 / 手动 / 自动
    val modes = listOf(FanMode.OFF, FanMode.MANUAL, FanMode.AUTO)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            val label = when (mode) {
                FanMode.OFF -> strings.homeModeOff
                FanMode.MANUAL -> strings.homeModeManual
                FanMode.AUTO -> strings.homeModeAuto
            }
            SegmentedButton(
                selected = state.fanMode == mode,
                onClick = {
                    if (mode == FanMode.AUTO) onStartAuto() else { onStopAuto(); onSetMode(mode) }
                },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) { Text(label) }
        }
    }

    // 转速滑块（手动模式可用）
    if (state.fanMode != FanMode.AUTO) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Speed, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.homeFanSpeed, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text("${state.fanPercent}%", style = MaterialTheme.typography.titleMedium)
            }
            Slider(
                value = state.fanPercent.toFloat(),
                onValueChange = { onSetMode(FanMode.MANUAL); onSetSpeed(it.toInt()) },
                valueRange = 0f..100f,
            )
        }
    } else {
        // 自动模式：显示运行状态 + 停止按钮
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.AutoMode, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(strings.homeAutoRunning, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                Button(onClick = onStopAuto) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(strings.homeStopService)
                }
            }
        }
    }
}
