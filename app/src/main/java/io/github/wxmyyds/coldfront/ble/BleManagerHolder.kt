package io.github.wxmyyds.coldfront.ble

import android.content.Context
import androidx.annotation.MainThread

/** One transport shared by explicit Activity/ViewModel and foreground-service owners. */
object BleManagerHolder {
    @Volatile
    private var instance: CoolerBleManager? = null
    private val owners = mutableSetOf<Any>()

    fun get(context: Context): CoolerBleManager =
        instance ?: synchronized(this) {
            instance ?: CoolerBleManager(context.applicationContext).also { instance = it }
        }

    @MainThread
    fun acquire(context: Context, owner: Any): CoolerBleManager {
        owners.add(owner)
        return get(context)
    }

    @MainThread
    fun release(owner: Any) {
        if (owners.remove(owner) && owners.isEmpty()) instance?.release()
    }
}
