package io.github.wxmyyds.coldfront.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RgbPreviewStateTest {
    @Test
    fun `preview requires both the RGB route and a resumed lifecycle`() {
        assertTrue(isRgbPreviewActive(isPageActive = true, isResumed = true))
        assertFalse(isRgbPreviewActive(isPageActive = true, isResumed = false))
        assertFalse(isRgbPreviewActive(isPageActive = false, isResumed = true))
        assertFalse(isRgbPreviewActive(isPageActive = false, isResumed = false))
    }

    @Test
    fun `kept composed RGB stops for other tabs secondary routes and background`() {
        val observed = listOf(
            true to true,   // RGB in foreground.
            false to true,  // Another top-level tab or scan/about covers RGB; Activity still resumed.
            false to false,
            false to true,  // Resume while still on another page must not restart it.
            true to true,   // Return to RGB.
            true to false,  // App paused on RGB.
            true to true,
        ).map { (pageActive, resumed) -> isRgbPreviewActive(pageActive, resumed) }

        assertEquals(listOf(true, false, false, false, true, false, true), observed)
    }
}
