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
import androidx.compose.animation.core.Easing
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

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
 * A quarter of the width, matching the Miuix reference (`MiuixDefault` parallaxes a covered page
 * `* 0.25f` toward the leading edge). At the full width the parent would slide entirely off-screen
 * and the two layers would read as swapping places; a quarter keeps them stacked. This is also the
 * fraction the parent's geometry is checked against in the rendering tests.
 */
internal const val PARENT_PARALLAX_FRACTION = 0.25f

/**
 * Parallax offset of the covered page, in pixels: negative while entering a detail, positive while
 * leaving one, so the parent returns to exactly where it started.
 *
 * A quarter of the width rather than the full width: at full width the parent would slide entirely
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
 * Duration of a detail page sliding in over the top-level page.
 *
 * 300ms matches the top-level page switch so the app has a single "page moves" duration.
 */
internal const val DETAIL_PUSH_DURATION_MS = 300

/**
 * Duration of the release settle: how long the leaving page (and the dimmed parent behind it) takes
 * to reach its resting position once the finger lifts. The Miuix/KernelSU navigation settles a
 * full step in roughly half a second, which is what makes a predictive-back release decelerate and
 * settle rather than snap over a short tween.
 */
internal const val RELEASE_SETTLE_MS = 500

/**
 * Ceiling on how long the leaving page stays in the composition after a pop commits.
 *
 * NavHost has to keep the leaving page around while [DetailDismissSurface]'s graphics layer
 * slides it out, so the predictive-back exit transition is a fixed-duration keep-alive that does
 * not move the page itself. Longer than the settle so the page is already fully off-screen before
 * NavHost detaches it.
 */
internal const val RELEASE_SETTLE_CEILING_MS = 600

/**
 * The release settle curve: the underdamped-oscillator step response the Miuix/KernelSU navigation
 * uses for a full step, baked into an [Easing] so a tween completes in exactly [RELEASE_SETTLE_MS]
 * and reaches the resting position.
 *
 * Implemented from the damped-oscillator equation of motion (not from the Miuix/KernelSU source) and
 * used only as a reference for the feel. `response` is the oscillation period in units of the played
 * duration and `damping` the damping ratio; the shipped pair (`0.8` / `0.95`) gives the established
 * miuix navigation pacing: a brisk middle and a long, gentle tail.
 */
internal class MiuixSettleEasing(
    private val response: Float = 0.8f,
    private val damping: Float = 0.95f,
) : Easing {
    // y(t) = 1 - e^(r t) * (cos(w t) + (dampingRatio * wN / wD) * sin(w t)), the classic step
    // response of an underdamped oscillator, with t the played fraction. The decay `r` and the
    // damped frequency `w` are derived from the natural frequency `wN`.
    private val naturalFreq: Float = (2.0 * PI / response).toFloat()
    private val decay: Float = -damping * naturalFreq
    private val dampedFreq: Float = naturalFreq * sqrt(1f - damping * damping)

    override fun transform(fraction: Float): Float {
        val t = fraction.toDouble()
        val envelope = exp(decay * t)
        val phase = cos(dampedFreq * t) + (damping * naturalFreq / dampedFreq) * sin(dampedFreq * t)
        return (1.0 - envelope * phase).toFloat().coerceIn(0f, 1f)
    }
}

/**
 * The release settle spec: a fixed-duration tween shaped by [MiuixSettleEasing].
 *
 * A tween (rather than a live spring) is used so the settle always completes in exactly
 * [RELEASE_SETTLE_MS] and reaches the resting position, whatever the distance travelled or the
 * release speed, and so a gesture-commit and a cancel describe the same finishing motion. The
 * *drag* is unaffected: see the decoupling note on [DetailDismissSurface] - the finger axis stays
 * linear and only the post-release settle uses this curve.
 */
internal fun releaseSettleSpec(): FiniteAnimationSpec<Float> =
    tween(RELEASE_SETTLE_MS, easing = MiuixSettleEasing())

/**
 * The leaving page exits without NavHost moving it: the spec is a fixed-duration keep-alive that
 * holds the page in place for [RELEASE_SETTLE_CEILING_MS] while its own graphics layer slides it
 * out with [releaseSettleSpec].
 *
 * Sharing one instance keeps the predictive-back gesture and the back-button pop describing the
 * same keep-alive, so the two cannot drift apart. Built once because `slideOutHorizontally`
 * allocates per call.
 */
internal fun predictiveBackExit(): ExitTransition = detailPopKeepAlive

private val detailPopKeepAlive: ExitTransition = slideOutHorizontally(
    animationSpec = tween(RELEASE_SETTLE_CEILING_MS, easing = LinearEasing),
    targetOffsetX = { 0 },
)

/**
 * The parent steps back a quarter of the width as the page above it leaves, then returns to centre.
 *
 * Driven by the same gesture progress as the leaving page, so the two cannot drift apart. The offset
 * is signed for the incoming direction: a page coming from the right uncovers the parent's leading
 * side first, so the parent retreats the same way.
 */
internal fun predictiveBackParentEnter(): EnterTransition = EnterTransition.None

/** Resting alpha for the detail page's push fade. */
internal const val DETAIL_FADE_ALPHA = 0.94f

/**
 * Opacity of the fullscreen black scrim that dims the top-level page while a detail page covers it.
 *
 * `0.5f` is the Miuix `NavDisplayEffects.dimAmount` default: how dark a covered page can get. The
 * scrim is drawn as a solid black layer over the parent (never as a fade of the page's own pixels)
 * because fading would let what is behind the NavHost show through and read *lighter*; black
 * darkens in both themes and gives the covered page its backdrop look.
 */
internal const val PARENT_SCRIM_ALPHA = 0.5f

/**
 * How strongly a top-level page's *own pixels* fade while a detail page covers it.
 *
 * `0.1f` is the Miuix covered-layer alpha falloff (`alpha = 1 - 0.1 * coverProgress`): in addition
 * to the black scrim, the covered page itself fades by up to a tenth so the backdrop reads as
 * extending outward rather than as a hard patch. The two effects stack; both are driven by the same
 * gesture progress so one finger dims and fades the parent and slides the page above it together.
 */
internal const val PARENT_FADE_FRACTION = 0.1f

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
 * How opaque a covered page's own pixels are, as a function of gesture progress.
 *
 * `1f` at full brightness (the page is uncovered), `1 - [PARENT_FADE_FRACTION]` when fully covered.
 * Linear in progress so the page fades in step with the scrim and the page above it. Kept as a pure
 * function so the curve can be sampled in a JVM test.
 */
internal fun parentPageAlphaForProgress(progress: Float): Float =
    1f - PARENT_FADE_FRACTION * (1f - progress.coerceIn(0f, 1f))

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
            // The parent steps back a quarter of the width as the page above leaves.
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
