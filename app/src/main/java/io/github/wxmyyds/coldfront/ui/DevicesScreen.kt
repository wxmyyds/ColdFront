package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设备页:显示连接过的(已保存)设备,支持一键直连/删除;
 * 「添加设备」按钮进入扫描页。
 */
@Composable
fun DevicesScreen(vm: CoolerViewModel, onAddDevice: () -> Unit) {
    val strings = LocalStrings.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val state by vm.liveState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                strings.devicesTitle,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onAddDevice) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(0.dp))
                Text(strings.devicesAdd)
            }
        }

        if (profiles.isEmpty()) {
            EmptyState(strings, onAddDevice)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    SavedDeviceCard(
                        strings = strings,
                        profile = profile,
                        state = state,
                        onConnect = { vm.connectProfile(profile) },
                        onDelete = { vm.deleteProfile(profile.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedDeviceCard(
    strings: io.github.wxmyyds.coldfront.ui.i18n.AppStrings,
    profile: CoolerProfile,
    state: CoolerLiveState,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
) {
    val connected = state.isConnected && state.deviceAddress == profile.macAddress
    val connecting = state.connection in listOf(
        ConnectionState.CONNECTING, ConnectionState.DISCOVERING,
    ) && state.deviceAddress == profile.macAddress
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val deviceImage = when (profile.deviceType) {
                io.github.wxmyyds.coldfront.domain.CoolerDeviceType.JACKET_8_PRO ->
                    io.github.wxmyyds.coldfront.R.drawable.img_cooler_8pro
                io.github.wxmyyds.coldfront.domain.CoolerDeviceType.JACKET_4 ->
                    io.github.wxmyyds.coldfront.R.drawable.img_cooler_4pro
                else -> 0
            }
            if (deviceImage != 0) {
                Image(
                    painter = painterResource(deviceImage),
                    contentDescription = profile.displayName,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Text(
                    profile.deviceType.suggestedIcon,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    profile.deviceType.deviceName + " · " + profile.macAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (profile.lastConnectedAtMs > 0) {
                    Text(
                        strings.devicesLastSeen.format(
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(Date(profile.lastConnectedAtMs))
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = strings.devicesDelete,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                connected -> Text(
                    strings.devicesConnected,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
                connecting -> Text(
                    "…",
                    style = MaterialTheme.typography.labelLarge,
                )
                else -> OutlinedButton(onClick = onConnect) {
                    Text(strings.devicesConnect)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    strings: io.github.wxmyyds.coldfront.ui.i18n.AppStrings,
    onAddDevice: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.AcUnit,
            contentDescription = null,
            modifier = Modifier.padding(top = 48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(strings.devicesEmpty, style = MaterialTheme.typography.titleMedium)
        Text(
            strings.devicesEmptyHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAddDevice) { Text(strings.devicesAdd) }
    }
}
