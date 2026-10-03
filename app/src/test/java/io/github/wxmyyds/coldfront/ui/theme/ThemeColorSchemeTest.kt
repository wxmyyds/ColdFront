package io.github.wxmyyds.coldfront.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorSchemeTest {
    private val seeds = listOf(
        ThemeSeed.Brand,
        Color(0xFF008800),
        Color(0xFFCC6600),
    )

    @Test
    fun `generated scheme is readable for every seed in both modes`() {
        for (dark in listOf(false, true)) {
            for (seed in seeds) {
                val scheme = colorSchemeFromSeed(seed, dark)
                assertTrue("onSurface/surface $seed dark=$dark", contrast(scheme.onSurface, scheme.surface) >= 4.5f)
                assertTrue("onBackground/background $seed dark=$dark", contrast(scheme.onBackground, scheme.background) >= 4.5f)
                assertTrue("onPrimary/primary $seed dark=$dark", contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
            }
        }
    }

    @Test
    fun `dynamic color reaches surfaces and not only accents`() {
        for (dark in listOf(false, true)) {
            val purple = colorSchemeFromSeed(ThemeSeed.Brand, dark)
            val green = colorSchemeFromSeed(Color(0xFF008800), dark)
            assertNotEquals(purple.primary, green.primary)
            assertNotEquals(purple.background, green.background)
            assertNotEquals(purple.surface, green.surface)
            assertNotEquals(purple.surfaceContainerHighest, green.surfaceContainerHighest)
        }
    }

    @Test
    fun `page background keeps the wallpaper tint`() {
        // SPEC_2025 remaps background onto surface (tone 98/4), which still carries the
        // neutral tint. It must NOT be replaced with surfaceContainerLowest: that role
        // has zero chroma (pure white/black) and would strip the wallpaper tint.
        for (dark in listOf(false, true)) {
            val scheme = colorSchemeFromSeed(ThemeSeed.Brand, dark)
            assertEquals(scheme.surface, scheme.background)
            assertNotEquals(scheme.surfaceContainerLowest, scheme.background)
        }
    }

    @Test
    fun `surfaces are not forced to the retired fixed constants`() {
        // Regression guard: background/surface used to be overwritten with hardcoded values,
        // which made dynamic color change accents only.
        for (dark in listOf(false, true)) {
            val scheme = colorSchemeFromSeed(ThemeSeed.Brand, dark)
            assertNotEquals(Color(0xFFEFECF6), scheme.background)
            assertNotEquals(Color(0xFFFBF9FE), scheme.surface)
            assertNotEquals(Color(0xFF191920), scheme.background)
            assertNotEquals(Color(0xFF2B2B34), scheme.surface)
        }
    }

    @Test
    fun `option rows stay one step above the page background`() {
        // Segmented rows rely on surfaceContainerHighest (tone 90/15) reading darker than
        // background (tone 98/4) in light mode and lighter in dark mode.
        for (dark in listOf(false, true)) {
            val scheme = colorSchemeFromSeed(ThemeSeed.Brand, dark)
            val row = scheme.surfaceContainerHighest.luminance()
            val page = scheme.background.luminance()
            if (dark) assertTrue("dark", row > page) else assertTrue("light", row < page)
        }
    }

    @Test
    fun `light and dark schemes differ`() {
        assertNotEquals(
            colorSchemeFromSeed(ThemeSeed.Brand, false).primary,
            colorSchemeFromSeed(ThemeSeed.Brand, true).primary,
        )
    }

    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance()
        val y = b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }
}
