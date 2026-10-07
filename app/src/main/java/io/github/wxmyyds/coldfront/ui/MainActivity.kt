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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wxmyyds.coldfront.R
import io.github.wxmyyds.coldfront.ble.BlePermissionManager
import io.github.wxmyyds.coldfront.data.AppSettings
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
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

/**
 * Destinations owned by miuix-nav. [Main] hosts the four tabs; everything else is a detail page
 * drawn above it. Data objects keep `rememberSaveable` stable across process death.
 */
@Serializable
private sealed interface AppRoute : NavKey {
    @Serializable
    data object Main : AppRoute

    @Serializable
    data object Scan : AppRoute

    @Serializable
    data object About : AppRoute
}

/**
 * The four primary tabs, inside the top-level destination.
 *
 * [WindowInsets.navigationBars] is consumed here rather than by an outer layout: the bar that draws
 * over that inset is now a sibling of this content inside MAIN, so the two belong to one page and
 * must reserve the inset between them - the content pads away from it and the bar sits on top of it.
 */
@Composable
private fun MainPage(
    vm: CoolerViewModel,
    settings: AppSettings,
    isActive: Boolean,
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
    onOpenScan: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val contentInsets = WindowInsets.navigationBars
    // windowInsetsPadding, not padding(insets): WindowInsets is not a PaddingValues, and converting
    // it first would bake the values in at composition rather than per-layout pass.
    Box(
        Modifier.fillMaxSize()
            .windowInsetsPadding(contentInsets)
            .consumeWindowInsets(contentInsets),
    ) {
        TopLevelPager(
            vm = vm,
            settings = settings,
            isActive = isActive,
            selectedPage = selectedPage,
            onSelectPage = onSelectPage,
            onOpenScan = onOpenScan,
            onOpenAbout = onOpenAbout,
        )
    }
}

/** Identifies the primary tabs inside the pager; these are not NavHost routes. */
private object TabRoutes {
    const val HOME = "home"
    const val DEVICES = "devices"
    const val RGB = "rgb"
    const val SETTINGS = "settings"
}

/** 底部导航/Rail 的 tab 项：图标按选中态 composable 绘制，支持选中/未选中两套图标。 */
private data class TabItem(
    val route: String,
    val icon: @Composable (selected: Boolean) -> Unit,
    val label: @Composable () -> String,
)

