package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import io.github.wxmyyds.coldfront.ui.component.PARENT_SCRIM_DURATION_MS
import io.github.wxmyyds.coldfront.ui.component.parentScrimAlphaForProgress

/**
 * Dims a top-level page while a detail page is on top of it, so that it reads as background.
 *
 * Two effects have to stay in step with each other: the scrim appearing as a detail page is pushed
 * over the top, and the parent brightening again as the back gesture uncovers it. Neither is left
 * to a timer here - both are driven by the gesture's own progress, so one finger moves the page
 * and its backdrop together.
 *
 * The scrim is drawn over the page rather than fading the page's own pixels. Fading would let the
 * window background show through, which reads lighter, whereas the brief is for the parent to get
 * *darker*, and a themed scrim colour does that correctly in both light and dark modes.
 */
@Composable
internal fun ParentScrimSurface(
    isCovered: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = rememberBackGestureProgress(isDismissible = false)
    val target = if (isCovered) parentScrimAlphaForProgress(progress.value) else 0f
    val alpha by animateFloatAsState(
        targetValue = target,
        animationSpec = if (isCovered) {
            // While a gesture is running, the value is already correct every frame, so it must be
            // taken as-is: any easing here would be applied on top of the finger's own position.
            snap()
        } else {
            tween(PARENT_SCRIM_DURATION_MS)
        },
        label = "parentScrimAlpha",
    )
    Box(modifier.fillMaxSize()) {
        content()
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .background(MaterialTheme.colorScheme.scrimContainer),
        )
    }
}
