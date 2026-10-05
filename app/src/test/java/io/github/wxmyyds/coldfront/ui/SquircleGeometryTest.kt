package io.github.wxmyyds.coldfront.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SquircleGeometryTest {

    @Test
    fun theTileWidensTheRadiusByTheSquircleExtension() {
        // The Miuix continuous corner extends a tenth wider than the nominal radius, so the leading
        // edge of the corner sits at radius * extension rather than at the radius. A plain circular
        // arc would use a tile exactly equal to the radius.
        assertEquals(110f, squircleCornerTile(radiusPx = 100f, minSidePx = 1000f), 1e-5f)
        assertEquals(11f, squircleCornerTile(radiusPx = 10f, minSidePx = 1000f), 1e-5f)
    }

    @Test
    fun theTileNeverExceedsHalfTheSmallerSide() {
        // A tiny page must not over-round to a full circle: the tile is capped at half the smaller
        // dimension, which would be an oval shell.
        assertEquals(50f, squircleCornerTile(radiusPx = 100f, minSidePx = 100f), 1e-5f)
        assertEquals(200f, squircleCornerTile(radiusPx = 500f, minSidePx = 400f), 1e-5f)
    }

    @Test
    fun theControlHandleIsTheMiuixContinuousCornerRatio() {
        // The handle is tile * (1 - control) with control = 0.643. This is what makes the corner a
        // squircle rather than a circular arc.
        assertEquals(35.7f, squircleControlHandle(tilePx = 100f), 1e-5f)
        // A zero tile produces a zero handle, so a degenerate corner is a plain rectangle.
        assertEquals(0f, squircleControlHandle(tilePx = 0f), 1e-5f)
    }

    @Test
    fun theSquircleDiffersFromACircularArc() {
        // A circular arc's quarter-corner uses the kappa handle ratio 0.5523 * radius. The squircle's
        // is tile * (1 - control) = radius * extension * 0.357 = radius * 0.3927. The two are
        // measurably different, which is what lets the leaving page show the device's smooth
        // continuous corner instead of a plain circular arc.
        val radius = 100f
        val tile = squircleCornerTile(radius, minSidePx = 1000f)
        val squircleHandle = squircleControlHandle(tile)
        val arcHandle = radius * 0.5523f
        assertTrue(
            "squircle handle ($squircleHandle) must differ from a circular arc handle ($arcHandle)",
            kotlin.math.abs(squircleHandle - arcHandle) > 5f,
        )
        // And the tile is not the radius, so the corner extends differently from an arc.
        assertTrue(
            "squircle tile ($tile) must not equal the radius ($radius)",
            kotlin.math.abs(tile - radius) > 5f,
        )
    }

    @Test
    fun theLeadingShapeDealsWithANegativeRadiusAsARectangle() {
        // A gesture at zero progress (or a cancelled one settling back) must not produce a negative
        // tile that would inscribe a bogus curve.
        assertEquals(0f, squircleCornerTile(radiusPx = -10f, minSidePx = 1000f), 1e-5f)
    }
}
