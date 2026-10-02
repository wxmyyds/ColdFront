package io.github.wxmyyds.coldfront.ble

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Identity of a system-dropped link that foreground return may resume exactly once. */
data class BackgroundLinkLoss(val address: String?, val type: CoolerDeviceType?)

/** Lifecycle-agnostic link-loss queue: production manager plus JVM fakes share this contract. */
interface BackgroundLinkLossStore {
    fun consumeLinkLoss(): BackgroundLinkLoss?
    fun clearLinkLoss()
}

/**
 * Exactly-once foreground resume without timers, polling or wake locks.
 *
 * The first ON_START belongs to process creation, not a background return, so it only arms
 * the observer. Each later ON_START consumes at most one stored loss and dials it only when
 * no session is running; [CoolerBleManager] clears the same record on every explicit
 * connect/disconnect and on service activation, so a newer user intent can never be
 * resurrected into a duplicate session.
 */
class BackgroundResume(
    private val lifecycle: Lifecycle,
    private val scope: CoroutineScope,
    private val store: BackgroundLinkLossStore,
    private val hasRunningSession: () -> Boolean,
    private val connect: (address: String, type: CoolerDeviceType) -> Unit,
) : LifecycleEventObserver {
    private var armed = false
    private var attached = false

    fun attach(owner: LifecycleOwner? = null) {
        if (attached) return
        attached = true
        if (owner == null) lifecycle.addObserver(this) else owner.lifecycle.addObserver(this)
    }

    fun detach(owner: LifecycleOwner? = null) {
        if (!attached) return
        attached = false
        if (owner == null) lifecycle.removeObserver(this) else owner.lifecycle.removeObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event != Lifecycle.Event.ON_START) return
        if (!armed) {
            armed = true
            return
        }
        scope.launch {
            val loss = store.consumeLinkLoss() ?: return@launch
            val address = loss.address ?: return@launch
            val type = loss.type ?: return@launch
            if (hasRunningSession()) {
                store.clearLinkLoss()
                return@launch
            }
            connect(address, type)
        }
    }
}
