package io.github.wxmyyds.coldfront.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.R
import io.github.wxmyyds.coldfront.data.AppSettings
import io.github.wxmyyds.coldfront.ui.component.RowIcon
import io.github.wxmyyds.coldfront.ui.component.PageScaffold
import io.github.wxmyyds.coldfront.ui.component.SegmentedDropdownRow
import io.github.wxmyyds.coldfront.ui.component.SegmentedGroup
import io.github.wxmyyds.coldfront.ui.component.SegmentedRow
import io.github.wxmyyds.coldfront.ui.component.SegmentedSwitchRow
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings
import io.github.wxmyyds.coldfront.data.PaletteStyles

/**
 * 设置页(MD3E 分段选项列表):
 * - 外观组 = 动态取色开关行 + 「主题模式」下拉行；
 * - 通用设置、连接默认设备与关于入口分别成组。
 * 三档主题、三档语言都收成一行 + 下拉菜单：选项本身没有需要常驻展示的信息，
 * 铺成多行只是把页面拉长。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: CoolerViewModel, settings: AppSettings, onAbout: () -> Unit) {
    val strings = LocalStrings.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val defaultDevice by vm.defaultDevice.collectAsStateWithLifecycle()
    val defaultDeviceLoaded by vm.defaultDeviceLoaded.collectAsStateWithLifecycle()
    val defaultDeviceOptions = listOf<String?>(null) + profiles.map { it.id }
    val defaultDeviceId = defaultDevice?.id
    val dynamicColor = settings.dynamicColor
    val darkMode = settings.darkMode
    val appLanguage = settings.appLanguage
    val palette = settings.palette
    val predictiveBack = settings.predictiveBack
    val contentScrollState = rememberScrollState()
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

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

    PageScaffold(title = strings.settingsTitle) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .verticalScroll(contentScrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SegmentedGroup {
                item(key = "dynamicColor") {
                    SegmentedSwitchRow(
                        title = strings.settingsDynamicColor,
                        summary = if (supportsDynamicColor) {
                            strings.settingsDynamicColorDesc
                        } else if (strings.langIsZh) {
                            "需要 Android 12 或更高版本"
                        } else {
                            "Requires Android 12 or later"
                        },
                        checked = supportsDynamicColor && dynamicColor,
                        enabled = supportsDynamicColor,
                        onCheckedChange = { if (supportsDynamicColor) vm.setDynamicColor(it) },
                        leadingContent = { RowIcon(R.drawable.materialsymbols_ic_invert_colors_rounded_filled) },
                    )
                }
                item(key = "palette") {
                    SegmentedDropdownRow(
                        title = strings.settingsPalette,
                        options = PaletteStyles.all,
                        selected = palette,
                        onSelect = vm::setPalette,
                        optionLabel = { value -> when (value) {
                            PaletteStyles.NEUTRAL -> strings.settingsPaletteNeutral
                            PaletteStyles.VIBRANT -> strings.settingsPaletteVibrant
                            PaletteStyles.EXPRESSIVE -> strings.settingsPaletteExpressive
                            PaletteStyles.RAINBOW -> strings.settingsPaletteRainbow
                            PaletteStyles.FRUIT_SALAD -> strings.settingsPaletteFruitSalad
                            PaletteStyles.MONOCHROME -> strings.settingsPaletteMonochrome
                            PaletteStyles.FIDELITY -> strings.settingsPaletteFidelity
                            PaletteStyles.CONTENT -> strings.settingsPaletteContent
                            else -> strings.settingsPaletteTonalSpot
                        } },
                        leadingContent = { RowIcon(R.drawable.ms_style_fill1_24) },
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
                        leadingContent = { RowIcon(R.drawable.materialsymbols_ic_dark_mode_rounded_filled) },
                    )
                }
            }

            SegmentedGroup {
                item(key = "predictiveBack") {
                    SegmentedSwitchRow(
                        title = strings.settingsPredictiveBack,
                        summary = strings.settingsPredictiveBackDesc,
                        checked = predictiveBack,
                        onCheckedChange = vm::setPredictiveBack,
                        leadingContent = { RowIcon(R.drawable.materialsymbols_ic_swipe_rounded_filled) },
                    )
                }
            }

            SegmentedGroup {
                // 档案文档读完前不渲染：未加载的值不能冒充「不自动连接」。
                item(key = "defaultDevice", visible = defaultDeviceLoaded) {
                    SegmentedDropdownRow(
                        title = strings.settingsDefaultDevice,
                        summary = strings.settingsDefaultDeviceDesc,
                        options = defaultDeviceOptions,
                        selected = defaultDeviceId,
                        onSelect = vm::setDefaultDevice,
                        // 档案被删除时选中值本身已是 null，因此不会出现指向已删设备的文案。
                        optionLabel = { id ->
                            id?.let { profileId ->
                                profiles.firstOrNull { it.id == profileId }?.displayName
                            } ?: strings.settingsDefaultDeviceOff
                        },
                        leadingContent = { RowIcon(R.drawable.materialsymbols_ic_bluetooth_rounded_filled) },
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
                        leadingContent = { RowIcon(R.drawable.materialsymbols_ic_language_rounded_filled) },
                    )
                }
            }

            SegmentedGroup {
                item(key = "about") {
                    SegmentedRow(
                        title = strings.settingsAbout,
                        leadingContent = { RowIcon(R.drawable.materialsymbols_ic_info_rounded_filled) },
                        trailingContent = { RowIcon(R.drawable.materialsymbols_ic_chevron_right_rounded_filled) },
                        onClick = onAbout,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

}
