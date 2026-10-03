package io.github.wxmyyds.coldfront.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

/**
 * MD3E 主题：[MaterialExpressiveTheme] + 弹簧动效 [MotionScheme.expressive]。
 *
 * 取色与 InstallerX 一致：用 [com.materialkolor.dynamicColorScheme] 从单一 seed 生成整套角色色，
 * 动态取色时 seed 取系统 accent 资源 [android.R.color.system_accent1_500]，否则用应用品牌紫灰。
 * 不再把系统 ColorScheme 的 primary 当 seed 二次生成，也不再覆盖 background / surface 角色，
 * 页面背景与选项容器都跟随当前色板。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RedmagicCoolerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    palette: String = PaletteStyles.DEFAULT,
    content: @Composable () -> Unit,
) {
    val seed = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        colorResource(android.R.color.system_accent1_500)
    } else {
        BrandSeed
    }
    val colorScheme = remember(seed, darkTheme, palette) {
        appColorScheme(seed, darkTheme, palette)
    }
    val motionScheme = MotionScheme.expressive()
    MaterialExpressiveTheme(
        // 切深浅/开关动态取色时整套角色色平滑过渡，而非瞬时跳变（MD3E 动效表达因果）
        colorScheme = animateColorScheme(colorScheme, motionScheme),
        motionScheme = motionScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Pure, testable palette generation straight from a seed color. */
internal fun appColorScheme(seed: Color, darkTheme: Boolean, palette: String): ColorScheme =
    dynamicColorScheme(
        seedColor = seed,
        isDark = darkTheme,
        style = paletteStyle(palette),
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

/** SPEC_2025 only exists for the four styles M3E defines it for; others fall back to 2021. */
internal fun paletteSpecVersion(palette: String): ColorSpec.SpecVersion =
    if (palette in SPEC_2025_STYLES) ColorSpec.SpecVersion.SPEC_2025 else ColorSpec.SpecVersion.SPEC_2021

private val SPEC_2025_STYLES = listOf(
    PaletteStyles.TONAL_SPOT,
    PaletteStyles.NEUTRAL,
    PaletteStyles.VIBRANT,
    PaletteStyles.EXPRESSIVE,
)

/** Animate role values only; keep the existing theme transition and role semantics. */
@Composable
private fun animateColorScheme(target: ColorScheme, motionScheme: MotionScheme): ColorScheme {
    @Composable
    fun animated(color: Color): Color =
        animateColorAsState(
            color,
            motionScheme.defaultEffectsSpec<Color>(),
            label = "themeColor",
        ).value

    return target.copy(
        primary = animated(target.primary),
        onPrimary = animated(target.onPrimary),
        primaryContainer = animated(target.primaryContainer),
        onPrimaryContainer = animated(target.onPrimaryContainer),
        inversePrimary = animated(target.inversePrimary),
        secondary = animated(target.secondary),
        onSecondary = animated(target.onSecondary),
        secondaryContainer = animated(target.secondaryContainer),
        onSecondaryContainer = animated(target.onSecondaryContainer),
        tertiary = animated(target.tertiary),
        onTertiary = animated(target.onTertiary),
        tertiaryContainer = animated(target.tertiaryContainer),
        onTertiaryContainer = animated(target.onTertiaryContainer),
        background = animated(target.background),
        onBackground = animated(target.onBackground),
        surface = animated(target.surface),
        onSurface = animated(target.onSurface),
        surfaceVariant = animated(target.surfaceVariant),
        onSurfaceVariant = animated(target.onSurfaceVariant),
        surfaceTint = animated(target.surfaceTint),
        inverseSurface = animated(target.inverseSurface),
        inverseOnSurface = animated(target.inverseOnSurface),
        error = animated(target.error),
        onError = animated(target.onError),
        errorContainer = animated(target.errorContainer),
        onErrorContainer = animated(target.onErrorContainer),
        outline = animated(target.outline),
        outlineVariant = animated(target.outlineVariant),
        scrim = animated(target.scrim),
        surfaceBright = animated(target.surfaceBright),
        surfaceDim = animated(target.surfaceDim),
        surfaceContainer = animated(target.surfaceContainer),
        surfaceContainerHigh = animated(target.surfaceContainerHigh),
        surfaceContainerHighest = animated(target.surfaceContainerHighest),
        surfaceContainerLow = animated(target.surfaceContainerLow),
        surfaceContainerLowest = animated(target.surfaceContainerLowest),
        primaryFixed = animated(target.primaryFixed),
        primaryFixedDim = animated(target.primaryFixedDim),
        onPrimaryFixed = animated(target.onPrimaryFixed),
        onPrimaryFixedVariant = animated(target.onPrimaryFixedVariant),
        secondaryFixed = animated(target.secondaryFixed),
        secondaryFixedDim = animated(target.secondaryFixedDim),
        onSecondaryFixed = animated(target.onSecondaryFixed),
        onSecondaryFixedVariant = animated(target.onSecondaryFixedVariant),
        tertiaryFixed = animated(target.tertiaryFixed),
        tertiaryFixedDim = animated(target.tertiaryFixedDim),
        onTertiaryFixed = animated(target.onTertiaryFixed),
        onTertiaryFixedVariant = animated(target.onTertiaryFixedVariant),
    )
}
