package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset

/** A small shared vocabulary for app-owned motion; physics come from the active Material motion scheme. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal object AppMotion {
    fun pageEnter(forward: Boolean, motionScheme: MotionScheme): EnterTransition =
        slideInHorizontally(
            animationSpec = motionScheme.slowSpatialSpec<IntOffset>(),
            initialOffsetX = { if (forward) it / 8 else -it / 8 },
        ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec<Float>())

    fun pageExit(forward: Boolean, motionScheme: MotionScheme): ExitTransition =
        slideOutHorizontally(
            animationSpec = motionScheme.slowSpatialSpec<IntOffset>(),
            targetOffsetX = { if (forward) -it / 8 else it / 8 },
        ) + fadeOut(animationSpec = motionScheme.defaultEffectsSpec<Float>())

    /** Restrained spatial continuity for connection/empty-content state changes. */
    fun contentChange(motionScheme: MotionScheme): ContentTransform =
        contentChange(
            spatialSpec = motionScheme.defaultSpatialSpec<IntOffset>(),
            effectsSpec = motionScheme.defaultEffectsSpec<Float>(),
        )

    private fun contentChange(
        spatialSpec: FiniteAnimationSpec<IntOffset>,
        effectsSpec: FiniteAnimationSpec<Float>,
    ): ContentTransform =
        (fadeIn(animationSpec = effectsSpec) +
            slideInVertically(animationSpec = spatialSpec) { it / 24 }) togetherWith
            (fadeOut(animationSpec = effectsSpec) +
                slideOutVertically(animationSpec = spatialSpec) { -it / 24 })
}

/** Crossfade a changing row icon without adding arbitrary rotation or scale to state semantics. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedRowIcon(
    imageVector: ImageVector,
    contentDescription: String? = null,
) {
    Crossfade(
        targetState = imageVector,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>(),
        label = "rowIconState",
    ) { icon ->
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}
