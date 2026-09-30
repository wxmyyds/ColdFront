package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.ui.component.RowIcon
import io.github.wxmyyds.coldfront.ui.component.SegmentedDropdownRow
import io.github.wxmyyds.coldfront.ui.component.SegmentedGroup
import io.github.wxmyyds.coldfront.ui.component.SegmentedRow
import io.github.wxmyyds.coldfront.ui.component.SegmentedSwitchRow
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings

/**
 * 设置页(MD3E 分段选项列表):
 * - 外观组 = 动态取色开关行 + 「主题模式」下拉行；
 * - 通用组 = 「语言」下拉行 + 关于行。
 * 三档主题、三档语言都收成一行 + 下拉菜单：选项本身没有需要常驻展示的信息，
 * 铺成多行只是把页面拉长。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: CoolerViewModel) {
    val strings = LocalStrings.current
    val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()
    val darkMode by vm.darkMode.collectAsStateWithLifecycle()
    val appLanguage by vm.appLanguage.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // (存储值, 展示文案)；MainActivity 按同一套 "system"/"light"/"dark" 解析
    val themeOptions = listOf(
        "system" to strings.settingsFollowSystem,
        "light" to strings.settingsDarkModeLight,
        "dark" to strings.settingsDarkModeDark,
    )
    // 语言选项用自称(endonym)：不管当前界面是什么语言，用户都能认出自己的语言，
    // 所以 "中文"/"English" 故意不进双语表。
    val languageOptions = listOf(
        "system" to strings.settingsFollowSystem,
        "zh" to "中文",
        "en" to "English",
    )
    // 存储里出现意外值时按「跟随系统」呈现，避免下拉行显示空白
    val themeMode = if (themeOptions.any { it.first == darkMode }) darkMode else "system"
    val language = if (languageOptions.any { it.first == appLanguage }) appLanguage else "system"

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle) },
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
                item(key = "themeMode") {
                    SegmentedDropdownRow(
                        title = strings.settingsThemeMode,
                        options = themeOptions.map { it.first },
                        selected = themeMode,
                        onSelect = { vm.setDarkMode(it) },
                        optionLabel = { value ->
                            themeOptions.firstOrNull { it.first == value }?.second ?: value
                        },
                        leadingContent = { RowIcon(themeModeIcon(themeMode)) },
                    )
                }
            }

            SegmentedGroup {
                item(key = "language") {
                    SegmentedDropdownRow(
                        title = strings.settingsLanguage,
                        options = languageOptions.map { it.first },
                        selected = language,
                        onSelect = { vm.setAppLanguage(it) },
                        optionLabel = { value ->
                            languageOptions.firstOrNull { it.first == value }?.second ?: value
                        },
                        leadingContent = { RowIcon(Icons.Filled.Language) },
                    )
                }
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

/** 主题模式的行首图标跟着当前档位走，扫一眼就知道现在是什么模式 */
private fun themeModeIcon(mode: String): ImageVector = when (mode) {
    "light" -> Icons.Filled.LightMode
    "dark" -> Icons.Filled.DarkMode
    else -> Icons.Filled.AutoMode
}
