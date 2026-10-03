package io.github.wxmyyds.coldfront.service

import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import io.github.wxmyyds.coldfront.ui.i18n.stringsFor
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceStateTest {
    private val profile = CoolerProfile(
        id = "a", name = "Cooler A", deviceType = CoolerDeviceType.JACKET_8_PRO,
        macAddress = "AA:BB:CC:DD:EE:FF",
    )
    private val connected = CoolerLiveState(
        connection = ConnectionState.CONNECTED,
        deviceAddress = profile.macAddress,
        deviceType = profile.deviceType,
        connectionSessionId = 1L,
        fanPercent = 65,
    )

    @Test
    fun `deletion while startup holds an old snapshot queues validation after activation`() = runTest {
        val saved = MutableStateFlow<CoolerProfile?>(profile)
        val commands = Channel<Int>(Channel.UNLIMITED)
        var latestStartId = 1
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            saved.collectTargetInvalidations({ latestStartId }) { commands.trySend(it) }
        }
        val oldSnapshot = saved.value
        val continueStartup = CompletableDeferred<Unit>()
        var target: CoolerProfile? = null
        var stopped = false
        val startup = launch {
            continueStartup.await()
            target = oldSnapshot
            // The real service processes validation on the same command lane, after start.
            assertEquals(latestStartId, commands.receive())
            if (saved.value == null) {
                target = null
                stopped = true
            }
        }
        saved.value = null
        runCurrent() // Deliver invalidation before activate() has assigned target.
        assertNull(target)
        continueStartup.complete(Unit)
        startup.join()
        assertTrue(stopped)
        assertNull(target)
    }

    @Test
    fun `initial empty snapshot before first start does not stop foreground startup`() = runTest {
        val saved = MutableStateFlow<CoolerProfile?>(null)
        val validations = mutableListOf<Int>()
        var latestStartId = 0
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            saved.collectTargetInvalidations({ latestStartId }, validations::add)
        }
        assertTrue(validations.isEmpty())
        latestStartId = 3
        saved.value = profile
        runCurrent()
        saved.value = null
        runCurrent()
        assertEquals(listOf(3), validations)
    }

    @Test
    fun `queued null emission does not override a newer persisted start`() = runTest {
        val saved = MutableStateFlow<CoolerProfile?>(profile)
        val commands = Channel<Int>(Channel.UNLIMITED)
        var latestStartId = 1
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            saved.collectTargetInvalidations({ latestStartId }) { commands.trySend(it) }
        }
        saved.value = null
        runCurrent()
        latestStartId = 2
        saved.value = profile
        assertEquals(1, commands.receive())
        // ACTION_VALIDATE_TARGET deliberately re-reads, rather than trusting the old null.
        assertEquals(profile, saved.value)
    }

    @Test
    fun `tile never starts the old active profile while another device is connected`() {
        val oldActive = profile.copy(id = "b", macAddress = "11:22:33:44:55:66")
        assertNull(tileStartProfile(connected, connected, oldActive))
        assertEquals(profile, tileStartProfile(connected, connected, profile))
        assertEquals(profile.copy(macAddress = profile.macAddress.lowercase(Locale.ROOT)),
            tileStartProfile(connected, connected, profile.copy(macAddress = profile.macAddress.lowercase(Locale.ROOT))))
    }

    @Test
    fun `tile abandons a suspended click after session or connection intent changes`() {
        listOf(
            connected.copy(connectionSessionId = 2L),
            connected.copy(deviceAddress = "11:22:33:44:55:66"),
            connected.copy(connection = ConnectionState.DISCONNECTED),
            connected.copy(deviceType = CoolerDeviceType.JACKET_5),
            connected.copy(smartOn = true),
        ).forEach { newer -> assertNull(tileStartProfile(connected, newer, profile)) }
    }

    @Test
    fun `tile telemetry updates do not invalidate an otherwise current click`() {
        val refreshed = connected.copy(fanPercent = 90, temperatureC = 12f, rssi = -50)
        assertEquals(profile, tileStartProfile(connected, refreshed, profile))
    }

    @Test
    fun `tile respects a connecting target and still supports disconnected resume`() {
        val oldActive = profile.copy(id = "b", macAddress = "11:22:33:44:55:66")
        listOf(ConnectionState.CONNECTING, ConnectionState.DISCOVERING).forEach { phase ->
            val inProgress = connected.copy(connection = phase)
            assertNull(tileStartProfile(inProgress, inProgress, oldActive))
        }
        val disconnected = CoolerLiveState()
        assertEquals(profile, tileStartProfile(disconnected, disconnected, profile))
        assertNull(tileStartProfile(disconnected, disconnected, null))
    }

    @Test
    fun `successful auto retry removes failure text and action without BLE emission`() {
        val state = connected.copy(smartOn = true)
        val strings = stringsFor(Locale.ENGLISH)
        val failed = serviceNotification(state, strings, activationFailed = true)
        val recovered = serviceNotification(state, strings, activationFailed = false)
        assertEquals(strings.serviceControlFailed, failed.text)
        assertTrue(failed.showReconnect)
        assertEquals(strings.serviceAutoOn, recovered.text)
        assertFalse(recovered.showReconnect)
        assertNotEquals(failed, recovered)
        assertEquals(failed.fanPercent, recovered.fanPercent)
    }

    @Test
    fun `notification cache key includes reconnect action even when text is identical`() {
        val notification = ServiceNotification("same text", 50, showReconnect = false)
        assertNotEquals(notification, notification.copy(showReconnect = true))
    }

    @Test
    fun `notification state retains connection and manual feedback`() {
        val strings = stringsFor(Locale.ENGLISH)
        assertEquals(strings.serviceManual, serviceNotification(connected, strings, false).text)
        assertEquals(strings.homeConnecting,
            serviceNotification(connected.copy(connection = ConnectionState.CONNECTING), strings, false).text)
        assertEquals(strings.homeConnectionFailed,
            serviceNotification(connected.copy(connection = ConnectionState.FAILED), strings, false).text)
        assertTrue(serviceNotification(CoolerLiveState(), strings, false).showReconnect)
    }
}
