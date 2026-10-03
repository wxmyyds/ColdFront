package io.github.wxmyyds.coldfront.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The predictive-back constants are the parts of the AOSP cross-activity motion that survive the
 * move onto NavHost, which exposes only the swipe edge. They are pinned here so a well-meaning
 * tweak does not quietly flatten the gesture.
 */
class PredictiveBackMotionTest {
    @Test
    fun outgoingPageShrinksButStaysVisible() {
        assertEquals(0.9f, AppPredictiveBack.MIN_SCALE, 0f)
        assertTrue(
            "the page must actually shrink, otherwise both pages only translate and the " +
                "gesture reads as flat",
            AppPredictiveBack.MIN_SCALE < 1f,
        )
        assertTrue(
            "shrinking too far makes the page look like a card leaving, not a window receding",
            AppPredictiveBack.MIN_SCALE >= 0.8f,
        )
    }

    @Test
    fun driftCarriesThePageOffScreenWithoutOverrunning() {
        assertTrue(
            "drift must be enough to clear the page as it leaves",
            AppPredictiveBack.DRIFT_FRACTION > 0.1f,
        )
        assertTrue(
            "overshooting far past the edge is wasted travel and reads as sluggish",
            AppPredictiveBack.DRIFT_FRACTION < 0.5f,
        )
    }

    @Test
    fun theTwoPagesNeverLookIdenticalMidGesture() {
        // If both pages sat at the same alpha the stack would read as a dissolve rather than a
        // page being pushed behind another.
        assertTrue(
            AppPredictiveBack.OUTGOING_FADE_END < AppPredictiveBack.INCOMING_FADE_START,
        )
    }

    @Test
    fun fadeEndpointsStayInRange() {
        assertTrue(AppPredictiveBack.OUTGOING_FADE_END in 0f..1f)
        assertTrue(AppPredictiveBack.INCOMING_FADE_START in 0f..1f)
        assertTrue(AppPredictiveBack.CLASSIC_FADE_DURATION_MS > 0)
    }

    @Test
    fun gestureFromTheRightEdgePushesThePageLeft() {
        // NavEvent.EDGE_LEFT == 0, EDGE_RIGHT == 1.
        assertEquals(1f, AppPredictiveBack.outgoingDirection(EDGE_LEFT), 0f)
        assertEquals(-1f, AppPredictiveBack.outgoingDirection(EDGE_RIGHT), 0f)
    }

    @Test
    fun aNonSwipeBackFallsBackToTheLeftEdge() {
        // EDGE_NONE and any unrecognised value must still produce a usable direction rather than
        // crashing or collapsing the motion to zero.
        assertEquals(1f, AppPredictiveBack.outgoingDirection(EDGE_NONE), 0f)
        assertEquals(1f, AppPredictiveBack.outgoingDirection(-1), 0f)
    }

    @Test
    fun everyDetailPopCallbackDecidesTheSameWay() {
        // The regression: the commit and gesture callbacks used to pick their motion separately, so
        // one return could play the gesture branch first and the commit branch afterwards. Any
        // disagreement here reintroduces two animations for a single pop.
        val roots = setOf("home", "devices", "rgb", "settings")

        for (enabled in listOf(true, false)) {
            assertEquals(
                "gesture and commit disagreed with predictive back $enabled",
                detailPopMotionEnabled(enabled, NavigationMotionKind.PopDetail),
                predictiveGestureMotionEnabled(
                    predictiveBackEnabled = enabled,
                    currentRoute = "about",
                    previousRoute = "settings",
                    topLevelRoutes = roots,
                ),
            )
        }
    }

    @Test
    fun disablingPredictiveBackSilencesBothHalves() {
        // If only the gesture branch opted out, releasing a gesture would jump: the page would
        // stop tracking the finger and then snap into the commit animation.
        assertEquals(
            false,
            detailPopMotionEnabled(false, NavigationMotionKind.PopDetail),
        )
        assertEquals(
            false,
            predictiveGestureMotionEnabled(
                predictiveBackEnabled = false,
                currentRoute = "about",
                previousRoute = "settings",
                topLevelRoutes = setOf("home", "devices", "rgb", "settings"),
            ),
        )
    }

    @Test
    fun otherKindsKeepTheirExistingMotion() {
        // Only a detail pop moves to the predictive motion; top-level paging must be untouched.
        assertEquals(
            false,
            detailPopMotionEnabled(true, NavigationMotionKind.TopLevel),
        )
        assertEquals(
            false,
            detailPopMotionEnabled(true, NavigationMotionKind.PushDetail),
        )
    }

    @Test
    fun theGestureKeepsItsDestinationGuard() {
        val roots = setOf("home", "devices", "rgb", "settings")

        // Popping into another detail page is not the detail pop this motion is defined for.
        assertEquals(
            false,
            predictiveGestureMotionEnabled(
                predictiveBackEnabled = true,
                currentRoute = "about",
                previousRoute = "scan",
                topLevelRoutes = roots,
            ),
        )
        assertEquals(
            true,
            predictiveGestureMotionEnabled(
                predictiveBackEnabled = true,
                currentRoute = "scan",
                previousRoute = "devices",
                topLevelRoutes = roots,
            ),
        )
    }
}
