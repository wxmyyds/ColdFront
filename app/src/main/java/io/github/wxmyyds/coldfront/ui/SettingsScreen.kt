package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings

/**
 * 设置页(MD3E):外观分组用官方 [ListItem] + 色调图标容器,与首页/设备页同一套列表语言;
 * 深色模式用单选分段按钮(M3 stable)。
 */
@Composable
fun SettingsScreen(vm: CoolerViewModel) {
    val strings = LocalStrings.current
    val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()
    val darkMode by vm.darkMode.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        MediumFlexibleTopAppBar(title = { Text(strings.settingsTitle) })
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 外观 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column {
                    Text(
                        strings.settingsTheme,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                    SettingItem(
                        icon = Icons.Filled.Palette,
                        title = strings.settingsDynamicColor,
                        desc = strings.settingsDynamicColorDesc,
                        trailing = {
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = { vm.setDynamicColor(it) },
                            )
                        },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingItem(
                        icon = Icons.Filled.DarkMode,
                        title = strings.settingsDarkMode,
                        desc = null,
                    )
                    val options = listOf(
                        "system" to strings.settingsDarkModeSystem,
                        "light" to strings.settingsDarkModeLight,
                        "dark" to strings.settingsDarkModeDark,
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    ) {
                        options.forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = darkMode == value,
                                onClick = { vm.setDarkMode(value) },
                                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            ) { Text(label) }
                        }
                    }
                }
            }

            // ── 关于 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                SettingItem(
                    icon = Icons.Filled.Info,
                    title = strings.settingsAbout,
                    desc = strings.settingsAboutDesc,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 设置行:官方 ListItem + 色调图标容器 */
@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    desc: String?,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.titleMedium)
        },
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
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
