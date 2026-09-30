package io.github.wxmyyds.coldfront.thermal

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import java.io.File

private const val TAG = "ThermalMonitor"

/**
 * 设备温度监控：优先读取 CPU 热区节点，回退电池温度。
 * 自动模式在散热器未上报温度时用此温度做调速依据。
 */
class ThermalMonitor(private val context: Context) {

    /** 当前设备温度（°C），读取失败返回 null */
    fun currentDeviceTempC(): Float? {
        readCpuTemp()?.let { return it }
        readBatteryTemp()?.let { return it }
        return null
    }

    /** 读取 /sys/class/thermal 下 thermal_zoneN 节点的 temp 文件 */
    private fun readCpuTemp(): Float? {
        return try {
            val dir = File("/sys/class/thermal")
            val zones = dir.listFiles { f -> f.name.startsWith("thermal_zone") }
                ?.sortedBy { f -> f.name }
                ?: return null
            for (zone in zones) {
                val tempFile = File(zone, "temp")
                if (!tempFile.canRead()) continue
                val raw = tempFile.readText().trim()
                val value = raw.toFloatOrNull() ?: continue
                // 内核节点单位通常是毫摄氏度（×1000），少数直接是摄氏度
                val celsius = if (value > 1000f) value / 1000f else value
                if (celsius in -20f..120f) return celsius
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "读取 CPU 温度失败: ${e.message}")
            null
        }
    }

    /** 电池温度（°C，精度 0.1） */
    private fun readBatteryTemp(): Float? {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?: return null
            if (temp == Int.MIN_VALUE) null else temp / 10f
        } catch (e: Exception) {
            Log.w(TAG, "读取电池温度失败: ${e.message}")
            null
        }
    }
}
