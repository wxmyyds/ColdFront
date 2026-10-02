package io.github.wxmyyds.coldfront.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.wxmyyds.coldfront.ble.BlePermissionManager
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.ui.component.AppMotion
import io.github.wxmyyds.coldfront.ui.component.NavigationMotionKind
import io.github.wxmyyds.coldfront.ui.component.isForwardTopLevelTransition
import io.github.wxmyyds.coldfront.ui.component.isSecondaryDestination
import io.github.wxmyyds.coldfront.ui.component.navigationMotionKind
import io.github.wxmyyds.coldfront.ui.component.shouldUsePredictivePop
import io.github.wxmyyds.coldfront.ui.component.topLevelRouteDistance
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings
import io.github.wxmyyds.coldfront.ui.i18n.rememberStrings
import io.github.wxmyyds.coldfront.ui.theme.RedmagicCoolerTheme
import kotlinx.coroutines.flow.collect

class MainActivity : ComponentActivity() {

    private val vm: CoolerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 关掉系统对导航栏的强制对比度遮罩，edge-to-edge 下保持纯净透明。
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            AppContent(vm)
        }
    }

    @Composable
    private fun AppContent(vm: CoolerViewModel) {
        val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()
        val darkMode by vm.darkMode.collectAsStateWithLifecycle()
        val appLanguage by vm.appLanguage.collectAsStateWithLifecycle()
        val palette by vm.palette.collectAsStateWithLifecycle()
        // 语言覆盖必须在取文案之前生效
        val strings = rememberStrings(override = appLanguage)
        val latestStrings by rememberUpdatedState(strings)
        val context = LocalContext.current
        LaunchedEffect(vm) {
            vm.errors.collect {
                Toast.makeText(context, latestStrings.storageOperationFailed, Toast.LENGTH_LONG).show()
            }
        }
        val dark = when (darkMode) {
            "light" -> false
            "dark" -> true
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }
        CompositionLocalProvider(
            LocalStrings provides strings,
        ) {
            RedmagicCoolerTheme(darkTheme = dark, dynamicColor = dynamicColor, palette = palette) {
                SystemBarAppearance(dark)
                PermissionAndBluetoothEffects(vm)
                AppNav(vm)
            }
        }
    }

    @Composable
    private fun SystemBarAppearance(dark: Boolean) {
        DisposableEffect(dark) {
            // WindowCompat returns WindowInsetsControllerCompat. Keep bars transparent for
            // edge-to-edge content, changing only the icon contrast with the app theme.
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val previousStatus = controller.isAppearanceLightStatusBars
            val previousNavigation = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
            onDispose {
                controller.isAppearanceLightStatusBars = previousStatus
                controller.isAppearanceLightNavigationBars = previousNavigation
            }
        }
    }

    @Composable
    private fun PermissionAndBluetoothEffects(vm: CoolerViewModel) {
        val context = LocalContext.current

        // 申请扫描 + 通知权限
        val perms = remember {
            buildList {
                addAll(BlePermissionManager.scanPermissionsToRequest().toList())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
        }
        val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { vm.refreshBluetoothState() }
        LaunchedEffect(Unit) { launcher.launch(perms) }

        // 蓝牙开关状态广播
        DisposableEffect(context, vm) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    // Query the adapter, never trust state extras from an exported receiver.
                    if (i?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                        vm.refreshBluetoothState()
                    }
                }
            }
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Bluetooth broadcasts can originate in a privileged process outside the system UID.
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            onDispose { runCatching { context.unregisterReceiver(receiver) } }
        }
    }
}

private fun routeIsSelected(current: NavDestination?, route: String): Boolean =
    current?.hierarchy?.any { it.route == route } == true ||
        (current?.route == Routes.ABOUT && route == Routes.SETTINGS)

