package io.github.wxmyyds.coldfront.ui.theme

import io.github.wxmyyds.coldfront.data.PaletteStyles

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource

/**
 * MD3E 主题：[MaterialExpressiveTheme] + 弹簧动效 [MotionScheme.expressive]。
 *
 * 取色种子：开启动态取色时取系统动态色板的 accent 资源，关闭时用品牌紫灰。
 * 两者都交给同一个生成器（[colorSchemeFromSeed]），变体由设置中的调色板决定，
 * 因此所有 surface 角色都由种子推导，没有写死的常量。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RedmagicCoolerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    palette: String = PaletteStyles.DEFAULT,
    content: @Composable () -> Unit,
) {
    // 读取放在版本判断之后：Android 12 以下不存在该资源。
    // 用 Compose 的 colorResource 而非 Context.getColor：前者是配置感知的，
    // 壁纸或深浅模式变化时会重新读取。
    val dynamicSeed = if (dynamicColor && ThemeSeed.supportsDynamic()) {
        colorResource(ThemeSeed.dynamicResourceId())
    } else {
        null
    }
    val colorScheme = remember(dynamicSeed, darkTheme, palette) {
        // 页面底色落到 surfaceContainer，SPEC_2025 下 background 已被重映射到 surface。
        pageLayerScheme(colorSchemeFromSeed(dynamicSeed ?: BrandSeed, darkTheme, palette))
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
