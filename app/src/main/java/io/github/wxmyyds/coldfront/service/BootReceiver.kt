package io.github.wxmyyds.coldfront.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.github.wxmyyds.coldfront.data.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "BootReceiver"

/**
 * 开机自启：若存在激活的自动模式档案，恢复前台服务。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val profile = ProfileRepository(context).loadActiveProfile()
                if (profile != null) {
                    val svc = Intent(context, CoolerService::class.java).apply {
                        action = CoolerService.ACTION_START_AUTO
                        putExtra(CoolerService.EXTRA_PROFILE_ID, profile.id)
                        putExtra(CoolerService.EXTRA_DEVICE_TYPE, profile.deviceType.name)
                        putExtra(CoolerService.EXTRA_DEVICE_MAC, profile.macAddress)
                        putExtra(CoolerService.EXTRA_DEVICE_NAME, profile.name)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(svc)
                    } else {
                        context.startService(svc)
                    }
                    Log.i(TAG, "开机恢复自动模式：${profile.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "开机恢复失败: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
