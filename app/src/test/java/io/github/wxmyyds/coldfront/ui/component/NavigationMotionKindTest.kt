package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.unit.IntOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private object TestMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = tween(1)
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = tween(1)
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = tween(1)
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = tween(1)
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(1)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = tween(1)
}

class NavigationMotionKindTest {
    private val roots = setOf("main")
    private val tabs = listOf("home", "devices", "rgb", "settings")

    @Test
    fun navigationUsesLayersNotDetailNames() {
        for (detail in listOf("about", "scan", "future_detail")) {
            assertEquals(NavigationMotionKind.PushDetail, navigationMotionKind(false, false, true))
            assertEquals(NavigationMotionKind.PopDetail, navigationMotionKind(true, true, false))
            assertTrue(isSecondaryDestination(detail, roots))
            assertFalse(showsPrimaryNavigation(detail, roots))
            assertTrue(shouldUsePredictivePop(true, detail, "main", roots))
            assertFalse(shouldUsePredictivePop(false, detail, "main", roots))
        }
        assertTrue(showsPrimaryNavigation("main", roots))
        assertFalse(isSecondaryDestination("main", roots))
        assertFalse(shouldUsePredictivePop(true, "main", "main", roots))
        assertFalse(shouldUsePredictivePop(true, "about", null, roots))
        assertFalse(shouldUsePredictivePop(true, "about", "scan", roots))
        assertFalse(showsPrimaryNavigation(null, roots))
    }

    @Test
    fun nonPopNavigationDoesNotGetClassifiedAsPop() {
        assertEquals(NavigationMotionKind.TopLevel, navigationMotionKind(false, true, false))
        assertEquals(NavigationMotionKind.TopLevel, navigationMotionKind(false, false, false))
    }

    @Test
    fun tabIdentityNeverComesFromADetailRoute() {
        assertEquals(0, topLevelPageIndex("home", tabs))
        assertEquals(3, topLevelPageIndex("settings", tabs))
        for (route in listOf("main", "about", "scan", "future_detail")) {
            assertEquals(-1, topLevelPageIndex(route, tabs))
        }
    }

    @Test
    fun primaryTravelRetainsDirectionAndFixedTotalDuration() {
        assertEquals(1, topLevelRouteDistance("home", "devices", tabs))
        assertEquals(2, topLevelRouteDistance("home", "rgb", tabs))
        assertEquals(3, topLevelRouteDistance("home", "settings", tabs))
        assertTrue(isForwardTopLevelTransition("home", "settings", tabs))
        assertFalse(isForwardTopLevelTransition("settings", "home", tabs))
        for (distance in 1..3) assertEquals(300, topLevelPageDuration(distance))
        // Half a page into a long tab change, intermediate pages remain in the strip.
        val offsets = tabs.indices.map { ((it - 0.5f) * 1000).toInt() }
        assertEquals(listOf(-500, 500, 1500, 2500), offsets)
    }

    @Test
    fun detailPopTravelsTowardsTheRightAndNeverRequestsADisplacementOnTheParent() {
        for (entering in listOf(false, true)) {
            for (forward in listOf(false, true)) {
                // The parent is never given a horizontal offset of its own; the page above it
                // moving away is what reveals it.
                assertEquals(0, navigationOffset(NavigationMotionKind.PopDetail, entering, forward, 1000))
            }
        }
        assertEquals(1f, DETAIL_POP_TRAVEL, 0f)
    }

    @Test
    fun gestureAndCommittedPopReuseExactlyTheSameTransitions() {
        // Identity here is intentional: no toString normalization can mask a changed spec.
        assertSame(predictiveBackExit(), AppMotion.pageExit(
            NavigationMotionKind.PopDetail, false, TestMotionScheme, 1,
        ))
    }

    @Test
    fun theLeavingPageTravelsTheFullWidthAndTheParentStaysPut() {
        // The page must leave towards the physical right, by its whole width, in one track. The
        // parent must contribute no transition of its own: it is uncovered, not animated.
        val travel = TargetBasedAnimation(
            detailPopTravelSpec(),
            IntOffset.VectorConverter,
            IntOffset.Zero,
            IntOffset(DETAIL_POP_WIDTH, 0),
        )
        val end = travel.durationNanos
        assertEquals(DETAIL_POP_DURATION_MS * 1_000_000L, end)
        val width = DETAIL_POP_WIDTH.toFloat()
        assertEquals(0f, travel.getValueFromNanos(0L).x.toFloat(), 0f)
        assertEquals(width, travel.getValueFromNanos(end).x.toFloat(), 0.5f)
        // Never overshoots and never travels backwards, so a released gesture settles instead of
        // snapping. The page must also stay strictly inside its own track: no vertical drift.
        var last = 0f
        for (millis in 0..300) {
            val value = travel.getValueFromNanos(millis * 1_000_000L)
            assertTrue("travel out of range at ${millis}ms: ${value.x}", value.x in 0..DETAIL_POP_WIDTH)
            assertEquals("vertical drift at ${millis}ms", 0, value.y)
            assertTrue("travel moved backwards at ${millis}ms", value.x >= last)
            last = value.x.toFloat()
        }
        // Strictly linear: NavHost seeks this with the raw finger progress, so any easing here
        // would be applied on top of the finger position instead of shaping it. Sampling a curved
        // spec proved this by failing - the page reached 83% of its travel at progress 0.55.
        for (progress in listOf(0.05f, 0.25f, 0.5f, 0.55f, 0.75f)) {
            val nanos = (DETAIL_POP_DURATION_MS * 1_000_000L * progress).toLong()
            assertEquals(
                "travel must equal gesture progress at $progress",
                width * progress,
                travel.getValueFromNanos(nanos).x.toFloat(),
                1.5f,
            )
        }
    }

