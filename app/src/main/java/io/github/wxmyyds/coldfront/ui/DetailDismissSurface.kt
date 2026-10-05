package io.github.wxmyyds.coldfront.ui

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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
        val tile = max(0f, radius * SQUIRCLE_EXTENSION).coerceAtMost(min(width, height) * 0.5f)
        val path = Path()
        if (tile <= 0f) {
            path.addRect(Rect(0f, 0f, width, height))
        } else {
            val handle = tile * (1f - SQUIRCLE_CONTROL)
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
    val context = LocalContext.current
    val insets = LocalView.current.rootWindowInsets
    val radiusPx = remember(context, insets) {
        val systemRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.takeIf { it > 0 }
        } else {
            null
        }
        systemRadius ?: (DetailDismissCornerRadius.value * density).toInt()
    }
    return (radiusPx / density).dp
}

/**
 * Wraps a secondary page so it reads as a surface that is being pushed away by the back gesture.
 *
 * NavHost drives the page's *position* with the real gesture progress, but the transition API
 * cannot round the page's corners - its graphics-layer hook is internal - so the corner is applied
 * here, on the page's own root, from the same progress value.
 *
 * The radius is scaled by progress and is exactly zero when no gesture is running, so a page at
 * rest keeps its ordinary full-bleed rectangular shape and this changes nothing until the user
 * actually starts dragging.
 *
 * Only the leading corners are rounded: the trailing edge travels off-screen, so rounding it would
 * be invisible, and a rounded trailing edge is what makes a page look like it shrank rather than
 * moved.
 *
 * @param isDismissible whether a back gesture on this page would dismiss it, and so whether this
 * page should react to the gesture at all. `false` for the page being uncovered: it is not what the
 * gesture dismisses, but it still has to brighten while the gesture runs.
 *
 * @param isLeaving whether a gesture has already dismissed this page, so it is on its way out
 * rather than being cancelled. This is read from the navigation layer rather than from the gesture
 * because [androidx.navigationevent.NavigationEventTransitionState] cannot express it: the
 * dispatcher goes back to `Idle` for a commit and for a cancel alike, so the two are
 * indistinguishable from progress alone. A leaving page keeps its corner until it is gone; a
 * cancelled one un-rounds as it slides back.
 */
@Composable
internal fun DetailDismissSurface(
    isDismissible: Boolean,
    isLeaving: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Settles rather than tracks: the dispatcher zeroes progress the instant the finger lifts, so
    // a raw read would snap this page square while it was still visibly sliding away.
    val progress = rememberGestureSettleProgress(
        observeBackGesture = isDismissible,
        settleTo = if (isLeaving) 1f else 0f,
    )
    // Resolved in composition, where the insets and density are available, so a drag only reads the
    // State in the layer block and does not recompose the page every frame.
    val cornerRadius = rememberScreenCornerRadius()
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                // Read the State, not a by-delegate local, so a drag does not recompose the page.
                val radius = cornerRadius.toPx() * progress.value
                // The page always leaves toward the physical right, so the exposed leading edge is
                // always the physical left. SquircleLeadingShape rounds those two corners with the
                // device's smooth continuous-corner curve; the trailing edge stays square.
                shape = if (radius > 0f) SquircleLeadingShape(radius) else RectangleShape
                clip = radius > 0f
            }
    ) {
        content()
    }
}
