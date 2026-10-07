package io.github.wxmyyds.coldfront.ui

import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.LightEffect
import io.github.wxmyyds.coldfront.domain.RGBConfig
import io.github.wxmyyds.coldfront.domain.RgbWriteState
import io.github.wxmyyds.coldfront.domain.RgbWriteStatus

/** Device state and an unsent draft are separate; only the matching write may clear dirty. */
internal data class RgbEditorState(
    val address: String?,
    val sessionId: Long,
    val connected: Boolean,
    val config: RGBConfig,
    val dirty: Boolean = false,
    val submittedRequestId: Long? = null,
    val explicitApplyRequestId: Long? = null,
) {
    fun edit(config: RGBConfig): RgbEditorState = copy(
        config = config,
        dirty = true,
        submittedRequestId = null,
        explicitApplyRequestId = null,
    )

    fun submit(request: RgbWriteState, explicitApply: Boolean): RgbEditorState = copy(
        config = request.config,
        dirty = true,
        submittedRequestId = request.requestId,
        explicitApplyRequestId = request.requestId.takeIf { explicitApply },
    )

    fun currentWrite(write: RgbWriteState?): RgbWriteState? = write?.takeIf {
        connected && it.requestId == submittedRequestId && it.config == config
    }

    fun receive(device: CoolerLiveState, write: RgbWriteState?): RgbEditorState {
        // Validate the saved identity as well as remember inputs: navigation restoration may
        // restore a draft from a different connection without observing the intermediate states.
        if (address != device.deviceAddress || sessionId != device.connectionSessionId ||
            connected != device.isConnected
        ) return fromDevice(device)
        val received = device.rgb ?: return this
        if (!connected) return this
        if (!dirty) {
            return if (received == config) this else copy(
                config = received,
                submittedRequestId = null,
                explicitApplyRequestId = null,
            )
        }
        val sent = currentWrite(write)?.takeIf { it.status == RgbWriteStatus.SENT }
        return if (sent != null && device.rgbRevision > sent.readbackRevisionAtWrite) {
            // Delivery alone is not a new device value: a failed read leaves the old snapshot.
            // A revision also handles StateFlow conflating a matching report with a newer one.
            if (received == config) copy(dirty = false) else copy(
                config = received,
                dirty = false,
                submittedRequestId = null,
                explicitApplyRequestId = null,
            )
        } else this
    }

    companion object {
        fun fromDevice(device: CoolerLiveState) = RgbEditorState(
            address = device.deviceAddress,
            sessionId = device.connectionSessionId,
            connected = device.isConnected,
            config = device.rgb.takeIf { device.isConnected }
                ?: RGBConfig(LightEffect.ALWAYS_BRIGHT, 0, 80, 200),
        )
    }
}