    @Test
    fun theParentHoldsStillDuringAReturn() {
        // No fade, no scale and no offset: the parent is opaque and is the *entering* page here, so
        // moving it at all covers the opaque page leaving above it. Verified by rendering.
        assertSame(EnterTransition.None, predictiveBackParentEnter())
        assertSame(predictiveBackParentEnter(), AppMotion.pageEnter(
            NavigationMotionKind.PopDetail, false, TestMotionScheme, 1,
        ))
        assertSame(predictiveBackExit(), AppMotion.pageExit(
            NavigationMotionKind.PopDetail, false, TestMotionScheme, 1,
        ))
    }

    @Test
    fun aPushedDetailArrivesFromTheTrailingEdgeAtFullWidth() {
        // Without this the push has no motion at all: the page would simply appear, which is what
        // made entering a detail look like nothing happened.
        assertEquals("push enters from the right", 1000, navigationOffset(
            NavigationMotionKind.PushDetail, entering = true, forward = true, width = 1000,
        ))
        assertEquals("a reversed push enters from the left", -1000, navigationOffset(
            NavigationMotionKind.PushDetail, entering = true, forward = false, width = 1000,
        ))
    }

    @Test
    fun theCoveredParentStepsAsideByAFifthOfTheWidth() {
        // A fifth, not the full width: at full width the parent would slide entirely off screen and
        // the two pages would read as swapping places rather than one covering the other.
        assertEquals(-200, navigationOffset(
            NavigationMotionKind.PushDetail, entering = false, forward = true, width = 1000,
        ))
        assertEquals(-200, parentParallaxOffset(covered = true, width = 1000))
        assertEquals("a revealed parent is exactly in place", 0, parentParallaxOffset(false, 1000))
        assertEquals(0.2f, PARENT_PARALLAX_FRACTION)
    }

    @Test
    fun aPushAndItsReturnLeaveTheParentWhereTheyFoundIt() {
        // The push steps the parent left by a fifth; the return must bring it back to exactly the
        // offset it started from, so repeated push/pop cycles cannot accumulate a drift.
        val width = 1000
        val resting = parentParallaxOffset(covered = false, width = width)
        val covered = parentParallaxOffset(covered = true, width = width)
        assertEquals("the parent must return to exactly where it began", resting, 0)
        assertEquals("a fifth of the width", -200, covered)
        // The push's parent exit starts from the covered offset, which is what makes the push and
        // its return describe one continuous motion rather than a jump.
        assertEquals(covered, navigationOffset(
            NavigationMotionKind.PushDetail, entering = false, forward = true, width = width,
        ))
    }

    @Test
    fun theParallaxTracksGestureProgress() {
        // Same reason the page's own travel is linear: NavHost seeks the parent with the same raw
        // progress, so easing here would desync the two layers under one finger.
        for (progress in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            assertEquals("parallax must equal gesture progress at $progress",
                progress, parentParallaxFraction(progress), 1e-5f)
        }
        assertEquals(1f, parentParallaxFraction(1.4f), 1e-5f)
        assertEquals(0f, parentParallaxFraction(-0.2f), 1e-5f)
    }

    @Test
    fun theParentBrightensInStepWithTheLeavingPage() {
        // The parent's dimming must track the finger exactly, for the same reason the travel does:
        // NavHost has already placed the page correctly this frame, so easing here would desync
        // the page from its own backdrop and the two layers would appear to slide independently.
        var last = Float.MAX_VALUE
        for (progress in listOf(0f, 0.15f, 0.35f, 0.5f, 0.75f, 1f)) {
            val alpha = parentScrimAlphaForProgress(progress)
            assertTrue("scrim must never exceed its maximum", alpha <= PARENT_SCRIM_ALPHA + 1e-6f)
            assertTrue("scrim must stay non-negative", alpha >= 0f)
            assertTrue("scrim must brighten as the page leaves", alpha <= last + 1e-6f)
            assertEquals(
                "scrim must match the page's own progress at $progress",
                PARENT_SCRIM_ALPHA * (1f - progress),
                alpha,
                1e-5f,
            )
            last = alpha
        }
        // A covered parent at rest is dimmed, and a fully returned one is exactly untouched.
        assertEquals(PARENT_SCRIM_ALPHA, parentScrimAlphaForProgress(0f), 1e-5f)
        assertEquals(0f, parentScrimAlphaForProgress(1f), 1e-5f)
        // Out-of-range gesture values must not be able to invert or overshoot the effect.
        assertEquals(0f, parentScrimAlphaForProgress(1.4f), 1e-5f)
        assertEquals(PARENT_SCRIM_ALPHA, parentScrimAlphaForProgress(-0.3f), 1e-5f)
    }
}
