package io.github.wxmyyds.coldfront.ble

import android.content.Context

/**
 * 应用级 BLE 管理器单例。
 *
 * UI(CoolerViewModel)与后台服务(CoolerService)共用同一实例,
 * 避免服务启动时断开 UI 连接、各自维护独立 GATT 导致的状态分裂。
 */
object BleManagerHolder {
    @Volatile
    private var instance: CoolerBleManager? = null

    fun get(context: Context): CoolerBleManager =
        instance ?: synchronized(this) {
            instance ?: CoolerBleManager(context.applicationContext).also { instance = it }
        }
}
