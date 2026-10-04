package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import io.github.wxmyyds.coldfront.ui.component.parentScrimAlphaForProgress

private enum class NavigationSlot { Chrome, Content }

/**
 * Whether the bar or rail belongs on screen right now, given which layer is current.
 *
 * A back gesture in progress counts as "the top-level page is current", because that is where the
 * gesture is going: the destination does not change until the pop commits, so keying visibility off
 * the route alone would hide the bar for the whole drag and pop it in afterwards, instead of
 * revealing it underneath the leaving page as the page slides away.
 *
 * Kept as a derived state so reading it does not recompose the caller on every frame of the drag -
 * this boolean only flips twice per gesture.
 */
@Composable
internal fun rememberChromeVisibility(
    isTopLevelCurrent: Boolean,
    backProgress: State<Float?>,
): State<Boolean> = remember(backProgress) {
    derivedStateOf { isTopLevelCurrent || backProgress.value != null }
}

/**
 * Dims the bar or rail in step with the page it belongs to.
 *
 * The bar is a sibling of the NavHost rather than a child of the top-level page, so it does not
 * inherit that page's scrim. Without this it would appear at full brightness under a dimmed parent,
 * and being drawn last it would sit on top of the leaving page's own edge. Reading the progress
 * inside the layer block keeps this off the recomposition path entirely.
 */
internal fun Modifier.chromeDimming(backProgress: State<Float?>): Modifier =
    graphicsLayer {
        val progress = backProgress.value
        alpha = if (progress == null) 1f else parentScrimAlphaForProgress(progress)
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
 * @param chromeDimming applied to the bar or rail itself. The chrome is a sibling of the NavHost, so
 * it cannot inherit the top-level page's scrim and has to be dimmed separately to match.
 */
@Composable
internal fun NavigationChromeLayout(
    useRail: Boolean,
    showNavigation: Boolean,
    modifier: Modifier = Modifier,
    chromeDimming: Modifier = Modifier,
    navigation: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        // Box wraps its content, so the bar keeps the size its own constraints give it here; the
        // wrapper only exists to carry the dimming layer.
        val chrome = subcompose(NavigationSlot.Chrome) {
            Box(Modifier.then(chromeDimming)) { navigation() }
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
