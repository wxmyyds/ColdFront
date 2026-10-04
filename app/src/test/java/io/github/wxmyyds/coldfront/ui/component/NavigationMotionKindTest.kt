package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
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
    fun detailPopNeverRequestsHorizontalOffsets() {
        for (entering in listOf(false, true)) {
            for (forward in listOf(false, true)) {
                assertEquals(0, navigationOffset(NavigationMotionKind.PopDetail, entering, forward, 1000))
            }
        }
    }

    @Test
    fun gestureAndCommittedPopReuseExactlyTheSameTransitions() {
        // Identity here is intentional: no toString normalization can mask a changed spec.
        assertSame(AppMotion.predictiveBackEnter(), AppMotion.pageEnter(
            NavigationMotionKind.PopDetail, false, TestMotionScheme, 1,
        ))
        assertSame(AppMotion.predictiveBackExit(), AppMotion.pageExit(
            NavigationMotionKind.PopDetail, false, TestMotionScheme, 1,
        ))
    }

    @Test
    fun alphaTracksUseTheThresholdAndNeverUseScaleValues() {
        val exit = animation(predictiveBackExitSpec(), 1f, 0f)
        val enter = animation(predictiveBackEnterSpec(), 0f, 1f)
        assertEquals(1f, exit.getValueFromNanos(0), 0f)
        assertEquals(0f, enter.getValueFromNanos(0), 0f)
        assertEquals(0f, exit.getValueFromNanos(105_000_000), 0f)
        assertEquals(0f, enter.getValueFromNanos(105_000_000), 0f)
        assertEquals(0f, exit.getValueFromNanos(300_000_000), 0f)
        assertEquals(1f, enter.getValueFromNanos(300_000_000), 0f)
        assertTrue(exit.getValueFromNanos(50_000_000) in 0.001f..0.999f)
        assertTrue(enter.getValueFromNanos(200_000_000) in 0.001f..0.999f)
        var lastExit = 1f
        var lastEnter = 0f
        for (millis in 0..300) {
            val leaving = exit.getValueFromNanos(millis * 1_000_000L)
            val parent = enter.getValueFromNanos(millis * 1_000_000L)
            assertTrue(leaving in 0f..1f && parent in 0f..1f)
            assertTrue(leaving <= lastExit && parent >= lastEnter)
            lastExit = leaving
            lastEnter = parent
        }
    }

    @Test
    fun scaleAndAlphaShareOneDurationAndReverseWithoutOvershoot() {
        val exitScale = animation(predictiveBackScaleSpec(), 1f, PREDICTIVE_BACK_EXIT_SCALE)
        val enterScale = animation(predictiveBackScaleSpec(), PREDICTIVE_BACK_ENTER_START_SCALE, 1f)
        val exitAlpha = animation(predictiveBackExitSpec(), 1f, 0f)
        val enterAlpha = animation(predictiveBackEnterSpec(), 0f, 1f)
        for (track in listOf(exitScale, enterScale, exitAlpha, enterAlpha)) {
            assertEquals(300_000_000L, track.durationNanos)
        }
        // Seek forwards then backwards as a cancelled gesture does; values are timeline-based.
        for (millis in (0..300) + (300 downTo 0)) {
            val t = millis * 1_000_000L
            assertTrue(exitScale.getValueFromNanos(t) in 0.9f..1f)
            assertTrue(enterScale.getValueFromNanos(t) in 1f..1.1f)
        }
        assertEquals(1f, exitScale.getValueFromNanos(0), 0f)
        assertEquals(1.1f, enterScale.getValueFromNanos(0), 0f)
        assertEquals(0.9f, exitScale.getValueFromNanos(300_000_000), 0f)
        assertEquals(1f, enterScale.getValueFromNanos(300_000_000), 0f)
    }

    private fun animation(spec: FiniteAnimationSpec<Float>, from: Float, to: Float) =
        TargetBasedAnimation(spec, Float.VectorConverter, from, to)
}
