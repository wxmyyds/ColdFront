package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings

/**
 * 设置页(MD3E):
 * - 独立行用 [SegmentedListItem] + segmentedShapes(index = 0, count = 1) —— 四角 16dp,
 *   不再套 extraSmall(4dp) 的 Card;
 * - 开关行走 checked 重载(整行可点 + toggle 语义),Switch 只作视觉指示;
 * - 深色模式三档暂用 ToggleButton 行(改 ButtonGroup 属下一批),容器用 Card 默认 12dp。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: CoolerViewModel) {
    val strings = LocalStrings.current
    val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()
    val darkMode by vm.darkMode.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 外观 ──
            Text(
                strings.settingsTheme,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
            SwitchRow(
                icon = Icons.Filled.Palette,
                title = strings.settingsDynamicColor,
                desc = strings.settingsDynamicColorDesc,
                checked = dynamicColor,
                onChange = { vm.setDynamicColor(it) },
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingItem(
                        icon = Icons.Filled.DarkMode,
                        title = strings.settingsDarkMode,
                        desc = null,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val options = listOf(
                            Triple("system", Icons.Filled.AutoMode, strings.settingsDarkModeSystem),
                            Triple("light", Icons.Filled.LightMode, strings.settingsDarkModeLight),
                            Triple("dark", Icons.Filled.DarkMode, strings.settingsDarkModeDark),
                        )
                        options.forEach { (value, icon, label) ->
                            ToggleButton(
                                checked = darkMode == value,
                                onCheckedChange = { vm.setDarkMode(value) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(icon, contentDescription = label)
                            }
                        }
                    }
                }
            }

            // ── 关于 ──
            InfoRow(
                icon = Icons.Filled.Info,
                title = strings.settingsAbout,
                desc = strings.settingsAboutDesc,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * 独立开关行:[SegmentedListItem] 的 checked 重载,count = 1 → 四角 16dp。
 * Switch 的 onCheckedChange 传 null(纯指示),避免与整行点击双重交互;
 * 图标色不手写 tint,由组件按 SwitchTokens 注入 onPrimaryContainer。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    desc: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        modifier = Modifier.fillMaxWidth(),
        supportingContent = desc?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        },
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

/** 独立说明行(无交互):同样用 segmentedShapes 拿到 16dp 外角 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    desc: String?,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        modifier = Modifier.fillMaxWidth(),
        supportingContent = desc?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * 卡片内的设置行:非交互 [ListItem](content 尾随 lambda 重载,
 * 旧的 headlineContent 重载在 alpha28 已弃用),容器透明交给外层 Card。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    desc: String?,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        supportingContent = desc?.let {
            {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}
