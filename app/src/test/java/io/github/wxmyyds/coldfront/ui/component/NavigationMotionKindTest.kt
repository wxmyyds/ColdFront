package io.github.wxmyyds.coldfront.ui.component

import org.junit.Assert.assertEquals
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
    fun topLevelTravelControlsDurationAndDirection() {
        val roots = listOf("home", "devices", "rgb", "settings")

        assertEquals(1, topLevelRouteDistance("home", "devices", roots))
        assertEquals(3, topLevelRouteDistance("settings", "home", roots))
        assertEquals(200, topLevelPageDuration(1))
        assertEquals(400, topLevelPageDuration(3))
        assertEquals(500, topLevelPageDuration(8))
        assertEquals(true, isForwardTopLevelTransition("home", "settings", roots))
        assertEquals(false, isForwardTopLevelTransition("settings", "home", roots))
    }
}
