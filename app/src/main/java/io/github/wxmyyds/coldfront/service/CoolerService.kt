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
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "cooler_service_channel"
private const val NOTIFICATION_ID = 1001
private const val TAG = "CoolerService"

/** Owns background connection intent, not the Activity or the BLE protocol implementation. */
class CoolerService : Service() {
    companion object {
        const val ACTION_START_AUTO = "io.github.wxmyyds.coldfront.START_AUTO"
        const val ACTION_STOP = "io.github.wxmyyds.coldfront.STOP"
        const val ACTION_SWITCH_TO_MANUAL = "io.github.wxmyyds.coldfront.SWITCH_TO_MANUAL"
        const val ACTION_RECONNECT = "io.github.wxmyyds.coldfront.RECONNECT"
        internal const val EXTRA_CONTROL_ADDRESS = "control_address"
        private const val EXTRA_PROFILE_ID = "profile_id"
        private const val ACTION_VALIDATE_TARGET = "io.github.wxmyyds.coldfront.VALIDATE_TARGET"

        fun startForProfile(context: Context, profile: CoolerProfile) {
            start(context, Intent(context, CoolerService::class.java).apply {
                action = ACTION_START_AUTO
                putExtra(EXTRA_PROFILE_ID, profile.id)
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
    private var lastNotification: Pair<String, Int>? = null

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
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(strings.serviceWaitingConfig, 0), type)
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
                    // Do not proceed with an unpersisted user request or write defaults over
                    // unread data. In particular a failed stop persistence is logged explicitly.
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
                // Persist explicit opt-out before shutdown, so boot/sticky restart cannot
                // silently re-enable the device. Notification Close keeps device mode intact.
                val intendedAddress = intent.getStringExtra(EXTRA_CONTROL_ADDRESS)
                    ?: target?.macAddress ?: profiles.loadServiceProfile()?.macAddress
                stopping = true
                activation?.cancel()
                profiles.setServiceProfile(null)
                if (intent.action == ACTION_SWITCH_TO_MANUAL) {
                    val state = ble.state.value
                    val sent = intendedAddress != null && state.isConnected &&
                        intendedAddress.equals(state.deviceAddress, ignoreCase = true) &&
                        ble.setSmartAndAwait(false)
                    if (!sent) showControlFailure()
                }
                stopSelfSafely()
            }
            ACTION_RECONNECT, null -> {
                // START_STICKY delivers a null Intent: recover only an explicit saved target.
                val profile = profiles.loadServiceProfile() ?: run { stopSelfSafely(); return }
                activate(profile)
            }
            ACTION_VALIDATE_TARGET -> {
                // The flow emission may precede a newer command; re-read before acting.
                if (profiles.loadServiceProfile() == null) stopSelfSafely()
            }
            else -> stopSelfSafely()
        }
    }

    private fun activate(profile: CoolerProfile) {
        // The service owns this connection intent; a stale UI link-loss record must not
        // outlive it and resume a second session after the service already connected.
        ble.clearLinkLoss()
        stopping = false
        activation?.cancel()
        target = profile
        val current = ble.state.value
        if (!profile.matches(current) || current.connection !in setOf(
                ConnectionState.CONNECTED, ConnectionState.CONNECTING, ConnectionState.DISCOVERING,
            )
        ) ble.connectByAddress(profile.macAddress, profile.deviceType)
        activation = scope.launch {
            ble.state.filter { it.isConnected && profile.matches(it) }
                .distinctUntilChangedBy { it.connectionSessionId }
                .collect {
                    // CONNECTED means initialization is done. No optimistic smart=true before
                    // characteristics exist; the manager acknowledges the actual write.
                    if (!ble.setSmartAndAwait(true)) {
                        activationFailed = true
                        showControlFailure()
                        lastNotification = null
                        updateNotification(strings.serviceControlFailed, ble.state.value.fanPercent)
                    } else activationFailed = false
                }
        }
    }

    private fun observeState() {
        scope.launch {
            combine(
                ble.state,
                SettingsRepository(applicationContext).appLanguage,
            ) { state, language -> state to stringsFor(Locale.getDefault(), language) }
                .retryWhen { cause, _ ->
                    if (cause !is IOException) return@retryWhen false
                    Log.e(TAG, "Cannot read notification language", cause)
                    delay(2_000)
                    true
                }.collect { (state, localized) ->
                    if (strings !== localized) {
                        strings = localized
                        lastNotification = null
                        createNotificationChannel()
                    }
                    val text = when {
                        state.isConnected && state.smartOn -> strings.serviceAutoOn
                        state.isConnected -> strings.serviceManual
                        state.connection == ConnectionState.CONNECTING ||
                            state.connection == ConnectionState.DISCOVERING -> strings.homeConnecting
                        state.connection == ConnectionState.FAILED -> strings.homeConnectionFailed
                        else -> strings.serviceReconnect
                    }
                    updateNotification(text, state.fanPercent)
                }
        }
        scope.launch {
            profiles.serviceProfile.retryWhen { cause, _ ->
                if (cause !is IOException) return@retryWhen false
                Log.e(TAG, "Cannot observe service target", cause)
                delay(2_000)
                true
            }.collect { saved ->
                // Deleting the running profile or an explicit opt-out invalidates recovery.
                // Command handling performs the manual write before stop; don't race it here.
                if (target != null && saved == null && !stopping) {
                    commands.trySend(ServiceCommand(Intent().setAction(ACTION_VALIDATE_TARGET), latestStartId))
                }
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
            // A newer START is queued; removing foreground state or stopping here would
            // discard it. The consumer stays alive and processes the newer start next.
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

    private fun buildNotification(text: String, fanPercent: Int): Notification {
        val main = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(strings.appName)
            .setContentText("$text · $fanPercent%")
            .setContentIntent(main)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, strings.close, actionIntent(ACTION_STOP))
            .addAction(android.R.drawable.ic_menu_edit, strings.serviceSwitchManual, actionIntent(ACTION_SWITCH_TO_MANUAL))
            .apply {
                if (::ble.isInitialized && (!ble.state.value.isConnected || activationFailed)) {
                    addAction(android.R.drawable.ic_menu_rotate, strings.serviceReconnect, actionIntent(ACTION_RECONNECT))
                }
            }
            .build()
    }

    private fun updateNotification(text: String, fanPercent: Int) {
        if (stopping || !acceptedStartup) return
        val value = text to fanPercent
        if (value == lastNotification) return
        lastNotification = value
        try {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(text, fanPercent))
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission changed", e)
        }
    }

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(), Intent(this, CoolerService::class.java).apply { this.action = action },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun CoolerProfile.matches(state: CoolerLiveState): Boolean =
        macAddress.equals(state.deviceAddress, ignoreCase = true) && deviceType == state.deviceType
}
