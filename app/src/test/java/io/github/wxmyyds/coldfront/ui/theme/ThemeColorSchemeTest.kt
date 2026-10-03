package io.github.wxmyyds.coldfront.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorSchemeTest {
    @Test
    fun `brand scheme is readable in both modes`() {
        for (dark in listOf(false, true)) {
            val scheme = brandColorScheme(dark)
            assertTrue("onSurface/surface dark=$dark", contrast(scheme.onSurface, scheme.surface) >= 4.5f)
            assertTrue("onBackground/background dark=$dark", contrast(scheme.onBackground, scheme.background) >= 4.5f)
            assertTrue("onPrimary/primary dark=$dark", contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
        }
    }

    @Test
    fun `page background and option surfaces are not fixed constants`() {
        // Regression guard for the old hardcoded overlays: surfaces used to be forced to
        // PageBackground*/OptionSurface*, which made dynamic color change accents only.
        for (dark in listOf(false, true)) {
            val scheme = brandColorScheme(dark)
            assertNotEquals(Color(0xFFEFECF6), scheme.background)
            assertNotEquals(Color(0xFFFBF9FE), scheme.surface)
            assertNotEquals(Color(0xFF191920), scheme.background)
            assertNotEquals(Color(0xFF2B2B34), scheme.surface)
        }
    }

    @Test
    fun `option rows stay one step above the page background`() {
        // Segmented rows rely on surfaceContainerHighest reading darker than background.
        for (dark in listOf(false, true)) {
            val scheme = brandColorScheme(dark)
            if (dark) {
                assertTrue(scheme.surfaceContainerHighest.luminance() > scheme.background.luminance())
            } else {
                assertTrue(scheme.surfaceContainerHighest.luminance() < scheme.background.luminance())
            }
        }
    }

    @Test
    fun `light and dark schemes differ`() {
        assertNotEquals(
            brandColorScheme(false).primary,
            brandColorScheme(true).primary,
        )
    }

    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance()
        val y = b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }
}
