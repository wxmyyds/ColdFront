package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
 * the same settled progress as the leaving page ([rememberGestureSettleProgress], the mirror of
 * [DetailDismissSurface]'s use) instead of the raw gesture: on release the dispatcher zeroes its
 * progress the instant the finger lifts, and a raw read would snap the scrim back to full darkness
 * and the step-back back to full depth while the page above was still visibly sliding - which is
 * what made the parent jump behind the leaving page on release. Settling it on the same curve as
 * the page above means one release drives both layers through the same motion and they cannot drift
 * apart.
 *
 * The scrim is drawn *over* the page rather than merely fading the page's own pixels: fading alone
 * would let the window background show through, which reads lighter, whereas the brief is for the
 * parent to get darker. Black darkens in both themes. The two effects still stack (the page fades
 * and a black layer darkens on top), exactly as in the reference.
 *
 * @param isCovered whether a detail page covers this page right now. While covered the page is
 * tracked from the live gesture and settles back to the fully-covered state on release (the gesture
 * cancelled, or nothing happened); once the covering page is committed away (`isCovered` flips
 * false) the settle target becomes the fully-lit, at-rest state, so the parent and the leaving page
 * finish together.
 *
 * @param observeBackGesture whether this surface should react to a running back gesture at all,
 * when it is covered. Pass the user's predictive-back preference; the covered check is applied
 * internally so an uncovered page (which is what the gesture is returning to) never tracks.
 */
@Composable
internal fun ParentScrimSurface(
    isCovered: Boolean,
    modifier: Modifier = Modifier,
    observeBackGesture: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Settles rather than tracks the raw gesture, mirroring DetailDismissSurface: the dispatcher
    // zeroes its progress the instant the finger lifts, and a raw read would snap these effects
    // back to the covered state while the page above is still visibly sliding. Sharing the settle
    // curve with the leaving page means one release drives both layers through the same motion.
    //
    // Reads the shared gesture progress when [AppNavHost] provides one, so this page and the
    // leaving page above it animate on a single clock (see [LocalBackGestureSettleProgress]); falls
    // back to its own instance when called directly (e.g. in tests) so that path stays
    // self-contained.
    val progress = LocalBackGestureSettleProgress.current
        ?: rememberGestureSettleProgress(
            observeBackGesture = isCovered && observeBackGesture,
            // Covered: the gesture ends without dismissing (cancel), so settle back to 0 - fully
            // dimmed and stepped back. Uncovered: the pop committed, so settle to 1 - fully lit and
            // back at rest. Both targets are what [parentScrimAlphaForProgress] /
            // [parentPageAlphaForProgress] / the step-back expect, and settle exactly in step with the
            // leaving page's own settle.
            settleTo = if (isCovered) 0f else 1f,
        )
    android.util.Log.i("PBGDiag", "parent surface: shared=${LocalBackGestureSettleProgress.current != null}")
    Box(
        modifier.fillMaxSize().graphicsLayer {
            // Read the State here per-frame rather than via a by-delegate local, so a drag does not
            // recompose the page. `progress` is already the settled value, so the effects below
            // read it directly - there is no second animation layer on top.
            val p = progress.value
            // How far this page has stepped back, 1f when a detail fully covers it.
            //
            // Applied as a graphics layer rather than as a NavHost transition on purpose. The
            // transition API was tried first and is wrong here: an exit offset is retained by
            // Compose and re-applied when the push is interrupted, which slid the parent over the
            // page being dragged and made it vanish mid-gesture. A layer here also keeps the parent
            // exactly in place at rest, so repeated push/pop cycles cannot accumulate a drift.
            translationX = -size.width * PARENT_PARALLAX_FRACTION * (1f - p)
        },
    ) {
        // The page's own opacity, driven by the same progress so it fades in step with the scrim
        // and the page above it. Applied to the content only, not to the scrim, so the two effects
        // stack rather than the scrim being re-faded. Read per-frame so the drag does not recompose.
        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = parentPageAlphaForProgress(progress.value) }) {
            content()
        }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { this.alpha = parentScrimAlphaForProgress(progress.value) }
                .background(Color.Black),
        )
    }
}
