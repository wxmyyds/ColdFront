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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.wxmyyds.coldfront.ui.component.releaseSettleSpec

/**
 * How long the gesture progress has to stay frozen before we treat the drag as over, even though
 * the dispatcher is still reporting `InProgress`. While the finger is down and moving, the progress
 * changes frame to frame; a released drag on a device whose flow never completes leaves it frozen.
 *
 * 500ms is deliberately long, and that is the point: a drag that *pauses* mid-way (finger held
 * still for a moment before continuing) must not be mistaken for a release, or the page would
 * start settling under a still-down finger and then snap back when the drag resumes - which reads
 * as a stutter mid-gesture. A released drag is normally caught by the dispatcher going `Idle` in
 * the same frame, so this window only stands in for the pathological device where the flow never
 * reports Idle; on those it trades a short settling delay for never settling at all.
 */
private const val RELEASE_STALE_MS = 500L

/**
 * How long to hold the gesture's last progress after the flow goes [Idle] before committing to a
 * settle direction.
 *
 * The processor completes a back gesture in two async steps: it submits the pop (flipping
 * [NavigationEventTransitionState] to [Idle] is only cosmetic - the route flip that decides commit
 * vs cancel comes from the NavController, on its own flow). The two recompositions are not
 * guaranteed to land in the same batch, so the instant the flow reports Idle the route may not yet
 * have flipped. Settling immediately then would start the page moving toward the *old* direction
 * (roll-back on a commit) and reverse when the flip lands - a visible stutter exactly at release.
 *
 * So on Idle we hold the last progress for this window and watch [settleTo] (the route-derived
 * direction): if the pop's recomposition lands inside the window the direction flips and we settle
 * the new way; if not, the gesture was a cancel and we settle the old way. A commit settles with
 * only a frame or two of hold; a cancel waits out the window, which is imperceptible next to the
 * release settle itself.
 */
private const val RELEASE_DIRECTION_CONFIRM_MS = 48L

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
 * progress has been frozen for [RELEASE_STALE_MS]. A lifted finger and a stuck flow both freeze
 * the value, while an actively dragged finger keeps it changing every frame, so this does not cut a
 * slow drag short; a *pausing* finger (held still mid-drag) is distinguished by the window being
 * long enough to outlast ordinary hesitation.
 *
 * Which direction to settle is confirmed after a short hold (see [RELEASE_DIRECTION_CONFIRM_MS]):
 * the processor's pop submission and the flow's return to Idle are separate async steps, so at the
 * moment Idle arrives the route may not yet have flipped. Settling with the pre-flip [settleTo]
 * would roll the page back one frame on a commit before reversing - a stutter exactly at release.
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
    // Latest settleTo across recompositions, so the direction-confirm hold below can observe a
    // pop's route flip landing after the Idle frame.
    val latestSettleTo = rememberUpdatedState(settleTo)
    // The last finger progress, held while the settle direction is being confirmed after release.
    // Keyed on running so a role flip (push -> pop) resets it to the new role's starting progress.
    val lastProgress = remember(running) { mutableStateOf(running.value) }
    // Whether the finger has lifted. True when the flow reports Idle (running.value == null) or when
    // the progress is frozen long enough to be a released gesture even though the flow is still
    // InProgress (the device case where transitionState never goes back to Idle). Keyed on the
    // running state: when observeBackGesture flips (a push turning into a pop or vice versa) the
    // flag must reset to the new role's initial state rather than carry the old role's value into
    // the first frame of the new one.
    val gestureOver = remember(running) { mutableStateOf(running.value == null) }
    // The settle direction once confirmed. Initialised from the route-derived settleTo; a pop's
    // recomposition flips it (via the confirm hold below, or by rebuilding this state when running
    // flips) so a commit settles out while a cancel rolls back.
    val settleDirection = remember(running) { mutableStateOf(settleTo) }
    LaunchedEffect(running) {
        var lastValue: Float? = running.value
        while (true) {
            val v = running.value
            when {
                v == null -> {
                    // The flow reported Idle (or never started). Hold the last progress while the
                    // direction is confirmed: the pop's recomposition (flipping settleTo) may land
                    // after this frame, and settling with the old direction would stutter on a
                    // commit. A flip inside the window wins; a timeout means cancel, settle old way.
                    val flipped = withTimeoutOrNull(RELEASE_DIRECTION_CONFIRM_MS) {
                        snapshotFlow { latestSettleTo.value }.first { it != settleDirection.value }
                    }
                    if (flipped != null) settleDirection.value = flipped
                    gestureOver.value = true
                    // Nothing to track until a new gesture begins, so suspend instead of spinning
                    // forever on the frame clock.
                    snapshotFlow { running.value }.first { it != null }
                    lastValue = running.value
                }
                v != lastValue -> {
                    lastValue = v
                    lastProgress.value = v
                    gestureOver.value = false
                }
                else -> {
                    // Same progress as the last observed value: the drag has (at least
                    // momentarily) stopped. Wait for the value to move again, the flow to go
                    // Idle, or RELEASE_STALE_MS to elapse - whichever comes first. A moving
                    // finger changes it every frame, so only a genuinely released drag survives
                    // the whole window; a flow stuck in InProgress (the device case that never
                    // reports Idle) trips the timeout instead. The coroutine is suspended for
                    // the wait, not polling per frame.
                    val moved = withTimeoutOrNull(RELEASE_STALE_MS) {
                        snapshotFlow { running.value }.first { it != lastValue }
                    }
                    // A null here is either Idle (value went null) or the timeout; both mean
                    // the gesture is over. Same direction-confirm hold as the Idle branch.
                    if (moved == null) {
                        val flipped = withTimeoutOrNull(RELEASE_DIRECTION_CONFIRM_MS) {
                            snapshotFlow { latestSettleTo.value }.first { it != settleDirection.value }
                        }
                        if (flipped != null) settleDirection.value = flipped
                        gestureOver.value = true
                        snapshotFlow { running.value }.first { it != null }
                        lastValue = running.value
                    }
                }
            }
        }
    }
    return animateFloatAsState(
        // While a gesture runs the target is the live finger progress; once the finger is up it is
        // the confirmed settle direction; between the two - the Idle frame and the direction hold -
        // it is the last finger progress, so the page holds still instead of starting to move the
        // wrong way. gestureOver is checked first: a frozen InProgress flow (the stuck device case)
        // leaves running.value non-null even after the timeout decides the gesture is over, and
        // settling must win over the stale frozen value.
        targetValue = when {
            gestureOver.value -> settleDirection.value
            running.value != null -> running.value!!
            else -> lastProgress.value ?: settleDirection.value
        },
        // While a gesture is running the target is already correct for this frame, so it must be
        // taken as-is - any easing would be applied on top of the finger's own position, which is
        // the same mistake a curved tween made of the page's own travel. Once the finger is up this
        // is a real animation, so it settles on the release curve.
        animationSpec = if (gestureOver.value) releaseSettleSpec() else snap(),
        label = "backGestureSettleProgress",
    )
}
