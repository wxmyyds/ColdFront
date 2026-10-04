package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.SubcomposeLayout
import io.github.wxmyyds.coldfront.ui.component.parentScrimAlphaForProgress
import io.github.wxmyyds.coldfront.ui.component.shouldUsePredictivePop

private enum class NavigationSlot { Chrome, Content }

/**
 * Whether the bar or rail belongs on screen right now.
 *
 * A back gesture only counts when it is *popping a detail off a top-level page*, which is exactly
 * what [shouldUsePredictivePop] decides from the navigation layer.
 *
 * The narrower test matters, and an earlier version got this wrong. "Any back gesture is running" is
 * not equivalent: the platform reports the finger-down that precedes a push identically, so that
 * test placed the bar for the instant the user touched the screen to open a detail. Because the
 * chrome slot is drawn after the NavHost and therefore sits on top, the bar flashed over the
 * incoming page. Keying on the gesture's *destination* rather than on the gesture itself is what
 * keeps the bar on the page it actually belongs to.
 *
 * @param revealByGesture whether a gesture is currently uncovering a top-level page, i.e. the bar
 * should show before the route catches up.
 */
@Composable
internal fun rememberChromeVisibility(
    isTopLevelCurrent: Boolean,
    revealByGesture: Boolean,
): State<Boolean> = rememberUpdatedState(isTopLevelCurrent || revealByGesture)

/**
 * Draws a scrim over the bar or rail, matching the dimming of the page it belongs to.
 *
 * The chrome is a sibling of the NavHost, so it cannot inherit the top-level page's scrim and has to
 * be dimmed separately. Two things this deliberately avoids:
 *
 *  - Fading the bar. Lowering its alpha makes it translucent, so the page shows through and it stops
 *    reading as a solid surface - at half progress it all but disappears into the page behind it.
 *  - An overlaid child sized to the parent. A `matchParentSize` sibling is measured against the
 *    window's constraints, so it can end up covering far more than the bar and paint the whole strip
 *    flat black. Drawing inside this node's own bounds cannot exceed the bar.
 *
 * [drawWithContent] rather than a `graphicsLayer` alpha: the scrim must be drawn *over* the content,
 * not applied to it. Reading the progress inside the draw block keeps a drag off the recomposition
 * path entirely.
 */
@Composable
internal fun Modifier.chromeScrim(backProgress: State<Float?>): Modifier {
    val scrim = MaterialTheme.colorScheme.scrim
    return this.drawWithContent {
        drawContent()
        val progress = backProgress.value
        if (progress != null) {
            drawRect(color = scrim, alpha = parentScrimAlphaForProgress(progress))
        }
    }
}

/**
 * Navigation chrome is a sibling of the full-window transition viewport, not its size owner.
 *
 * Measure the bar/rail even while a detail covers MAIN, so MAIN's own content padding never changes
 * during preview, commit or cancellation. Hidden chrome is not placed (no drawing, hit targets or
 * accessibility nodes), rather than alpha-animated - except while a back gesture is uncovering the
 * top-level page, where it is placed and dimmed to match that page, since it is then genuinely part
 * of what is being revealed. Only MAIN consumes [PaddingValues]; detail destinations and the NavHost
 * always keep the same bounds and transform centre.
 *
 * @param scrim drawn over the bar or rail within its own bounds. The chrome is a sibling of
 * the NavHost, so it cannot inherit the top-level page's scrim and has to be dimmed separately.
 */
@Composable
internal fun NavigationChromeLayout(
    useRail: Boolean,
    showNavigation: Boolean,
    modifier: Modifier = Modifier,
    scrim: Modifier = Modifier,
    navigation: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        // The Box wraps the bar without adding a child of its own: it only carries the scrim, and
        // wrapping content keeps the bar's measurement exactly as it was.
        val chrome = subcompose(NavigationSlot.Chrome) {
            Box(scrim) { navigation() }
        }.map {
            it.measure(
                constraints.copy(
                    minWidth = if (useRail) 0 else constraints.maxWidth,
                    minHeight = if (useRail) constraints.maxHeight else 0,
                ),
            )
        }
        val chromeWidth = chrome.maxOfOrNull { it.width } ?: 0
        val chromeHeight = chrome.maxOfOrNull { it.height } ?: 0
        val padding = if (useRail) {
            PaddingValues(start = chromeWidth.toDp())
        } else {
            PaddingValues(bottom = chromeHeight.toDp())
        }
        val pages = subcompose(NavigationSlot.Content) { content(padding) }
            .map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            pages.forEach { it.placeRelative(0, 0) }
            if (showNavigation) {
                chrome.forEach {
                    it.placeRelative(0, if (useRail) 0 else constraints.maxHeight - it.height)
                }
            }
        }
    }
}
