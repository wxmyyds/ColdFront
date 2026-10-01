package io.github.wxmyyds.coldfront.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

/** InstallerX Revived's default seed color, used when system dynamic color is unavailable or disabled. */
val DefaultSeedColor = Color(0xFF6750A4)

/** Generate the same expressive 2025 palette style used by InstallerX Revived. */
fun expressiveColorScheme(seedColor: Color, darkTheme: Boolean): ColorScheme = dynamicColorScheme(
    seedColor = seedColor,
    isDark = darkTheme,
    style = PaletteStyle.Expressive,
    specVersion = ColorSpec.SpecVersion.SPEC_2025,
)
