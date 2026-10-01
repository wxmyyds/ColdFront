package io.github.wxmyyds.coldfront.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.wxmyyds.coldfront.ble.BlePermissionManager
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings
import io.github.wxmyyds.coldfront.ui.i18n.rememberStrings
import io.github.wxmyyds.coldfront.ui.component.LocalGlassHazeState
import io.github.wxmyyds.coldfront.ui.component.LocalInterfaceBlur
import io.github.wxmyyds.coldfront.ui.component.glassEffect
import io.github.wxmyyds.coldfront.ui.component.glassSource
import io.github.wxmyyds.coldfront.ui.component.rememberGlassHazeState
import io.github.wxmyyds.coldfront.ui.theme.RedmagicCoolerTheme

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
        val interfaceBlur by vm.interfaceBlur.collectAsStateWithLifecycle()
        val palette by vm.palette.collectAsStateWithLifecycle()
        val hazeState = rememberGlassHazeState()
        // 语言覆盖必须在取文案之前生效
        val strings = rememberStrings(override = appLanguage)
        val dark = when (darkMode) {
            "light" -> false
            "dark" -> true
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }
        CompositionLocalProvider(
            LocalStrings provides strings,
            LocalGlassHazeState provides hazeState,
            LocalInterfaceBlur provides interfaceBlur,
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
        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    vm.refreshBluetoothState()
                }
            }
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            onDispose { runCatching { context.unregisterReceiver(receiver) } }
        }
    }
}

private fun routeOrder(route: String?): Int = when (route) {
    Routes.HOME -> 0
    Routes.DEVICES -> 1
    Routes.RGB -> 2
    Routes.SETTINGS -> 3
    else -> 4
}

private object Routes {
    const val HOME = "home"
    const val DEVICES = "devices"
    const val SCAN = "scan"
    const val RGB = "rgb"
    const val SETTINGS = "settings"
}

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
                popUpTo(Routes.SCAN) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val items: List<Triple<String, ImageVector, () -> String>> = listOf(
        Triple(Routes.HOME, Icons.Filled.AcUnit) { strings.navHome },
        Triple(Routes.DEVICES, Icons.Filled.Bluetooth) { strings.navDevices },
        Triple(Routes.RGB, Icons.Filled.Palette) { strings.navRgb },
        Triple(Routes.SETTINGS, Icons.Filled.Settings) { strings.navSettings },
    )

    val navigateToTab: (String) -> Unit = { route ->
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Use existing foundation/Material3 APIs, without adding a window-size dependency.
    // Keep one NavHost at the same composition location across window resizing.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp),
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        modifier = Modifier
                            .semantics { isTraversalGroup = true }
                            .glassEffect(LocalGlassHazeState.current, LocalInterfaceBlur.current),
                        containerColor = MaterialTheme.colorScheme.surface.copy(
                            alpha = if (LocalInterfaceBlur.current) 0.62f else 1f,
                        ),
                        windowInsets = WindowInsets(
                            left = 0.dp,
                            top = 0.dp,
                            right = 0.dp,
                            bottom = 12.dp,
                        ),
                    ) {
                        items.forEach { (route, icon, label) ->
                            NavigationBarItem(
                                selected = current?.hierarchy?.any { it.route == route } == true,
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
                    // see only remaining insets. The bar retains a compact 12dp gesture inset.
                    .consumeWindowInsets(inner),
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .semantics { isTraversalGroup = true },
                        containerColor = MaterialTheme.colorScheme.background,
                    ) {
                        items.forEach { (route, icon, label) ->
                            NavigationRailItem(
                                selected = current?.hierarchy?.any { it.route == route } == true,
                                onClick = { navigateToTab(route) },
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(label()) },
                            )
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
                            val forward = routeOrder(targetState.destination.route) >= routeOrder(initialState.destination.route)
                            slideInHorizontally(initialOffsetX = { if (forward) it / 8 else -it / 8 }) + fadeIn()
                        },
                        exitTransition = {
                            val forward = routeOrder(targetState.destination.route) >= routeOrder(initialState.destination.route)
                            slideOutHorizontally(targetOffsetX = { if (forward) -it / 8 else it / 8 }) + fadeOut()
                        },
                        popEnterTransition = {
                            if (predictiveBack) {
                                val forward = routeOrder(targetState.destination.route) >= routeOrder(initialState.destination.route)
                                slideInHorizontally(initialOffsetX = { if (forward) it / 8 else -it / 8 }) + fadeIn()
                            } else EnterTransition.None
                        },
                        popExitTransition = {
                            if (predictiveBack) {
                                val forward = routeOrder(targetState.destination.route) >= routeOrder(initialState.destination.route)
                                slideOutHorizontally(targetOffsetX = { if (forward) -it / 8 else it / 8 }) + fadeOut()
                            } else ExitTransition.None
                        },
                        predictivePopEnterTransition = { swipeEdge ->
                            if (predictiveBack) {
                                slideInHorizontally(
                                    initialOffsetX = { if (swipeEdge == BackEventCompat.EDGE_LEFT) -it / 8 else it / 8 },
                                )
                            } else EnterTransition.None
                        },
                        predictivePopExitTransition = { swipeEdge ->
                            if (predictiveBack) {
                                slideOutHorizontally(
                                    targetOffsetX = { if (swipeEdge == BackEventCompat.EDGE_LEFT) it / 8 else -it / 8 },
                                )
                            } else ExitTransition.None
                        },
                        // Constrain the child, not the weighted slot: retain centering on wide windows.
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxSize()
                            .semantics { isTraversalGroup = true },
                    ) {
                        composable(Routes.HOME) { HomeScreen(vm, onAddDevice = { nav.navigate(Routes.SCAN) }) }
                        composable(Routes.DEVICES) { DevicesScreen(vm, onAddDevice = { nav.navigate(Routes.SCAN) }) }
                        composable(Routes.SCAN) { AddDeviceScreen(vm, onBack = { nav.popBackStack() }) }
                        composable(Routes.RGB) { RGBControlScreen(vm, onConnect = { nav.navigate(Routes.SCAN) }) }
                        composable(Routes.SETTINGS) { SettingsScreen(vm) }
                    }
                }
            }
        }
    }
}
