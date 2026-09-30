package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import io.github.wxmyyds.coldfront.ui.component.SegmentedRow
import io.github.wxmyyds.coldfront.ui.component.SegmentedRowGap
import io.github.wxmyyds.coldfront.ui.component.segmentedRowShapes
import io.github.wxmyyds.coldfront.ui.i18n.AppStrings
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设备页(MD3E):
 * - 主操作「添加设备」放在 [ExtendedFloatingActionButton](战术 6:主行动用 FAB)
 * - 已保存设备行用分段选项行(SegmentedRow),分组圆角由 segmentedRowShapes(index, count) 生成
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(vm: CoolerViewModel, onAddDevice: () -> Unit) {
    val strings = LocalStrings.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val state by vm.liveState.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(strings.devicesTitle) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(strings.devicesAdd) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = onAddDevice,
            )
        },
    ) { inner ->
        if (profiles.isEmpty()) {
            EmptyState(strings, Modifier.padding(inner))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(SegmentedRowGap),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 8.dp,
                    bottom = 96.dp, // 给 FAB 让位
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
                        onDelete = { vm.deleteProfile(profile.id) },
                    )
                }
            }
        }
    }
}

/**
 * 已保存设备行：分段选项行（非交互）。
 * 行本身不可点，操作全部放在 trailing 的图标按钮/按钮里——
 * 避开规范禁止的「可操作面上再放操作」。
 */
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

    SegmentedRow(
        title = profile.name,
        shapes = segmentedRowShapes(index = index, count = count),
        modifier = Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        supportingContent = {
            Column {
                Text(
                    profile.deviceType.deviceName + " · " + profile.macAddress,
                    style = MaterialTheme.typography.bodyMedium,
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
        },
        leadingContent = {
            CoolerArt(
                profile.deviceType,
                modifier = Modifier.size(40.dp),
                iconTint = if (connected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = strings.devicesDelete,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(4.dp))
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
        },
    )
}

@Composable
private fun EmptyState(strings: AppStrings, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
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
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
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
