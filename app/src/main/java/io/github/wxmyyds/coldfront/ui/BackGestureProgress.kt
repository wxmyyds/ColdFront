package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.wxmyyds.coldfront.ui.component.releaseSettleSpec

/**
 * How long the gesture progress has to be frozen before we treat the drag as over, even though the
 * dispatcher is still reporting `InProgress`. While the finger is down and moving, the progress
 * changes every frame, so it never stays frozen this long; once the finger lifts (or the flow gets
 * stuck at the last value on a device that never completes it) the value stops changing and this
 * window elapses, which is what lets the footer kick into the release settle.
 */
private const val RELEASE_STALE_NANOS = 72_000_000L

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
 * @param observeBackGesture whether this surface should react to a running back gesture at all. A
 * surface that is not on the receiving end of the gesture must not animate: the leaving page and
 * the page being uncovered both care about the drag, while an unrelated page underneath neither
 * does. Callers pass what their own role is, not a route name.
 *
 * @return progress in `0f..1f` while a back gesture is running, and `null` at every other moment.
 * `null` is deliberately distinct from `0f`: [rememberGestureSettleProgress] needs to tell "not
 * moving" apart from "held at the start".
 */
@Composable
internal fun rememberRunningBackProgress(observeBackGesture: Boolean): State<Float?> {
    val owner = checkNotNull(LocalNavigationEventDispatcherOwner.current) {
        "No NavigationEventDispatcher was provided via LocalNavigationEventDispatcherOwner"
    }
    // Read through the State, not through a delegated local, so a drag does not recompose this
    // composable on every frame; only the derived state below observes progress.
    val transitionState = owner.navigationEventDispatcher.transitionState.collectAsState()

    return remember(observeBackGesture) {
        derivedStateOf {
            val transition = transitionState.value
            if (observeBackGesture &&
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
 * gesture completes or cancels, while NavHost is still keeping the page around for
 * [io.github.wxmyyds.coldfront.ui.component.RELEASE_SETTLE_CEILING_MS]. Anything driven straight off
 * the raw progress would snap at the moment of release - a rounded page going suddenly square, a
 * backdrop flashing dark - while the page was still visibly moving.
 *
 * So once the finger is up the value settles to [settleTo] on the release curve (the same curve the
 * page's own slide uses via [DetailDismissSurface]), so an effect driven this way stays in step with
 * the page it decorates instead of running on a second, unrelated clock.
 *
 * The gesture is considered over when the dispatcher reports [NavigationEventTransitionState.Idle]
 * (its normal behaviour), but to be robust to a device where the system gesture never completes that
 * flow - leaving the progress frozen in `InProgress` - the drag is also treated as over once the
 * progress has been frozen for [RELEASE_STALE_NANOS]. A lifted finger and a stuck flow both freeze
 * the value, while an actively dragged finger keeps it changing every frame, so this does not cut a
 * slow drag short.
 *
 * @param settleTo where to go once no gesture is running: `1f` when the page is on its way out,
 * `0f` when it is coming back or was never dismissed.
 */
@Composable
internal fun rememberGestureSettleProgress(
    observeBackGesture: Boolean,
    settleTo: Float,
): State<Float> {
    val running = rememberRunningBackProgress(observeBackGesture)
    // Whether the finger has lifted. True when the flow reports Idle (running.value == null) or when
    // the progress is frozen long enough to be a released gesture even though the flow is still
    // InProgress (the device case where transitionState never goes back to Idle).
    val gestureOver = remember { mutableStateOf(running.value == null) }
    LaunchedEffect(running) {
        var lastValue = running.value
        var lastChange = 0L
        while (true) {
            withFrameNanos { now ->
                val v = running.value
                when {
                    v == null -> gestureOver.value = true
                    v != lastValue -> {
                        lastValue = v
                        lastChange = now
                        gestureOver.value = false
                    }
                    now - lastChange >= RELEASE_STALE_NANOS -> gestureOver.value = true
                }
            }
        }
    }
    return animateFloatAsState(
        // Once the finger is up the target is the settle point; while it is down it is the live
        // finger progress.
        targetValue = if (gestureOver.value) settleTo else running.value ?: settleTo,
        // While a gesture is running the target is already correct for this frame, so it must be
        // taken as-is - any easing would be applied on top of the finger's own position, which is
        // the same mistake a curved tween made of the page's own travel. Once the finger is up this
        // is a real animation, so it settles on the release curve.
        animationSpec = if (gestureOver.value) releaseSettleSpec() else snap(),
        label = "backGestureSettleProgress",
    )
}
