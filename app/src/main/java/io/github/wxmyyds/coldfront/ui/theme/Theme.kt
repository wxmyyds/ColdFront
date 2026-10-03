package io.github.wxmyyds.coldfront.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * MD3E 主题：[MaterialExpressiveTheme] + 弹簧动效 [MotionScheme.expressive]。
 *
 * 开启动态取色时直接使用系统色板 [dynamicLightColorScheme] / [dynamicDarkColorScheme]，
 * 不再对系统颜色二次量化，因此 primary 等角色与系统设置及其它 Material 应用完全一致，
 * 页面背景与全部 surface 角色也跟随壁纸。
 *
 * 关闭动态取色时用应用紫灰品牌色作为种子，由同一套算法生成整套角色色；两条路径只在
 * 种子来源上不同，明度分层与文字对比度表现一致。
 *
 * 最后统一经 [pageLayerScheme] 修正背景层角色，保证页面底色与分段行容器有一档可读分层。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RedmagicCoolerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = remember(context, darkTheme, dynamicColor) {
        val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 系统色板直接使用，不做二次量化，因此 primary 等角色与其它 Material 应用一致。
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            brandColorScheme(darkTheme)
        }
        // 2025 规范下 background 被重映射为 surface（卡片级），页面底色需改用最底层容器。
        pageLayerScheme(scheme)
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
