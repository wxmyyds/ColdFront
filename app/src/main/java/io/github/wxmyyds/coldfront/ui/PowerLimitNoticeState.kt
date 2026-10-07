package io.github.wxmyyds.coldfront.ui

import androidx.compose.runtime.saveable.listSaver

/** Acknowledgement belongs to one connection session and one uninterrupted power limit. */
internal data class PowerLimitNoticeState(
    val sessionId: Long,
    val acknowledgedLimit: Int? = null,
) {
    /** Validate restored state explicitly; rememberSaveable inputs do not validate restored values. */
    fun normalized(sessionId: Long, powerLimited: Boolean, limit: Int?): PowerLimitNoticeState =
        if (this.sessionId != sessionId || !powerLimited || acknowledgedLimit != limit) {
            PowerLimitNoticeState(sessionId)
        } else {
            this
        }

    /** Call on the normalized state, before rendering the existing notice. */
    fun shouldShow(powerLimited: Boolean, limit: Int?): Boolean =
        powerLimited && limit != null && acknowledgedLimit != limit
}

internal val PowerLimitNoticeStateSaver = listSaver<PowerLimitNoticeState, Any?>(
    save = { listOf(it.sessionId, it.acknowledgedLimit) },
    restore = { PowerLimitNoticeState(sessionId = it[0] as Long, acknowledgedLimit = it[1] as Int?) },
)
