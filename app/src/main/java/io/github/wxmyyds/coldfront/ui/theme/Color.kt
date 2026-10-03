package io.github.wxmyyds.coldfront.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

/**
 * 应用品牌紫灰种子。动态取色关闭时用它生成整套角色色。
 *
 * 页面背景、选项容器等 surface 角色不在这里写死，而是由种子推导，
 * 因此开启动态取色时它们能跟随壁纸，关闭时也保持 Material 的明度分层关系。
 */
internal val BrandSeed = Color(0xFF595A9E)

/**
 * 从种子生成色板。纯函数，可单测。
 *
 * 与动态取色路径走完全相同的算法，只有种子来源不同：
 * 动态取色用壁纸色，这里用品牌紫灰。因此两条路径的明度分层、
 * 容器层级和文字对比度表现一致，不会出现“开启动态后背景突然变样”。
 */
internal fun brandColorScheme(darkTheme: Boolean): ColorScheme = dynamicColorScheme(
    seedColor = BrandSeed,
    isDark = darkTheme,
    style = PaletteStyle.TonalSpot,
    specVersion = ColorSpec.SpecVersion.SPEC_2025,
)
