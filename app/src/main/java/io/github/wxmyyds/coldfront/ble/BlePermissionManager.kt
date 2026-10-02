package io.github.wxmyyds.coldfront.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * BLE 相关权限检查（请求在 UI 层用 rememberLauncherForActivityResult 完成）。
 */
object BlePermissionManager {

    /** 是否具备扫描 BLE 设备的权限 */
    fun hasScanPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(context, Manifest.permission.BLUETOOTH_SCAN) &&
                granted(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /** 是否具备连接/操作 BLE 设备的权限 */
    fun hasConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            granted(context, Manifest.permission.BLUETOOTH) &&
                granted(context, Manifest.permission.BLUETOOTH_ADMIN)
        }
    }

    /** 是否具备通知权限（Android 13+） */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else true
    }

    /** 扫描所需申请的权限数组（按系统版本）。12+ 也附带定位权限：部分 OEM ROM 即使有
     *  neverForLocation 仍强制定位授权才能出扫描结果 */
    fun scanPermissionsToRequest(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }

    /** 系统定位服务是否开启（旧系统扫描硬依赖；部分 OEM 12+ 仍要求） */
    fun isLocationServiceEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return true
        return runCatching {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(true)
    }

    fun notificationPermissionToRequest(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else emptyArray()

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
