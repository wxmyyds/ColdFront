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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

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

internal fun navigationOffset(
    kind: NavigationMotionKind,
    entering: Boolean,
    forward: Boolean,
    width: Int,
): Int = when (kind) {
    NavigationMotionKind.TopLevel -> if (entering) {
        if (forward) width else -width
    } else {
        if (forward) -width else width
    }
    // Push keeps the established parallax: the detail enters, the parent recedes.
    NavigationMotionKind.PushDetail -> if (entering) width / 5 else -width / 5
    // Pop reveals the parent in place. A parent that drifts during the gesture would jump when
    // the predictive transition hands off to the committed pop transition, because both are
    // re-evaluated at the same fraction and must describe the same motion to swap invisibly.
    NavigationMotionKind.PopDetail -> if (entering) 0 else width / 5
}

internal fun isSecondaryDestination(route: String?, topLevelRoutes: Set<String>): Boolean =
    route != null && route !in topLevelRoutes

internal fun isTopLevelDestination(route: String?, topLevelRoutes: Set<String>): Boolean =
    route != null && route in topLevelRoutes

internal fun topLevelRouteDistance(
    initialRoute: String?,
    targetRoute: String?,
    topLevelRoutes: List<String>,
): Int {
    val initialIndex = topLevelIndex(initialRoute, topLevelRoutes)
    val targetIndex = topLevelIndex(targetRoute, topLevelRoutes)
    return if (initialIndex < 0 || targetIndex < 0) 1
    else kotlin.math.abs(targetIndex - initialIndex).coerceAtLeast(1)
}

internal fun topLevelPageIndex(route: String?, topLevelRoutes: List<String>): Int =
    topLevelIndex(route, topLevelRoutes)

internal fun isForwardTopLevelTransition(
    initialRoute: String?,
    targetRoute: String?,
    topLevelRoutes: List<String>,
): Boolean {
    val initialIndex = topLevelIndex(initialRoute, topLevelRoutes)
    val targetIndex = topLevelIndex(targetRoute, topLevelRoutes)
    return initialIndex < 0 || targetIndex < 0 || targetIndex >= initialIndex
}

private fun topLevelIndex(route: String?, routes: List<String>): Int {
    val rootRoute = when (route) {
        "about" -> "settings"
        else -> route
    }
    return rootRoute?.let(routes::indexOf) ?: -1
}

internal const val TOP_LEVEL_PAGE_DURATION_MS = 300

@Suppress("UNUSED_PARAMETER")
internal fun topLevelPageDuration(routeDistance: Int): Int = TOP_LEVEL_PAGE_DURATION_MS

internal const val DETAIL_POP_DURATION_MS = 300

/**
 * Detail pop runs on a tween rather than a spring. The predictive gesture seeks this transition
 * on every frame, and a spring overshoots and settles back, so the page drifts off the finger and
 * rebounds after release. A tween keeps the drag linear with the finger and lets the committed
 * animation finish the remaining distance on the same curve, so the hand-off stays invisible.
 */
internal fun detailPopSpatialSpec(): FiniteAnimationSpec<IntOffset> =
    tween(DETAIL_POP_DURATION_MS, easing = FastOutSlowInEasing)

/**
 * Alpha for the pop counterpart of the push fade, on a tween for the same seeking reason as
 * [detailPopSpatialSpec]: a spring would overshoot the dimming and spring back.
 */
internal fun detailPopEffectsSpec(): FiniteAnimationSpec<Float> =
    tween(DETAIL_POP_DURATION_MS, easing = FastOutSlowInEasing)

/** Matches the push fade's resting alpha so the pair reads as one reversible motion. */
internal const val DETAIL_FADE_ALPHA = 0.94f

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
        val offset: (Int) -> Int = { width ->
            navigationOffset(kind, entering = true, forward = forward, width = width)
        }
        val effects = when (kind) {
            NavigationMotionKind.PushDetail -> fadeIn(
                animationSpec = motionScheme.defaultEffectsSpec<Float>(),
                initialAlpha = DETAIL_FADE_ALPHA,
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
            NavigationMotionKind.PopDetail -> detailPopSpatialSpec()
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
        val offset: (Int) -> Int = { width ->
            navigationOffset(kind, entering = false, forward = forward, width = width)
        }
        val effects = when (kind) {
            NavigationMotionKind.TopLevel -> fadeOut(
                animationSpec = tween(topLevelPageDuration(routeDistance), easing = FastOutSlowInEasing),
                targetAlpha = 0.96f,
            )
            NavigationMotionKind.PushDetail -> ExitTransition.None
            // Mirror the push fade so the detail dims as it leaves, revealing the parent.
            NavigationMotionKind.PopDetail -> fadeOut(
                animationSpec = detailPopEffectsSpec(),
                targetAlpha = DETAIL_FADE_ALPHA,
            )
        }
        val spatialSpec = when (kind) {
            NavigationMotionKind.TopLevel ->
                tween<IntOffset>(topLevelPageDuration(routeDistance), easing = FastOutSlowInEasing)
            NavigationMotionKind.PushDetail -> motionScheme.slowSpatialSpec<IntOffset>()
            NavigationMotionKind.PopDetail -> detailPopSpatialSpec()
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
