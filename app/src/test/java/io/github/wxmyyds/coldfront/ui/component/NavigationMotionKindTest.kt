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
    fun predictiveBackIsOnlyUsedFromSecondaryToTopLevel() {
        assertEquals(
            true,
            shouldUsePredictivePop(
                predictiveBackEnabled = true,
                initialIsSecondary = true,
                targetIsTopLevel = true,
            ),
        )
        assertEquals(
            false,
            shouldUsePredictivePop(
                predictiveBackEnabled = true,
                initialIsSecondary = false,
                targetIsTopLevel = true,
            ),
        )
        assertEquals(
            false,
            shouldUsePredictivePop(
                predictiveBackEnabled = true,
                initialIsSecondary = true,
                targetIsTopLevel = false,
            ),
        )
        assertEquals(
            false,
            shouldUsePredictivePop(
                predictiveBackEnabled = false,
                initialIsSecondary = true,
                targetIsTopLevel = true,
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
}
