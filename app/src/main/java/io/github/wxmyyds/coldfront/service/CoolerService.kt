package io.github.wxmyyds.coldfront.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.wxmyyds.coldfront.ble.BleManagerHolder
import io.github.wxmyyds.coldfront.ble.CoolerBleManager
import io.github.wxmyyds.coldfront.data.ProfileRepository
import io.github.wxmyyds.coldfront.data.SettingsRepository
import io.github.wxmyyds.coldfront.data.ThermalThresholds
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.FanMode
import io.github.wxmyyds.coldfront.domain.LightEffect
import io.github.wxmyyds.coldfront.domain.RGBConfig
import io.github.wxmyyds.coldfront.thermal.ThermalMonitor
import io.github.wxmyyds.coldfront.ui.i18n.AppStrings
import io.github.wxmyyds.coldfront.ui.i18n.stringsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

private const val TAG = "CoolerService"
private const val CHANNEL_ID = "cooler_service_channel"
private const val NOTIFICATION_ID = 1001

/**
 * 自动模式前台服务：持续连接散热器，依据温度自动调速，并保持状态通知。
 *
 * ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE 要求 manifest 声明
 * FOREGROUND_SERVICE_CONNECTED_DEVICE 权限（已声明）。
 */
class CoolerService : Service() {

    companion object {
        const val ACTION_START_AUTO = "io.github.wxmyyds.coldfront.START_AUTO"
        const val ACTION_STOP = "io.github.wxmyyds.coldfront.STOP"
        const val ACTION_SWITCH_TO_MANUAL = "io.github.wxmyyds.coldfront.SWITCH_TO_MANUAL"
        const val ACTION_RECONNECT = "io.github.wxmyyds.coldfront.RECONNECT"
        const val ACTION_SET_RGB = "io.github.wxmyyds.coldfront.SET_RGB"

        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_DEVICE_TYPE = "device_type"
        const val EXTRA_DEVICE_MAC = "device_mac"

        /** 自动调速周期 */
        private const val ADJUST_INTERVAL_MS = 3000L
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_RGB_EFFECT = "rgb_effect"
        const val EXTRA_RGB_R = "rgb_r"
        const val EXTRA_RGB_G = "rgb_g"
        const val EXTRA_RGB_B = "rgb_b"

        @Volatile
        private var instance: CoolerService? = null

        /** 当前运行实例（供 UI 在自动模式下设置 RGB） */
        fun getInstance(): CoolerService? = instance
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var bleManager: CoolerBleManager
    private lateinit var thermal: ThermalMonitor
    private lateinit var profiles: ProfileRepository
    private lateinit var settings: SettingsRepository
    private var monitorJob: Job? = null
    private var thresholds = ThermalThresholds()

    /** 服务层通知文案也走双语表 */
    private val strings: AppStrings get() = stringsFor(Locale.getDefault())

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 共享应用级单例:服务与 UI 同一连接,启动服务不再断开 UI 连接
        bleManager = BleManagerHolder.get(applicationContext)
        thermal = ThermalMonitor(applicationContext)
        profiles = ProfileRepository(applicationContext)
        settings = SettingsRepository(applicationContext)
        createNotificationChannel()
        startForegroundCompat(buildNotification(strings.serviceWaitingConfig, 0))
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_AUTO -> {
                val typeName = intent.getStringExtra(EXTRA_DEVICE_TYPE) ?: return START_STICKY
                val mac = intent.getStringExtra(EXTRA_DEVICE_MAC) ?: return START_STICKY
                val type = runCatching { CoolerDeviceType.valueOf(typeName) }.getOrNull()
                    ?: return START_STICKY
                startAuto(type, mac, intent.getStringExtra(EXTRA_DEVICE_NAME))
            }
            ACTION_STOP -> stopSelfSafely()
            ACTION_SWITCH_TO_MANUAL -> {
                bleManager.setSmart(false)
                stopSelfSafely()
            }
            ACTION_RECONNECT -> {
                val typeName = intent.getStringExtra(EXTRA_DEVICE_TYPE)
                val mac = intent.getStringExtra(EXTRA_DEVICE_MAC)
                if (typeName != null && mac != null) {
                    val type = runCatching { CoolerDeviceType.valueOf(typeName) }.getOrNull()
                    if (type != null) bleManager.connectByAddress(mac, type)
                }
            }
            ACTION_SET_RGB -> {
                val effectCode = intent.getIntExtra(EXTRA_RGB_EFFECT, 0).toByte()
                val r = intent.getIntExtra(EXTRA_RGB_R, 0)
                val g = intent.getIntExtra(EXTRA_RGB_G, 0)
                val b = intent.getIntExtra(EXTRA_RGB_B, 0)
                val effect = LightEffect.fromCode(effectCode)
                    ?: LightEffect.ALWAYS_BRIGHT
                bleManager.setRGB(RGBConfig(effect, r, g, b))
            }
        }
        return START_STICKY
    }

    private fun startAuto(type: CoolerDeviceType, mac: String, name: String?) {
        scope.launch {
            thresholds = settings.thresholds.first()
        }
        // 未连接时才连接(共享管理器:已连接则直接复用,绝不打断)
        if (!bleManager.state.value.isConnected) {
            bleManager.connectByAddress(mac, type)
        }
        // 智能温控为设备自主控制(1018 写 0x01),服务仅做状态常驻通知
        bleManager.setSmart(true)
        startForegroundCompat(buildNotification(strings.serviceAutoOn, 0))
    }

    /** 监控散热器状态，断连时尝试重连 */
    private fun observeState() {
        scope.launch {
            bleManager.state.collect { st ->
                if (st.isConnected) {
                    updateNotification(strings.serviceAutoOn, st.fanPercent)
                } else if (st.connection == ConnectionState.DISCONNECTED) {
                    updateNotification(strings.serviceReconnect, 0)
                }
            }
        }
    }

    /** 供 UI 调用：设置 RGB */
    fun setRGB(config: RGBConfig) = bleManager.setRGB(config)

    private fun stopSelfSafely() {
        monitorJob?.cancel()
        // 共享管理器:服务停止不断开连接(UI 可能仍在使用)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        scope.cancel()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ──────────────────────────── 通知 ────────────────────────────

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            strings.serviceChannelName,
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = strings.serviceChannelDesc }
        nm.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(text: String, fanPercent: Int): Notification {
        val main = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = actionIntent(ACTION_STOP)
        val manual = actionIntent(ACTION_SWITCH_TO_MANUAL)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("ColdFront")
            .setContentText("$text · ${fanPercent}%")
            .setContentIntent(main)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, strings.close, stop)
            .addAction(android.R.drawable.ic_menu_edit, strings.serviceSwitchManual, manual)
            .build()
    }

    private fun updateNotification(text: String, fanPercent: Int) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(text, fanPercent))
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, CoolerService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
