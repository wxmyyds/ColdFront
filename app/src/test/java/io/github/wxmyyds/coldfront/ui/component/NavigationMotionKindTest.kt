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
    fun predictiveBackRequiresASecondaryCurrentDestinationAndTopLevelParent() {
        val roots = setOf("home", "devices", "rgb", "settings")

        assertEquals(
            true,
            shouldUsePredictivePop(
                predictiveBackEnabled = true,
                currentRoute = "about",
                previousRoute = "settings",
                topLevelRoutes = roots,
            ),
        )
        assertEquals(
            true,
            shouldUsePredictivePop(
                predictiveBackEnabled = true,
                currentRoute = "scan",
                previousRoute = "devices",
                topLevelRoutes = roots,
            ),
        )
        assertEquals(
            false,
            shouldUsePredictivePop(
                predictiveBackEnabled = true,
                currentRoute = "about",
                previousRoute = "scan",
                topLevelRoutes = roots,
            ),
        )
        assertEquals(
            false,
            shouldUsePredictivePop(
                predictiveBackEnabled = true,
                currentRoute = "settings",
                previousRoute = "home",
                topLevelRoutes = roots,
            ),
        )
        assertEquals(
            false,
            shouldUsePredictivePop(
                predictiveBackEnabled = true,
                currentRoute = "home",
                previousRoute = null,
                topLevelRoutes = roots,
            ),
        )
        assertEquals(
            false,
            shouldUsePredictivePop(
                predictiveBackEnabled = false,
                currentRoute = "about",
                previousRoute = "settings",
                topLevelRoutes = roots,
            ),
        )
    }

    @Test
    fun topLevelPlaceholdersAreNeverAnimatedByTheNavHost() {
        val roots = setOf("home", "devices", "rgb", "settings")

        // Top-level pages are drawn by the pager; their NavHost entries are empty placeholders.
        assertFalse(isRenderedByNavHost("settings", roots))
        assertFalse(isRenderedByNavHost("home", roots))
        assertFalse(isRenderedByNavHost(null, roots))

        // Only secondary pages are real NavHost content.
        assertTrue(isRenderedByNavHost("about", roots))
        assertTrue(isRenderedByNavHost("scan", roots))
    }

    @Test
    fun returningFromAboutToSettingsAnimatesOnlyTheDetailPage() {
        val roots = setOf("home", "devices", "rgb", "settings")

        // The NavHost must not animate this pair, because one end is the top-level placeholder.
        // A motion-kind test would wrongly pass it: the pair resolves to PopDetail even though
        // its parent is a placeholder, which is exactly how the second animation slipped through.
        assertFalse(
            isNavHostTransitionPair(
                initialRoute = "about",
                targetRoute = "settings",
                topLevelRoutes = roots,
            ),
        )

        // Two detail pages still animate between each other.
        assertTrue(
            isNavHostTransitionPair(
                initialRoute = "about",
                targetRoute = "scan",
                topLevelRoutes = roots,
            ),
        )

        // The leaving detail page is what moves, and the revealed parent does not move at all.
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
    fun aDetailPopResolvesToPopDetailEvenThoughItsParentIsAPlaceholder() {
        val roots = setOf("home", "devices", "rgb", "settings")

        // Documents why the guard cannot be keyed on motion kind: About -> Settings is a
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
    fun switchingBetweenTopLevelTabsIsNotAnimatedTwice() {
        val roots = setOf("home", "devices", "rgb", "settings")

        // Tab switches are entirely the pager's business; the NavHost adds nothing on top.
        assertFalse(
            isNavHostTransitionPair(
                initialRoute = "home",
                targetRoute = "settings",
                topLevelRoutes = roots,
            ),
        )
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
