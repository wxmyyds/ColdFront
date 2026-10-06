package io.github.wxmyyds.coldfront.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.LocalOwnersProvider
import androidx.navigation.compose.NavBackStackEntryInfo
import androidx.navigation.createGraph
import androidx.navigation.get
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.wxmyyds.coldfront.ui.component.AppMotion
import io.github.wxmyyds.coldfront.ui.component.NavigationMotionKind
import io.github.wxmyyds.coldfront.ui.component.isSecondaryDestination
import io.github.wxmyyds.coldfront.ui.component.isTopLevelDestination
import io.github.wxmyyds.coldfront.ui.component.predictiveBackParentEnter
import io.github.wxmyyds.coldfront.ui.component.predictiveBackExit
import kotlinx.coroutines.launch

private const val COMPOSE_NAVIGATOR_NAME = "composable"

/** App-owned entry rendering keeps cancellation z-order cleanup coupled to visible-entry updates. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    topLevelRoutes: Set<String>,
    predictiveBack: Boolean,
    modifier: Modifier = Modifier,
    builder: NavGraphBuilder.() -> Unit,
    destinationContent: @Composable (NavBackStackEntry) -> Unit,
) {
    val graph = remember(navController, startDestination, builder) {
        navController.createGraph(startDestination = startDestination, builder = builder)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "AppNavHost requires a ViewModelStoreOwner to be provided via LocalViewModelStoreOwner"
    }
    navController.setViewModelStore(viewModelStoreOwner.viewModelStore)
    navController.graph = graph
    DisposableEffect(lifecycleOwner) {
        navController.setLifecycleOwner(lifecycleOwner)
        onDispose { }
    }

    val composeNavigator = remember(navController) {
        navController.navigatorProvider.get<ComposeNavigator>(COMPOSE_NAVIGATOR_NAME)
    }
    val backStack by composeNavigator.backStack.collectAsState()
    val allVisibleEntries by navController.visibleEntries.collectAsState()
    val entries = allVisibleEntries.filter { it.destination.navigatorName == COMPOSE_NAVIGATOR_NAME }
    val currentEntry = entries.lastOrNull()
    val previousBackEntry = backStack.getOrNull(backStack.lastIndex - 1)
    val navigationEventState = rememberNavigationEventState(
        currentInfo = NavBackStackEntryInfo(backStack.lastOrNull()),
        backInfo = backStack.dropLast(1).asReversed().map(::NavBackStackEntryInfo),
    )
    val eventInProgress = navigationEventState.transitionState as? NavigationEventTransitionState.InProgress
    val inPredictiveBack = eventInProgress?.direction ==
        NavigationEventTransitionState.TRANSITIONING_BACK
    NavigationEventHandler(
        state = navigationEventState,
        isBackEnabled = backStack.size > 1,
        isForwardEnabled = false,
        onBackCompleted = {
            if (!inPredictiveBack) {
                backStack.lastOrNull()?.let(composeNavigator::prepareForTransition)
                previousBackEntry?.let(composeNavigator::prepareForTransition)
            }
            navController.popBackStack()
        },
    )

    val progress = if (inPredictiveBack) eventInProgress?.latestEvent?.progress ?: 0f else 0f

    val transitionState = remember(currentEntry != null) {
        SeekableTransitionState<NavBackStackEntry?>(currentEntry)
    }
    val transition = rememberTransition(transitionState, label = "appNavHost")
    val zIndices = remember { mutableMapOf<String, Float>() }
    val saveableStateHolder = rememberSaveableStateHolder()

    LaunchedEffect(inPredictiveBack, currentEntry, previousBackEntry) {
        if (inPredictiveBack) {
            currentEntry?.let(composeNavigator::prepareForTransition)
            previousBackEntry?.let(composeNavigator::prepareForTransition)
        }
    }
    if (inPredictiveBack) {
        LaunchedEffect(progress, previousBackEntry) {
            previousBackEntry?.let { transitionState.seekTo(progress, it) }
        }
    } else {
        LaunchedEffect(currentEntry) {
            val target = currentEntry ?: return@LaunchedEffect
            if (transitionState.currentState != target) {
                transitionState.animateTo(target)
            } else if (transitionState.fraction > 0f) {
                val duration = (transitionState.fraction * transition.totalDurationNanos / 1_000_000).toInt()
                animate(
                    transitionState.fraction,
                    0f,
                    animationSpec = tween(duration),
                ) { value, _ ->
                    this@LaunchedEffect.launch {
                        if (value > 0f) transitionState.seekTo(value)
                        else transitionState.snapTo(target)
                    }
                }
            }
        }
    }

    LaunchedEffect(
        transition.currentState,
        transition.targetState,
        entries,
        currentEntry,
        inPredictiveBack,
    ) {
        if (!inPredictiveBack && transition.currentState == transition.targetState &&
            transition.targetState == currentEntry
        ) {
            entries.forEach(composeNavigator::onTransitionComplete)
            zIndices.clear()
            transition.targetState?.let { zIndices[it.id] = 0f }
        }
    }

    val route by navController.currentBackStackEntryFlow.collectAsState(initial = null)
    val topLevelIsCurrent = isTopLevelDestination(route?.destination?.route, topLevelRoutes)
    val sharedSettleProgress = rememberGestureSettleProgress(
        observeBackGesture = predictiveBack && !topLevelIsCurrent,
        settleTo = if (topLevelIsCurrent) 1f else 0f,
    )
    val motionScheme = MaterialTheme.motionScheme

    CompositionLocalProvider(LocalBackGestureSettleProgress provides sharedSettleProgress) {
        transition.AnimatedContent(
            modifier = modifier,
            contentAlignment = Alignment.Center,
            contentKey = { it?.id },
            transitionSpec = {
                val initial = initialState
                val target = targetState
                if (initial == null || initial !in entries) {
                    (EnterTransition.None togetherWith ExitTransition.None).using(null).apply {
                        targetContentZIndex = 0f
                    }
                } else {
                    val isPop = initial !in backStack
                    val initialZ = zIndices[initial.id] ?: 0f
                    val targetZ = when {
                        target == null -> 0f
                        isPop || inPredictiveBack -> initialZ - 1f
                        else -> initialZ + 1f
                    }
                    target?.let { zIndices[it.id] = targetZ }

                    val enter = when {
                        inPredictiveBack -> EnterTransition.None
                        isPop -> predictiveBackParentEnter()
                        else -> AppMotion.pageEnter(
                            kind = if (isSecondaryDestination(target?.destination?.route, topLevelRoutes)) {
                                NavigationMotionKind.PushDetail
                            } else NavigationMotionKind.PopDetail,
                            forward = true,
                            motionScheme = motionScheme,
                            routeDistance = 1,
                        )
                    }
                    val exit = when {
                        inPredictiveBack -> ExitTransition.None
                        isPop -> predictiveBackExit()
                        else -> AppMotion.pageExit(
                            kind = if (isSecondaryDestination(target?.destination?.route, topLevelRoutes)) {
                                NavigationMotionKind.PushDetail
                            } else NavigationMotionKind.PopDetail,
                            forward = true,
                            motionScheme = motionScheme,
                            routeDistance = 1,
                        )
                    }
                    (enter togetherWith exit).using(null).apply {
                        targetContentZIndex = targetZ
                    }
                }
            },
        ) { entry ->
            if (entry != null) {
                val isPredictiveBackCancelAnimation = transitionState.currentState == currentEntry
                val contentEntry = if (inPredictiveBack || isPredictiveBackCancelAnimation) {
                    entry
                } else {
                    entries.lastOrNull { it == entry }
                }
                contentEntry?.let { visibleEntry ->
                    visibleEntry.LocalOwnersProvider(saveableStateHolder) {
                        destinationContent(visibleEntry)
                    }
                }
            }
        }
    }
}
