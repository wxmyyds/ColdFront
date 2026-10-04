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
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
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
 * The system curve for predictive back, PathInterpolator(.1, .1, 0, 1).
 *
 * The official guidance is to feed the raw gesture progress through this rather than using it
 * directly, so the response is most visible at the start of the drag and eases off afterwards.
 */
internal val PREDICTIVE_BACK_EASING = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/** Progress at which the leaving page is fully faded out and the parent starts fading in. */
internal const val PREDICTIVE_BACK_CROSSFADE_AT = 0.35f

/** Exit scale of a full-screen surface over a predictive back, per the official motion spec. */
internal const val PREDICTIVE_BACK_EXIT_SCALE = 0.9f

/** The parent starts larger and settles to full size, so the surface appears to settle into place. */
internal const val PREDICTIVE_BACK_ENTER_START_SCALE = 1.1f

/**
 * Leaving page: shrink from full size to [PREDICTIVE_BACK_EXIT_SCALE] while fading out.
 *
 * The fade is held at full opacity until [PREDICTIVE_BACK_CROSSFADE_AT] and completed by 100%, so at
 * that instant neither page is visible, which is what the spec describes. Scale and alpha both use
 * [PREDICTIVE_BACK_EASING], and both are expressed as seekable specs so the gesture can drive them
 * frame by frame.
 */
internal fun predictiveBackExitSpec(): FiniteAnimationSpec<Float> = keyframes {
    durationMillis = DETAIL_POP_DURATION_MS
    PREDICTIVE_BACK_CROSSFADE_AT at 1f
    1f at PREDICTIVE_BACK_EXIT_SCALE
}

/** Parent page: settle from [PREDICTIVE_BACK_ENTER_START_SCALE] down to full size, fading in. */
internal fun predictiveBackEnterSpec(): FiniteAnimationSpec<Float> = keyframes {
    durationMillis = DETAIL_POP_DURATION_MS
    1f at PREDICTIVE_BACK_ENTER_START_SCALE
    PREDICTIVE_BACK_CROSSFADE_AT at PREDICTIVE_BACK_EXIT_SCALE
}

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
            // PopDetail never slides; it scales and fades via predictiveBack* instead.
            NavigationMotionKind.PopDetail -> motionScheme.defaultSpatialSpec<IntOffset>()
        }
        return if (kind == NavigationMotionKind.PopDetail) {
            predictiveBackEnter(motionScheme)
        } else {
            slideInHorizontally(
                animationSpec = spatialSpec,
                initialOffsetX = offset,
            ) + effects
        }
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
            // PopDetail never slides; it scales and fades via predictiveBack* instead.
            NavigationMotionKind.PopDetail -> motionScheme.defaultSpatialSpec<IntOffset>()
        }
        return if (kind == NavigationMotionKind.PopDetail) {
            predictiveBackExit(motionScheme)
        } else {
            slideOutHorizontally(
                animationSpec = spatialSpec,
                targetOffsetX = offset,
            ) + effects
        }
    }

    /**
     * Gesture-driven exit for a detail page, following the official full-screen surface spec.
     *
     * Deliberately a scale and a fade with no horizontal offset: [scaleOut] keeps the page centred,
     * so the page that is being revealed never slides or gets clipped. Adding a slide here is what
     * made the parent appear to drift and left an edge of the moving layer exposed at the clip
     * boundary.
     */
    fun predictiveBackExit(motionScheme: MotionScheme): ExitTransition =
        scaleOut(
            animationSpec = predictiveBackExitSpec(),
            targetScale = PREDICTIVE_BACK_EXIT_SCALE,
        ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec<Float>())

    /**
     * Gesture-driven enter for the revealed parent, as the counterpart to [predictiveBackExit].
     *
     * The parent starts slightly oversized and settles to full size while fading in, so it reads as
     * uncovered rather than as a new page arriving. It is also centred, so nothing shifts.
     */
    fun predictiveBackEnter(motionScheme: MotionScheme): EnterTransition =
        scaleIn(
            animationSpec = motionScheme.defaultSpatialSpec<Float>(),
            initialScale = PREDICTIVE_BACK_ENTER_START_SCALE,
        ) + fadeIn(
            animationSpec = predictiveBackEnterSpec(),
            initialAlpha = 0f,
        )

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
