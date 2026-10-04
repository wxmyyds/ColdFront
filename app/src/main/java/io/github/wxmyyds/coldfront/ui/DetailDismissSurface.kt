package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
 * Only the leading (left) corners are rounded: the trailing edge travels off-screen, so rounding
 * it would be invisible, and a rounded trailing edge is what makes a page look like it shrank
 * rather than moved.
 */
@Composable
internal fun DetailDismissSurface(
    isDismissible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = rememberBackGestureProgress(isDismissible)
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                // Read the State, not a by-delegate local, so a drag does not recompose the page.
                val current = progress.floatValue
                shape = if (current > 0f) {
                    LeadingCornersShape(DetailDismissCornerRadius.toPx() * current)
                } else {
                    RectangleShape
                }
                clip = current > 0f
            }
    ) {
        content()
    }
}

/** A shape that rounds only the two corners on the leading edge, in the given layout direction. */
private class LeadingCornersShape(private val radius: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val leading = CornerRadius(radius, radius)
        val trailing = CornerRadius.Zero
        val roundsLeft = layoutDirection == LayoutDirection.Ltr
        return Outline.Rounded(
            rect = Rect(0f, 0f, size.width, size.height),
            topLeft = if (roundsLeft) leading else trailing,
            topRight = if (roundsLeft) trailing else leading,
            bottomRight = if (roundsLeft) trailing else leading,
            bottomLeft = if (roundsLeft) leading else trailing,
        )
    }
}
