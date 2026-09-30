package io.github.wxmyyds.coldfront.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.wxmyyds.coldfront.data.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "CoolerTile"

/**
 * 快捷设置磁贴：点击切换自动模式开关。
 * 需要 API 24+（TileService 自 API 24 起可用）。
 */
@RequiresApi(Build.VERSION_CODES.N)
class CoolerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refresh()
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        val active = qsTile?.state == Tile.STATE_ACTIVE
        if (active) {
            // 停止
            startService(Intent(this, CoolerService::class.java).apply {
                action = CoolerService.ACTION_STOP
            })
            qsTile?.state = Tile.STATE_INACTIVE
        } else {
            // 启动自动模式（用激活档案）
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                try {
                    val profile = ProfileRepository(applicationContext).loadActiveProfile()
                    if (profile == null) {
                        Log.w(TAG, "无激活档案，无法从磁贴启动")
                        return@launch
                    }
                    val svc = Intent(applicationContext, CoolerService::class.java).apply {
                        action = CoolerService.ACTION_START_AUTO
                        putExtra(CoolerService.EXTRA_PROFILE_ID, profile.id)
                        putExtra(CoolerService.EXTRA_DEVICE_TYPE, profile.deviceType.name)
                        putExtra(CoolerService.EXTRA_DEVICE_MAC, profile.macAddress)
                        putExtra(CoolerService.EXTRA_DEVICE_NAME, profile.name)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(svc)
                    } else {
                        startService(svc)
                    }
                    qsTile?.state = Tile.STATE_ACTIVE
                } catch (e: Exception) {
                    Log.e(TAG, "磁贴启动失败: ${e.message}")
                } finally {
                    pending.finish()
                }
            }
        }
    }

    private fun refresh() {
        qsTile?.apply {
            // 通过是否已有 CoolerService 实例判断运行状态
            state = if (CoolerService.getInstance() != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "ColdFront"
            updateTile()
        }
    }
}
