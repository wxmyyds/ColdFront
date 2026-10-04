package io.github.wxmyyds.coldfront.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/**
 * Live predictive-back gesture progress, for effects the transition API cannot express.
 *
 * NavHost does drive its own transitions with the real progress - it calls
 * `SeekableTransitionState.seekTo(progress, previousEntry)` on every gesture frame - but the
 * transition callbacks themselves only ever receive the swipe edge, never a progress value. So any
 * transform that is not one of the built-in enter/exit effects cannot read the progress there.
 *
 * [androidx.navigationevent.NavigationEventDispatcher.transitionState] is the public, read-only
 * view of the gesture, and its documentation states it exists so "UI animations can subscribe to
 * this flow ... without needing to know about the specific ... NavigationEventInfo types".
 *
 * This function only reads that flow. It deliberately never registers a
 * [androidx.navigationevent.NavigationEventHandler]: the processor lets exactly one handler own a
 * gesture, so registering one here would take the gesture away from NavHost, and the parent page
 * would then never be drawn at all.
 *
 * @param isDismissible whether *this* page is the one the gesture would dismiss. A page that is
 * merely composed as the transition target must not react: `MAIN` stays composed while a detail is
 * on top, and a gesture that dismisses some deeper page would otherwise reach it. The caller
 * resolves this from the current route rather than from the page's own name.
 *
 * @return progress in `0f..1f`, or `0f` when the running gesture is not a back gesture on this
 * page. This is a [State] so a `graphicsLayer` block can read it without recomposing the page on
 * every frame of the drag.
 */
@Composable
internal fun rememberBackGestureProgress(isDismissible: Boolean): State<Float> {
    val dispatcher = LocalNavigationEventDispatcherOwner.current.navigationEventDispatcher
    // Read through the State, not through a delegated local: only the derived state below should
    // observe progress, so a drag must not recompose this composable on every frame.
    val transitionState = dispatcher.transitionState.collectAsState()

    return remember(isDismissible) {
        derivedStateOf {
            val transition = transitionState.value
            if (isDismissible &&
                transition is NavigationEventTransitionState.InProgress &&
                transition.direction == NavigationEventTransitionState.TRANSITIONING_BACK
            ) {
                transition.latestEvent.progress.coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }
}
