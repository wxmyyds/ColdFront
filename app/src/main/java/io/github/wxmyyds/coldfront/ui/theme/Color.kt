package io.github.wxmyyds.coldfront.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

/**
 * MD3E 色板来源。
 *
 * 动态取色：Android 12+ 读取系统动态色板中的 accent 资源作为种子。
 * 关闭时：使用应用品牌紫灰，保持既有观感。
 *
 * 种子只取系统的 accent 资源本身，不再对系统已生成的 ColorScheme 角色做二次处理。
 */
internal object ThemeSeed {
    /** 品牌紫灰。关闭动态取色时的种子。 */
    val Brand = Color(0xFF595A9E)

    /**
     * 动态取色种子的系统资源 ID。Android 12 以下不存在，需要调用方先判断版本。
     *
     * 该资源是平台公开的动态色板 accent，取的是种子本身，
     * 不是某个应用已生成好的角色色。
     */
    fun dynamicResourceId(): Int = android.R.color.system_accent1_500

    fun supportsDynamic(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * 从种子生成整套 Material 3 角色色。
 *
 * 参数与 Material Color Utilities 公开规范保持一致：
 * - TonalSpot 变体（Material You 默认）
 * - SPEC_2025 颜色规范（Material 3 Expressive 的色调映射）
 * - contrast 0.0，即标准对比度曲线
 *
 * 纯函数，不读系统状态，可单测。全部 surface 角色由种子推导，
 * 因此开启动态取色时背景与选项容器会跟随壁纸，不存在写死的常量覆盖。
 */
internal fun colorSchemeFromSeed(seed: Color, darkTheme: Boolean): ColorScheme =
    dynamicColorScheme(
        seedColor = seed,
        isDark = darkTheme,
        style = PaletteStyle.TonalSpot,
        contrastLevel = 0.0,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )

/**
 * 让 `background` 真正表示页面底色。
 *
 * ColorSpec2025 将 `background` 重映射为 `surface`，那是卡片级角色；
 * 页面底色应当是最底层容器 `surfaceContainerLowest`。不修正的话
 * PageScaffold 与导航栏都用 `background`，整页会深一档并失去分层。
 *
 * `onBackground` 同步换成 `onSurface`，保证底色变化后文字对比度仍然可读。
 */
internal fun pageLayerScheme(scheme: ColorScheme): ColorScheme = scheme.copy(
    background = scheme.surfaceContainerLowest,
    onBackground = scheme.onSurface,
)
