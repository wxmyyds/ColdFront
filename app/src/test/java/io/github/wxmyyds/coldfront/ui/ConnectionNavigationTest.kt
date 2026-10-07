package io.github.wxmyyds.coldfront.ui

import androidx.compose.runtime.saveable.SaverScope
import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionNavigationTest {
    private val connected = CoolerLiveState(connection = ConnectionState.CONNECTED, connectionSessionId = 1L)

    @Test
    fun `connected to connected session change leaves scan even when intermediate states were skipped`() {
        val previous = restoredKey(connectionNavigationKey(connected))
        val current = connectionNavigationKey(connected.copy(connectionSessionId = 2L))
        assertNotEquals(previous, current)
        assertTrue(shouldLeaveScanOnConnection(previous, current, isScanDestination = true))
    }

    @Test
    fun `opening scan for an existing session does not leave scan`() {
        val previous = connectionNavigationKey(connected)
        assertFalse(shouldLeaveScanOnConnection(previous, previous, isScanDestination = true))
        val telemetryUpdate = connectionNavigationKey(connected.copy(temperatureC = 30f))
        assertFalse(shouldLeaveScanOnConnection(previous, telemetryUpdate, isScanDestination = true))
    }

    @Test
    fun `restoring scan with the same connected session does not leave scan`() {
        val current = connectionNavigationKey(connected)
        val previous = restoredKey(current)
        assertFalse(shouldLeaveScanOnConnection(previous, current, isScanDestination = true))
    }

    @Test
    fun `restoring an offline or connecting observation still handles success during recreation`() {
        for (connection in listOf(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING)) {
            val previous = restoredKey(connectionNavigationKey(connected.copy(connection = connection)))
            val current = connectionNavigationKey(connected.copy(connectionSessionId = 2L))
            assertTrue(shouldLeaveScanOnConnection(previous, current, isScanDestination = true))
        }
    }

    @Test
    fun `success leaves scan when connecting and connected share the session id`() {
        val previous = restoredKey(connectionNavigationKey(connected.copy(connection = ConnectionState.CONNECTING)))
        val current = connectionNavigationKey(connected)
        assertTrue(shouldLeaveScanOnConnection(previous, current, isScanDestination = true))
    }

    @Test
    fun `a new connection on main does not pop and is recorded before scan opens`() {
        val previous = restoredKey(connectionNavigationKey(connected))
        val current = connectionNavigationKey(connected.copy(connectionSessionId = 2L))
        assertFalse(shouldLeaveScanOnConnection(previous, current, isScanDestination = false))
        // The effect records current even without popping, so opening Scan afterwards is safe.
        assertFalse(shouldLeaveScanOnConnection(current, current, isScanDestination = true))
    }

    @Test
    fun `connection status is still part of the key but telemetry is not`() {
        val discovering = connectionNavigationKey(connected.copy(connection = ConnectionState.DISCOVERING))
        val ready = connectionNavigationKey(connected)
        assertNotEquals(discovering, ready)
        assertFalse(shouldLeaveScanOnConnection(ready, discovering, isScanDestination = true))
        assertTrue(shouldLeaveScanOnConnection(discovering, ready, isScanDestination = true))
        assertEquals(ready, connectionNavigationKey(connected.copy(temperatureC = 27f, fanPercent = 70)))
    }

    @Test
    fun `saver preserves every connection enum name and long session id`() {
        for (connection in ConnectionState.entries) {
            val key = ConnectionNavigationKey(connection, Long.MAX_VALUE)
            assertEquals(key, restoredKey(key))
        }
    }

    @Test
    fun `app start dials the startup default only when no session is running`() {
        val saved = CoolerProfile(
            id = "saved",
            name = "My cooler",
            deviceType = CoolerDeviceType.JACKET_8_PRO,
            macAddress = "AA:BB:CC:DD:EE:FF",
        )
        assertEquals(saved, startupConnectTarget(saved, hasRunningSession = false))
        // The service or a manual connection already owns a session: app start must not dial.
        assertNull(startupConnectTarget(saved, hasRunningSession = true))
        assertNull(startupConnectTarget(null, hasRunningSession = false))
    }

    private fun restoredKey(key: ConnectionNavigationKey): ConnectionNavigationKey {
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = value is String || value is Long
        }
        val saved = with(ConnectionNavigationKeySaver) { scope.save(key) }
        assertEquals(listOf(key.connection.name, key.sessionId), saved)
        return requireNotNull(ConnectionNavigationKeySaver.restore(requireNotNull(saved)))
    }
}
