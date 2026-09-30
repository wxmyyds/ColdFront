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
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
        enableEdgeToEdge()
        setContent {
            AppContent(vm)
        }
    }

    @Composable
    private fun AppContent(vm: CoolerViewModel) {
        val strings = rememberStrings()
        val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()
        val darkMode by vm.darkMode.collectAsStateWithLifecycle()
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
        bottomBar = {
            NavigationBar {
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
            modifier = Modifier.padding(inner),
        ) {
            composable(Routes.HOME) { HomeScreen(vm, onAddDevice = { nav.navigate(Routes.SCAN) }) }
            composable(Routes.DEVICES) { DevicesScreen(vm, onAddDevice = { nav.navigate(Routes.SCAN) }) }
            composable(Routes.SCAN) { AddDeviceScreen(vm) }
            composable(Routes.RGB) { RGBControlScreen(vm, onConnect = { nav.navigate(Routes.SCAN) }) }
            composable(Routes.SETTINGS) { SettingsScreen(vm) }
        }
    }
}