private object Routes {
    const val HOME = "home"
    const val DEVICES = "devices"
    const val SCAN = "scan"
    const val RGB = "rgb"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppNav(vm: CoolerViewModel) {
    val nav = rememberNavController()
    val strings = LocalStrings.current
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    // 连接成功后自动离开扫描页,回主页看状态(避免连上后停在列表里像「没反应」)
    val liveState by vm.liveState.collectAsStateWithLifecycle()
    val predictiveBack by vm.predictiveBack.collectAsStateWithLifecycle()
    LaunchedEffect(liveState.connection) {
        if (liveState.connection == ConnectionState.CONNECTED &&
            current?.hierarchy?.any { it.route == Routes.SCAN } == true
        ) {
            nav.navigate(Routes.HOME) {
                popUpTo(nav.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }

    val items: List<Triple<String, ImageVector, () -> String>> = remember(strings) {
        listOf(
            Triple(Routes.HOME, Icons.Filled.AcUnit) { strings.navHome },
            Triple(Routes.DEVICES, Icons.Filled.Bluetooth) { strings.navDevices },
            Triple(Routes.RGB, Icons.Filled.Palette) { strings.navRgb },
            Triple(Routes.SETTINGS, Icons.Filled.Settings) { strings.navSettings },
        )
    }
    // Top-level destinations are exactly the primary navigation destinations in this NavHost.
    val topLevelRoutes = remember(items) { items.map { it.first } }
    val topLevelRouteSet = remember(topLevelRoutes) { topLevelRoutes.toSet() }

    val navigateToTab: (String) -> Unit = { route ->
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Use existing foundation/Material3 APIs, without adding a window-size dependency.
    // Keep one NavHost at the same composition location across window resizing.
    val motionScheme = MaterialTheme.motionScheme
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp),
            bottomBar = {
                AnimatedVisibility(
                    visible = !useRail,
                    enter = AppMotion.navigationBarEnter(motionScheme),
                    exit = AppMotion.navigationBarExit(motionScheme),
                ) {
                    NavigationBar(
                        modifier = Modifier.semantics { isTraversalGroup = true },
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars.union(
                            WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 12.dp),
                        ),
                    ) {
                        items.forEach { (route, icon, label) ->
                            NavigationBarItem(
                                selected = routeIsSelected(current, route),
                                onClick = { navigateToTab(route) },
                                // Label + native selectable semantics announce the destination once.
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(label()) },
                            )
                        }
                    }
                }
            },
        ) { inner ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    // Consume Scaffold's system/bar padding once; nested app bars and the rail
                    // see only remaining insets. The bar uses the real navigation inset, at least 12dp.
                    .consumeWindowInsets(inner),
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .semantics { isTraversalGroup = true },
                        containerColor = MaterialTheme.colorScheme.background,
                    ) {
                        // Scroll only the rail contents: keep system insets and the sibling NavHost fixed.
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            // Match NavigationRail's native spacing inside the scroll container.
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items.forEach { (route, icon, label) ->
                                NavigationRailItem(
                                    selected = routeIsSelected(current, route),
                                    onClick = { navigateToTab(route) },
                                    icon = { Icon(icon, contentDescription = null) },
                                    label = { Text(label()) },
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    NavHost(
                        navController = nav,
                        startDestination = Routes.HOME,
                        enterTransition = {
                            val initialRoute = initialState.destination.route
                            val targetRoute = targetState.destination.route
                            val kind = navigationMotionKind(
                                isPop = false,
                                initialIsSecondary = isSecondaryDestination(initialRoute, topLevelRouteSet),
                                targetIsSecondary = isSecondaryDestination(targetRoute, topLevelRouteSet),
                            )
                            AppMotion.pageEnter(
                                kind = kind,
                                forward = isForwardTopLevelTransition(initialRoute, targetRoute, topLevelRoutes),
                                motionScheme = motionScheme,
                                routeDistance = topLevelRouteDistance(initialRoute, targetRoute, topLevelRoutes),
                            )
                        },
                        exitTransition = {
                            val initialRoute = initialState.destination.route
                            val targetRoute = targetState.destination.route
                            val kind = navigationMotionKind(
                                isPop = false,
                                initialIsSecondary = isSecondaryDestination(initialRoute, topLevelRouteSet),
                                targetIsSecondary = isSecondaryDestination(targetRoute, topLevelRouteSet),
                            )
                            AppMotion.pageExit(
                                kind = kind,
                                forward = isForwardTopLevelTransition(initialRoute, targetRoute, topLevelRoutes),
                                motionScheme = motionScheme,
                                routeDistance = topLevelRouteDistance(initialRoute, targetRoute, topLevelRoutes),
                            )
                        },
                        popEnterTransition = {
                            val initialRoute = initialState.destination.route
                            val targetRoute = targetState.destination.route
                            val kind = navigationMotionKind(
                                isPop = true,
                                initialIsSecondary = isSecondaryDestination(initialRoute, topLevelRouteSet),
                                targetIsSecondary = isSecondaryDestination(targetRoute, topLevelRouteSet),
                            )
                            AppMotion.pageEnter(
                                kind = kind,
                                forward = isForwardTopLevelTransition(initialRoute, targetRoute, topLevelRoutes),
                                motionScheme = motionScheme,
                                routeDistance = topLevelRouteDistance(initialRoute, targetRoute, topLevelRoutes),
                            )
                        },
                        popExitTransition = {
                            val initialRoute = initialState.destination.route
                            val targetRoute = targetState.destination.route
                            val kind = navigationMotionKind(
                                isPop = true,
                                initialIsSecondary = isSecondaryDestination(initialRoute, topLevelRouteSet),
                                targetIsSecondary = isSecondaryDestination(targetRoute, topLevelRouteSet),
                            )
                            AppMotion.pageExit(
                                kind = kind,
                                forward = isForwardTopLevelTransition(initialRoute, targetRoute, topLevelRoutes),
                                motionScheme = motionScheme,
                                routeDistance = topLevelRouteDistance(initialRoute, targetRoute, topLevelRoutes),
                            )
                        },
                        predictivePopEnterTransition = { _ ->
                            if (shouldUsePredictivePop(
                                    predictiveBackEnabled = predictiveBack,
                                    currentRoute = initialState.destination.route,
                                    previousRoute = targetState.destination.route,
                                    topLevelRoutes = topLevelRouteSet,
                                )
                            ) {
                                AppMotion.pageEnter(
                                    kind = NavigationMotionKind.PopDetail,
                                    forward = false,
                                    motionScheme = motionScheme,
                                    routeDistance = 1,
                                )
                            } else EnterTransition.None
                        },
                        predictivePopExitTransition = { _ ->
                            if (shouldUsePredictivePop(
                                    predictiveBackEnabled = predictiveBack,
                                    currentRoute = initialState.destination.route,
                                    previousRoute = targetState.destination.route,
                                    topLevelRoutes = topLevelRouteSet,
                                )
                            ) {
                                AppMotion.pageExit(
                                    kind = NavigationMotionKind.PopDetail,
                                    forward = false,
                                    motionScheme = motionScheme,
                                    routeDistance = 1,
                                )
                            } else ExitTransition.None
                        },
                        // Constrain the child, not the weighted slot: retain centering on wide windows.
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxSize()
                            .semantics { isTraversalGroup = true },
                    ) {
                        composable(Routes.HOME) {
                            HomeScreen(vm, onAddDevice = { nav.navigate(Routes.SCAN) { launchSingleTop = true } })
                        }
                        composable(Routes.DEVICES) {
                            DevicesScreen(vm, onAddDevice = { nav.navigate(Routes.SCAN) { launchSingleTop = true } })
                        }
                        composable(Routes.SCAN) { AddDeviceScreen(vm, onBack = { nav.popBackStack() }) }
                        composable(Routes.RGB) {
                            RGBControlScreen(vm, onConnect = { nav.navigate(Routes.SCAN) { launchSingleTop = true } })
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(vm, onAbout = { nav.navigate(Routes.ABOUT) { launchSingleTop = true } })
                        }
                        composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
                    }
                }
            }
        }
    }
}
