package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Corner radius reached by the leaving page at full gesture progress. */
internal val DetailDismissCornerRadius = 28.dp

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
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                // Read the State, not a by-delegate local, so a drag does not recompose the page.
                val radius = DetailDismissCornerRadius.toPx() * progress.value
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
