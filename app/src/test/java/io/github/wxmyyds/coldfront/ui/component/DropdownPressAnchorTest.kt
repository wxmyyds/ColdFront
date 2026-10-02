package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure bookkeeping, not a replacement for device popup/RTL validation. */
class DropdownPressAnchorTest {
    @Test
    fun `matching pointer release anchors before click and is not reused`() {
        val anchor = DropdownPressAnchor()
        val press = PressInteraction.Press(Offset(120f, 24f))
        anchor.beginPointer()
        anchor.press(press)
        anchor.release(press)
        assertNull(anchor.position)
        anchor.click()
        assertEquals(IntOffset(120, 24), anchor.position)
        anchor.clearPending()
        anchor.click()
        assertNull(anchor.position)
    }

    @Test
    fun `click can arrive before its asynchronous native press and release`() {
        val anchor = DropdownPressAnchor()
        val press = PressInteraction.Press(Offset(38f, 12f))
        anchor.beginPointer()
        anchor.click()
        assertNull(anchor.position)
        anchor.press(press)
        anchor.release(press)
        assertEquals(IntOffset(38, 12), anchor.position)
    }

    @Test
    fun `cancelled and unmatched releases do not provide coordinates`() {
        val anchor = DropdownPressAnchor()
        val press = PressInteraction.Press(Offset(80f, 20f))
        anchor.beginPointer()
        anchor.press(press)
        anchor.release(PressInteraction.Press(press.pressPosition))
        anchor.click()
        assertNull(anchor.position)
        anchor.cancel(press)
        anchor.release(press)
        assertNull(anchor.position)
    }

    @Test
    fun `keyboard interactions and dismissed late release keep fallback`() {
        val anchor = DropdownPressAnchor()
        val press = PressInteraction.Press(Offset(80f, 20f))
        anchor.beginPointer()
        anchor.press(press)
        anchor.click()
        anchor.clearPending()
        anchor.release(press)
        anchor.press(press)
        anchor.release(press)
        anchor.click()
        assertNull(anchor.position)
    }

    @Test
    fun `new pointer gesture discards a previous release awaiting activation`() {
        val anchor = DropdownPressAnchor()
        val oldPress = PressInteraction.Press(Offset(80f, 20f))
        val newPress = PressInteraction.Press(Offset(24f, 32f))
        anchor.beginPointer()
        anchor.press(oldPress)
        anchor.release(oldPress)
        anchor.beginPointer()
        anchor.press(newPress)
        anchor.click()
        anchor.release(oldPress)
        assertNull(anchor.position)
        anchor.release(newPress)
        assertEquals(IntOffset(24, 32), anchor.position)
    }

    @Test
    fun `unspecified coordinates use the stable fallback`() {
        val anchor = DropdownPressAnchor()
        val press = PressInteraction.Press(Offset.Unspecified)
        anchor.beginPointer()
        anchor.press(press)
        anchor.click()
        anchor.release(press)
        assertNull(anchor.position)
    }

    @Test
    fun `fallback lies below the row at logical start in both directions`() {
        val size = IntSize(320, 64)
        assertEquals(IntOffset(0, 64), dropdownFallbackAnchor(size, LayoutDirection.Ltr))
        assertEquals(IntOffset(320, 64), dropdownFallbackAnchor(size, LayoutDirection.Rtl))
    }
}
