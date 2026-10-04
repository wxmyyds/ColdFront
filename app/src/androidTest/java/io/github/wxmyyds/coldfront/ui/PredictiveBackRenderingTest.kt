package io.github.wxmyyds.coldfront.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
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
            assertTravel("page must track the finger at $progress", (progress * width).toInt())
            // The page must still be covering most of the screen, and the parent visible beside
            // it, so the two layers read as stacked rather than cross-faded.
            // Rendered coverage, not layout width: the slide is a graphics-layer transform, so
            // the page's layout width never changes while it visibly shrinks on screen.
            assertEquals(
                "the page must still cover most of the screen",
                (1f - progress) * width,
                renderedPageWidth().toFloat(),
                4f,
            )
            // The parent is genuinely visible to the left of the page's leading edge, which is
            // what makes the two layers read as stacked.
            val travel = renderedTravelX()
            val pixels = rule.onNodeWithTag("viewport").captureToImage().toPixelMap()
            val y = pixels.height / 2
            assertTrue(
                "parent must be uncovered left of the page edge at progress $progress",
                travel > 2 && pixels[travel - 2, y].green > 0.5f,
            )
        }
        rule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        frames(600)
        rule.runOnIdle {
            assertEquals("detail", nav.currentDestination?.route)
            assertCentred(detail)
            assertFalse("cancel must remove the preview parent", parent.isAttached)
        }
        // captureToImage reads the real surface, so it must be sampled on the UI thread rather
        // than from inside runOnIdle.
        assertTravel("cancel must restore the page to its resting position", 0)
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
        assertTravel("page must track the finger at 0.55f", (0.55f * viewport.size.width).toInt())
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
        assertTravel("page must track the finger at 0.6f", (0.6f * viewport.size.width).toInt())
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
        assertTravel("page must track the finger at 0.7f", (0.7f * viewport.size.width).toInt())
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
        // The page always leaves towards the physical right, whatever the layout direction or the
        // edge the swipe started from; the parent stays centred underneath it.
        assertCentred(parent)
        assertTravel("page must track the finger at 0.7f", (0.7f * viewport.size.width).toInt())
        commitAndCheck()
    }

    @Test
    fun theLeavingPageIsOnlyRoundedWhileTheGestureRuns() {
        setup()
        openDetail()
        val radius = 28.dp.value * rule.activity.resources.displayMetrics.density
        // At rest the page is a plain rectangle: its own top-left pixel belongs to the page.
        assertCornerPixel(2, 2, isPage = true, label = "at rest")
        gesture(BackEventCompat.EDGE_LEFT)
        for (progress in listOf(0.25f, 0.5f, 0.75f)) {
            progress(progress, BackEventCompat.EDGE_LEFT)
            val pixels = rule.onNodeWithTag("viewport").captureToImage().toPixelMap()
            val travel = renderedTravelX()
            val corner = (radius * progress).toInt()
            // Sample the very top row: a rounded corner's leftmost extent sits one full radius in
            // from the page's leading edge, whereas mid-height it sits at the edge itself. Using
            // one pixel diagonally would land deep inside the page and prove nothing.
            assertTrue(
                "corner must be cut at progress $progress (travel=$travel, r=$corner)",
                corner > 0 && pixels[travel + 1, 0].green > 0.5f,
            )
            // One radius in, along the top edge, the page is present again.
            assertTrue(
                "page must be present one radius in at progress $progress",
                pixels[(travel + corner + 2).coerceAtMost(pixels.width - 1), 0].red > 0.9f,
            )
            // Mid-height the page is not clipped at all.
            assertTrue(
                "page must be unclipped mid-height at progress $progress",
                pixels[travel, pixels.height / 2].red > 0.9f,
            )
        }
        // Releasing restores the rectangular page rather than leaving a rounded shell behind.
        rule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        frames(600)
        rule.runOnIdle { assertFalse(parent.isAttached) }
        assertCornerPixel(2, 2, isPage = true, label = "after cancel")
    }

    private fun assertCornerPixel(x: Int, y: Int, isPage: Boolean, label: String) {
        val pixels = rule.onNodeWithTag("viewport").captureToImage().toPixelMap()
        val pixel = pixels[x, y]
        assertEquals(
            "corner pixel ($x,$y) should be ${if (isPage) "page" else "parent"} $label",
            isPage,
            pixel.red > 0.9f,
        )
    }

    @Test
    fun theBarBelongsToTheTopLevelPageAndNeverCoversADetail() {
        setup()
        assertTrue("the bar belongs to the top-level page", barIsOnScreen())
        openDetail()
        rule.runOnIdle { assertEquals("detail", nav.currentDestination?.route) }
        assertFalse("the bar must not be drawn over a detail page", barIsOnScreen())
        // The bar returns with the page it belongs to, during the gesture rather than after it.
        gesture(BackEventCompat.EDGE_LEFT)
        assertTrue("the bar must appear while the return uncovers the page", barIsOnScreen())
        commitAndCheck()
        assertTrue("the bar must be back on the top-level page", barIsOnScreen())
    }

    /**
     * Whether the bar's own colour reaches the bottom of the screen.
     *
     * Placement cannot be read from the semantics tree: [NavigationChromeLayout] subcomposes the
     * bar unconditionally and only skips placing it, so the node exists with stale bounds whether
     * or not it is drawn. Rendering is the only honest signal - a covered bar is not on screen.
     * Cyan survives both a lit bar and a dimmed one, and nothing else in this harness is cyan.
     */
    private fun barIsOnScreen(): Boolean {
        val pixels = rule.onNodeWithTag("viewport").captureToImage().toPixelMap()
        val from = (pixels.height * 3 / 4).coerceIn(0, pixels.height)
        for (y in from until pixels.height) {
            for (x in 0 until pixels.width step 8) {
                val pixel = pixels[x, y]
                // Cyan: low red, high green and blue. The bar is the only cyan thing here.
                if (pixel.red < 0.35f && pixel.green > 0.35f && pixel.blue > 0.35f) return true
            }
        }
        return false
    }

    @Test
    fun theParentDimsUnderneathThePageAndRestoresAsItLeaves() {
        setup()
        openDetail()
        gesture(BackEventCompat.EDGE_LEFT)
        // The parent can only be measured while the gesture has uncovered some of it: while the
        // page is still on top there is no parent on screen to sample, and reading the middle of
        // the viewport would just be reading the page itself.
        val dimmed = renderedParentBrightness()
        assertTrue(
            "a covered parent must be dimmed, not full brightness",
            dimmed < 250,
        )
        var last = dimmed
        for (progress in listOf(0.5f, 0.75f)) {
            progress(progress, BackEventCompat.EDGE_LEFT)
            val brightness = renderedParentBrightness()
            assertTrue(
                "parent must brighten as the page leaves (progress=$progress, $brightness)",
                brightness > last,
            )
            last = brightness
        }
        // The bar belongs to the top-level page, so it must already be on screen *during* the
        // return. This is the regression: keying visibility off the current route alone hides the
        // bar for the whole gesture, because the route only becomes top-level once the pop commits,
        // so the bar appeared only after the gesture had already finished.
        //
        // Asserted on placement rather than on pixels: the bar's rendered colours are produced by a
        // scrim drawn over it, so they say nothing reliable about whether it is present, and a
        // pixel hunt here had already been wrong several times.
        gesture(BackEventCompat.EDGE_LEFT)
        assertTrue(
            "the bar must be on screen while the return uncovers the top-level page",
            barIsOnScreen(),
        )
        // It must still read as a dimmed bar, not as a solid black strip. A scrim drawn at the
        // wrong size turns the whole strip flat black, which is what an overlay sized to the parent
        // instead of to the bar produced.
        progress(0.5f, BackEventCompat.EDGE_LEFT)
        val barStrip = rule.onNodeWithTag("chrome").captureToImage().toPixelMap()
        val row = barStrip.height / 2
        val sample = barStrip[barStrip.width / 2, row]
        assertTrue(
            "the bar must be dimmed, not flat black (r=${sample.red} g=${sample.green} b=${sample.blue})",
            sample.red + sample.green + sample.blue > 0.3f,
        )
        commitAndCheck()
    }

    /**
     * Brightness of the uncovered strip of parent, from rendered pixels.
     *
     * Sampled halfway across the region the page has vacated, which is the only place the parent is
     * visible during the gesture. The parent is `Color.Green`, so its green channel is the signal:
     * averaging RGB cannot tell "dimmed green" from "nothing drawn here", which is what made an
     * earlier version of this test report a meaningless 0.
     */
    private fun renderedParentBrightness(): Int {
        val pixels = rule.onNodeWithTag("viewport").captureToImage().toPixelMap()
        val travel = renderedTravelX()
        val x = (travel / 2).coerceIn(0, pixels.width - 1)
        return (pixels[x, pixels.height / 2].green * 255f).toInt()
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
                    Box(Modifier.fillMaxSize().background(Color.Blue).testTag("viewport")
                        .onGloballyPositioned { viewport = it }) {
                        AppNavHost(nav, "root", setOf("root"), predictiveBack = true) {
                            composable("root") {
                                // Mirrors production: the bar is a *sibling of the content inside
                                // this destination*, so the detail covers the two together. That is
                                // the whole point - as a sibling of the NavHost it was drawn last
                                // and therefore on top of every detail page.
                                ParentScrimSurface(isCovered = !active) {
                                    val chrome = @Composable {
                                        Box(
                                            (if (rail.value) Modifier.width(80.dp).fillMaxHeight()
                                            else Modifier.fillMaxWidth().height(80.dp))
                                                .background(Color.Cyan).testTag("chrome"),
                                        )
                                    }
                                    if (rail.value) {
                                        Row(Modifier.fillMaxSize()) {
                                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                                RootPage(active)
                                            }
                                            chrome()
                                        }
                                    } else {
                                        Column(Modifier.fillMaxSize()) {
                                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                                RootPage(active)
                                            }
                                            chrome()
                                        }
                                    }
                                }
                            }
                            composable("detail") {
                                DetailDismissSurface(
                                    // The gesture dismisses the detail, which is on top exactly
                                    // while "root" is not the current destination; it is only
                                    // leaving once root has become current again.
                                    isDismissible = !active,
                                    isLeaving = active,
                                ) {
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

    @Composable
    private fun RootPage(isActive: Boolean) {
        Box(Modifier.fillMaxSize().background(Color.Green)
            .onGloballyPositioned { parent = it }) {
            PrimaryPageStrip(
                pageCount = 4,
                selectedPage = selected.intValue,
                isActive = isActive,
                onSelectPage = { selected.intValue = it },
            ) { index ->
                Box(Modifier.fillMaxSize()
                    .background(if (index == selected.intValue) Color.Green else Color.Magenta)
                    .onGloballyPositioned { if (index == selected.intValue) selectedTab = it })
            }
        }
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

    /** The page must have travelled exactly as far as the finger says it has. */
    private fun assertTravel(message: String, expectedPixels: Int) {
        assertEquals(
            "$message (expected ${expectedPixels}px)",
            expectedPixels.toFloat(),
            renderedTravelX().toFloat(),
            3f,
        )
    }

    /**
     * How far the leaving page has travelled, measured from the rendered pixels.
     *
     * `localToRoot` reports layout position, which deliberately excludes a graphics-layer
     * translation, so it cannot see this slide at all. The first column that is still the page's
     * own colour is the page's leading edge, which is what actually moves on screen.
     */
    private fun renderedTravelX(): Int {
        val pixels = rule.onNodeWithTag("viewport").captureToImage().toPixelMap()
        val y = pixels.height / 2
        for (x in 0 until pixels.width) {
            if (pixels[x, y].red > 0.9f) return x
        }
        return pixels.width
    }

    /** How much of the viewport the page still covers, measured from rendered pixels. */
    private fun renderedPageWidth(): Int {
        val pixels = rule.onNodeWithTag("viewport").captureToImage().toPixelMap()
        val y = pixels.height / 2
        var count = 0
        for (x in 0 until pixels.width) {
            if (pixels[x, y].red > 0.9f) count++
        }
        return count
    }

    private fun horizontalScale(coordinates: LayoutCoordinates): Float =
        (coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), 0f)).x -
            coordinates.localToRoot(Offset.Zero).x) / coordinates.size.width

    private fun assertCentred(coordinates: LayoutCoordinates) {
        assertTrue("destination must still be attached", coordinates.isAttached)
        assertEquals("horizontal centre moved", centre(viewport).x, centre(coordinates).x, 1f)
        assertEquals("vertical centre moved", centre(viewport).y, centre(coordinates).y, 1f)
    }
}
