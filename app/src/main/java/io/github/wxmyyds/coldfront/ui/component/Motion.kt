package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.scaleOut

internal enum class NavigationMotionKind {
    TopLevel,
    PushDetail,
    PopDetail,
}

internal fun navigationMotionKind(
    isPop: Boolean,
    initialIsSecondary: Boolean,
    targetIsSecondary: Boolean,
): NavigationMotionKind = when {
    isPop && initialIsSecondary && !targetIsSecondary -> NavigationMotionKind.PopDetail
    !isPop && !initialIsSecondary && targetIsSecondary -> NavigationMotionKind.PushDetail
    else -> NavigationMotionKind.TopLevel
}

internal fun shouldUsePredictivePop(
    predictiveBackEnabled: Boolean,
    initialIsSecondary: Boolean,
    targetIsTopLevel: Boolean,
): Boolean = predictiveBackEnabled && initialIsSecondary && targetIsTopLevel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal object AppMotion {
    fun pageEnter(
        kind: NavigationMotionKind,
        forward: Boolean,
        motionScheme: MotionScheme,
    ): EnterTransition {
        val offset: (Int) -> Int = when (kind) {
            NavigationMotionKind.TopLevel -> { width -> if (forward) width / 12 else -width / 12 }
            NavigationMotionKind.PushDetail -> { width -> width / 5 }
            NavigationMotionKind.PopDetail -> { width -> -width / 32 }
        }
        val effects = when (kind) {
            NavigationMotionKind.PushDetail,
            NavigationMotionKind.TopLevel -> fadeIn(
                animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                initialAlpha = 0.94f,
            )
            NavigationMotionKind.PopDetail -> EnterTransition.None
        }
        return slideInHorizontally(
            animationSpec = if (kind == NavigationMotionKind.PopDetail) {
                motionScheme.defaultSpatialSpec<IntOffset>()
            } else {
                motionScheme.slowSpatialSpec<IntOffset>()
            },
            initialOffsetX = offset,
        ) + effects
    }

    fun pageExit(
        kind: NavigationMotionKind,
        forward: Boolean,
        motionScheme: MotionScheme,
    ): ExitTransition {
        val offset: (Int) -> Int = when (kind) {
            NavigationMotionKind.TopLevel -> { width -> if (forward) -width / 12 else width / 12 }
            NavigationMotionKind.PushDetail -> { width -> -width / 32 }
            NavigationMotionKind.PopDetail -> { width -> width / 5 }
        }
        val effects = when (kind) {
            NavigationMotionKind.PopDetail -> fadeOut(
                animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                targetAlpha = 0.96f,
            ) + scaleOut(
                animationSpec = motionScheme.defaultSpatialSpec<Float>(),
                targetScale = 0.96f,
                transformOrigin = TransformOrigin(0f, 0.5f),
            )
            NavigationMotionKind.TopLevel -> fadeOut(
                animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                targetAlpha = 0.94f,
            )
            NavigationMotionKind.PushDetail -> ExitTransition.None
        }
        return slideOutHorizontally(
            animationSpec = if (kind == NavigationMotionKind.PopDetail) {
                motionScheme.defaultSpatialSpec<IntOffset>()
            } else {
                motionScheme.slowSpatialSpec<IntOffset>()
            },
            targetOffsetX = offset,
        ) + effects
    }

    fun navigationBarEnter(motionScheme: MotionScheme) =
        expandVertically(
            animationSpec = motionScheme.fastSpatialSpec<IntSize>(),
            expandFrom = Alignment.Bottom,
        ) + slideInVertically(
            animationSpec = motionScheme.fastSpatialSpec<IntOffset>(),
            initialOffsetY = { it / 3 },
        ) + fadeIn(animationSpec = motionScheme.fastEffectsSpec<Float>())

    fun navigationBarExit(motionScheme: MotionScheme) =
        shrinkVertically(
            animationSpec = motionScheme.fastSpatialSpec<IntSize>(),
            shrinkTowards = Alignment.Bottom,
        ) + slideOutVertically(
            animationSpec = motionScheme.fastSpatialSpec<IntOffset>(),
            targetOffsetY = { it / 3 },
        ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec<Float>())

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
