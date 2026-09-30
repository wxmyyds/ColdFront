package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.ui.component.RowIcon
import io.github.wxmyyds.coldfront.ui.component.SegmentedGroup
import io.github.wxmyyds.coldfront.ui.component.SegmentedRadioRow
import io.github.wxmyyds.coldfront.ui.component.SegmentedRow
import io.github.wxmyyds.coldfront.ui.component.SegmentedSwitchRow
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings

/**
 * 设置页(MD3E 分段选项列表):
 * - 外观组 = 1 个开关行 + 3 个单选行,同一组内共享外角 16dp / 内角 4dp;
 * - 深色模式改单选行(leading RadioButton),替掉原来无标签的图标 ToggleButton 行;
 * - 关于组是独立单行(count = 1 → 四角 16dp)。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: CoolerViewModel) {
    val strings = LocalStrings.current
    val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()
    val darkMode by vm.darkMode.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val darkModeOptions = listOf(
        "system" to strings.settingsDarkModeSystem,
        "light" to strings.settingsDarkModeLight,
        "dark" to strings.settingsDarkModeDark,
    )

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
            SegmentedGroup(title = strings.settingsTheme) {
                item(key = "dynamicColor") {
                    SegmentedSwitchRow(
                        title = strings.settingsDynamicColor,
                        summary = strings.settingsDynamicColorDesc,
                        checked = dynamicColor,
                        onCheckedChange = { vm.setDynamicColor(it) },
                        leadingContent = { RowIcon(Icons.Filled.Palette) },
                    )
                }
                darkModeOptions.forEach { (value, label) ->
                    item(key = "darkMode-$value") {
                        SegmentedRadioRow(
                            title = label,
                            selected = darkMode == value,
                            onClick = { vm.setDarkMode(value) },
                        )
                    }
                }
            }

            SegmentedGroup {
                item(key = "about") {
                    SegmentedRow(
                        title = strings.settingsAbout,
                        summary = strings.settingsAboutDesc,
                        leadingContent = { RowIcon(Icons.Filled.Info) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
