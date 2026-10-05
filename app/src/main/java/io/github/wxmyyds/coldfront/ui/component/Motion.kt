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
import androidx.compose.animation.core.LinearEasing
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

/**
 * Horizontal offset of a page within the transition viewport, in pixels.
 *
 * [entering] is the page that is arriving; [forward] distinguishes a push from its reversal, so an
 * interrupted navigation resolves to the same offsets it would have had without the interruption.
 *
 * A pushed detail enters from the trailing edge at full width and settles centred, which is what
 * makes a push read as a page arriving rather than a page appearing. Its *exit* is centred instead
 * ([NavigationMotionKind.PopDetail]), because a pop leaves under the finger via
 * [predictiveBackExit] and a cancelled push must not leave the page off-centre.
 */
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
    NavigationMotionKind.PushDetail -> if (entering) {
        if (forward) width else -width
    } else {
        // The parent does not move through NavHost. Its step-back is applied by ParentScrimSurface
        // as a graphics layer driven by the real gesture progress, so repeating push/pop cycles
        // cannot drift and an interrupted push cannot slide the parent over the page being dragged
        // (which made the detail vanish mid-gesture).
        0
    }
    NavigationMotionKind.PopDetail -> 0
}

/**
 * How far a covered top-level page shifts aside, as a fraction of the viewport width.
 *
 * The parent does not move to make room; it steps back a fifth of the width, so a detail never
 * fully uncovers it and the two layers stay stacked rather than swapping places. This is the same
 * parallax the reference MIUI-style navigation uses.
 */
internal const val PARENT_PARALLAX_FRACTION = 0.2f

/**
 * Parallax offset of the covered page, in pixels: negative while entering a detail, positive while
 * leaving one, so the parent returns to exactly where it started.
 *
 * A fifth of the width rather than the full width: at full width the parent would slide entirely
 * off-screen, which reads as two unrelated pages swapping rather than one covering the other.
 */
internal fun parentParallaxOffset(covered: Boolean, width: Int): Int =
    (if (covered) -width * PARENT_PARALLAX_FRACTION else 0f).toInt()

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

/**
 * Duration of the settle that follows releasing the gesture.
 *
 * This is not the speed of the drag itself: while the finger is down, NavHost seeks the transition
 * with the raw gesture progress, so the page tracks the finger exactly and this value has no
 * effect on it. It governs only how long the page takes to reach its destination after release -
 * either completing the return or sliding back - and 200ms keeps that hand-off quick without
 * feeling abrupt, matching the system's predictive-back spring (stiffness 1600, damping 1.0).
 */
internal const val DETAIL_POP_DURATION_MS = 200

/**
 * Duration of a detail page sliding in over the top-level page.
 *
 * Longer than [DETAIL_POP_DURATION_MS] because a push moves much further: the arriving page
 * crosses its whole width while the parent steps back a fifth of the viewport, whereas a release
 * only finishes the motion the finger had already covered. 300ms matches the top-level page switch
 * so the app has a single "page moves" duration.
 */
internal const val DETAIL_PUSH_DURATION_MS = 300

/**
 * Opacity of the black scrim that dims the top-level page while a detail page covers it.
 *
 * The parent must read as *background*, which means darker than its own resting appearance rather
 * than merely faded: fading it would let whatever is behind the NavHost show through and would
 * look lighter, not dimmer. A scrim colour is used so this darkens correctly in both themes.
 */
internal const val PARENT_SCRIM_ALPHA = 0.32f

/** How long the parent takes to dim as a detail page arrives over it. */
internal const val PARENT_SCRIM_DURATION_MS = 300

/**
 * How strongly a top-level page - its content *and* its navigation bar - is dimmed right now.
 *
 * `0f` means the page is at full brightness, [PARENT_SCRIM_ALPHA] means fully dimmed. Linear in
 * gesture progress, so the parent brightens exactly as fast as the page above it slides away and
 * one finger drives both. Kept as a pure function so the curve can be sampled in a JVM test.
 */
internal fun parentScrimAlphaForProgress(progress: Float): Float =
    PARENT_SCRIM_ALPHA * (1f - progress.coerceIn(0f, 1f))

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

/**
 * Spec for the leaving page's travel, as a horizontal offset in pixels.
 *
 * The easing must be [LinearEasing]. NavHost drives this transition by seeking it with the raw
 * gesture progress, and a tween applies its easing to whatever fraction it is seeked to - so a
 * curved easing would be applied *on top of* the finger position, not to it. With
 * FastOutSlowInEasing the page reaches 83% of its travel by gesture progress 0.55, which is not
 * tracking the finger. LinearEasing makes the offset equal the finger's own progress exactly.
 *
 * The release is therefore linear too. That is what keeps a committed pop describing the same
 * motion the gesture ended on, so the hand-off at release stays invisible.
 *
 * Exposed separately from [predictiveBackExit] so the curve can be sampled directly: an
 * [androidx.compose.animation.ExitTransition] carries its spec inside and exposes no way to read
 * it back. [DETAIL_POP_WIDTH] is the page width the spec is evaluated against when sampling.
 */
internal fun detailPopTravelSpec(): FiniteAnimationSpec<IntOffset> =
    tween(DETAIL_POP_DURATION_MS, easing = LinearEasing)

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
 * The parent's enter is [EnterTransition.None]: it holds still and is simply uncovered. Giving it
 * an entering offset here was tried and reverted - a moving, opaque parent entering underneath an
 * opaque leaving page hid the leaving page outright, so the detail vanished from the screen
 * mid-gesture. The parent's parallax therefore lives only on the push path, where the parent is the
 * *exiting* page; on a return it is already at its resting offset.
 */internal fun predictiveBackExit(): ExitTransition = detailPopExit

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

/**
 * The parent steps back a fifth of the width as the page above it leaves, then returns to centre.
 *
 * Driven by the same gesture progress as the leaving page, so the two cannot drift apart. The offset
 * is signed for the incoming direction: a page coming from the right uncovers the parent's leading
 * side first, so the parent retreats the same way.
 */
internal fun predictiveBackParentEnter(): EnterTransition = EnterTransition.None

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
            // The parent steps back a fifth of the width as the page above leaves.
            NavigationMotionKind.PopDetail -> predictiveBackParentEnter()
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
            // The parent steps aside as the detail arrives: this is the *exiting* page during a
            // push, because AppNavHost keys the kind off the destination being entered.
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
