package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelDragTest {
    @Test
    fun `foundation reverses both drag and fling only for RTL`() {
        assertFalse(topLevelDragReversed(LayoutDirection.Ltr))
        assertTrue(topLevelDragReversed(LayoutDirection.Rtl))
        for (direction in LayoutDirection.entries) {
            // Mirrored physical gestures must select the same logical next/previous pages.
            val physicalForward = if (direction == LayoutDirection.Rtl) 1f else -1f
            val position = topLevelPositionAfterDrag(
                1f, logical(120f * physicalForward, direction), 400f, 3,
            )
            assertEquals(1.3f, position, 0.0001f)
            assertEquals(2, topLevelTargetAfterDrag(position, logical(900f * physicalForward, direction), 3))

            val backPosition = topLevelPositionAfterDrag(
                2f, logical(-120f * physicalForward, direction), 400f, 3,
            )
            assertEquals(1.7f, backPosition, 0.0001f)
            assertEquals(1, topLevelTargetAfterDrag(backPosition, logical(-900f * physicalForward, direction), 3))
        }
    }

    @Test
    fun `slow drag rounds while fling retains existing threshold and direction`() {
        assertEquals(1, topLevelTargetAfterDrag(1.3f, 0f, 3))
        assertEquals(2, topLevelTargetAfterDrag(1.7f, 0f, 3))
        assertEquals(1, topLevelTargetAfterDrag(1.3f, -700f, 3))
        assertEquals(2, topLevelTargetAfterDrag(1.3f, -701f, 3))
        assertEquals(0, topLevelTargetAfterDrag(1.3f, 701f, 3))
    }

    @Test
    fun `drag and fling stay within first and last pages in either direction`() {
        assertEquals(0f, topLevelPositionAfterDrag(0f, 100f, 400f, 3), 0f)
        assertEquals(3f, topLevelPositionAfterDrag(3f, -100f, 400f, 3), 0f)
        assertEquals(0, topLevelTargetAfterDrag(0f, 1_000f, 3))
        assertEquals(3, topLevelTargetAfterDrag(3f, -1_000f, 3))
        assertEquals(0f, topLevelPositionAfterDrag(1f, 2f, 0f, 3), 0f)
    }

    /** Model Foundation's reverseDirection contract; production must not mirror either value again. */
    private fun logical(physical: Float, direction: LayoutDirection): Float =
        if (topLevelDragReversed(direction)) -physical else physical
}
