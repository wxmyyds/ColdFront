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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.wxmyyds.coldfront.ble.BlePermissionManager
import io.github.wxmyyds.coldfront.data.AppSettings
import io.github.wxmyyds.coldfront.ui.component.isSecondaryDestination
import io.github.wxmyyds.coldfront.ui.component.showsPrimaryNavigation
import io.github.wxmyyds.coldfront.ui.component.topLevelPageIndex
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings
import io.github.wxmyyds.coldfront.ui.i18n.rememberStrings
import io.github.wxmyyds.coldfront.ui.theme.BrandSeed
import io.github.wxmyyds.coldfront.ui.theme.RedmagicCoolerTheme
import io.github.wxmyyds.coldfront.ui.theme.ThemeSeed
import io.github.wxmyyds.coldfront.ui.theme.colorSchemeFromSeed
import io.github.wxmyyds.coldfront.ui.theme.pageLayerScheme
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
        // One collected snapshot owns both readiness and every visible setting. Do not
        // split these into StateFlows: their independent scheduling can mix disk revisions.
        val snapshot by vm.settings.collectAsState()
        val settings = snapshot
        // Until the first read succeeds, only error messages use the system language.
        val strings = rememberStrings(override = settings?.appLanguage)
        val latestStrings by rememberUpdatedState(strings)
        val context = LocalContext.current
        // Install before the loading gate, so a failed first read can actually be reported.
        LaunchedEffect(vm) {
            vm.errors.collect {
                Toast.makeText(context, latestStrings.storageOperationFailed, Toast.LENGTH_LONG).show()
            }
        }
        if (settings == null) {
            StartupPlaceholder()
            return
        }
        val dark = when (settings.darkMode) {
            "light" -> false
            "dark" -> true
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }
        CompositionLocalProvider(
            LocalStrings provides strings,
        ) {
            RedmagicCoolerTheme(
                darkTheme = dark,
                dynamicColor = settings.dynamicColor,
                palette = settings.palette,
            ) {
                SystemBarAppearance(dark)
                PermissionAndBluetoothEffects(vm)
                AppNav(vm, settings)
            }
        }
    }

    /**
     * 冷启动占位层：设置还没从 DataStore 读完时不渲染真实界面，只铺一层底色。
     *
     * 底色刻意用与动态取色最终态相同的种子与算法（而非品牌色），因为种子是同步可读的框架
     * 资源，不依赖 DataStore——真正需要异步读盘的只有 dynamicColor 开关本身。若占位层用品牌色，
     * 动态取色用户会先看到一帧品牌紫再跳到壁纸色，正是这里要消除的闪烁。
     */
    @Composable
    private fun StartupPlaceholder() {
        val dark = androidx.compose.foundation.isSystemInDarkTheme()
        val seed = if (ThemeSeed.supportsDynamic()) {
            androidx.compose.ui.res.colorResource(ThemeSeed.dynamicResourceId())
        } else {
            BrandSeed
        }
        val scheme = pageLayerScheme(colorSchemeFromSeed(seed, dark))
        Box(Modifier.fillMaxSize().background(scheme.background))
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

private object Routes {
    /** The single entry that hosts all four primary destinations as a pager. */
    const val MAIN = "main"
    const val SCAN = "scan"
    const val ABOUT = "about"
}

/** Kept in one place so the NavHost and the not-yet-resolved route can never disagree. */
private const val NavHostStartDestination = Routes.MAIN

/** Identifies the primary tabs inside the pager; these are not NavHost routes. */
private object TabRoutes {
    const val HOME = "home"
    const val DEVICES = "devices"
    const val RGB = "rgb"
    const val SETTINGS = "settings"
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppNav(vm: CoolerViewModel, settings: AppSettings) {
    val nav = rememberNavController()
    val strings = LocalStrings.current
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    // 连接成功后自动离开扫描页,回主页看状态(避免连上后停在列表里像「没反应」)
    val liveState by vm.liveState.collectAsStateWithLifecycle()
    val predictiveBack = settings.predictiveBack
    // Include the session even if lifecycle collection skipped reconnect's intermediate states.
    // Never key on route: an already-connected user must still be able to open scan.
    val connectionKey = connectionNavigationKey(liveState)
    LaunchedEffect(connectionKey) {
        if (shouldLeaveScanOnConnection(
                connectionKey,
                isScanDestination = current?.hierarchy?.any { it.route == Routes.SCAN } == true,
            )
        ) {
            nav.navigate(Routes.MAIN) {
                popUpTo(nav.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }

    val items: List<Triple<String, ImageVector, () -> String>> = remember(strings) {
        listOf(
            Triple(TabRoutes.HOME, Icons.Filled.AcUnit) { strings.navHome },
            Triple(TabRoutes.DEVICES, Icons.Filled.Bluetooth) { strings.navDevices },
            Triple(TabRoutes.RGB, Icons.Filled.Palette) { strings.navRgb },
            Triple(TabRoutes.SETTINGS, Icons.Filled.Settings) { strings.navSettings },
        )
    }
    // The four tabs identify pages inside the pager, so they are not navigation destinations.
    val tabRoutes = remember(items) { items.map { it.first } }
    // For the NavHost, MAIN is the only primary destination: everything else is a detail page with
    // a real parent underneath it. Keying the transitions on this is what makes the parent a page
    // that can stay still, instead of something that has to be special-cased per route.
    val navTopLevelRouteSet = remember { setOf(Routes.MAIN) }

    val currentRoute = current?.route
    // The four primary tabs are pages inside MAIN, not destinations of their own, so the selected
    // tab is its own state. It survives a push of a secondary page, which is what keeps the tab
    // strip showing the same page underneath after returning.
    val selectedPage = rememberSaveable { mutableIntStateOf(0) }

    // Switching tabs stays inside MAIN: no navigation, so no NavHost transition and no predictive
    // back. The strip animates itself.
    val navigateToTab: (String) -> Unit = { route ->
        topLevelPageIndex(route, tabRoutes)
            .takeIf { it >= 0 }
            ?.let { selectedPage.intValue = it }
    }

    // Use existing foundation/Material3 APIs, without adding a window-size dependency.
    // Keep one NavHost at the same composition location across window resizing.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        // The bar belongs to the top-level page, so it must be visible whenever that page is the
        // one on screen - including while a back gesture is uncovering it. The route alone cannot
        // answer that: during the drag the current destination is still the detail page, and the
        // route only becomes top-level once the pop commits, which would hide the bar for the whole
        // gesture and pop it in at the end instead of revealing it under the leaving page.
        // Which layer is current, from the route alone. A null route means the back stack has not
        // emitted yet, which is the first frame of a cold start; the graph's start destination is a
        // top-level page, so resolve to it rather than treating the frame as "no page".
        val topLevelIsCurrent = showsPrimaryNavigation(
            currentRoute ?: NavHostStartDestination,
            navTopLevelRouteSet,
        )
        val backProgress = rememberRunningBackProgress(observeBackGesture = true)
        // True while a detail page is the one being dismissed, i.e. the gesture pops back onto a
        // top-level page. Resolved from the navigation layer, so any future detail page is covered
        // without naming it.
        val currentIsDetail = isSecondaryDestination(
            currentRoute ?: NavHostStartDestination,
            navTopLevelRouteSet,
        )
        // The gesture is revealing the top-level page only when it is popping a detail off one.
        // Keying this on "some back gesture is running" instead also fires on the finger-down that
        // opens a detail, which put the bar and its scrim over the page being pushed in.
        val returningToTopLevel = backProgress.value != null
        val showPrimaryNavigation = rememberChromeVisibility(
            isTopLevelCurrent = topLevelIsCurrent,
            revealByGesture = returningToTopLevel,
        )
        NavigationChromeLayout(
            useRail = useRail,
            showNavigation = showPrimaryNavigation.value,
            scrim = if (returningToTopLevel) {
                Modifier.chromeScrim(backProgress)
            } else {
                Modifier
            },
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            navigation = {
                if (!useRail) {
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
                                selected = tabRoutes.getOrNull(selectedPage.intValue) == route,
                                onClick = { navigateToTab(route) },
                                // Label + native selectable semantics announce the destination once.
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(label()) },
                            )
                        }
                    }
                } else {
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
                                    selected = tabRoutes.getOrNull(selectedPage.intValue) == route,
                                    onClick = { navigateToTab(route) },
                                    icon = { Icon(icon, contentDescription = null) },
                                    label = { Text(label()) },
                                )
                            }
                        }
                    }
                }
            },
        ) { primaryPadding ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                AppNavHost(
                    navController = nav,
                    startDestination = NavHostStartDestination,
                    topLevelRoutes = navTopLevelRouteSet,
                    predictiveBack = predictiveBack,
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()
                        .semantics { isTraversalGroup = true },
                ) {
                    composable(Routes.MAIN) {
                        // The scrim wraps the padding rather than sitting inside it, so it covers the
                        // strip the bar occupies as well as the page's own content. Dimming only the
                        // padded content left the bar's footprint bright, which is visible for the
                        // whole gesture and made the parent look half-dimmed.
                        ParentScrimSurface(isCovered = !topLevelIsCurrent) {
                            // Reserve chrome in the parent itself, even while a detail is on top.
                            // The NavHost viewport never resizes when the bar/rail is placed on commit.
                            Box(Modifier.fillMaxSize().padding(primaryPadding).consumeWindowInsets(primaryPadding)) {
                                TopLevelPager(
                                    vm = vm,
                                    settings = settings,
                                    isActive = topLevelIsCurrent,
                                    selectedPage = selectedPage.intValue,
                                    onSelectPage = { selectedPage.intValue = it },
                                    onOpenScan = { nav.navigate(Routes.SCAN) { launchSingleTop = true } },
                                    onOpenAbout = { nav.navigate(Routes.ABOUT) { launchSingleTop = true } },
                                )
                            }
                        }
                    }
                    composable(Routes.SCAN) {
                        DetailDismissSurface(
                            isDismissible = currentIsDetail,
                            isLeaving = !currentIsDetail,
                        ) {
                            AddDeviceScreen(vm, onBack = { nav.popBackStack() })
                        }
                    }
                    composable(Routes.ABOUT) {
                        DetailDismissSurface(
                            isDismissible = currentIsDetail,
                            isLeaving = !currentIsDetail,
                        ) {
                            AboutScreen(onBack = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
