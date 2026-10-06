package io.github.wxmyyds.coldfront.ui

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** Floor for the leaving page's corner radius, used when the device does not report one. */
internal val DetailDismissCornerRadius = 28.dp

/**
 * Cubic-Bézier handle ratio for a squircle rounded-corner path, matching the Miuix `squircle`
 * geometry so a page slides in with the smooth, continuous corner the device draws rather than a
 * plain circular arc. `0.643` is the lock-step value Miuix uses.
 */
internal const val SQUIRCLE_CONTROL = 0.643f

/**
 * Corner-tile size as a multiple of the corner radius. `1.1` (the Miuix default) makes the corner
 * continuous rather than a circular arc; `1.0` would be the arc.
 */
internal const val SQUIRCLE_EXTENSION = 1.1f

/**
 * The squircle corner's tile size in pixels: the nominal radius widened by the continuous-corner
 * factor, capped at half the smaller side so a tiny page cannot over-round. Kept as a pure function
 * so the geometry can be sampled in a JVM test.
 */
internal fun squircleCornerTile(radiusPx: Float, minSidePx: Float): Float =
    max(0f, radiusPx * SQUIRCLE_EXTENSION).coerceAtMost(minSidePx * 0.5f)

/**
 * The cubic-Bézier control handle for a squircle corner, from the corner's tile size. The handle is
 * `tile * (1 - control)`, so `0.643` is the lock-step value Miuix uses. Kept as a pure function so
 * the geometry can be sampled in a JVM test.
 */
internal fun squircleControlHandle(tilePx: Float): Float = tilePx * (1f - SQUIRCLE_CONTROL)

/**
 * A [Shape] that rounds only the leading (physical left) corners of the leaving page with a
 * squircle curve, and leaves the trailing corners square.
 *
 * The predictive-back page always travels out toward the physical right whatever the layout
 * direction, so the exposed leading edge is always the physical left. Rounding only that side keeps
 * the trailing edge square, which is the edge that travels off-screen and would otherwise read as a
 * page shrinking. When the radius is zero the shape is a plain rectangle, so a page at rest is
 * unchanged.
 */
internal class SquircleLeadingShape(private val radius: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val width = size.width
        val height = size.height
        // The corner tile extends the radius by the squircle factor, capped at half the smaller
        // side so a tiny page cannot over-round.
        val tile = squircleCornerTile(radius, min(width, height))
        val path = Path()
        if (tile <= 0f) {
            path.addRect(Rect(0f, 0f, width, height))
        } else {
            val handle = squircleControlHandle(tile)
            // Walk the rectangle clockwise, replacing the two leading (left) corners with a
            // squircle cubic-Bézier and keeping the two trailing (right) corners square.
            path.moveTo(tile, 0f)
            path.lineTo(width, 0f)
            path.lineTo(width, height)
            path.lineTo(tile, height)
            path.cubicTo(handle, height, 0f, height - handle, 0f, height - tile)
            path.lineTo(0f, tile)
            path.cubicTo(0f, handle, handle, 0f, tile, 0f)
            path.close()
        }
        return Outline.Generic(path)
    }
}

/**
 * The screen's corner radius, so a full-bleed page slides in clipped to the device's actual
 * rounded corners rather than to an arbitrary value.
 *
 * Prefers the standard [RoundedCorner] window-insets API (API 31+, bottom-left position, which is
 * what the predictive-back leading edge meets on a portrait screen). A device that reports no
 * rounded corner - an emulator, or a flat-corner screen - falls back to [DetailDismissCornerRadius]
 * so the edge effect a back gesture promises can never collapse to zero: that would make a page go
 * square mid-drag, which reads as a jump rather than as a card being uncovered.
 *
 * This is the same technique Miuix's `rememberNavSystemCornerRadius` uses, re-expressed for this
 * project rather than copied: read the system inset, fall back to a dimensional, never to zero.
 */
@Composable
internal fun rememberScreenCornerRadius(): Dp {
    val density = LocalDensity.current.density
    // Read the current rootWindowInsets on every composition rather than caching them in a
    // remember key: WindowInsets are not a reactive State, so remembering the first frame's
    // insets object would pin the radius to whatever the window reported at cold start and
    // never refresh it on a rotation or an inset change. Re-reading each composition is
    // cheap (a single system call) and lets a later recomposition pick up the new window.
    val insets = LocalView.current.rootWindowInsets
    val systemRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.takeIf { it > 0 }
    } else {
        null
    }
    val radiusPx = systemRadius ?: (DetailDismissCornerRadius.value * density).toInt()
    return (radiusPx / density).dp
}