@Composable
private fun AppNav(vm: CoolerViewModel, settings: AppSettings) {
    val nav = rememberNavController<AppRoute>(AppRoute.Main)
    val strings = LocalStrings.current
    val current = nav.backStack.lastOrNull()

    // 连接成功后自动离开扫描页,回主页看状态(避免连上后停在列表里像「没反应」)
    val liveState by vm.liveState.collectAsStateWithLifecycle()
    val predictiveBack = settings.predictiveBack
    // Include the session even if lifecycle collection skipped reconnect's intermediate states.
    // Never key on route: an already-connected user must still be able to open scan.
    val connectionKey = connectionNavigationKey(liveState)
    LaunchedEffect(connectionKey) {
        if (shouldLeaveScanOnConnection(
                connectionKey,
                isScanDestination = current == AppRoute.Scan,
            )
        ) {
            nav.popUntil { it == AppRoute.Main }
        }
    }

    val open = { route: AppRoute ->
        if (nav.backStack.lastOrNull() != route) nav.push(route)
    }

    val items: List<TabItem> = remember(strings) {
        listOf(
            TabItem(
                TabRoutes.HOME,
                {
                    Icon(
                        painterResource(if (it) R.drawable.home_fill_24 else R.drawable.home_24),
                        contentDescription = null,
                    )
                },
            ) { strings.navHome },
            TabItem(
                TabRoutes.DEVICES,
                {
                    Icon(
                        painterResource(if (it) R.drawable.devices_fill_24 else R.drawable.devices_24),
                        contentDescription = null,
                    )
                },
            ) { strings.navDevices },
            TabItem(TabRoutes.RGB, { Icon(Icons.Filled.Palette, contentDescription = null) }) { strings.navRgb },
            // 设置 tab 用 Rounded 齿轮：选中 Fill、未选中 outline，由 NavigationBar/Rail 的选中态驱动。
            TabItem(
                TabRoutes.SETTINGS,
                {
                    Icon(
                        painterResource(if (it) R.drawable.settings_fill_24 else R.drawable.settings_24),
                        contentDescription = null,
                    )
                },
            ) { strings.navSettings },
        )
    }
    // The four tabs identify pages inside the pager, so they are not navigation destinations.
    val tabRoutes = remember(items) { items.map { it.route } }
    // The four primary tabs are pages inside Main, not destinations of their own, so the selected
    // tab is its own state. It survives a push of a secondary page, which is what keeps the tab
    // strip showing the same page underneath after returning.
    val selectedPage = rememberSaveable { mutableIntStateOf(0) }

    // Switching tabs stays inside Main: no navigation, so no transition and no predictive back.
    // The strip animates itself.
    val navigateToTab: (String) -> Unit = { route ->
        tabRoutes.indexOf(route)
            .takeIf { it >= 0 }
            ?.let { selectedPage.intValue = it }
    }

    // Use existing foundation/Material3 APIs, without adding a window-size dependency.
    // Keep one NavDisplay at the same composition location across window resizing.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        val topLevelIsCurrent = current == null || current == AppRoute.Main
        val cornerRadius = rememberScreenCornerRadius()
        val chrome = @Composable {
            if (!useRail) {
                    NavigationBar(
                        modifier = Modifier.semantics { isTraversalGroup = true },
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars.union(
                            WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 12.dp),
                        ),
                    ) {
                        items.forEach { item ->
                            val itemSelected = tabRoutes.getOrNull(selectedPage.intValue) == item.route
                            NavigationBarItem(
                                selected = itemSelected,
                                onClick = { navigateToTab(item.route) },
                                // Label + native selectable semantics announce the destination once.
                                icon = { item.icon(itemSelected) },
                                label = { Text(item.label()) },
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
                    // Scroll only the rail contents: keep system insets and the sibling page fixed.
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        // Match NavigationRail's native spacing inside the scroll container.
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items.forEach { item ->
                            val itemSelected = tabRoutes.getOrNull(selectedPage.intValue) == item.route
                            NavigationRailItem(
                                selected = itemSelected,
                                onClick = { navigateToTab(item.route) },
                                icon = { item.icon(itemSelected) },
                                label = { Text(item.label()) },
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                NavDisplay(
                    navController = nav,
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()
                        .semantics { isTraversalGroup = true },
                    transition = NavTransitions.MiuixDefault,
                    effects = NavDisplayEffects(
                        cornerClipRadius = cornerRadius,
                        cornerClipMode = NavCornerClipMode.Leading,
                        dimAmount = 0.5f,
                    ),
                ) {
                    entry<AppRoute.Main> {
                        if (useRail) {
                            Row(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    MainPage(
                                        vm = vm,
                                        settings = settings,
                                        isActive = topLevelIsCurrent,
                                        selectedPage = selectedPage.intValue,
                                        onSelectPage = { selectedPage.intValue = it },
                                        onOpenScan = { open(AppRoute.Scan) },
                                        onOpenAbout = { open(AppRoute.About) },
                                    )
                                }
                                chrome()
                            }
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    MainPage(
                                        vm = vm,
                                        settings = settings,
                                        isActive = topLevelIsCurrent,
                                        selectedPage = selectedPage.intValue,
                                        onSelectPage = { selectedPage.intValue = it },
                                        onOpenScan = { open(AppRoute.Scan) },
                                        onOpenAbout = { open(AppRoute.About) },
                                    )
                                }
                                chrome()
                            }
                        }
                    }
                    entry<AppRoute.Scan> {
                        if (!predictiveBack) BackHandler { nav.pop() }
                        AddDeviceScreen(vm, onBack = { nav.pop() })
                    }
                    entry<AppRoute.About> {
                        if (!predictiveBack) BackHandler { nav.pop() }
                        AboutScreen(onBack = { nav.pop() })
                    }
                }
            }
        }
    }
}
