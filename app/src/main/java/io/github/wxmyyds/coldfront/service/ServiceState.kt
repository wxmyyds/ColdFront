package io.github.wxmyyds.coldfront.service

import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import io.github.wxmyyds.coldfront.ui.i18n.AppStrings
import kotlinx.coroutines.flow.Flow

/**
 * Queue invalidations even while a start command is still loading its target. Validation is
 * serialized behind that command, so it sees the latest persisted intent rather than a stale
 * flow emission. The initial empty snapshot before the first start must not stop the service.
 */
internal suspend fun Flow<CoolerProfile?>.collectTargetInvalidations(
    latestStartId: () -> Int,
    enqueueValidation: (Int) -> Unit,
) {
    collect { saved ->
        val startId = latestStartId()
        if (saved == null && startId > 0) enqueueValidation(startId)
    }
}

/** A tile click must not replace a newer connection or fall back to a different saved device. */
internal fun tileStartProfile(
    requested: CoolerLiveState,
    current: CoolerLiveState,
    active: CoolerProfile?,
): CoolerProfile? {
    if (active == null || !active.deviceType.supportsAutoMode) return null
    if (requested.connectionSessionId != current.connectionSessionId ||
        requested.connection != current.connection ||
        requested.deviceAddress != current.deviceAddress ||
        requested.deviceType != current.deviceType ||
        requested.smartOn != current.smartOn
    ) return null
    val running = current.connection in setOf(
        ConnectionState.CONNECTED, ConnectionState.CONNECTING, ConnectionState.DISCOVERING,
    )
    if (running && (!active.macAddress.equals(current.deviceAddress, ignoreCase = true) ||
            active.deviceType != current.deviceType)
    ) return null
    return active
}

/** Every visible notification field, including actions, participates in deduplication. */
internal data class ServiceNotification(
    val text: String,
    val fanPercent: Int,
    val showReconnect: Boolean,
)

internal fun serviceNotification(
    state: CoolerLiveState,
    strings: AppStrings,
    activationFailed: Boolean,
): ServiceNotification = ServiceNotification(
    text = when {
        state.isConnected && activationFailed -> strings.serviceControlFailed
        state.isConnected && state.smartOn -> strings.serviceAutoOn
        state.isConnected -> strings.serviceManual
        state.connection == ConnectionState.CONNECTING ||
            state.connection == ConnectionState.DISCOVERING -> strings.homeConnecting
        state.connection == ConnectionState.FAILED -> strings.homeConnectionFailed
        else -> strings.serviceReconnect
    },
    fanPercent = state.fanPercent,
    showReconnect = !state.isConnected || activationFailed,
)
