package io.github.wxmyyds.coldfront.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTestConfig
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.wxmyyds.coldfront.ui.component.DETAIL_POP_DURATION_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Runs production NavHost + chrome + pager, not reconstructed transition strings. */
class PredictiveBackRenderingTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>(ComposeUiTestConfig())

    private lateinit var nav: NavHostController
    private lateinit var viewport: LayoutCoordinates
    private lateinit var parent: LayoutCoordinates
    private lateinit var detail: LayoutCoordinates
    private lateinit var selectedTab: LayoutCoordinates
    private val selected = mutableIntStateOf(3)
    private val rail = mutableStateOf(false)
    private val rtl = mutableStateOf(false)
    private val dispatcher get() = rule.activity.onBackPressedDispatcher

    @Test
    fun settledReturnIsCentredAndCancelRestoresBothLayers() {
        setup()
        openDetail()
        gesture(BackEventCompat.EDGE_LEFT)
        val width = viewport.size.width.toFloat()
        for (progress in listOf(0.15f, 0.35f, 0.5f, 0.85f)) {
            progress(progress, BackEventCompat.EDGE_LEFT)
            // The parent is revealed, never moved: its centre must stay exactly put at every
            // progress value, and it must still fill the whole viewport.
            assertCentred(parent)
            assertEquals(1f, horizontalScale(parent), 0.002f)
            // The page tracks the finger: travel is linear in progress, so at 0.5 it has moved
            // exactly half its width and still covers half the screen.
            assertEquals(progress * width, detailOriginX(detail), 1.5f)
            assertEquals(
                "the page must still cover most of the screen halfway through",
                (1f - progress) * width,
                detail.size.width.toFloat(),
                2f,
            )
            // The parent's left edge is genuinely uncovered at this progress.
            val pixels = rule.onNodeWithTag("viewport").captureToImage().toPixelMap()
            val y = pixels.height / 2
            assertTrue(
                "parent should show through at x=${(progress * width).toInt()}",
                pixels[(progress * width).toInt() + 2, y].green > 0.5f,
            )
        }
        rule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        frames(600)
        rule.runOnIdle {
            assertEquals("detail", nav.currentDestination?.route)
            assertCentred(detail)
            assertEquals(0f, detailOriginX(detail), 1.5f)
            assertFalse("cancel must remove the preview parent", parent.isAttached)
        }
        // Repeated gestures use the same input owner; release must continue, not replay a pop.
        gesture(BackEventCompat.EDGE_RIGHT)
        progress(0.65f, BackEventCompat.EDGE_RIGHT)
        commitAndCheck()
    }

    @Test
    fun reversingAnUnfinishedPushDoesNotRetainItsHorizontalExit() {
        setup()
        openDetail(settleMillis = 48)
        gesture(BackEventCompat.EDGE_LEFT)
        progress(0.55f, BackEventCompat.EDGE_LEFT)
        assertCentred(parent)
        assertEquals(0.55f * viewport.size.width, detailOriginX(detail), 2f)
        commitAndCheck()
    }

    @Test
    fun startingAgainDuringCancellationDoesNotAccumulateOffsets() {
        setup()
        openDetail()
        gesture(BackEventCompat.EDGE_LEFT)
        progress(0.7f, BackEventCompat.EDGE_LEFT)
        rule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        frames(32)
        gesture(BackEventCompat.EDGE_RIGHT)
        progress(0.6f, BackEventCompat.EDGE_RIGHT)
        assertCentred(parent)
        assertEquals(0.6f * viewport.size.width, detailOriginX(detail), 2f)
        commitAndCheck()
    }

    @Test
    fun detailPreviewDoesNotReplayAnInterruptedTabAnimation() {
        selected.intValue = 0
        setup()
        rule.runOnUiThread { selected.intValue = 3 }
        frames(64)
        openDetail()
        gesture(BackEventCompat.EDGE_LEFT)
        progress(0.65f, BackEventCompat.EDGE_LEFT)
        rule.runOnIdle {
            // Bottom chrome only affects Y. The selected tab must be centred in X, not still
            // travelling from another tab or showing only its right side at the pager clip edge.
            assertEquals(centre(viewport).x, centre(selectedTab).x, 1f)
        }
        commitAndCheck()
    }

    @Test
    fun railPlacementDoesNotResizeOrTranslateTheNavHostOnCommit() {
        rail.value = true
        setup()
        openDetail()
        gesture(BackEventCompat.EDGE_LEFT)
        progress(0.7f, BackEventCompat.EDGE_LEFT)
        assertCentred(parent)
        assertEquals(0.7f * viewport.size.width, detailOriginX(detail), 2f)
        commitAndCheck()
    }

    @Test
    fun rtlUsesTheSameCentredReturnAndStationaryViewport() {
        rtl.value = true
        rail.value = true
        setup()
        openDetail()
        gesture(BackEventCompat.EDGE_RIGHT)
        progress(0.7f, BackEventCompat.EDGE_RIGHT)
        // A right-edge swipe in RTL still leaves to the physical right: the page's origin moves
        // towards +x, and the parent is still centred underneath it.
        assertCentred(parent)
        assertEquals(0.7f * viewport.size.width, detailOriginX(detail), 2f)
        commitAndCheck()
    }

    @Test
    fun theLeavingPageIsOnlyRoundedWhileTheGestureRuns() {
        setup()
        openDetail()
        val radius = 28.dp.value * rule.activity.resources.displayMetrics.density
        // At rest the page is a plain rectangle: no corner is cut, and the pixel at the very
        // top-left corner belongs to the page.
        assertCornerPixel(0, 0, isPage = true, radius = radius)
        gesture(BackEventCompat.EDGE_LEFT)
        for (progress in listOf(0.25f, 0.5f, 0.75f)) {
            progress(progress, BackEventCompat.EDGE_LEFT)
            val pixels = rule.onNodeWithTag("viewport").captureToImage().toPixelMap()
            val inset = (radius * progress).toInt()
            // Just inside the leading corners the page is gone; the parent shows through.
            assertTrue(
                "corner must be cut at progress $progress",
                pixels[inset, inset].green > 0.5f,
            )
            // Well inside the page it is still red, so the surface is only clipped, not tinted.
            assertTrue(
                "page interior must stay opaque at progress $progress",
                pixels[(inset + radius).toInt(), pixels.height / 2].red > 0.9f,
            )
        }
        // Releasing restores the rectangular page rather than leaving a rounded shell behind.
        rule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        frames(600)
        rule.runOnIdle { assertFalse(parent.isAttached) }
        assertCornerPixel(0, 0, isPage = true, radius = radius)
    }

    private fun assertCornerPixel(x: Int, y: Int, isPage: Boolean, radius: Float) {
        val pixels = rule.onNodeWithTag("viewport").captureToImage().toPixelMap()
        val pixel = pixels[x, y]
        val isPagePixel = pixel.red > 0.9f
        assertEquals(
            "corner pixel ($x,$y) should be ${if (isPage) "page" else "parent"}",
            isPage,
            isPagePixel,
        )
    }

    private fun setup() {
        rule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                MaterialTheme {
                    nav = rememberNavController()
                    val entry by nav.currentBackStackEntryAsState()
                    val active = (entry?.destination?.route ?: "root") == "root"
                    NavigationChromeLayout(
                        useRail = rail.value,
                        showNavigation = active,
                        modifier = Modifier.fillMaxSize().background(Color.Blue).testTag("viewport")
                            .onGloballyPositioned { viewport = it },
                        navigation = {
                            Box(
                                (if (rail.value) Modifier.width(80.dp).fillMaxHeight()
                                else Modifier.fillMaxWidth().height(80.dp)).background(Color.Cyan),
                            )
                        },
                    ) { chromePadding ->
                        AppNavHost(nav, "root", setOf("root"), predictiveBack = true) {
                            composable("root") {
                                Box(Modifier.fillMaxSize().background(Color.Green)
                                    .onGloballyPositioned { parent = it }) {
                                    PrimaryPageStrip(
                                        pageCount = 4,
                                        selectedPage = selected.intValue,
                                        isActive = active,
                                        onSelectPage = { selected.intValue = it },
                                        modifier = Modifier.padding(chromePadding),
                                    ) { index ->
                                        Box(Modifier.fillMaxSize()
                                            .background(if (index == selected.intValue) Color.Green else Color.Magenta)
                                            .onGloballyPositioned { if (index == selected.intValue) selectedTab = it })
                                    }
                                }
                            }
                            composable("detail") {
                                DetailDismissSurface(isDismissible = !active) {
                                    Box(Modifier.fillMaxSize().background(Color.Red)
                                        .onGloballyPositioned { detail = it })
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    private fun openDetail(settleMillis: Long = 800) {
        rule.runOnUiThread { nav.navigate("detail") }
        frames(settleMillis)
    }

    private fun gesture(edge: Int) {
        rule.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, edge)) }
        frames(48)
    }

    private fun progress(progress: Float, edge: Int) {
        rule.runOnUiThread {
            dispatcher.dispatchOnBackProgressed(BackEventCompat(150f, 400f, progress, edge))
        }
        frames(48)
    }

    private fun commitAndCheck() {
        val originalSize = viewport.size
        val originalCentre = centre(viewport)
        rule.runOnUiThread { dispatcher.onBackPressed() }
        repeat(25) {
            frames(16)
            rule.runOnIdle {
                assertEquals(originalSize, viewport.size)
                assertEquals(originalCentre, centre(viewport))
                assertCentred(parent)
            }
        }
        rule.runOnIdle {
            assertEquals("root", nav.currentDestination?.route)
            assertCentred(parent)
            assertEquals(1f, horizontalScale(parent), 0.002f)
            assertFalse("commit must remove the outgoing detail", detail.isAttached)
        }
    }

    private fun frames(millis: Long) {
        rule.mainClock.advanceTimeBy(millis)
        rule.waitForIdle()
    }

    private fun centre(coordinates: LayoutCoordinates): Offset = coordinates.localToRoot(
        Offset(coordinates.size.width / 2f, coordinates.size.height / 2f),
    )

    /** Left edge of the page in viewport coordinates: how far it has travelled to the right. */
    private fun detailOriginX(coordinates: LayoutCoordinates): Float =
        coordinates.localToRoot(Offset.Zero).x

    private fun horizontalScale(coordinates: LayoutCoordinates): Float =
        (coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), 0f)).x -
            coordinates.localToRoot(Offset.Zero).x) / coordinates.size.width

    private fun assertCentred(coordinates: LayoutCoordinates) {
        assertTrue("destination must still be attached", coordinates.isAttached)
        assertEquals("horizontal centre moved", centre(viewport).x, centre(coordinates).x, 1f)
        assertEquals("vertical centre moved", centre(viewport).y, centre(coordinates).y, 1f)
    }
}
