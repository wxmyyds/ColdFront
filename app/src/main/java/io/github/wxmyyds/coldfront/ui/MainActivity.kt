package io.github.wxmyyds.coldfront.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.activity.viewModels
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import io.github.wxmyyds.coldfront.ui.theme.RedmagicCoolerTheme

class MainActivity : ComponentActivity() {

    private val vm: CoolerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        setContent {
            AppContent(vm)
        }
    }

    @Composable
    private fun AppContent(vm: CoolerViewModel) {
        val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()
        val darkMode by vm.darkMode.collectAsStateWithLifecycle()
        val appLanguage by vm.appLanguage.collectAsStateWithLifecycle()
        // 语言覆盖必须在取文案之前生效
        val strings = rememberStrings(override = appLanguage)
        val dark = when (darkMode) {
            "light" -> false
            "dark" -> true
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }
        CompositionLocalProvider(LocalStrings provides strings) {
            RedmagicCoolerTheme(darkTheme = dark, dynamicColor = dynamicColor) {
                PermissionAndBluetoothEffects(vm)
                AppNav(vm)
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
    LaunchedEffect(liveState.connection) {
        if (liveState.connection == ConnectionState.CONNECTED &&
            current?.hierarchy?.any { it.route == Routes.SCAN } == true
        ) {
            nav.navigate(Routes.HOME) { launchSingleTop = true }
        }
    }

    val items: List<Triple<String, ImageVector, () -> String>> = listOf(
        Triple(Routes.HOME, Icons.Filled.AcUnit) { strings.navHome },
        Triple(Routes.DEVICES, Icons.Filled.Bluetooth) { strings.navDevices },
        Triple(Routes.RGB, Icons.Filled.Palette) { strings.navRgb },
        Triple(Routes.SETTINGS, Icons.Filled.Settings) { strings.navSettings },
    )

    Scaffold(
        // MD3E:body area 用 background,surfaceContainer 留给导航区/卡片——
        // 之前整页铺 surfaceContainer,和 NavigationBar 默认容器色撞成一片、没有分界。
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // 动态取色下默认容器 surfaceContainer 与页面 background 色差太小，
            // 显式用高一档容器色保持 tab 栏与内容的分层边界。
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                items.forEach { (route, icon, label) ->
                    val selected = current?.hierarchy?.any { it.route == route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label()) },
                    )
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            // 外层 Scaffold 已用 innerPadding 垫掉系统栏(状态栏+导航栏)；但 padding 不消费
            // insets，嵌套的页面 Scaffold/TopAppBar 会再次消费同样的 insets：
            // 底部→页面内容被再抬一个手势条高度，白色背景露出成一条白带盖住内容底部；
            // 顶部→标题再降一个状态栏高度。consumeWindowInsets 把子层可见 insets 清零。
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
        ) {
            composable(Routes.HOME) { HomeScreen(vm, onAddDevice = { nav.navigate(Routes.SCAN) }) }
            composable(Routes.DEVICES) { DevicesScreen(vm, onAddDevice = { nav.navigate(Routes.SCAN) }) }
            composable(Routes.SCAN) { AddDeviceScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Routes.RGB) { RGBControlScreen(vm, onConnect = { nav.navigate(Routes.SCAN) }) }
            composable(Routes.SETTINGS) { SettingsScreen(vm) }
        }
    }
}
