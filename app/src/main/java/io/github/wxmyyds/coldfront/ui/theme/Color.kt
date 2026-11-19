package io.github.wxmyyds.coldfront.ui.theme

import io.github.wxmyyds.coldfront.data.PaletteStyles

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

/** 动态取色种子取自平台公开的动态色板 accent 资源。 */
internal object ThemeSeed {
    /** 该资源仅在 Android 12 及以上存在，调用前需先判断版本。 */
    fun dynamicResourceId(): Int = android.R.color.system_accent1_500

    fun supportsDynamic(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * 页面底色使用 `surfaceContainer`（tone 94/9）。SPEC_2025 会将 `background` 映射到
 * `surface`（tone 98/4），颜色较浅，与内容容器几乎相同。`surfaceContainerLowest` 的 tone
 * 是 100/0，chroma 为 0，呈纯白或纯黑，因此不适合作为页面底色。
 *
 * 将页面底色改为 `surfaceContainer` 后，`onBackground` 也改为 `onSurface`，以维持文字对比度。
 */
internal fun pageLayerScheme(scheme: ColorScheme): ColorScheme = scheme.copy(
    background = scheme.surfaceContainer,
    onBackground = scheme.onSurface,
)

/**
 * 选项行和分组卡片使用的容器色，比页面底色（surfaceContainer）亮一档。
 *
 * SPEC_2025 中，surfaceBright 的 tone 是 98/18，页面底色 surfaceContainer 是 94/9，
 * 因此选项行比页面底色更浅，形成卡片浮在页面上的层次。
 */
internal fun optionContainerColor(scheme: ColorScheme): Color = scheme.surfaceBright
/**
 * 从种子生成整套 Material 3 角色色。纯函数，可单测。
 *
 * 参数遵循 Material Color Utilities 公开规范：变体由设置中的调色板决定，
 * SPEC_2025 色调映射，标准对比度曲线。
 */
internal fun colorSchemeFromSeed(
    seed: Color,
    darkTheme: Boolean,
    palette: String = PaletteStyles.DEFAULT,
): ColorScheme = dynamicColorScheme(
    seedColor = seed,
    isDark = darkTheme,
    style = paletteStyle(palette),
    contrastLevel = 0.0,
    specVersion = paletteSpecVersion(palette),
)

internal fun paletteStyle(palette: String): PaletteStyle = when (palette) {
    PaletteStyles.NEUTRAL -> PaletteStyle.Neutral
    PaletteStyles.VIBRANT -> PaletteStyle.Vibrant
    PaletteStyles.EXPRESSIVE -> PaletteStyle.Expressive
    PaletteStyles.RAINBOW -> PaletteStyle.Rainbow
    PaletteStyles.FRUIT_SALAD -> PaletteStyle.FruitSalad
    PaletteStyles.MONOCHROME -> PaletteStyle.Monochrome
    PaletteStyles.FIDELITY -> PaletteStyle.Fidelity
    PaletteStyles.CONTENT -> PaletteStyle.Content
    else -> PaletteStyle.TonalSpot
}

/**
 * SPEC_2025 只对 Material 3 Expressive 定义的四种变体有效，
 * 其余变体回退到 SPEC_2021，避免传入不支持的规范版本。
 */
internal fun paletteSpecVersion(palette: String): ColorSpec.SpecVersion =
    if (palette in SPEC_2025_PALETTES) {
        ColorSpec.SpecVersion.SPEC_2025
    } else {
        ColorSpec.SpecVersion.SPEC_2021
    }

private val SPEC_2025_PALETTES = listOf(
    PaletteStyles.TONAL_SPOT,
    PaletteStyles.NEUTRAL,
    PaletteStyles.VIBRANT,
    PaletteStyles.EXPRESSIVE,
)
