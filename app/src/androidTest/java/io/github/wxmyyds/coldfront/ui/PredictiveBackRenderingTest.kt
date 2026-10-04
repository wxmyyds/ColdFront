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
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
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
        for (progress in listOf(0.15f, 0.5f, 0.85f)) {
            progress(progress, BackEventCompat.EDGE_LEFT)
            assertCentred(parent)
            assertCentred(detail)
            assertSymmetricEdges()
        }
        rule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        frames(600)
        rule.runOnIdle {
            assertEquals("detail", nav.currentDestination?.route)
            assertCentred(detail)
            assertEquals(1f, horizontalScale(detail), 0.002f)
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
        assertCentred(detail)
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
        assertCentred(detail)
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
        assertSymmetricEdges()
        commitAndCheck()
    }

    @Test
    fun railPlacementDoesNotResizeOrTranslateTheNavHostOnCommit() {
        rail.value = true
        setup()
        openDetail()
        gesture(BackEventCompat.EDGE_LEFT)
        progress(0.7f, BackEventCompat.EDGE_LEFT)
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
        assertCentred(parent)
        assertCentred(detail)
        commitAndCheck()
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
                                Box(Modifier.fillMaxSize().background(Color.Red)
                                    .onGloballyPositioned { detail = it })
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
            assertEquals(1f, horizontalScale(parent), 0.002f)
        }
    }

    private fun frames(millis: Long) {
        rule.mainClock.advanceTimeBy(millis)
        rule.waitForIdle()
    }

    private fun centre(coordinates: LayoutCoordinates): Offset = coordinates.localToRoot(
        Offset(coordinates.size.width / 2f, coordinates.size.height / 2f),
    )

    private fun horizontalScale(coordinates: LayoutCoordinates): Float =
        (coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), 0f)).x -
            coordinates.localToRoot(Offset.Zero).x) / coordinates.size.width

    private fun assertCentred(coordinates: LayoutCoordinates) {
        assertTrue("destination must still be attached", coordinates.isAttached)
        assertEquals("horizontal centre moved", centre(viewport).x, centre(coordinates).x, 1f)
        assertEquals("vertical centre moved", centre(viewport).y, centre(coordinates).y, 1f)
    }

    private fun assertSymmetricEdges() {
        // Capture the whole viewport, not the parent's cropped bounds. A unilateral uncovered
        // region, horizontal offset, or additional clip produces unequal edge colours.
        val pixels = rule.onNodeWithTag("viewport").captureToImage().toPixelMap()
        for (yFraction in listOf(0.25f, 0.5f, 0.75f)) {
            val y = (pixels.height * yFraction).toInt()
            val x = (pixels.width * 0.02f).toInt()
            val left = pixels[x, y]
            val right = pixels[pixels.width - 1 - x, y]
            assertEquals("red edge channel", left.red, right.red, 0.025f)
            assertEquals("green edge channel", left.green, right.green, 0.025f)
            assertEquals("blue edge channel", left.blue, right.blue, 0.025f)
        }
    }
}
