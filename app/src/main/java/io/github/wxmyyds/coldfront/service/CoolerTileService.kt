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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "CoolerTile"

/**
 * 快捷设置磁贴：点击切换自动模式开关。
 * 需要 API 24+（TileService 自 API 24 起可用）。
 */
@RequiresApi(Build.VERSION_CODES.N)
class CoolerTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val active = qsTile?.state == Tile.STATE_ACTIVE
        if (active) {
            // 停止
            startService(Intent(this, CoolerService::class.java).apply {
                action = CoolerService.ACTION_STOP
            })
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.updateTile()
        } else {
            // 启动自动模式（用激活档案；TileService 本身是 Service，用自持协程作用域）
            scope.launch {
                try {
                    val profile = ProfileRepository(applicationContext).loadActiveProfile()
                    if (profile == null) {
                        Log.w(TAG, "无激活档案，无法从磁贴启动")
                        return@launch
                    }
                    CoolerService.startForProfile(applicationContext, profile)
                    qsTile?.state = Tile.STATE_ACTIVE
                    qsTile?.updateTile()
                } catch (e: Exception) {
                    Log.e(TAG, "磁贴启动失败: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
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
