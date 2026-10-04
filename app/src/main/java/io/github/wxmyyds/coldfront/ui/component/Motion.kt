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
import androidx.compose.ui.graphics.TransformOrigin
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
    // Every detail navigation uses centred layers, including an interrupted push.
    NavigationMotionKind.PushDetail -> 0
    NavigationMotionKind.PopDetail -> 0
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

private fun topLevelIndex(route: String?, routes: List<String>): Int =
    route?.let(routes::indexOf) ?: -1

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

/** All four tracks share the NavHost seek timeline; no MotionScheme spring can extend it. */
internal fun predictiveBackScaleSpec(): FiniteAnimationSpec<Float> =
    tween(DETAIL_POP_DURATION_MS, easing = PREDICTIVE_BACK_EASING)

/** Alpha, not scale: the detail fades from 1 to 0 over the first 35% of the timeline. */
internal fun predictiveBackExitSpec(): FiniteAnimationSpec<Float> = keyframes {
    durationMillis = DETAIL_POP_DURATION_MS
    1f atFraction 0f using PREDICTIVE_BACK_EASING
    0f atFraction PREDICTIVE_BACK_CROSSFADE_AT
    0f atFraction 1f
}

/** The parent is transparent until 35%, then fades to 1 over the remaining timeline. */
internal fun predictiveBackEnterSpec(): FiniteAnimationSpec<Float> = keyframes {
    durationMillis = DETAIL_POP_DURATION_MS
    0f atFraction 0f
    0f atFraction PREDICTIVE_BACK_CROSSFADE_AT using PREDICTIVE_BACK_EASING
    1f atFraction 1f
}

/** Resting alpha for the detail page's push fade. */
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
        return when (kind) {
            NavigationMotionKind.PopDetail -> predictiveBackEnter()
            NavigationMotionKind.PushDetail -> scaleIn(
                animationSpec = predictiveBackScaleSpec(),
                initialScale = PREDICTIVE_BACK_ENTER_START_SCALE,
                transformOrigin = TransformOrigin.Center,
            ) + effects
            NavigationMotionKind.TopLevel -> slideInHorizontally(
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
            NavigationMotionKind.PopDetail -> ExitTransition.None
        }
        val spatialSpec = when (kind) {
            NavigationMotionKind.TopLevel ->
                tween<IntOffset>(topLevelPageDuration(routeDistance), easing = FastOutSlowInEasing)
            NavigationMotionKind.PushDetail -> motionScheme.slowSpatialSpec<IntOffset>()
            // PopDetail never slides; it scales and fades via predictiveBack* instead.
            NavigationMotionKind.PopDetail -> motionScheme.defaultSpatialSpec<IntOffset>()
        }
        return when (kind) {
            NavigationMotionKind.PopDetail -> predictiveBackExit()
            // Compose retains this exit boundary if a push is reversed before settling. It must
            // therefore be centred too; a slide here survives a later scale-only popEnter.
            NavigationMotionKind.PushDetail -> scaleOut(
                animationSpec = predictiveBackScaleSpec(),
                targetScale = PREDICTIVE_BACK_EXIT_SCALE,
                transformOrigin = TransformOrigin.Center,
            )
            NavigationMotionKind.TopLevel -> slideOutHorizontally(
                animationSpec = spatialSpec,
                targetOffsetX = offset,
            ) + effects
        }
    }

    /** The only exit tracks during a detail pop: centred scale and timeline-bound alpha. */
    fun predictiveBackExit(): ExitTransition = detailPopExit

    private val detailPopExit =
        scaleOut(
            animationSpec = predictiveBackScaleSpec(),
            targetScale = PREDICTIVE_BACK_EXIT_SCALE,
            transformOrigin = TransformOrigin.Center,
        ) + fadeOut(animationSpec = predictiveBackExitSpec())

    /** The parent uses the same clock and pivot; there is no independent settle animation. */
    fun predictiveBackEnter(): EnterTransition = detailPopEnter

    private val detailPopEnter =
        scaleIn(
            animationSpec = predictiveBackScaleSpec(),
            initialScale = PREDICTIVE_BACK_ENTER_START_SCALE,
            transformOrigin = TransformOrigin.Center,
        ) + fadeIn(animationSpec = predictiveBackEnterSpec(), initialAlpha = 0f)

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
