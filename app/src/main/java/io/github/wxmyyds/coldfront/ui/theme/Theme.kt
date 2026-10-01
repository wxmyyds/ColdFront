package io.github.wxmyyds.coldfront.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

/**
 * MD3E 主题：[MaterialExpressiveTheme] + 弹簧动效 [MotionScheme.expressive]。
 * 默认使用应用紫灰色板；开启动态取色后，Android 12+ 跟随系统壁纸，深色模式可跟随系统/强制。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RedmagicCoolerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    palette: String = "tonal_spot",
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val seed = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val systemScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        systemScheme.primary.toArgb()
    } else 0xFF595A9E.toInt()
    val scheme = when (palette) {
        "neutral" -> SchemeNeutral(Hct.fromInt(seed), darkTheme, 0.0)
        "vibrant" -> SchemeVibrant(Hct.fromInt(seed), darkTheme, 0.0)
        else -> SchemeTonalSpot(Hct.fromInt(seed), darkTheme, 0.0)
    }
    val baseColorScheme = paletteColorScheme(scheme, if (darkTheme) DarkColorScheme else LightColorScheme)
    val pageColor = if (darkTheme) PageBackgroundDark else PageBackgroundLight
    val optionColor = if (darkTheme) OptionSurfaceDark else OptionSurfaceLight
    val colorScheme = baseColorScheme.copy(
        background = pageColor,
        surface = optionColor,
        surfaceVariant = optionColor,
        surfaceBright = optionColor,
        surfaceDim = pageColor,
        surfaceContainer = optionColor,
        surfaceContainerHigh = optionColor,
        surfaceContainerHighest = optionColor,
        surfaceContainerLow = optionColor,
        surfaceContainerLowest = optionColor,
    )
    MaterialExpressiveTheme(
        // 切深浅/开关动态取色时整套角色色平滑过渡，而非瞬时跳变（MD3E 动效表达因果）
        colorScheme = animateColorScheme(colorScheme),
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/**
 * 主题切换（深浅模式/动态取色开关）时对全部 48 个角色色做颜色补间，
 * 让整界面颜色渐变而不是瞬间替换。只动画颜色值，不改变角色语义。
 */
@Composable
private fun paletteColorScheme(scheme: DynamicScheme, base: ColorScheme): ColorScheme {
    val roles = MaterialDynamicColors()
    fun color(role: com.materialkolor.dynamiccolor.DynamicColor): Color =
        Color(role.getArgb(scheme))
    return base.copy(
        primary = color(roles.primary()),
        onPrimary = color(roles.onPrimary()),
        primaryContainer = color(roles.primaryContainer()),
        onPrimaryContainer = color(roles.onPrimaryContainer()),
        inversePrimary = color(roles.primary()),
        secondary = color(roles.secondary()),
        onSecondary = color(roles.onSecondary()),
        secondaryContainer = color(roles.secondaryContainer()),
        onSecondaryContainer = color(roles.onSecondaryContainer()),
        tertiary = color(roles.tertiary()),
        onTertiary = color(roles.onTertiary()),
        tertiaryContainer = color(roles.tertiaryContainer()),
        onTertiaryContainer = color(roles.onTertiaryContainer()),
        surfaceTint = color(roles.primary()),
    )
}

private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(durationMillis = 400)

    @Composable
    fun animated(color: Color): Color =
        animateColorAsState(color, spec, label = "themeColor").value

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
