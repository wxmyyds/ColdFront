package io.github.wxmyyds.coldfront.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationMotionKindTest {
    @Test
    fun rootToSecondaryUsesDetailPush() {
        assertEquals(
            NavigationMotionKind.PushDetail,
            navigationMotionKind(initialIsSecondary = false, targetIsSecondary = true),
        )
    }

    @Test
    fun secondaryToRootUsesReverseDetailPop() {
        assertEquals(
            NavigationMotionKind.PopDetail,
            navigationMotionKind(initialIsSecondary = true, targetIsSecondary = false),
        )
    }

    @Test
    fun transitionsWithinSameLayerUseTopLevelMotion() {
        assertEquals(
            NavigationMotionKind.TopLevel,
            navigationMotionKind(initialIsSecondary = false, targetIsSecondary = false),
        )
        assertEquals(
            NavigationMotionKind.TopLevel,
            navigationMotionKind(initialIsSecondary = true, targetIsSecondary = true),
        )
    }
}
