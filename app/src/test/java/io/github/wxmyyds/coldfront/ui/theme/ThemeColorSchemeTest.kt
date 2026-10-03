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
                val scheme = pageLayerScheme(colorSchemeFromSeed(seed, dark))
                assertTrue("onSurface/surface $seed dark=$dark", contrast(scheme.onSurface, scheme.surface) >= 4.5f)
                assertTrue("onBackground/background $seed dark=$dark", contrast(scheme.onBackground, scheme.background) >= 4.5f)
                assertTrue("onPrimary/primary $seed dark=$dark", contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
            }
        }
    }

    @Test
    fun `page background uses the lowest surface container`() {
        // ColorSpec2025 remaps background to surface (tone 98/4), a card-level role.
        // The page floor must be surfaceContainerLowest (tone 100/0) or the page sits too dark.
        for (dark in listOf(false, true)) {
            val scheme = pageLayerScheme(colorSchemeFromSeed(ThemeSeed.Brand, dark))
            assertEquals(scheme.surfaceContainerLowest, scheme.background)
            assertNotEquals(scheme.surface, scheme.background)
        }
    }

    @Test
    fun `option rows stay one step above the page background`() {
        // Segmented rows rely on surfaceContainerHighest reading darker than background in light mode.
        for (dark in listOf(false, true)) {
            val scheme = pageLayerScheme(colorSchemeFromSeed(ThemeSeed.Brand, dark))
            if (dark) {
                assertTrue(scheme.surfaceContainerHighest.luminance() > scheme.background.luminance())
            } else {
                assertTrue(scheme.surfaceContainerHighest.luminance() < scheme.background.luminance())
            }
        }
    }

    @Test
    fun `surfaces are not forced to the retired fixed constants`() {
        // Regression guard: background/surface used to be overwritten with hardcoded values,
        // which made dynamic color change accents only.
        for (dark in listOf(false, true)) {
            val scheme = pageLayerScheme(colorSchemeFromSeed(ThemeSeed.Brand, dark))
            assertNotEquals(Color(0xFFEFECF6), scheme.background)
            assertNotEquals(Color(0xFFFBF9FE), scheme.surface)
            assertNotEquals(Color(0xFF191920), scheme.background)
            assertNotEquals(Color(0xFF2B2B34), scheme.surface)
        }
    }

    @Test
    fun `different seeds produce different schemes`() {
        for (dark in listOf(false, true)) {
            val purple = pageLayerScheme(colorSchemeFromSeed(ThemeSeed.Brand, dark))
            val green = pageLayerScheme(colorSchemeFromSeed(Color(0xFF008800), dark))
            // Dynamic color must reach surfaces, not just accent roles.
            assertNotEquals(purple.primary, green.primary)
            assertNotEquals(purple.background, green.background)
            assertNotEquals(purple.surfaceContainerHighest, green.surfaceContainerHighest)
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
