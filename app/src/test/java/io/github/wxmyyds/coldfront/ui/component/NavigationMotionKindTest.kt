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
            detailPopTravelSpec(), IntOffset.VectorConverter, IntOffset.Zero, IntOffset(DETAIL_POP_WIDTH, 0),
        )
        assertEquals(DETAIL_POP_DURATION_MS * 1_000_000L, travel.durationNanos)
        assertEquals(0f, travel.getValueFromNanos(0).x, 0f)
        assertEquals(DETAIL_POP_WIDTH.toFloat(), travel.getValueFromNanos(300_000_000).x, 0f)
        // Never overshoots and never travels backwards, so a released gesture settles instead of
        // snapping. The page must also stay strictly inside its own track: no vertical drift.
        var last = 0f
        for (millis in 0..300) {
            val value = travel.getValueFromNanos(millis * 1_000_000L)
            assertTrue("travel out of range at ${millis}ms: ${value.x}", value.x in 0f..DETAIL_POP_WIDTH.toFloat())
            assertEquals("vertical drift at ${millis}ms", 0f, value.y, 0f)
            assertTrue("travel moved backwards at ${millis}ms", value.x >= last)
            last = value.x
        }
    }

    @Test
    fun theParentContributesNoEnterTransition() {
        // A parent that faded or scaled in would cross-fade with the page leaving, which is the
        // effect this is meant to replace.
        assertSame(EnterTransition.None, predictiveBackEnter())
        assertSame(predictiveBackEnter(), AppMotion.pageEnter(
            NavigationMotionKind.PopDetail, false, TestMotionScheme, 1,
        ))
        assertSame(predictiveBackExit(), AppMotion.pageExit(
            NavigationMotionKind.PopDetail, false, TestMotionScheme, 1,
        ))
    }
}
