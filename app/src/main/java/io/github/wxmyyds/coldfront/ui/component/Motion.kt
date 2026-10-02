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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
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

internal fun isSecondaryDestination(route: String?, topLevelRoutes: Set<String>): Boolean =
    route != null && route !in topLevelRoutes

internal fun isTopLevelDestination(route: String?, topLevelRoutes: Set<String>): Boolean =
    route != null && route in topLevelRoutes

private fun topLevelRouteIndex(route: String?, routes: List<String>): Int =
    route?.let { routes.indexOf(it) }?.coerceAtLeast(0) ?: 0

internal fun topLevelRouteDistance(
    initialRoute: String?,
    targetRoute: String?,
    topLevelRoutes: List<String>,
): Int {
    val initialIndex = topLevelRouteIndex(initialRoute, topLevelRoutes)
    val targetIndex = topLevelRouteIndex(targetRoute, topLevelRoutes)
    return kotlin.math.abs(targetIndex - initialIndex).coerceAtLeast(1)
}

internal fun isForwardTopLevelTransition(
    initialRoute: String?,
    targetRoute: String?,
    topLevelRoutes: List<String>,
): Boolean {
    val initialIndex = topLevelRouteIndex(initialRoute, topLevelRoutes)
    val targetIndex = topLevelRouteIndex(targetRoute, topLevelRoutes)
    return targetIndex >= initialIndex
}

internal fun topLevelPageDuration(routeDistance: Int): Int =
    100 * (routeDistance.coerceIn(1, 4) + 1)

internal fun shouldUsePredictivePop(
    predictiveBackEnabled: Boolean,
    currentRoute: String?,
    previousRoute: String?,
    topLevelRoutes: Set<String>,
): Boolean = predictiveBackEnabled &&
    isSecondaryDestination(currentRoute, topLevelRoutes) &&
    isTopLevelDestination(previousRoute, topLevelRoutes)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal object AppMotion {
    fun pageEnter(
        kind: NavigationMotionKind,
        forward: Boolean,
        motionScheme: MotionScheme,
        routeDistance: Int,
    ): EnterTransition {
        val offset: (Int) -> Int = when (kind) {
            NavigationMotionKind.TopLevel -> { width -> if (forward) width else -width }
            NavigationMotionKind.PushDetail -> { width -> width / 5 }
            NavigationMotionKind.PopDetail -> { width -> -width / 32 }
        }
        val effects = when (kind) {
            NavigationMotionKind.PushDetail -> fadeIn(
                animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                initialAlpha = 0.94f,
            )
            NavigationMotionKind.TopLevel -> fadeIn(
                animationSpec = tween(topLevelPageDuration(routeDistance), easing = FastOutSlowInEasing),
                initialAlpha = 0.96f,
            )
            NavigationMotionKind.PopDetail -> EnterTransition.None
        }
        val spatialSpec = when (kind) {
            NavigationMotionKind.TopLevel ->
                tween<IntOffset>(topLevelPageDuration(routeDistance), easing = FastOutSlowInEasing)
            NavigationMotionKind.PushDetail -> motionScheme.slowSpatialSpec<IntOffset>()
            NavigationMotionKind.PopDetail -> motionScheme.defaultSpatialSpec<IntOffset>()
        }
        return slideInHorizontally(
            animationSpec = spatialSpec,
            initialOffsetX = offset,
        ) + effects
    }

    fun pageExit(
        kind: NavigationMotionKind,
        forward: Boolean,
        motionScheme: MotionScheme,
        routeDistance: Int,
    ): ExitTransition {
        val offset: (Int) -> Int = when (kind) {
            NavigationMotionKind.TopLevel -> { width -> if (forward) -width else width }
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
                animationSpec = tween(topLevelPageDuration(routeDistance), easing = FastOutSlowInEasing),
                targetAlpha = 0.96f,
            )
            NavigationMotionKind.PushDetail -> ExitTransition.None
        }
        val spatialSpec = when (kind) {
            NavigationMotionKind.TopLevel ->
                tween<IntOffset>(topLevelPageDuration(routeDistance), easing = FastOutSlowInEasing)
            NavigationMotionKind.PushDetail -> motionScheme.slowSpatialSpec<IntOffset>()
            NavigationMotionKind.PopDetail -> motionScheme.defaultSpatialSpec<IntOffset>()
        }
        return slideOutHorizontally(
            animationSpec = spatialSpec,
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
