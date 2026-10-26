package io.github.wxmyyds.coldfront.service

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
import io.github.wxmyyds.coldfront.ble.BlePermissionManager
import io.github.wxmyyds.coldfront.ble.CoolerBleManager
import io.github.wxmyyds.coldfront.data.ProfileRepository
import io.github.wxmyyds.coldfront.data.SettingsRepository
import io.github.wxmyyds.coldfront.data.retryStorageReads
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import io.github.wxmyyds.coldfront.ui.i18n.AppStrings
import io.github.wxmyyds.coldfront.ui.i18n.stringsFor
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "cooler_service_channel"
private const val NOTIFICATION_ID = 1001
private const val TAG = "CoolerService"

/** Owns background connection intent; the Activity and BLE protocol remain separate. */
class CoolerService : Service() {
    companion object {
        const val ACTION_START_AUTO = "io.github.wxmyyds.coldfront.START_AUTO"
        const val ACTION_STOP = "io.github.wxmyyds.coldfront.STOP"
        const val ACTION_SWITCH_TO_MANUAL = "io.github.wxmyyds.coldfront.SWITCH_TO_MANUAL"
        const val ACTION_RECONNECT = "io.github.wxmyyds.coldfront.RECONNECT"
        private const val EXTRA_CONTROL_ADDRESS = "control_address"
        private const val EXTRA_CONTROL_SESSION = "control_session"
        private const val EXTRA_PROFILE_ID = "profile_id"
        private const val ACTION_VALIDATE_TARGET = "io.github.wxmyyds.coldfront.VALIDATE_TARGET"

        fun startForProfile(context: Context, profile: CoolerProfile) {
            start(context, Intent(context, CoolerService::class.java).apply {
                action = ACTION_START_AUTO
                putExtra(EXTRA_PROFILE_ID, profile.id)
            })
        }

        internal fun switchToManual(context: Context, target: ManualControlTarget) =
            sendControl(context, target, ACTION_SWITCH_TO_MANUAL)

        /** Stop background recovery; the BLE coordinator handles the Boost transition. */
        internal fun stopForTarget(context: Context, target: ManualControlTarget) =
            sendControl(context, target, ACTION_STOP)

        private fun sendControl(context: Context, target: ManualControlTarget, action: String) {
            start(context, Intent(context, CoolerService::class.java).apply {
                this.action = action
                putExtra(EXTRA_CONTROL_ADDRESS, target.address)
                putExtra(EXTRA_CONTROL_SESSION, target.sessionId)
            })
        }

        fun start(context: Context, intent: Intent) {
            if (!BlePermissionManager.hasConnectPermission(context)) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private data class ServiceCommand(val intent: Intent?, val startId: Int)
    private val commands = Channel<ServiceCommand>(Channel.UNLIMITED)
    private var latestStartId = 0
    private var processingStartId = 0
    private lateinit var ble: CoolerBleManager
    private lateinit var profiles: ProfileRepository
    private var target: CoolerProfile? = null
    private var activation: Job? = null
    private var acceptedStartup = false
    private var strings: AppStrings = stringsFor(Locale.getDefault())
    private var lastNotification: ServiceNotification? = null

    override fun onCreate() {
        super.onCreate()
        // Permission checks also belong here: exceptions from onCreate are NOT caught by
        // the receiver/tile that merely scheduled startForegroundService().
        if (!BlePermissionManager.hasConnectPermission(this)) {
            stopSelf()
            return
        }
        ble = BleManagerHolder.acquire(applicationContext, this)
        profiles = ProfileRepository(applicationContext)
        createNotificationChannel()
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else 0
            val waiting = ServiceNotification(strings.serviceWaitingConfig, 0, showReconnect = true)
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(waiting), type)
            acceptedStartup = true
        } catch (e: RuntimeException) {
            Log.e(TAG, "Cannot enter foreground", e)
            stopSelf()
            return
        }
        observeState()
        scope.launch {
            for (command in commands) {
                processingStartId = command.startId
                try {
                    handleCommand(command.intent)
                } catch (e: IOException) {
                    Log.e(TAG, "Cannot read/save service intent", e)
                    // Do not continue with an unpersisted request or overwrite unread data. A failed
                    // stop persistence is logged before the service shuts down.
                    stopSelfSafely()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!acceptedStartup) return START_NOT_STICKY
        latestStartId = startId
        commands.trySend(ServiceCommand(intent, startId))
        return START_STICKY
    }

    private suspend fun handleCommand(intent: Intent?) {
        when (intent?.action) {
            ACTION_START_AUTO -> {
                val id = intent.getStringExtra(EXTRA_PROFILE_ID) ?: run { stopSelfSafely(); return }
                profiles.setServiceProfile(id)
                val profile = profiles.loadServiceProfile() ?: run { stopSelfSafely(); return }
                if (!profile.deviceType.supportsAutoMode) {
                    profiles.setServiceProfile(null)
                    stopSelfSafely()
                    return
                }
                activate(profile)
            }
            ACTION_STOP, ACTION_SWITCH_TO_MANUAL -> {
                val controlTarget = if (intent.hasExtra(EXTRA_CONTROL_SESSION)) {
                    ManualControlTarget(
                        intent.getStringExtra(EXTRA_CONTROL_ADDRESS).orEmpty(),
                        intent.getLongExtra(EXTRA_CONTROL_SESSION, -1L),
                    )
                } else null
                // Notification actions without a captured target address the service's current target.
                if (controlTarget != null && !controlTarget.matches(ble.state.value)) {
                    // A delayed action must not affect a newer target or replacement session, even
                    // when both sessions use the same address.
                    if (target == null) stopSelfSafely()
                    return
                }
                // Persist the opt-out before shutdown so boot or sticky restart cannot re-enable it.
                // Notification Close leaves the device mode unchanged.
                val intendedAddress = intent.getStringExtra(EXTRA_CONTROL_ADDRESS)
                    ?: target?.macAddress ?: profiles.loadServiceProfile()?.macAddress
                stopping = true
                activation?.cancel()
                ble.clearLinkLoss()
                profiles.setServiceProfile(null)
                if (intent.action == ACTION_SWITCH_TO_MANUAL) {
                    val state = ble.state.value
                    val sent = intendedAddress != null && state.isConnected &&
                        intendedAddress.equals(state.deviceAddress, ignoreCase = true) &&
                        (controlTarget == null || controlTarget.matches(state)) &&
                        ble.setSmartAndAwait(false)
                    if (!sent) showControlFailure()
                }
                stopSelfSafely()
            }
            ACTION_RECONNECT, null -> {
                // START_STICKY may deliver a null Intent. Recover only an explicitly saved target.
                val profile = profiles.loadServiceProfile() ?: run { stopSelfSafely(); return }
                activate(profile)
            }
            ACTION_VALIDATE_TARGET -> {
                // A flow emission can be stale by the time this command runs; read storage again.
                if (profiles.loadServiceProfile() == null) stopSelfSafely()
            }
            else -> stopSelfSafely()
        }
    }

    private fun activate(profile: CoolerProfile) {
        // Clear stale UI link-loss state so it cannot resume a second session after the service connects.
        ble.clearLinkLoss()
        stopping = false
        activation?.cancel()
        target = profile
        activationFailed = false
        val current = ble.state.value
        if (serviceNeedsConnection(profile, current)) {
            ble.connectByAddress(profile.macAddress, profile.deviceType)
        }
        activation = scope.launch {
            ble.state.filter { it.isConnected && profile.matches(it) }
                .distinctUntilChangedBy { it.connectionSessionId }
                .collect {
                    // CONNECTED follows initialization. The manager reports Smart only after the write succeeds.
                    activationFailed = !ble.setSmartAndAwait(true)
                    if (activationFailed) showControlFailure()
                    // A retry may leave StateFlow unchanged, so refresh notification text and actions both ways.
                    updateNotification()
                }
        }
    }

    private fun observeState() {
        scope.launch {
            combine(
                ble.state,
                SettingsRepository(applicationContext).appLanguage.recoverNotificationLanguage { cause ->
                    Log.e(TAG, "Cannot read notification language", cause)
                },
            ) { state, language -> state to stringsFor(Locale.getDefault(), language) }
                .collect { (state, localized) ->
                    if (strings !== localized) {
                        strings = localized
                        lastNotification = null
                        createNotificationChannel()
                    }
                    updateNotification(serviceNotification(state, strings, activationFailed))
                }
        }
        scope.launch {
            profiles.observeServiceProfile { cause ->
                Log.e(TAG, "Cannot decode service target", cause)
            }.retryStorageReads { cause ->
                Log.e(TAG, "Cannot observe service target", cause)
            }.collectTargetInvalidations(latestStartId = { latestStartId }) { startId ->
                // Keep deletion queued while the target is loading. The validator re-reads storage
                // after startup and manual commands.
                commands.trySend(ServiceCommand(Intent().setAction(ACTION_VALIDATE_TARGET), startId))
            }
        }
    }

    private var stopping = false
    private var activationFailed = false

    private fun showControlFailure() {
        android.widget.Toast.makeText(this, strings.serviceControlFailed, android.widget.Toast.LENGTH_LONG).show()
    }

    private fun stopSelfSafely() {
        stopping = true
        activation?.cancel()
        target = null
        if (processingStartId != latestStartId) {
            // A newer START is queued. Keep the foreground state and consumer alive so that command is not discarded.
            stopping = false
            return
        }
        if (stopSelfResult(processingStartId)) stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        commands.close()
        scope.cancel()
        BleManagerHolder.release(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, strings.serviceChannelName, NotificationManager.IMPORTANCE_LOW)
                .apply { description = strings.serviceChannelDesc }
        )
    }

    private fun buildNotification(value: ServiceNotification): Notification {
        val main = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(strings.appName)
            .setContentText("${value.text} · ${value.fanPercent}%")
            .setContentIntent(main)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, strings.close, actionIntent(ACTION_STOP))
            .addAction(android.R.drawable.ic_menu_edit, strings.serviceSwitchManual, actionIntent(ACTION_SWITCH_TO_MANUAL))
            .apply {
                if (value.showReconnect) {
                    addAction(android.R.drawable.ic_menu_rotate, strings.serviceReconnect, actionIntent(ACTION_RECONNECT))
                }
            }
            .build()
    }

    private fun updateNotification(
        value: ServiceNotification = serviceNotification(ble.state.value, strings, activationFailed),
    ) {
        if (stopping || !acceptedStartup) return
        if (value == lastNotification) return
        try {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(value))
            lastNotification = value
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission changed", e)
        }
    }

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(), Intent(this, CoolerService::class.java).apply { this.action = action },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

}
