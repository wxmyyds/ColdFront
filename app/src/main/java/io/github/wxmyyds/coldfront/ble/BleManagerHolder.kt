package io.github.wxmyyds.coldfront.ble

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import androidx.annotation.MainThread

/** One transport shared by explicit Activity/ViewModel and foreground-service owners. */
object BleManagerHolder {
    // The singleton receives applicationContext only; never an Activity or Service context.
    @SuppressLint("StaticFieldLeak")
    private var instance: CoolerBleManager? = null
    private val owners = mutableSetOf<Any>()

    @MainThread
    fun get(context: Context): CoolerBleManager {
        checkMainThread()
        return instance ?: CoolerBleManager(context.applicationContext).also { instance = it }
    }

    @MainThread
    fun acquire(context: Context, owner: Any): CoolerBleManager {
        checkMainThread()
        owners.add(owner)
        return get(context)
    }

    @MainThread
    fun release(owner: Any) {
        checkMainThread()
        if (owners.remove(owner) && owners.isEmpty()) instance?.release()
    }

    private fun checkMainThread() {
        check(Looper.myLooper() === Looper.getMainLooper()) {
            "BleManagerHolder must be accessed on the main thread"
        }
    }
}
