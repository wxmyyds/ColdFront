package io.github.wxmyyds.coldfront.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * MD3E 主题：[MaterialExpressiveTheme] + 弹簧动效 [MotionScheme.expressive]。
 *
 * 取色种子：开启动态取色时取系统动态色板的 accent 资源，关闭时用品牌紫灰。
 * 两者都交给同一个生成器（[colorSchemeFromSeed]），因此明度分层与文字对比度表现一致。
 * 最后经 [pageLayerScheme] 修正背景层角色，保证页面底色与选项容器有一档可读分层。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RedmagicCoolerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // 资源读取放在版本判断之后：Android 12 以下不存在该资源。
    // minSdk 已为 24，Context.getColor 可直接使用，无需 compose 的 @Composable 重载。
    val dynamicSeed = if (dynamicColor && ThemeSeed.supportsDynamic()) {
        Color(context.getColor(ThemeSeed.dynamicResourceId()))
    } else {
        null
    }
    val colorScheme = remember(dynamicSeed, darkTheme) {
        pageLayerScheme(colorSchemeFromSeed(dynamicSeed ?: ThemeSeed.Brand, darkTheme))
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
