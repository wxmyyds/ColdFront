package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import io.github.wxmyyds.coldfront.ui.component.PARENT_PARALLAX_FRACTION
import io.github.wxmyyds.coldfront.ui.component.parentScrimAlphaForProgress

/**
 * Dims a top-level page while a detail page sits on top of it, so that it reads as background.
 *
 * The scrim follows the back gesture rather than a timer, so one finger brightens the parent and
 * slides the page away together. That is why it reads [rememberRunningBackProgress] directly instead
 * of reusing the leaving page's own settle: the parent is *not* the page being dismissed, so its
 * brightness has to be a function of the gesture alone. It cannot settle to `1f` on commit, because
 * by then this page is the one on screen and must be fully lit - it goes to `0f` instead, and
 * [parentScrimAlphaForProgress] inverts progress into brightness.
 *
 * The scrim is drawn *over* the page rather than fading the page's own pixels. Fading would let the
 * window background show through, which reads lighter, whereas the brief is for the parent to get
 * darker; a black scrim does that in both themes.
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
        content()
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                // `scrim` is the only scrim role this Material 3 build exposes; there is no
                // `scrimContainer`.
                .background(MaterialTheme.colorScheme.scrim),
        )
    }
}

/** How long the scrim takes to appear or clear when no gesture is driving it. */
private const val PARENT_SCRIM_SETTLE_MS = 200

/** How long the parent takes to step back when a detail arrives, and to return. */
private const val PARENT_STEP_BACK_MS = 300
