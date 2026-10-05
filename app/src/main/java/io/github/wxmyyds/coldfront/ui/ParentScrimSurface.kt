package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import io.github.wxmyyds.coldfront.ui.component.PARENT_PARALLAX_FRACTION
import io.github.wxmyyds.coldfront.ui.component.parentPageAlphaForProgress
import io.github.wxmyyds.coldfront.ui.component.parentScrimAlphaForProgress

/**
 * Dims and fades a top-level page while a detail page sits on top of it, so that it reads as
 * background.
 *
 * Two effects stack, matching the Miuix covered-layer treatment: a fullscreen **black** scrim that
 * darkens the page, and a faint **fade of the page's own pixels** so the backdrop reads as
 * extending outward instead of as a hard patch. Both are driven by the back gesture rather than a
 * timer, so one finger brightens the parent and slides the page away together. That is why it reads
 * [rememberRunningBackProgress] directly instead of reusing the leaving page's own settle: the
 * parent is *not* the page being dismissed, so its brightness has to be a function of the gesture
 * alone. It cannot settle to `1f` on commit, because by then this page is the one on screen and
 * must be fully lit - it goes to `0f` instead, and [parentScrimAlphaForProgress] /
 * [parentPageAlphaForProgress] invert progress into brightness.
 *
 * The scrim is drawn *over* the page rather than merely fading the page's own pixels: fading alone
 * would let the window background show through, which reads lighter, whereas the brief is for the
 * parent to get darker. Black darkens in both themes. The two effects still stack (the page fades
 * and a black layer darkens on top), exactly as in the reference.
 */
@Composable
internal fun ParentScrimSurface(
    isCovered: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = rememberRunningBackProgress(observeBackGesture = true)
    // While a detail covers the page, the scrim is a pure function of the gesture; snap(), not a
    // tween, because easing it would slide the backdrop out of step with the page above it.
    // When nothing covers the page there is nothing to track, so the scrim animates itself out.
    val target = if (isCovered) parentScrimAlphaForProgress(progress.value ?: 0f) else 0f
    val alpha by animateFloatAsState(
        targetValue = target,
        animationSpec = if (isCovered) snap() else tween(PARENT_SCRIM_SETTLE_MS),
        label = "parentScrimAlpha",
    )
    // The page's own opacity, driven by the same gesture so it fades in step with the scrim and the
    // page above it. Same settle choice as the scrim: while a gesture runs the value is already
    // right for the frame, so snap; otherwise animate the parent back to fully lit.
    val contentTarget = if (isCovered) parentPageAlphaForProgress(progress.value ?: 0f) else 1f
    val contentAlpha by animateFloatAsState(
        targetValue = contentTarget,
        animationSpec = if (isCovered) snap() else tween(PARENT_SCRIM_SETTLE_MS),
        label = "parentPageAlpha",
    )
    // How far this page has stepped back, 1f when a detail fully covers it.
    //
    // Applied as a graphics layer rather than as a NavHost transition on purpose. The transition
    // API was tried first and is wrong here: an exit offset is retained by Compose and re-applied
    // when the push is interrupted, which slid the parent over the page being dragged and made it
    // vanish mid-gesture. A layer here also keeps the parent exactly in place at rest, so repeated
    // push/pop cycles cannot accumulate a drift.
    val stepBack by animateFloatAsState(
        // 1f while a detail covers this page, easing to 0 as a gesture uncovers it. The route only
        // reports the cover once the push has finished, so before that the page reads as uncovered
        // and animates in - which is exactly the step-back a push should produce.
        targetValue = if (isCovered) 1f - (progress.value ?: 0f) else 0f,
        // While a gesture uncovers the page the value is already right for this frame; any easing
        // would be applied on top of the finger. On a push nothing is moving the page, so it eases.
        animationSpec = if (progress.value != null) snap() else tween(PARENT_STEP_BACK_MS),
        label = "parentStepBack",
    )
    Box(
        modifier.fillMaxSize().graphicsLayer {
            translationX = -size.width * PARENT_PARALLAX_FRACTION * stepBack
        },
    ) {
        // The page's own pixels fade a tenth as the backdrop darkens, matching the Miuix covered-
        // layer alpha falloff. It is applied to the content only, not to the scrim, so the two
        // effects stack rather than the scrim being re-faded. Read here per-frame so the drag does
        // not recompose.
        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = contentAlpha }) {
            content()
        }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .background(Color.Black),
        )
    }
}

/** How long the scrim takes to appear or clear when no gesture is driving it. */
private const val PARENT_SCRIM_SETTLE_MS = 200

/** How long the parent takes to step back when a detail arrives, and to return. */
private const val PARENT_STEP_BACK_MS = 300
