package io.github.wxmyyds.coldfront.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.materialkolor.dynamiccolor.ColorSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorSchemeTest {
    private val seeds = listOf(
        BrandSeed,
        Color(0xFF008800),
        Color(0xFFCC6600),
    )

    @Test
    fun `every palette style produces a readable scheme in both modes`() {
        for (dark in listOf(false, true)) {
            for (palette in PaletteStyles.all) {
                for (seed in seeds) {
                    val scheme = appColorScheme(seed, dark, palette)
                    assertTrue("$palette dark=$dark", contrast(scheme.onSurface, scheme.surface) >= 4.5f)
                    assertTrue("$palette dark=$dark", contrast(scheme.onBackground, scheme.background) >= 4.5f)
                    assertTrue(
                        "$palette dark=$dark",
                        contrast(scheme.onPrimary, scheme.primary) >= 4.5f,
                    )
                }
            }
        }
    }

    @Test
    fun `surfaces follow the generated palette instead of fixed overrides`() {
        for (dark in listOf(false, true)) {
            val purple = appColorScheme(BrandSeed, dark, PaletteStyles.TONAL_SPOT)
            val green = appColorScheme(Color(0xFF008800), dark, PaletteStyles.TONAL_SPOT)

            // Dynamic color must reach surfaces too, not only accent roles.
            assertNotEquals(purple.background, green.background)
            assertNotEquals(purple.surface, green.surface)
            assertNotEquals(purple.surfaceContainer, green.surfaceContainer)
            assertNotEquals(purple.surfaceContainerHighest, green.surfaceContainerHighest)
        }
    }

    @Test
    fun `palette style and dark mode both change the generated scheme`() {
        // Rainbow/FruitSalad/Content keep the seed's primary tone by design and only rotate
        // hues downstream, so primary is not required to differ per style. The other roles are.
        val lightSchemes = PaletteStyles.all.associateWith { appColorScheme(BrandSeed, false, it) }
        val secondaryRoles = lightSchemes.values.map { it.secondary }.toSet()
        val tertiaryRoles = lightSchemes.values.map { it.tertiary }.toSet()
        assertTrue("styles must produce distinct secondary roles", secondaryRoles.size > 1)
        assertTrue("styles must produce distinct tertiary roles", tertiaryRoles.size > 1)
        assertNotEquals(
            lightSchemes.getValue(PaletteStyles.TONAL_SPOT).secondary,
            lightSchemes.getValue(PaletteStyles.RAINBOW).secondary,
        )
        assertNotEquals(
            appColorScheme(BrandSeed, false, PaletteStyles.TONAL_SPOT).primary,
            appColorScheme(BrandSeed, true, PaletteStyles.TONAL_SPOT).primary,
        )
    }

    @Test
    fun `only spec 2025 capable styles request the 2025 color spec`() {
        for (palette in PaletteStyles.all) {
            val expected = if (palette in setOf(
                    PaletteStyles.TONAL_SPOT,
                    PaletteStyles.NEUTRAL,
                    PaletteStyles.VIBRANT,
                    PaletteStyles.EXPRESSIVE,
                )
            ) {
                ColorSpec.SpecVersion.SPEC_2025
            } else {
                ColorSpec.SpecVersion.SPEC_2021
            }
            assertEquals(expected, paletteSpecVersion(palette))
        }
    }

    @Test
    fun `unknown stored palette keys fall back to the default style`() {
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
