package io.github.wxmyyds.coldfront.ble

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exactly-once foreground resume contract: pure lifecycle/queue logic, no BLE platform. */
class BackgroundResumeTest {
    private class FakeStore(var loss: BackgroundLinkLoss? = null) : BackgroundLinkLossStore {
        var consumed = 0
        var cleared = 0
        override fun consumeLinkLoss(): BackgroundLinkLoss? {
            val current = loss ?: return null
            loss = null
            consumed++
            return current
        }

        override fun clearLinkLoss() {
            loss = null
            cleared++
        }
    }

    /** Plain JVM Lifecycle: no main-thread check, so LifecycleRegistry cannot be used here. */
    private class FakeLifecycle : Lifecycle() {
        private val observers = mutableListOf<LifecycleObserver>()
        override val currentState: State get() = State.CREATED
        override fun addObserver(observer: LifecycleObserver) {
            if (observer !in observers) observers += observer
        }

        override fun removeObserver(observer: LifecycleObserver) {
            observers -= observer
        }

        fun dispatch(event: Event) {
            val owner = object : LifecycleOwner {
                override val lifecycle: Lifecycle get() = this@FakeLifecycle
            }
            observers.forEach { (it as? LifecycleEventObserver)?.onStateChanged(owner, event) }
        }
    }

    @Test
    fun `first start only arms, later start resumes a stored loss exactly once`() =
        runTest(UnconfinedTestDispatcher()) {
        val lifecycle = FakeLifecycle()
        val store = FakeStore(BackgroundLinkLoss(TEST_MAC, CoolerDeviceType.JACKET_8_PRO))
        val dialed = mutableListOf<Pair<String, CoolerDeviceType>>()
        val resume = BackgroundResume(
            lifecycle = lifecycle, scope = this, store = store,
            hasRunningSession = { false },
            connect = { address, type -> dialed += address to type },
        )
        resume.attach()

        lifecycle.dispatch(Lifecycle.Event.ON_START) // process creation: arm only
        assertEquals(0, dialed.size)
        assertEquals(0, store.consumed)

        lifecycle.dispatch(Lifecycle.Event.ON_START) // real background return
        assertEquals(listOf(TEST_MAC to CoolerDeviceType.JACKET_8_PRO), dialed)
        assertEquals(1, store.consumed)

        lifecycle.dispatch(Lifecycle.Event.ON_START) // no second dial
        assertEquals(1, dialed.size)
    }

    @Test
    fun `resume is skipped when a session is already running and record is cleared`() =
        runTest(UnconfinedTestDispatcher()) {
        val lifecycle = FakeLifecycle()
        val store = FakeStore(BackgroundLinkLoss(TEST_MAC, CoolerDeviceType.JACKET_8_PRO))
        val dialed = mutableListOf<Pair<String, CoolerDeviceType>>()
        val resume = BackgroundResume(
            lifecycle = lifecycle, scope = this, store = store,
            hasRunningSession = { true },
            connect = { address, type -> dialed += address to type },
        )
        resume.attach()
        lifecycle.dispatch(Lifecycle.Event.ON_START)
        lifecycle.dispatch(Lifecycle.Event.ON_START)
        assertEquals(0, dialed.size)
        assertEquals(1, store.cleared)
        assertTrue(store.consumed > 0)
    }

    @Test
    fun `explicit clear prevents any resume`() =
        runTest(UnconfinedTestDispatcher()) {
        val lifecycle = FakeLifecycle()
        val store = FakeStore(BackgroundLinkLoss(TEST_MAC, CoolerDeviceType.JACKET_8_PRO))
        var dialed = false
        val resume = BackgroundResume(
            lifecycle = lifecycle, scope = this, store = store,
            hasRunningSession = { false },
            connect = { _, _ -> dialed = true },
        )
        store.clearLinkLoss()
        resume.attach()
        lifecycle.dispatch(Lifecycle.Event.ON_START)
        lifecycle.dispatch(Lifecycle.Event.ON_START)
        assertFalse(dialed)
    }

    @Test
    fun `detach stops observing lifecycle`() =
        runTest(UnconfinedTestDispatcher()) {
        val lifecycle = FakeLifecycle()
        val store = FakeStore(BackgroundLinkLoss(TEST_MAC, CoolerDeviceType.JACKET_8_PRO))
        var dialed = false
        val resume = BackgroundResume(
            lifecycle = lifecycle, scope = this, store = store,
            hasRunningSession = { false },
            connect = { _, _ -> dialed = true },
        )
        resume.attach()
        lifecycle.dispatch(Lifecycle.Event.ON_START)
        resume.detach()
        lifecycle.dispatch(Lifecycle.Event.ON_START)
        assertFalse(dialed)
    }

    private companion object {
        const val TEST_MAC = "AA:BB:CC:DD:EE:FF"
    }
}