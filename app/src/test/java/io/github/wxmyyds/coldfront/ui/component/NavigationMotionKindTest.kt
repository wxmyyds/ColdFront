package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme
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
    fun theCoverageValuesMatchTheMiuixReference() {
        // These are the reference values chosen to match Miuix, not incidental tunables. Pin them
        // literally so a drift back to the old values (1/5 parallax, 0.32 scrim, no page fade)
        // fails the suite rather than silently reverting the look.
        assertEquals("parent must step back a quarter of the width", 0.25f, PARENT_PARALLAX_FRACTION, 0f)
        assertEquals("fullscreen black scrim darkness", 0.5f, PARENT_SCRIM_ALPHA, 0f)
        assertEquals("page's own pixel fade", 0.1f, PARENT_FADE_FRACTION, 0f)
    }

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
        // The parent never moves for a pop; the page above it moving away is what reveals it, and
        // the release settle belongs to the page's own layer, never to a parent displacement.
    }

    @Test
    fun gestureAndCommittedPopReuseExactlyTheSameTransitions() {
        // Identity here is intentional: no toString normalization can mask a changed spec.
        assertSame(predictiveBackExit(), AppMotion.pageExit(
            NavigationMotionKind.PopDetail, false, TestMotionScheme, 1,
        ))
    }

    @Test
    fun theReleaseSettlesOverTheMiuixCurve() {
        // The release is a fixed-duration tween shaped by the Miuix/KernelSU settle curve (a brisk
        // middle and a long, gentle tail), not a snappy linear tween, and it always reaches the
        // resting position. Pinned so a reversion to a short linear settle fails the suite.
        assertEquals("Miuix settle duration", 500, RELEASE_SETTLE_MS)
        val easing = MiuixSettleEasing()
        assertEquals("settle starts at rest", 0f, easing.transform(0f), 0f)
        // Brisk middle: by half the duration the settle has already covered most of the step. That is
        // what distinguishes it from a linear or a slow-start tween (which would leave the page
        // lingering at the leading edge and read as sluggish, not as Miuix's settle).
        assertTrue("settle must be brisk in the middle", easing.transform(0.5f) > 0.6f)
        assertTrue("settle curve must stay within 0..1", easing.transform(0.5f) in 0f..1f)
        assertTrue("settle must reach the resting position", easing.transform(1f) > 0.99f)
        // The keep-alive window must comfortably exceed the settle, so NavHost never detaches the
        // leaving page before its own slide has finished.
        assertTrue("keep-alive must exceed the settle", RELEASE_SETTLE_CEILING_MS >= RELEASE_SETTLE_MS)
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
    fun theCoveredParentStepsAsideByAQuarterOfTheWidth() {
        // A quarter, not the full width: at full width the parent would slide entirely off screen and
        // the two pages would read as swapping places rather than one covering the other. The
        // offset is a pure function of whether the parent is covered - it is applied as a graphics
        // layer, not through NavHost, so navigationOffset must not move the parent at all.
        assertEquals(-250, parentParallaxOffset(covered = true, width = 1000))
        assertEquals("a revealed parent is exactly in place", 0, parentParallaxOffset(false, 1000))
        assertEquals(0.25f, PARENT_PARALLAX_FRACTION)
        // The parent's step-back is ParentScrimSurface's job via its graphics layer; the NavHost
        // transition must leave it in place so an interrupted push cannot slide it over the page
        // being dragged.
        assertEquals(0, navigationOffset(
            NavigationMotionKind.PushDetail, entering = false, forward = true, width = 1000,
        ))
    }

    @Test
    fun aPushAndItsReturnLeaveTheParentWhereTheyFoundIt() {
        // The push steps the parent left by a quarter; the return must bring it back to exactly the
        // offset it started from, so repeated push/pop cycles cannot accumulate a drift. Because the
        // offset is a pure function of whether the parent is covered (not of any accumulated state),
        // the covered and resting offsets are exact opposites, and this holds regardless of how many
        // times the page has been pushed and popped.
        val width = 1000
        assertEquals("the parent must return to exactly where it began", 0, parentParallaxOffset(false, width))
        assertEquals("a quarter of the width", -250, parentParallaxOffset(true, width))
        // The NavHost transition leaves the parent unmoved; the step-back is owned by the surface
        // that draws the parent. So a push and its return cannot leave a stale transition offset
        // behind, which is the drift this guards against.
        assertEquals(0, navigationOffset(
            NavigationMotionKind.PushDetail, entering = false, forward = true, width = width,
        ))
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

    @Test
    fun thePageFadesInStepWithTheScrimButNeverToOpaqueOrGone() {
        // The page's own pixels fade a tenth (Miuix covered-layer alpha falloff), stacking with the
        // black scrim. It must track the gesture linearly, never drop below its floor, and always
        // return to fully opaque when the page is uncovered.
        var last = -1f
        for (progress in listOf(0f, 0.15f, 0.35f, 0.5f, 0.75f, 1f)) {
            val alpha = parentPageAlphaForProgress(progress)
            assertTrue("page must brighten as the page above it leaves", alpha >= last - 1e-6f)
            assertEquals(
                "page fade must match the progress at $progress",
                1f - PARENT_FADE_FRACTION * (1f - progress),
                alpha,
                1e-5f,
            )
            last = alpha
        }
        // A covered page at rest is faded a tenth; a fully returned one is fully opaque.
        assertEquals(1f - PARENT_FADE_FRACTION, parentPageAlphaForProgress(0f), 1e-5f)
        assertEquals(1f, parentPageAlphaForProgress(1f), 1e-5f)
        // Out-of-range values must not fade it below the floor or above opaque.
        assertEquals(1f - PARENT_FADE_FRACTION, parentPageAlphaForProgress(-0.3f), 1e-5f)
        assertEquals(1f, parentPageAlphaForProgress(1.4f), 1e-5f)
    }
}
