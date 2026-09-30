package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings
import kotlin.math.roundToInt

@Composable
fun HomeScreen(vm: CoolerViewModel, onAddDevice: () -> Unit) {
    val strings = LocalStrings.current
    val state by vm.liveState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // MD3E 弹性顶栏
        MediumFlexibleTopAppBar(
            title = { Text(strings.homeTitle) },
            actions = {
                IconButton(onClick = onAddDevice) {
                    Icon(Icons.Filled.Add, contentDescription = strings.devicesAdd)
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            when (state.connection) {
                ConnectionState.CONNECTED -> ConnectedContent(vm, state)
                ConnectionState.CONNECTING, ConnectionState.DISCOVERING ->
                    ConnectingContent(strings)
                ConnectionState.FAILED -> FailedContent(strings, onAddDevice)
                else -> NotConnectedContent(strings, onAddDevice)
            }
        }
    }
}

@Composable
private fun ConnectedContent(vm: CoolerViewModel, state: io.github.wxmyyds.coldfront.domain.CoolerLiveState) {
    val strings = LocalStrings.current
    val type = state.deviceType

    // 温度卡(含实测转速/功率)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val deviceImage = when (type) {
                io.github.wxmyyds.coldfront.domain.CoolerDeviceType.JACKET_8_PRO ->
                    io.github.wxmyyds.coldfront.R.drawable.img_cooler_8pro
                io.github.wxmyyds.coldfront.domain.CoolerDeviceType.JACKET_4 ->
                    io.github.wxmyyds.coldfront.R.drawable.img_cooler_4pro
                io.github.wxmyyds.coldfront.domain.CoolerDeviceType.JACKET_6,
                io.github.wxmyyds.coldfront.domain.CoolerDeviceType.JACKET_6_PRO ->
                    io.github.wxmyyds.coldfront.R.drawable.img_cooler_6pro
                io.github.wxmyyds.coldfront.domain.CoolerDeviceType.JACKET_1 ->
                    io.github.wxmyyds.coldfront.R.drawable.img_cooler_dual
                io.github.wxmyyds.coldfront.domain.CoolerDeviceType.JACKET_2 ->
                    io.github.wxmyyds.coldfront.R.drawable.img_cooler_turbo
                io.github.wxmyyds.coldfront.domain.CoolerDeviceType.JACKET_5 ->
                    io.github.wxmyyds.coldfront.R.drawable.img_cooler_5pro
                io.github.wxmyyds.coldfront.domain.CoolerDeviceType.JACKET_3 ->
                    io.github.wxmyyds.coldfront.R.drawable.img_cooler_gen3
                else -> 0
            }
            if (deviceImage != 0) {
                Image(
                    painter = painterResource(deviceImage),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                )
            } else {
                Icon(Icons.Filled.Thermostat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(strings.homeTemp, style = MaterialTheme.typography.labelMedium)
                Text(
                    state.temperatureText,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                if (state.fanRpm != null || state.powerW != null) {
                    Text(
                        buildString {
                            state.fanRpm?.let { append("$it RPM") }
                            if (state.fanRpm != null && state.powerW != null) append(" · ")
                            state.powerW?.let { append("$it W") }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(state.deviceName ?: "", style = MaterialTheme.typography.labelMedium)
                Text("${state.rssi} dBm", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    // 独立控制卡(MD3E 弹簧尺寸动画:开/关滑条平滑展开收起)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
            )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ControlRow(
                icon = { Icon(Icons.Filled.AcUnit, null, tint = MaterialTheme.colorScheme.primary) },
                title = strings.homeCoolingSwitch,
                desc = strings.homeCoolingSwitchDesc,
                checked = state.coolingOn,
                enabled = true,
                onChange = { vm.setCooling(it) },
            )
            ControlRow(
                icon = { Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.primary) },
                title = strings.homeBoost,
                desc = strings.homeBoostDesc,
                checked = state.boostOn,
                enabled = state.coolingOn,
                onChange = { vm.setBoost(it) },
            )
            ControlRow(
                icon = { Icon(Icons.Filled.Thermostat, null, tint = MaterialTheme.colorScheme.primary) },
                title = strings.homeSmart,
                desc = strings.homeSmartDesc,
                checked = state.smartOn,
                enabled = state.coolingOn,
                onChange = { vm.setSmart(it) },
            )
            ControlRow(
                icon = { Icon(Icons.Filled.AcUnit, null, tint = MaterialTheme.colorScheme.primary) },
                title = strings.homeOvercold,
                desc = strings.homeOvercoldDesc,
                checked = state.overcoldOn,
                enabled = true,
                onChange = { vm.setOvercoldProtection(it) },
            )
        }
    }

    // 制冷档位
    if (state.coolingOn && state.smartOn) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                strings.homeSmartActiveLevel,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
    } else if (state.coolingOn) {
        Column(
            modifier = Modifier.animateContentSize(
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Speed, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.homeLevel, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    if (type?.generation == 8) strings.homeLevelGear.format(levelToGear(state.fanPercent))
                    else strings.homeLevelPercent.format(state.fanPercent),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (type?.generation == 8) {
                // 8 Pro:8 档离散滑条
                Slider(
                    value = levelToGear(state.fanPercent).toFloat(),
                    onValueChange = { vm.setFanSpeed(gearToLevel(it.roundToInt())) },
                    valueRange = 1f..8f,
                    steps = 6,
                )
            } else {
                Slider(
                    value = state.fanPercent.toFloat(),
                    onValueChange = { vm.setFanSpeed(it.toInt()) },
                    valueRange = 0f..100f,
                )
            }
        }
    }
}

/** 8 Pro:百分比 → 1..8 档 */
private fun levelToGear(percent: Int): Int = ((percent + 12) / 13f).toInt().coerceIn(1, 8)

/** 8 Pro:档位 → 百分比(中心值) */
private fun gearToLevel(gear: Int): Int = ((gear - 0.5f) / 8f * 100f).roundToInt()

@Composable
private fun ControlRow(
    icon: @Composable () -> Unit,
    title: String,
    desc: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectingContent(strings: io.github.wxmyyds.coldfront.ui.i18n.AppStrings) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // MD3E 形态渐变加载指示器
            LoadingIndicator(Modifier.size(32.dp))
            Text(strings.homeConnecting, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FailedContent(
    strings: io.github.wxmyyds.coldfront.ui.i18n.AppStrings,
    onAddDevice: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(strings.homeConnectionFailed, style = MaterialTheme.typography.titleMedium)
            Text(
                strings.homeRetryHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onAddDevice, shapes = ButtonDefaults.shapes()) { Text(strings.homeGoScan) }
        }
    }
}

@Composable
private fun NotConnectedContent(
    strings: io.github.wxmyyds.coldfront.ui.i18n.AppStrings,
    onAddDevice: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.AcUnit,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(strings.homeNotConnected, style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onAddDevice, shapes = ButtonDefaults.shapes()) { Text(strings.homeGoScan) }
        }
    }
}
