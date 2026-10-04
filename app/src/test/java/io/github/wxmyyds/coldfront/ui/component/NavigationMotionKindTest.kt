package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Detail pop uses fixed tween specs, so the scheme's own values cannot change these assertions.
 * Mirrors the six [MotionScheme] members; all specs collapse to a trivial tween.
 */
private object TestMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = tween(1)
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = tween(1)
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = tween(1)
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = tween(1)
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(1)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = tween(1)
}

class NavigationMotionKindTest {
    @Test
    fun pushToSecondaryUsesDetailPush() {
        assertEquals(
            NavigationMotionKind.PushDetail,
            navigationMotionKind(
                isPop = false,
                initialIsSecondary = false,
                targetIsSecondary = true,
            ),
        )
    }

    @Test
    fun popFromSecondaryUsesReverseDetailMotion() {
        assertEquals(
            NavigationMotionKind.PopDetail,
            navigationMotionKind(
                isPop = true,
                initialIsSecondary = true,
                targetIsSecondary = false,
            ),
        )
    }

    @Test
    fun navigatingFromDetailToRootIsNotMisclassifiedAsPop() {
        assertEquals(
            NavigationMotionKind.TopLevel,
            navigationMotionKind(
                isPop = false,
                initialIsSecondary = true,
                targetIsSecondary = false,
            ),
        )
    }

    @Test
    fun predictiveBackAppliesOnlyToADetailReturningToItsParent() {
        // MAIN is the only primary destination, so every detail returns to it.
        val navTopLevel = setOf("main")

        assertEquals(
            true,
            shouldUsePredictivePop(
                predictiveBackEnabled = true,
                currentRoute = "about",
                previousRoute = "main",
                topLevelRoutes = navTopLevel,
            ),
        )
        assertEquals(
            true,
            shouldUsePredictivePop(
                predictiveBackEnabled = true,
                currentRoute = "scan",
                previousRoute = "main",
                topLevelRoutes = navTopLevel,
            ),
        )

        // Switching tabs never touches the NavHost, so there is no back stack and no gesture.
        assertEquals(
            false,
            shouldUsePredictivePop(
                predictiveBackEnabled = true,
                currentRoute = "main",
                previousRoute = "main",
                topLevelRoutes = navTopLevel,
            ),
        )

        // The user's setting still turns the gesture off entirely.
        assertEquals(
            false,
            shouldUsePredictivePop(
                predictiveBackEnabled = false,
                currentRoute = "about",
                previousRoute = "main",
                topLevelRoutes = navTopLevel,
            ),
        )
    }


    @Test
    fun primaryNavigationShowsOnlyOnTopLevelPages() {
        val roots = setOf("home", "devices", "rgb", "settings")

        // Top-level pages own the bar/rail: they are what those affordances navigate between.
        assertTrue(showsPrimaryNavigation("home", roots))
        assertTrue(showsPrimaryNavigation("devices", roots))
        assertTrue(showsPrimaryNavigation("rgb", roots))
        assertTrue(showsPrimaryNavigation("settings", roots))

        // A detail page is pushed on top of one of them, so the bar must get out of the way.
        assertFalse(showsPrimaryNavigation("about", roots))
        assertFalse(showsPrimaryNavigation("scan", roots))

        // The predicate itself rejects null; the caller resolves the cold-start frame to the
        // graph's start destination rather than relying on this to guess.
        assertFalse(showsPrimaryNavigation(null, roots))
    }

    @Test
    fun primaryNavigationIsKeyedOnLayerNotOnRouteNames() {        val roots = setOf("home", "devices", "rgb", "settings")

        // Any route outside the top-level set is a detail, so adding one later needs no change
        // here. This is what keeps the rule from degrading into per-page special cases.
        assertFalse(showsPrimaryNavigation("some_future_detail", roots))
        assertFalse(showsPrimaryNavigation("about", roots))
    }

    @Test
    fun primaryNavigationIsVisibleOnTheFirstFrameOfAColdStart() {
        val roots = setOf("home", "devices", "rgb", "settings")

        // currentBackStackEntryAsState collects with a null seed, so the first frame has no route.
        // Resolving that frame to the graph's start destination keeps the bar visible from frame
        // one; passing null through would hide it and reveal it a frame later.
        assertTrue(showsPrimaryNavigation("home", roots))

        // Only the not-yet-resolved case is substituted, never a real detail route.
        assertFalse(showsPrimaryNavigation("about", roots))
        assertFalse(showsPrimaryNavigation(null, roots))
    }



