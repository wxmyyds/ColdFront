package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import io.github.wxmyyds.coldfront.ui.component.PageScaffold
import io.github.wxmyyds.coldfront.ui.component.SegmentedRowGap
import io.github.wxmyyds.coldfront.ui.component.LocalGlassHazeState
import io.github.wxmyyds.coldfront.ui.component.glassSource
import io.github.wxmyyds.coldfront.ui.component.segmentedRowShapes
import io.github.wxmyyds.coldfront.ui.i18n.AppStrings
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings
import io.github.wxmyyds.coldfront.ui.theme.EmphasizedTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设备页(MD3E):
 * - 主操作「添加设备」放在 [ExtendedFloatingActionButton](战术 6:主行动用 FAB)
 * - 已保存设备用非交互 Surface,分组圆角由 segmentedRowShapes(index, count) 生成
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(vm: CoolerViewModel, onAddDevice: () -> Unit) {
    val strings = LocalStrings.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val state by vm.liveState.collectAsStateWithLifecycle()
    var profileToDelete by remember { mutableStateOf<CoolerProfile?>(null) }
    val listState = rememberLazyListState()
    PageScaffold(
        title = strings.devicesTitle,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(strings.devicesAdd) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = onAddDevice,
            )
        },
    ) { inner ->
        if (profiles.isEmpty()) {
            EmptyState(
                strings,
                Modifier.padding(inner).glassSource(LocalGlassHazeState.current),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(horizontal = 16.dp)
                    .glassSource(LocalGlassHazeState.current),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(SegmentedRowGap),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = FabClearance,
                ),
            ) {
                itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
                    SavedDeviceCard(
                        strings = strings,
                        profile = profile,
                        index = index,
                        count = profiles.size,
                        state = state,
                        onConnect = { vm.connectProfile(profile) },
                        onDelete = { profileToDelete = profile },
                    )
                }
            }
        }
    }

    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text(strings.devicesDelete) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(profile.name)
                    Text(profile.macAddress)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    profileToDelete = null
                    vm.deleteProfile(profile.id)
                }) { Text(strings.delete) }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) { Text(strings.cancel) }
            },
        )
    }
}

/**
 * 已保存设备卡：保留分组形状的非交互 Surface。
 * 操作独立放在信息下方，不与设备名称、地址争抢行宽。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LoadingIndicator 在 alpha28 仍为实验 API
@Composable
private fun SavedDeviceCard(
    strings: AppStrings,
    profile: CoolerProfile,
    index: Int,
    count: Int,
    state: CoolerLiveState,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
) {
    val connected = state.isConnected && state.deviceAddress == profile.macAddress
    val connecting = state.connection in listOf(
        ConnectionState.CONNECTING, ConnectionState.DISCOVERING,
    ) && state.deviceAddress == profile.macAddress

    Surface(
        shape = segmentedRowShapes(index = index, count = count).shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CoolerArt(
                    profile.deviceType,
                    modifier = Modifier.size(40.dp),
                    iconTint = if (connected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    profile.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                profile.deviceType.deviceName + " · " + profile.macAddress,
                style = MaterialTheme.typography.bodyMedium,
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
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
                    connecting -> LoadingIndicator(Modifier.size(24.dp))
                    else -> FilledTonalButton(
                        onClick = onConnect,
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(strings.devicesConnect) }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(strings: AppStrings, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, top = 32.dp, end = 32.dp, bottom = FabClearance),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(
                modifier = Modifier.size(104.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AcUnit,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            strings.devicesEmpty,
            style = EmphasizedTypography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            strings.devicesEmptyHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 主操作「添加设备」由 Scaffold 的 ExtendedFAB 承担,此处不重复放按钮
    }
}

/** 列表底部给 ExtendedFAB 让位：FAB 56dp + Scaffold 默认 16dp 边距 + 一行呼吸空间。 */
private val FabClearance = 96.dp
