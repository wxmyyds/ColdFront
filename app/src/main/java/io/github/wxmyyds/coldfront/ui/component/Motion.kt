package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset

internal object AppMotion {
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

/** Crossfade a changing row icon (drawable resource) without adding arbitrary rotation or scale to state semantics. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedRowIcon(
    @DrawableRes resId: Int,
    contentDescription: String? = null,
) {
    Crossfade(
        targetState = resId,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>(),
        label = "rowIconState",
    ) { id ->
        Icon(painter = painterResource(id), contentDescription = contentDescription)
    }
}
