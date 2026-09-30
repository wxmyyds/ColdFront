package io.github.wxmyyds.coldfront.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * MD3E 主题：[MaterialExpressiveTheme] + 弹簧动效 [MotionScheme.expressive]。
 * 动态取色(默认开,Android 12+ 跟随壁纸);深色模式可跟随系统/强制。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RedmagicCoolerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // 品牌冷蓝（种子 #0288D1）成对使用。此前浅色走 expressiveLightColorScheme()，
        // 会退回官方默认紫，而深色仍是自建冷蓝 —— Color.kt 里的 LightColorScheme
        // 定义了却从未被引用，关掉动态取色后品牌割裂。
        // alpha28 没有 expressiveDarkColorScheme()，深色本来就只能自建，故两侧统一。
        if (darkTheme) DarkColorScheme else LightColorScheme
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
