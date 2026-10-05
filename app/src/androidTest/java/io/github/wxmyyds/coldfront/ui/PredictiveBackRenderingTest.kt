package io.github.wxmyyds.coldfront.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import io.github.wxmyyds.coldfront.ui.component.PARENT_PARALLAX_FRACTION
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
    /** Where the top-level page area sits at rest, so the step-back is measured from it. */
    private var parentRestX: Float? = null
    private var parentRestY: Float? = null
    private val dispatcher get() = rule.activity.onBackPressedDispatcher

    @Test
    fun settledReturnIsCentredAndCancelRestoresBothLayers() {
        setup()
        openDetail()
        gesture(BackEventCompat.EDGE_LEFT)
        val width = viewport.size.width.toFloat()
        for (progress in listOf(0.15f, 0.35f, 0.5f, 0.85f)) {
            progress(progress, BackEventCompat.EDGE_LEFT)
            // The parent is revealed, stepping back a fifth of the width as the page above leaves,
            // and must never scale or move vertically.
            assertSteppedBack(progress)
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
        // Grabbed 48ms into a 300ms push, so the page is still short of centre and sits to the
        // right. The seek re-bases the interrupted transition to describe the whole route from the
        // previous entry, so the page's mid-gesture path is NavHost's to decide - what we own is
        // the *endpoint*: the page must have reached its full exit once the finger has gone all
        // the way, and the committed return must restore root exactly.
        openDetail(settleMillis = 48)
        gesture(BackEventCompat.EDGE_LEFT)
        val start = renderedTravelX()
        assertTrue("an interrupted push should leave the page off centre", start > 0)
        // Let the gesture run to completion and confirm the page exits fully rather than stalling
        // part-way because it was interrupted.
        progress(0.8f, BackEventCompat.EDGE_LEFT)
        val nearEnd = renderedTravelX()
        assertTrue(
            "the page must have progressed toward its exit ($start -> $nearEnd)",
            nearEnd > start + viewport.size.width / 2,
        )
        commitAndCheck()
        assertStationary(parent)
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
        assertSteppedBack(0.6f)
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
            // Bottom chrome only affects Y. The selected tab must be centred within the *parent*,
            // not within the viewport: the parent is stepped back by a fifth of the width while the
            // detail above it is leaving, and the tab moves with its page. What must not happen is
            // the tab still travelling from another pager page, or sitting half-clipped at the
            // pager's edge - both of which shift it within the parent itself.
            assertEquals(centre(parent).x, centre(selectedTab).x, 1f)
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
        assertSteppedBack(0.7f)
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
        // edge the swipe started from; the parent steps back the same amount underneath it.
        assertSteppedBack(0.7f)
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
        // The regression: as a sibling of the NavHost the bar was drawn after every destination, so
        // it sat on top of the detail page instead of under it. Being inside the top-level page now
        // means the detail covers it exactly as it covers the rest of that page.
        openDetail()
        rule.runOnIdle { assertEquals("detail", nav.currentDestination?.route) }
        assertFalse("the bar must not be drawn over a detail page", barIsOnScreen())
        // Returning reveals the bar together with the page it belongs to, rather than after the
        // gesture. Mid-gesture it is only partly uncovered, so what is asserted is that the
        // revealed strip has grown - not that the whole bar is already visible.
        gesture(BackEventCompat.EDGE_LEFT)
        progress(0.5f, BackEventCompat.EDGE_LEFT)
        val half = revealedBarWidth()
        progress(0.85f, BackEventCompat.EDGE_LEFT)
        val most = revealedBarWidth()
        assertTrue(
            "the bar must be uncovered with the page (half=$half, most=$most)",
            most > half,
        )
        commitAndCheck()
        assertTrue("the bar must be back on the top-level page", barIsOnScreen())
    }

    /** How much of the bar's colour is on screen, used to check it is uncovered progressively. */
    private fun revealedBarWidth(): Int {
        val pixels = rule.onNodeWithTag("viewport").captureToImage().toPixelMap()
        // Sampled inside the bar's own vertical span. A row near the very bottom of the window can
        // fall under the leaving page still covering it, which reads as "nothing revealed" and made
        // this assertion fail for the wrong reason.
        val row = (pixels.height * 91 / 100).coerceIn(0, pixels.height - 1)
        var count = 0
        for (x in 0 until pixels.width) {
            val pixel = pixels[x, row]
            if (pixel.red < 0.35f && pixel.green > 0.35f && pixel.blue > 0.35f) count++
        }
        return count
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
        // Recorded while nothing is moving, so the step-back is measured from the page's true resting
        // position. Capturing it at first assertion instead would bake in whatever shift had already
        // happened and then assert nothing.
        parentRestX = centre(parent).x
        parentRestY = centre(parent).y
        rule.mainClock.autoAdvance = false
    }

    @Composable
    private fun RootPage(isActive: Boolean) {
        // `parent` is the page area only. That is deliberate: it fills the space the bar leaves, so
        // its centre is the centre of the *content*, not of the destination. That is why the
        // assertions below compare it against where it was first laid out instead of against the
        // viewport centre, which would just be re-asserting the absence of the bar.
        Box(Modifier.fillMaxSize().onGloballyPositioned { parent = it }) {
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
        // Per-frame: the NavHost must not resize or shift the viewport while the pop settles.
        // The parent is deliberately *not* asserted here: it animates back to rest over
        // PARENT_STEP_BACK_MS after the finger lifts, so the first frames legitimately show it
        // still mid-return. Its final resting place is asserted once the settle has elapsed below.
        repeat(25) {
            frames(16)
            rule.runOnIdle {
                assertEquals(originalSize, viewport.size)
                assertEquals(originalCentre, centre(viewport))
            }
        }
        rule.runOnIdle {
            assertEquals("root", nav.currentDestination?.route)
            assertStationary(parent)
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

    /**
     * The top-level page is exactly a fifth of the viewport further left than when it was uncovered.
     *
     * It used to be required not to move at all, but a page covered by a detail now steps back so the
     * two stay stacked. Asserted against the measured formula rather than a recorded baseline: the
     * shift is a deliberate part of the motion, and a baseline captured at an arbitrary progress
     * would only assert that the shift does not change between two arbitrary moments.
     */
    private fun assertSteppedBack(progress: Float) {
        assertTrue("destination must still be attached", parent.isAttached)
        // Likewise horizontal: with a rail the page area is laid out beside it, so the page's own
        // rest centre is not the window's. The shift measured from there, not from the centre.
        val restCentre = parentRestX ?: centre(parent).x
        val expected = restCentre - PARENT_PARALLAX_FRACTION * viewport.size.width * (1f - progress)
        assertEquals("parent must step back a fifth of the width", expected, centre(parent).x, 1.5f)
        // Vertical is compared against where the page content actually sits, not the window centre:
        // the page fills the space the bottom bar leaves, so its vertical centre is legitimately
        // above the window's. Only a *change* in it would be a bug.
        assertEquals(
            "the parent must not move vertically",
            parentRestY ?: centre(parent).y,
            centre(parent).y,
            1f,
        )
        assertEquals("the parent must not scale", 1f, horizontalScale(parent), 0.002f)
    }

    /**
     * The top-level page is exactly back where it was laid out, as after a return completes.
     *
     * Compared against the position recorded in [setup] rather than the window centre: the page is
     * laid out beside the bar or rail and inside the viewport's width cap, so its own resting centre
     * is legitimately not the window's.
     */
    private fun assertStationary(coordinates: LayoutCoordinates) {
        assertTrue("destination must still be attached", coordinates.isAttached)
        assertEquals("horizontal centre moved", requireNotNull(parentRestX), centre(coordinates).x, 1.5f)
        assertEquals("vertical centre moved", requireNotNull(parentRestY), centre(coordinates).y, 1f)
    }

    /** A destination that must be centred in the viewport, such as a detail page. */
    private fun assertCentred(coordinates: LayoutCoordinates) {
        assertTrue("destination must still be attached", coordinates.isAttached)
        assertEquals("horizontal centre moved", centre(viewport).x, centre(coordinates).x, 1f)
        assertEquals("vertical centre moved", centre(viewport).y, centre(coordinates).y, 1f)
    }
}
