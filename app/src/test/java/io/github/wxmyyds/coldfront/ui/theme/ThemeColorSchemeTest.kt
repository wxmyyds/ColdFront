package io.github.wxmyyds.coldfront.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorSchemeTest {
    @Test
    fun `all palettes and seeds retain fixed surfaces in both modes`() {
        for (dark in listOf(false, true)) {
            for (palette in listOf("tonal_spot", "neutral", "vibrant")) {
                for (seed in listOf(0xFF595A9E.toInt(), 0xFF008800.toInt(), 0xFFCC6600.toInt())) {
                    val scheme = appColorScheme(seed, dark, palette)
                    val page = if (dark) PageBackgroundDark else PageBackgroundLight
                    val option = if (dark) OptionSurfaceDark else OptionSurfaceLight
                    assertEquals(page, scheme.background)
                    assertEquals(page, scheme.surfaceDim)
                    listOf(scheme.surface, scheme.surfaceVariant, scheme.surfaceBright,
                        scheme.surfaceContainer, scheme.surfaceContainerLow, scheme.surfaceContainerLowest,
                        scheme.surfaceContainerHigh, scheme.surfaceContainerHighest,
                    ).forEach { assertEquals(option, it) }
                    assertTrue(contrast(scheme.onSurface, option) >= 4.5f)
                    assertTrue(contrast(scheme.onBackground, page) >= 4.5f)
                    assertTrue(contrast(scheme.inversePrimary, scheme.inverseSurface) >= 4.5f)
                }
            }
        }
    }

    @Test
    fun `inverse and fixed accents use selected palette not static purple`() {
        val purple = appColorScheme(0xFF595A9E.toInt(), false, "vibrant")
        val green = appColorScheme(0xFF008800.toInt(), false, "vibrant")
        assertNotEquals(green.primary, green.inversePrimary)
        assertNotEquals(purple.primaryFixed, green.primaryFixed)
        assertEquals(green.primaryFixed, appColorScheme(0xFF008800.toInt(), true, "vibrant").primaryFixed)
    }

    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance()
        val y = b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }
}
