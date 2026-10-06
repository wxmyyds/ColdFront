package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.wxmyyds.coldfront.ui.component.AppMotion
import io.github.wxmyyds.coldfront.ui.component.NavigationMotionKind
import io.github.wxmyyds.coldfront.ui.component.isSecondaryDestination
import io.github.wxmyyds.coldfront.ui.component.isTopLevelDestination
import io.github.wxmyyds.coldfront.ui.component.predictiveBackParentEnter
import io.github.wxmyyds.coldfront.ui.component.predictiveBackExit
import io.github.wxmyyds.coldfront.ui.component.shouldUsePredictivePop

/** One transition owner for real destinations. Tab travel belongs exclusively to PrimaryPageStrip. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    topLevelRoutes: Set<String>,
    predictiveBack: Boolean,
    modifier: Modifier = Modifier,
    builder: NavGraphBuilder.() -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    // One shared gesture-progress value for both the leaving page and the parent underneath it.
    // The two surfaces drive mirror transforms of the same value and their settle targets are
    // numerically identical (rest at 0 while a detail is on top, settle to 1 once it pops), so
    // sharing one instance - one collector, one Idle-detector, one animation clock - keeps them
    // pixel-synchronised under real 60fps rendering, where two independent instances can disagree
    // by a frame and read as the pages drifting apart on release.
    val backStackEntry by navController.currentBackStackEntryAsState()
    // The back stack emits a null seed on the very first frame of a cold start, before the graph
    // has produced an entry. Resolve it to the start destination (which is top-level) exactly like
    // MainActivity does: treating that frame as "no page" would initialise the settle direction to
    // the covered value, then flip it to 1f once the start destination lands - one bogus settle
    // animation on first launch.
    val currentRoute = backStackEntry?.destination?.route ?: startDestination
    val topLevelIsCurrent = isTopLevelDestination(currentRoute, topLevelRoutes)
    val sharedSettleProgress = rememberGestureSettleProgress(
        // Both roles observe exactly while a detail is on top: the leaving page is dismissible only
        // then, and the parent is only covered then. Flipping to false on the pop rebuilds the
        // running state, which resets gestureOver and starts the settle immediately - the same fast
        // commit path the surfaces had independently.
        observeBackGesture = predictiveBack && !topLevelIsCurrent,
        // Unified settle target for both roles: 1 once the pop commits (page slid out / parent
        // returned), 0 while a detail is on top. DetailDismissSurface reads the same shared value
        // for its own slide, so one release drives both layers through the same motion.
        settleTo = if (topLevelIsCurrent) 1f else 0f,
    )
    CompositionLocalProvider(LocalBackGestureSettleProgress provides sharedSettleProgress) {
        // Debug: log the shared progress value on every change to confirm the second gesture
        // actually moves it (0 -> 0.5) as the parent's scrim implies.
        val sharedValue = sharedSettleProgress.value
        LaunchedEffect(sharedValue) {
            android.util.Log.i("PBGDiag", "shared progress=$sharedValue")
        }
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier,
            contentAlignment = Alignment.Center,
            // Chrome and destination content must not animate the transition viewport's dimensions.
            sizeTransform = { null },
            enterTransition = {
                AppMotion.pageEnter(
                    kind = if (isSecondaryDestination(targetState.destination.route, topLevelRoutes)) {
                        NavigationMotionKind.PushDetail
                    } else NavigationMotionKind.PopDetail,
                    forward = true,
                    motionScheme = motionScheme,
                    routeDistance = 1,
                )
            },
            exitTransition = {
                AppMotion.pageExit(
                    kind = if (isSecondaryDestination(targetState.destination.route, topLevelRoutes)) {
                        NavigationMotionKind.PushDetail
                    } else NavigationMotionKind.PopDetail,
                    forward = true,
                    motionScheme = motionScheme,
                    routeDistance = 1,
                )
            },
            popEnterTransition = { predictiveBackParentEnter() },
            popExitTransition = { predictiveBackExit() },
            predictivePopEnterTransition = { _ ->
                if (shouldUsePredictivePop(
                        predictiveBack, initialState.destination.route, targetState.destination.route,
                        topLevelRoutes,
                    )
                ) predictiveBackParentEnter() else EnterTransition.None
            },
            predictivePopExitTransition = { _ ->
                if (shouldUsePredictivePop(
                        predictiveBack, initialState.destination.route, targetState.destination.route,
                        topLevelRoutes,
                    )
                ) {
                    android.util.Log.i(
                        "PBGDiag",
                        "popExit: initial=${initialState.destination.route} target=${targetState.destination.route} " +
                            "predictiveBack=$predictiveBack",
                    )
                    predictiveBackExit()
                } else ExitTransition.None
            },
            builder = builder,
        )
    }
}
