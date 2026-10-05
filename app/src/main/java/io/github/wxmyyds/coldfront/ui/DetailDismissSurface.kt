package io.github.wxmyyds.coldfront.ui

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Floor for the leaving page's corner radius, used when the device does not report one. */
internal val DetailDismissCornerRadius = 28.dp

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
                // AbsoluteRoundedCornerShape takes its corners positionally as topLeft,
                // topRight, bottomRight, bottomLeft, and resolves "start" against the ambient
                // layout direction, so the corner stays on the leading edge in RTL too.
                shape = if (radius > 0f) {
                    val corner = CornerSize(radius)
                    AbsoluteRoundedCornerShape(
                        topLeft = corner,
                        topRight = CornerSize(0f),
                        bottomRight = CornerSize(0f),
                        bottomLeft = corner,
                    )
                } else {
                    RectangleShape
                }
                clip = radius > 0f
            }
    ) {
        content()
    }
}
