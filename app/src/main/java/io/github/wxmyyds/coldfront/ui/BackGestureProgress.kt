package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.wxmyyds.coldfront.ui.component.DETAIL_POP_DURATION_MS

/**
 * Live predictive-back gesture progress, for effects the transition API cannot express.
 *
 * NavHost does drive its own transitions with the real progress - it calls
 * `SeekableTransitionState.seekTo(progress, previousEntry)` on every gesture frame - but the
 * transition callbacks themselves only ever receive the swipe edge, never a progress value. So any
 * transform that is not one of the built-in enter/exit effects cannot read the progress there.
 *
 * [androidx.navigationevent.NavigationEventDispatcher.transitionState] is the public, read-only view
 * of the gesture, and its documentation states it exists so "UI animations can subscribe to this
 * flow ... without needing to know about the specific ... NavigationEventInfo types".
 *
 * This reads that flow and nothing else. It deliberately never registers a
 * [androidx.navigationevent.NavigationEventHandler]: the processor allows exactly one handler to
 * own a gesture, so registering one here would take the gesture away from NavHost, and the parent
 * page would then never be drawn at all.
 *
 * @param isDismissible whether *this* page is the one the gesture would dismiss. A page that is
 * merely composed as the transition target must not react: the top-level page stays composed while
 * a detail is on top, and a gesture dismissing some deeper page would otherwise reach it. The
 * caller resolves this from the current route rather than from the page's own name.
 *
 * @return progress in `0f..1f` while a back gesture on this page is running, and `null` at every
 * other moment. `null` is deliberately distinct from `0f`: [rememberGestureSettleProgress] needs to
 * tell "not moving" apart from "held at the start".
 */
@Composable
internal fun rememberRunningBackProgress(isDismissible: Boolean): State<Float?> {
    val owner = checkNotNull(LocalNavigationEventDispatcherOwner.current) {
        "No NavigationEventDispatcher was provided via LocalNavigationEventDispatcherOwner"
    }
    // Read through the State, not through a delegated local, so a drag does not recompose this
    // composable on every frame; only the derived state below observes progress.
    val transitionState = owner.navigationEventDispatcher.transitionState.collectAsState()

    return remember(isDismissible) {
        derivedStateOf {
            val transition = transitionState.value
            if (isDismissible &&
                transition is NavigationEventTransitionState.InProgress &&
                transition.direction == NavigationEventTransitionState.TRANSITIONING_BACK
            ) {
                transition.latestEvent.progress.coerceIn(0f, 1f)
            } else {
                null
            }
        }
    }
}

/**
 * Gesture progress that keeps describing a page's position after the finger lifts.
 *
 * [androidx.navigationevent.NavigationEventDispatcher] resets its progress to `0f` the instant a
 * gesture completes or cancels, while NavHost is still animating the page to its destination for
 * [DETAIL_POP_DURATION_MS]. Anything driven straight off the raw progress would snap at the moment
 * of release - a rounded page going suddenly square, a backdrop flashing dark - while the page was
 * still visibly moving.
 *
 * So once the finger is up the value settles to [settleTo] over exactly the duration and easing
 * NavHost uses for the page itself. An effect driven this way stays in step with the page it
 * decorates instead of running on a second, unrelated clock.
 *
 * @param settleTo where to go once no gesture is running: `1f` when the page is leaving for good,
 * `0f` when it is staying.
 */
@Composable
internal fun rememberGestureSettleProgress(
    isDismissible: Boolean,
    settleTo: Float,
): State<Float> {
    val running = rememberRunningBackProgress(isDismissible)
    return animateFloatAsState(
        targetValue = running.value ?: settleTo,
        // While a gesture is running the target is already correct for this frame, so it must be
        // taken as-is - any easing would be applied on top of the finger's own position, which is
        // the same mistake a curved tween made of the page's own travel. Once the finger is up this
        // is a real animation, so it must share NavHost's linear spec for the two to agree.
        animationSpec = if (running.value != null) {
            snap()
        } else {
            tween(DETAIL_POP_DURATION_MS, easing = LinearEasing)
        },
        label = "backGestureSettleProgress",
    )
}
