package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import io.github.wxmyyds.coldfront.ui.component.AppMotion
import io.github.wxmyyds.coldfront.ui.component.NavigationMotionKind
import io.github.wxmyyds.coldfront.ui.component.isSecondaryDestination
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
        popEnterTransition = { AppMotion.predictiveBackEnter() },
        popExitTransition = { AppMotion.predictiveBackExit() },
        predictivePopEnterTransition = { _ ->
            if (shouldUsePredictivePop(
                    predictiveBack, initialState.destination.route, targetState.destination.route,
                    topLevelRoutes,
                )
            ) AppMotion.predictiveBackEnter() else EnterTransition.None
        },
        predictivePopExitTransition = { _ ->
            if (shouldUsePredictivePop(
                    predictiveBack, initialState.destination.route, targetState.destination.route,
                    topLevelRoutes,
                )
            ) AppMotion.predictiveBackExit() else ExitTransition.None
        },
        builder = builder,
    )
}
