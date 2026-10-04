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
 * Travel of the leaving page as a function of gesture progress.
 *
 * The page must track the finger, so this is deliberately linear in progress with no easing: at
 * progress 0.5 the page has moved exactly half its width and still covers half the screen, with the
 * parent revealed beside it. The system predictive-back curve, CubicBezier(.1, .1, 0, 1), is wrong
 * for a translation - it reaches 68% of the travel by progress 0.25 and 90% by 0.5, which would put
 * the page almost entirely off screen halfway through the gesture, the opposite of the intended
 * reading. That curve still shapes the release animation below, where a settle is wanted.
 */
internal const val DETAIL_POP_TRAVEL = 1f

/** Easing used only when the gesture is released and the page settles. */
internal val DETAIL_POP_SETTLE_EASING = FastOutSlowInEasing

/**
 * Spec for the leaving page's travel, as a horizontal offset in pixels.
 *
 * Exposed separately from [predictiveBackExit] so the curve can be sampled directly: an
 * [androidx.compose.animation.ExitTransition] carries its spec inside and exposes no way to read
 * it back, which is why a previous version of this test could only compare objects by identity.
 * [DETAIL_POP_WIDTH] is the page width the spec is evaluated against when sampling.
 */
internal fun detailPopTravelSpec(): FiniteAnimationSpec<IntOffset> =
    tween(DETAIL_POP_DURATION_MS, easing = DETAIL_POP_SETTLE_EASING)

/** Page width the travel spec is sampled against, in pixels. */
internal const val DETAIL_POP_WIDTH = 1000

/**
 * The leaving page slides out under the finger while the parent is revealed beside it.
 *
 * Both halves come from here so the gesture and the committed pop describe one motion and the
 * hand-off at release is invisible. NavHost seeks this spec with the real gesture progress, so the
 * slide follows the finger directly; there is no separate hand-written offset animation and no
 * fade, scale or size change competing with the translation.
 *
 * The parent's enter is [EnterTransition.None]: it never moves, it is simply uncovered as the page
 * above it travels away, which is what keeps the two layers visibly stacked instead of cross-fading.
 */
internal fun predictiveBackExit(): ExitTransition = detailPopExit

/**
 * One shared instance, so the gesture and the committed pop cannot drift apart: `NavHost` seeks
 * this exact object for the drag and then hands the release to the same transition. Built once
 * because `slideOutHorizontally` allocates per call, and two equal-looking instances would make
 * that guarantee untestable.
 */
private val detailPopExit: ExitTransition = slideOutHorizontally(
    animationSpec = detailPopTravelSpec(),
    targetOffsetX = { width -> (width * DETAIL_POP_TRAVEL).toInt() },
)

/** The parent holds still and is revealed by the page above leaving. */
internal fun predictiveBackEnter(): EnterTransition = EnterTransition.None

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
            // The leaving page translates under the finger; see predictiveBackExit.
            NavigationMotionKind.PopDetail -> predictiveBackEnter()
            // A push whose reverse is interrupted can have its exit boundary retained by Compose,
            // so the page that is about to be dismissed must not be off-centre to begin with.
            NavigationMotionKind.PushDetail -> slideInHorizontally(
                animationSpec = spatialSpec,
                initialOffsetX = offset,
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
            NavigationMotionKind.PushDetail -> slideOutHorizontally(
                animationSpec = spatialSpec,
                targetOffsetX = offset,
            ) + effects
            NavigationMotionKind.TopLevel -> slideOutHorizontally(
                animationSpec = spatialSpec,
                targetOffsetX = offset,
            ) + effects
        }
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
