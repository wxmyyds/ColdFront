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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset

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

/**
 * Whether the NavHost actually draws this destination's content.
 *
 * Top-level destinations are rendered by the pager beside the NavHost; their NavHost entries are
 * empty placeholders that exist only to hold back stack state. Animating a placeholder stacks a
 * second motion on the pager's own offset, so a parent revealed by a detail pop would appear to
 * slide back in from the edge while it was already sitting underneath. Only secondary pages are
 * real NavHost content, so only they may be animated.
 *
 * This is deliberately keyed on the route rather than on [NavigationMotionKind]: a detail pop
 * resolves to [NavigationMotionKind.PopDetail] even though its parent is a top-level placeholder,
 * so a motion-kind test would let exactly the offending transition through.
 */
internal fun isRenderedByNavHost(route: String?, topLevelRoutes: Set<String>): Boolean =
    isSecondaryDestination(route, topLevelRoutes)

/**
 * Whether the primary navigation affordance belongs on screen.
 *
 * The bottom bar and the rail are primary navigation: they switch between top-level destinations.
 * A secondary page is a detail pushed on top of one of them, so leaving the affordance visible
 * both offers a way to jump away from the detail and shrinks the page for no reason. Keyed on the
 * navigation layer rather than on any route name, so a new detail page is covered automatically.
 *
 * [route] may be null while the back stack has not produced an entry yet, which happens on the very
 * first frame of a cold start: [NavController.currentBackStackEntryAsState] collects with a null
 * seed. Treating that as "not top level" would hide the bar and then reveal it a frame later, so
 * the caller substitutes the graph's start destination instead of passing null through.
 */
internal fun showsPrimaryNavigation(
    route: String?,
    topLevelRoutes: Set<String>,
): Boolean = isTopLevelDestination(route, topLevelRoutes)

/**
 * Whether the NavHost may run a transition's enter motion.
 *
 * An entering top-level destination is a placeholder being revealed by the pager's own layout, so
 * the NavHost must add nothing on top of it. Two detail pages, or a detail being pushed onto a
 * top-level page, still animate.
 */
internal fun shouldAnimateNavHostEnter(targetRoute: String?, topLevelRoutes: Set<String>): Boolean =
    isRenderedByNavHost(targetRoute, topLevelRoutes)

/**
 * Whether the NavHost may run a transition's exit motion.
 *
 * Keyed on the route that is *leaving*, not on both ends of the pair. A detail pop is
 * [about -> settings], where only [about] is real NavHost content and [settings] is a placeholder
 * the pager already has on screen. Requiring both ends would suppress the pop exit, which is the
 * only motion the user should see, and the whole return would collapse into an instant jump with
 * no predictive gesture. So the leaving page alone decides: it moves, and the placeholder it
 * uncovers stays exactly where it is.
 */
internal fun shouldAnimatePopExit(initialRoute: String?, topLevelRoutes: Set<String>): Boolean =
    isRenderedByNavHost(initialRoute, topLevelRoutes)

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

/** Resting alpha for the detail page's push fade; the pop direction deliberately does not fade. */
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
            // No fade on the way out. Dimming the moving page would make the window behind it show
            // through as a dark veil over the whole surface, which reads as a mask rather than as
            // the edge treatment the platform draws on a page being swiped away. The page must
            // stay opaque so only its shadowed edge reads.
            NavigationMotionKind.PopDetail -> ExitTransition.None
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
