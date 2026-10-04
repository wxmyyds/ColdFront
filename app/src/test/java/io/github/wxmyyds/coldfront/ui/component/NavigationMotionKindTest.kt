package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val effects = detailPopEffectsSpec()

        // A spring would overshoot while the predictive gesture seeks every frame, so the page
        // would drift off the finger and rebound after release.
        assertFalse("detail pop spatial must not spring", spatial is SpringSpec<*>)
        assertTrue("detail pop spatial must tween", spatial is TweenSpec<*>)
        assertEquals(DETAIL_POP_DURATION_MS, (spatial as TweenSpec).durationMillis)

        assertFalse("detail pop alpha must not spring", effects is SpringSpec<*>)
        assertTrue("detail pop alpha must tween", effects is TweenSpec<*>)
        assertEquals(DETAIL_POP_DURATION_MS, (effects as TweenSpec).durationMillis)
    }

    @Test
    fun detailPopAndPushShareOneFadeLevelSoThePairReverses() {
        // Push dims the detail in; pop must dim it out by the same amount, otherwise the parent
        // reveals at a different brightness depending on which direction the user travelled.
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
