package io.github.wxmyyds.coldfront.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.wxmyyds.coldfront.data.ProfileRepository

private const val TAG = "FanAdjustmentWorker"

/**
 * 周期性安全 Worker：若存在激活档案但服务未运行，则拉起。
 * 作为前台服务被系统杀死后的兜底恢复机制（主恢复靠 Service 自身的 START_STICKY）。
 */
class FanAdjustmentWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            if (CoolerService.getInstance() != null) return Result.success()
            val profile = ProfileRepository(applicationContext).loadActiveProfile() ?: return Result.success()
            val svc = Intent(applicationContext, CoolerService::class.java).apply {
                action = CoolerService.ACTION_START_AUTO
                putExtra(CoolerService.EXTRA_PROFILE_ID, profile.id)
                putExtra(CoolerService.EXTRA_DEVICE_TYPE, profile.deviceType.name)
                putExtra(CoolerService.EXTRA_DEVICE_MAC, profile.macAddress)
                putExtra(CoolerService.EXTRA_DEVICE_NAME, profile.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(svc)
            } else {
                applicationContext.startService(svc)
            }
            Log.i(TAG, "Worker 恢复自动模式：${profile.displayName}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker 失败: ${e.message}")
            Result.retry()
        }
    }
}