    @Test
    fun aDetailPopResolvesToPopDetailEvenThoughItsParentIsAPlaceholder() {
        val roots = setOf("home", "devices", "rgb", "settings")

        // Documents why the guards cannot be keyed on motion kind: About -> Settings is a
        // PopDetail, so a kind-based check would let the placeholder through and reintroduce the
        // second animation.
        assertEquals(
            NavigationMotionKind.PopDetail,
            navigationMotionKind(
                isPop = true,
                initialIsSecondary = isSecondaryDestination("about", roots),
                targetIsSecondary = isSecondaryDestination("settings", roots),
            ),
        )
    }


    @Test
    fun primaryNavigationVisibilityIsTheOnlyInputToTheBar() {
        // The bar is rendered by a plain conditional on this single predicate. There is no
        // transition to configure any more, so nothing can reintroduce motion on the way back:
        // a route that is top-level either draws the bar at its final position or does not draw
        // it at all.
        val roots = setOf("home", "devices", "rgb", "settings")
        assertTrue(showsPrimaryNavigation("settings", roots))
        assertFalse(showsPrimaryNavigation("about", roots))
    }

    @Test
    fun theNavHostHasOnePrimaryDestinationAndEverythingElseIsADetail() {
        // The four tabs are pages inside MAIN, so MAIN is the only primary destination. Every
        // other destination is a detail page with a real parent underneath it, which is what lets
        // a predictive pop reveal a page that genuinely exists.
        val navTopLevel = setOf("main")

        assertTrue(isTopLevelDestination("main", navTopLevel))
        assertFalse(isTopLevelDestination("about", navTopLevel))
        assertFalse(isTopLevelDestination("scan", navTopLevel))

        assertTrue(isSecondaryDestination("about", navTopLevel))
        assertTrue(isSecondaryDestination("scan", navTopLevel))
        assertFalse(isSecondaryDestination("main", navTopLevel))
    }

    @Test
    fun returningToMainIsAPopDetailThatMovesOnlyTheLeavingPage() {
        val navTopLevel = setOf("main")

        // ABOUT -> MAIN is a detail pop, so the leaving page gets the pop motion.
        assertEquals(
            NavigationMotionKind.PopDetail,
            navigationMotionKind(
                isPop = true,
                initialIsSecondary = isSecondaryDestination("about", navTopLevel),
                targetIsSecondary = isSecondaryDestination("main", navTopLevel),
            ),
        )

        // The leaving detail page slides out; the revealed parent does not move at all, because it
        // is uncovered rather than entered.
        assertEquals(
            200,
            navigationOffset(NavigationMotionKind.PopDetail, entering = false, forward = false, width = 1000),
        )
        assertEquals(
            0,
            navigationOffset(NavigationMotionKind.PopDetail, entering = true, forward = false, width = 1000),
        )
    }

    @Test
    fun switchingPrimaryTabsIsPagerStateAndNotNavigation() {
        val tabs = listOf("home", "devices", "rgb", "settings")

        // Tab identity lives inside the pager, so it maps to a page index rather than to a route.
        assertEquals(0, topLevelPageIndex("home", tabs))
        assertEquals(3, topLevelPageIndex("settings", tabs))
        assertEquals(-1, topLevelPageIndex("about", tabs))

        // Jumping tabs costs the same time regardless of distance, so crossing several pages reads
        // as one action instead of a chain of them.
        assertEquals(TOP_LEVEL_PAGE_DURATION_MS, topLevelPageDuration(1))
        assertEquals(TOP_LEVEL_PAGE_DURATION_MS, topLevelPageDuration(3))
    }

    @Test
    fun theStripPassesThroughPagesInBetween() {
        val tabs = listOf("home", "devices", "rgb", "settings")
        val width = 1000f
        val from = topLevelPageIndex("home", tabs)
        val to = topLevelPageIndex("settings", tabs)
        val mid = topLevelPageIndex("devices", tabs)

        // Mid-drag, every page sits at its own offset, so the pages in between are genuinely
        // travelled through instead of being cut away.
        val position = from + 0.5f
        val offsets = tabs.indices.associateWith { index ->
            ((index - position) * width).toInt()
        }
        assertTrue("start is left of centre", offsets.getValue(from) > 0)
        assertTrue("target is right of centre", offsets.getValue(to) < 0)
        assertEquals(0, offsets.getValue(mid))
    }

    @Test
    fun transitionsWithinSameLayerUseTopLevelMotion() {
        assertEquals(
            NavigationMotionKind.TopLevel,
            navigationMotionKind(
                isPop = false,
                initialIsSecondary = false,
                targetIsSecondary = false,
            ),
        )
        assertEquals(
            NavigationMotionKind.TopLevel,
            navigationMotionKind(
                isPop = true,
                initialIsSecondary = true,
                targetIsSecondary = true,
            ),
        )
    }

