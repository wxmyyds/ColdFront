package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
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
import kotlin.math.roundToInt

/**
 * Swipe edge constants from [androidx.navigationevent.NavigationEvent]; a value the transition
 * layer passes through untouched, so they are mirrored here rather than imported.
 */
internal const val EDGE_LEFT = 0
internal const val EDGE_RIGHT = 1
internal const val EDGE_NONE = 2

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
    NavigationMotionKind.PushDetail -> if (entering) width / 5 else -width / 5
    NavigationMotionKind.PopDetail -> if (entering) -width / 5 else width / 5
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

/**
 * AOSP cross-activity predictive back constants, for [androidx.navigation] NavHost.
 *
 * InstallerX-Revived implements this motion on Miuix Nav, which exposes the live gesture to its
 * transition scope. NavHost exposes only the swipe edge and seeks the transition state itself, so
 * the values here are the subset that survives that mapping: the scale the page settles to, how far
 * it drifts, and where the cross-fade sits. Nothing here is copied from the GPL project — the
 * numbers are the published Material cross-activity motion constants.
 */
internal object AppPredictiveBack {
    /** The outgoing page shrinks to this before it leaves. */
    internal const val MIN_SCALE = 0.9f

    /** Drift off-screen, as a fraction of page width. */
    internal const val DRIFT_FRACTION = 0.22f

    /** Duration for the settle once the gesture is released. */
    internal const val CLASSIC_FADE_DURATION_MS = 300

    /** The outgoing page keeps most of its opacity until late, then drops away quickly. */
    internal const val OUTGOING_FADE_END = 0.4f

    /** The incoming page starts dim and resolves to opaque; avoids a flat cross-fade at rest. */
    internal const val INCOMING_FADE_START = 0.72f

    /**
     * Direction the outgoing page travels for a given swipe edge, in Compose's left-to-right
     * coordinate space: a gesture from the right edge pushes the page left, and vice versa.
     */
    internal fun outgoingDirection(swipeEdge: Int): Float = when (swipeEdge) {
        EDGE_RIGHT -> -1f
        EDGE_LEFT, EDGE_NONE -> 1f
        else -> 1f
    }
}

internal const val TOP_LEVEL_PAGE_DURATION_MS = 300

@Suppress("UNUSED_PARAMETER")
internal fun topLevelPageDuration(routeDistance: Int): Int = TOP_LEVEL_PAGE_DURATION_MS

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
        val offset: (Int) -> Int = { width ->
            navigationOffset(kind, entering = false, forward = forward, width = width)
        }
        val effects = when (kind) {
            NavigationMotionKind.PopDetail -> ExitTransition.None
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

    /**
     * AOSP-style predictive pop.
     *
     * InstallerX builds this on Miuix Nav, which hands its transition scope a live [NavGesture]
     * (progress, touch, release velocity) every frame. NavHost instead drives progress by seeking
     * the transition state and exposes only [swipeEdge], so the shape below is expressed through
     * the specs Compose interpolates, not through a per-frame callback:
     *
     *  - the outgoing page shrinks to [MIN_SCALE] while sliding toward the gesture edge; both are
     *    standard Enter/ExitTransition effects that NavHost seeks with the finger.
     *  - the incoming page stays hidden until the gesture is underway (see [incomingAlpha]) and is
     *    applied as an initial alpha so there is no cross-fade at rest.
     *  - the damped-oscillator settle bounce needs the release velocity, and the vertical drift
     *    needs touch Y. Neither is available here, so both are dropped rather than faked from
     *    swipeEdge; the spring below is the closest honest substitute for the settle.
     */
    fun predictivePopExit(swipeEdge: Int): ExitTransition {
        val direction = AppPredictiveBack.outgoingDirection(swipeEdge)
        // InstallerX `CrossActivityDrift`, as a fraction of the page so the motion scales with the
        // window instead of drifting by a fixed pixel count on wide screens.
        val drift = slideOutHorizontally(
            animationSpec = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 1500f),
            targetOffsetX = { width -> (direction * AppPredictiveBack.DRIFT_FRACTION * width).roundToInt() },
        )
        // The page shrinks as it leaves. scaleOut interpolates 1 -> MIN_SCALE alongside the slide,
        // which is what gives the gesture its depth; without it both pages only translate and the
        // effect reads as flat.
        val shrink = scaleOut(
            animationSpec = spring<Float>(dampingRatio = 0.9f, stiffness = 1500f),
            targetScale = AppPredictiveBack.MIN_SCALE,
            transformOrigin = TransformOrigin(0.5f, 0.5f),
        )
        return drift + shrink + fadeOut(
            animationSpec = tween(AppPredictiveBack.CLASSIC_FADE_DURATION_MS),
            targetAlpha = AppPredictiveBack.OUTGOING_FADE_END,
        )
    }

    fun predictivePopEnter(swipeEdge: Int): EnterTransition = fadeIn(
        animationSpec = tween(AppPredictiveBack.CLASSIC_FADE_DURATION_MS),
        initialAlpha = AppPredictiveBack.INCOMING_FADE_START,
    )

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
