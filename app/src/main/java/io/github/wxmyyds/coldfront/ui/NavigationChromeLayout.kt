package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout

private enum class NavigationSlot { Chrome, Content }

/**
 * Navigation chrome is a sibling of the full-window transition viewport, not its size owner.
 *
 * Measure the bar/rail even while a detail covers MAIN, so MAIN's own content padding never changes
 * during preview, commit or cancellation. Hidden chrome is not placed (no drawing, hit targets or
 * accessibility nodes), rather than alpha-animated. Only MAIN consumes [PaddingValues]; detail
 * destinations and the NavHost always keep the same bounds and transform centre.
 */
@Composable
internal fun NavigationChromeLayout(
    useRail: Boolean,
    showNavigation: Boolean,
    modifier: Modifier = Modifier,
    navigation: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val chrome = subcompose(NavigationSlot.Chrome, navigation).map {
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