    @Test
    fun destinationLayerComesFromTheTopLevelNavigationSet() {
        val topLevelRoutes = setOf("home", "devices", "rgb", "settings")

        assertEquals(true, isTopLevelDestination("settings", topLevelRoutes))
        assertEquals(false, isSecondaryDestination("settings", topLevelRoutes))
        assertEquals(true, isSecondaryDestination("about", topLevelRoutes))
        assertEquals(false, isTopLevelDestination("about", topLevelRoutes))
        assertEquals(false, isSecondaryDestination(null, topLevelRoutes))
    }

    @Test
    fun detailPushKeepsParallaxButPopRevealsTheParentInPlace() {
        val width = 1000

        // Push: the detail enters from the right while the parent recedes left.
        assertEquals(200, navigationOffset(NavigationMotionKind.PushDetail, true, true, width))
        assertEquals(-200, navigationOffset(NavigationMotionKind.PushDetail, false, true, width))

        // Pop: the parent does not drift, so it reads as revealed rather than moving on its own.
        assertEquals(0, navigationOffset(NavigationMotionKind.PopDetail, true, false, width))
        assertEquals(200, navigationOffset(NavigationMotionKind.PopDetail, false, false, width))
    }

    @Test
    fun detailPopIsSeekingSafe() {
        val spatial = detailPopSpatialSpec()

        // A spring would overshoot while the predictive gesture seeks every frame, so the page
        // would drift off the finger and rebound after release.
        assertFalse("detail pop spatial must not spring", spatial is SpringSpec<*>)
        assertTrue("detail pop spatial must tween", spatial is TweenSpec<*>)
        assertEquals(DETAIL_POP_DURATION_MS, (spatial as TweenSpec).durationMillis)
    }

    @Test
    fun detailPopKeepsThePageOpaqueSoOnlyItsEdgeReads() {
        // The leaving page must not fade. Dimming it would let the window behind show through as a
        // dark veil across the surface, which reads as a mask rather than the platform's edge
        // treatment on a page being swiped away. The exit is a pure slide, so the page stays
        // opaque and only its shadowed edge reads while it moves.
        val exit = AppMotion.pageExit(
            kind = NavigationMotionKind.PopDetail,
            forward = false,
            motionScheme = TestMotionScheme,
            routeDistance = 1,
        )
        assertTrue("detail pop must slide", exit.toString().contains("Slide"))
        assertFalse("detail pop must not fade", exit.toString().contains("Fade - Fade"))
    }

    @Test
    fun detailPushAndPopShareOneFadeLevel() {
        // Push dims the detail in, so both directions agree on the resting alpha.
        assertEquals(0.94f, DETAIL_FADE_ALPHA, 0f)
    }

    @Test
    fun topLevelTravelUsesTheParentRouteForSecondaryDestinations() {
        val roots = listOf("home", "devices", "rgb", "settings")

        assertEquals(3, topLevelRouteDistance("about", "home", roots))
        assertEquals(false, isForwardTopLevelTransition("about", "home", roots))
        assertEquals(1, topLevelRouteDistance("about", "settings", roots))
        assertEquals(true, isForwardTopLevelTransition("about", "settings", roots))
    }

    @Test
    fun topLevelTravelUsesFixedDurationAndDirection() {
        val roots = listOf("home", "devices", "rgb", "settings")

        assertEquals(1, topLevelRouteDistance("home", "devices", roots))
        assertEquals(2, topLevelRouteDistance("home", "rgb", roots))
        assertEquals(3, topLevelRouteDistance("home", "settings", roots))
        assertEquals(TOP_LEVEL_PAGE_DURATION_MS, topLevelPageDuration(1))
        assertEquals(TOP_LEVEL_PAGE_DURATION_MS, topLevelPageDuration(2))
        assertEquals(TOP_LEVEL_PAGE_DURATION_MS, topLevelPageDuration(3))
        assertEquals(TOP_LEVEL_PAGE_DURATION_MS, topLevelPageDuration(8))
        assertEquals(true, isForwardTopLevelTransition("home", "settings", roots))
        assertEquals(false, isForwardTopLevelTransition("settings", "home", roots))
    }

    @Test
    fun topLevelPageIndexKeepsAboutOnSettingsAndLeavesSecondaryRoutesUnmapped() {
        val roots = listOf("home", "devices", "rgb", "settings")

        assertEquals(3, topLevelPageIndex("about", roots))
        assertEquals(-1, topLevelPageIndex("scan", roots))
    }

}
