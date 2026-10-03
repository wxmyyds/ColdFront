package io.github.wxmyyds.coldfront.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorSchemeTest {
    private val seeds = listOf(BrandSeed, Color(0xFF008800), Color(0xFFCC6600))

    @Test
    fun `generated scheme is readable for every seed and palette in both modes`() {
        for (dark in listOf(false, true)) {
            for (palette in PaletteStyles.all) {
                for (seed in seeds) {
                    val scheme = pageLayerScheme(colorSchemeFromSeed(seed, dark, palette))
                    val tag = "$palette $seed dark=$dark"
                    assertTrue(tag, contrast(scheme.onSurface, scheme.surface) >= 4.5f)
                    assertTrue(tag, contrast(scheme.onBackground, scheme.background) >= 4.5f)
                    assertTrue(tag, contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
                    assertTrue(tag, contrast(scheme.onSurface, optionContainerColor(scheme)) >= 4.5f)
                }
            }
        }
    }

    @Test
    fun `page background uses surfaceContainer, not surface or lowest`() {
        // SPEC_2025 remaps background onto surface (tone 98/4), which is too close to the
        // card level. surfaceContainerLowest is chroma 0 (pure white/black). The page floor
        // must be surfaceContainer (tone 94/9).
        for (dark in listOf(false, true)) {
            val scheme = pageLayerScheme(colorSchemeFromSeed(BrandSeed, dark))
            assertEquals(scheme.surfaceContainer, scheme.background)
            assertNotEquals(scheme.surface, scheme.background)
            assertNotEquals(scheme.surfaceContainerLowest, scheme.background)
        }
    }

    @Test
    fun `option container sits one step below the page background`() {
        // surfaceContainerHigh (92/12) must read darker than surfaceContainer (94/9) in light
        // mode and lighter in dark mode, otherwise rows disappear into the page.
        for (dark in listOf(false, true)) {
            val scheme = pageLayerScheme(colorSchemeFromSeed(BrandSeed, dark))
            val option = optionContainerColor(scheme).luminance()
            val page = scheme.background.luminance()
            if (dark) assertTrue("dark", option > page) else assertTrue("light", option < page)
        }
    }

    @Test
    fun `dynamic color reaches surfaces and not only accents`() {
        for (dark in listOf(false, true)) {
            val purple = pageLayerScheme(colorSchemeFromSeed(BrandSeed, dark))
            val green = pageLayerScheme(colorSchemeFromSeed(Color(0xFF008800), dark))
            assertNotEquals(purple.primary, green.primary)
            assertNotEquals(purple.background, green.background)
            assertNotEquals(optionContainerColor(purple), optionContainerColor(green))
        }
    }

    @Test
    fun `surfaces are not forced to the retired fixed constants`() {
        for (dark in listOf(false, true)) {
            val scheme = pageLayerScheme(colorSchemeFromSeed(BrandSeed, dark))
            assertNotEquals(Color(0xFFEFECF6), scheme.background)
            assertNotEquals(Color(0xFFFBF9FE), scheme.surface)
            assertNotEquals(Color(0xFF191920), scheme.background)
            assertNotEquals(Color(0xFF2B2B34), scheme.surface)
        }
    }

    @Test
    fun `palette choices produce distinguishable schemes`() {
        val schemes = PaletteStyles.all.associateWith {
            colorSchemeFromSeed(BrandSeed, false, it)
        }
        // Rainbow/FruitSalad/Content keep the seed's primary tone by design and only rotate
        // hues downstream, so distinguish them through the secondary role instead.
        assertTrue(schemes.values.map { it.secondary }.toSet().size > 1)
        assertNotEquals(
            schemes.getValue(PaletteStyles.TONAL_SPOT).secondary,
            schemes.getValue(PaletteStyles.RAINBOW).secondary,
        )
        assertNotEquals(
            colorSchemeFromSeed(BrandSeed, false, PaletteStyles.TONAL_SPOT).primary,
            colorSchemeFromSeed(BrandSeed, true, PaletteStyles.TONAL_SPOT).primary,
        )
    }

    @Test
    fun `spec 2025 is only requested for the styles that define it`() {
        val supports2025 = setOf(
            PaletteStyles.TONAL_SPOT,
            PaletteStyles.NEUTRAL,
            PaletteStyles.VIBRANT,
            PaletteStyles.EXPRESSIVE,
        )
        PaletteStyles.all.forEach { palette ->
            val expected = if (palette in supports2025) "SPEC_2025" else "SPEC_2021"
            assertEquals(palette, expected, paletteSpecVersion(palette).name)
        }
    }

    @Test
    fun `unknown stored palette keys fall back to tonal spot`() {
        assertEquals("TonalSpot", paletteStyle("future_palette").name)
        assertEquals("TonalSpot", paletteStyle(PaletteStyles.DEFAULT).name)
        assertTrue(PaletteStyles.isValid(PaletteStyles.CONTENT))
        assertTrue(!PaletteStyles.isValid("future_palette"))
    }

    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance()
        val y = b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }
}
