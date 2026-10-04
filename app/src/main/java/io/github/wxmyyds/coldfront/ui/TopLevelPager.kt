package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import io.github.wxmyyds.coldfront.data.AppSettings
import io.github.wxmyyds.coldfront.ui.component.isSecondaryDestination
import io.github.wxmyyds.coldfront.ui.component.topLevelDragReversed
import io.github.wxmyyds.coldfront.ui.component.topLevelPositionAfterDrag
import io.github.wxmyyds.coldfront.ui.component.topLevelTargetAfterDrag
import kotlin.math.roundToInt

/**
 * The four primary destinations, held side by side and moved as one strip.
 *
 * They are a NavHost destination rather than a sibling of the NavHost so that a secondary page
 * pushed on top of them has a parent that genuinely exists underneath. The strip is not rebuilt on
 * navigation: all four pages stay composed and only their offsets change, which is what lets a
 * jump across several pages pass the pages in between continuously instead of cutting from the
 * first to the last.
 */
@Composable
internal fun TopLevelPager(
    vm: CoolerViewModel,
    settings: AppSettings,
    currentRoute: String?,
    topLevelRoutes: List<String>,
    selectedPage: Int,
    onNavigate: (String) -> Unit,
    onOpenScan: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val topLevelRouteSet = remember(topLevelRoutes) { topLevelRoutes.toSet() }
    // Seeded from the selected page so a restore starts on the right page instead of animating
    // in from the first one.
    val pagePosition = remember { Animatable(selectedPage.coerceAtLeast(0).toFloat()) }
    val pageScope = rememberCoroutineScope()
    LaunchedEffect(selectedPage) {
        if (selectedPage >= 0) {
            pagePosition.animateTo(
                targetValue = selectedPage.toFloat(),
                // A fixed duration, not one per page: travelling further costs no more time, so
                // crossing two pages or three feels like the same single action.
                animationSpec = tween(TOP_LEVEL_PAGE_DURATION_MS, easing = FastOutSlowInEasing),
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val pageWidth = maxWidth
        val pageWidthPx = with(LocalDensity.current) { pageWidth.toPx() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    // A secondary page owns the horizontal gesture for its own predictive back,
                    // so the strip must not compete with it.
                    enabled = !isSecondaryDestination(currentRoute, topLevelRouteSet),
                    orientation = Orientation.Horizontal,
                    // Foundation reverses both delta and stop velocity; offset below already
                    // mirrors in RTL, so do not reverse it a second time.
                    reverseDirection = topLevelDragReversed(LocalLayoutDirection.current),
                    state = rememberDraggableState { delta ->
                        pageScope.launch {
                            pagePosition.snapTo(
                                topLevelPositionAfterDrag(
                                    pagePosition.value, delta, pageWidthPx, topLevelRoutes.lastIndex,
                                ),
                            )
                        }
                    },
                    onDragStopped = { velocity ->
                        val target = topLevelTargetAfterDrag(
                            pagePosition.value, velocity, topLevelRoutes.lastIndex,
                        )
                        val targetRoute = topLevelRoutes[target]
                        if (targetRoute != currentRoute) {
                            onNavigate(targetRoute)
                        } else {
                            // A drag that settles back on the current page still has to return the
                            // strip from wherever the finger left it.
                            pageScope.launch {
                                pagePosition.animateTo(
                                    target.toFloat(),
                                    tween(TOP_LEVEL_PAGE_DURATION_MS, easing = FastOutSlowInEasing),
                                )
                            }
                        }
                    },
                ),
        ) {
            listOf<@Composable () -> Unit>(
                { HomeScreen(vm, onAddDevice = onOpenScan) },
                { DevicesScreen(vm, onAddDevice = onOpenScan) },
                {
                    RGBControlScreen(
                        vm,
                        // The strip stays composed while a secondary page covers it; without this
                        // its infinite preview animation would keep running behind that page.
                        isPageActive = !isSecondaryDestination(currentRoute, topLevelRouteSet),
                        onConnect = onOpenScan,
                    )
                },
                { SettingsScreen(vm, settings, onAbout = onOpenAbout) },
            ).forEachIndexed { index, content ->
                Box(
                    modifier = Modifier
                        .width(pageWidth)
                        .fillMaxHeight()
                        .offset {
                            IntOffset(
                                ((index - pagePosition.value) * pageWidthPx).roundToInt(),
                                0,
                            )
                        },
                ) { content() }
            }
        }
    }
}
