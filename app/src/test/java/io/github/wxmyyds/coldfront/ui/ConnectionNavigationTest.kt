package io.github.wxmyyds.coldfront.ui

import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionNavigationTest {
    private val connected = CoolerLiveState(connection = ConnectionState.CONNECTED, connectionSessionId = 1L)

    @Test
    fun `connected to connected session change restarts navigation even when intermediate states were skipped`() {
        val first = connectionNavigationKey(connected)
        val reconnected = connectionNavigationKey(connected.copy(connectionSessionId = 2L))
        assertNotEquals(first, reconnected)
        assertTrue(shouldLeaveScanOnConnection(reconnected, isScanDestination = true))
        assertFalse(shouldLeaveScanOnConnection(reconnected, isScanDestination = false))
    }

    @Test
    fun `opening scan for an existing session does not create a new navigation event`() = runTest {
        val reconnected = connected.copy(connectionSessionId = 2L)
        // Distinct key equality models LaunchedEffect: route is read inside the effect, not a key.
        val navigations = flowOf(
            Observation(connected, isScan = false),
            Observation(connected, isScan = true), // Existing connection: allow browsing scan.
            Observation(connected.copy(temperatureC = 30f), isScan = true),
            Observation(reconnected, isScan = true), // Lifecycle skipped all intermediate states.
        ).distinctUntilChangedBy { connectionNavigationKey(it.state) }
            .filter { shouldLeaveScanOnConnection(connectionNavigationKey(it.state), it.isScan) }
            .toList()

        assertEquals(listOf(Observation(reconnected, isScan = true)), navigations)
    }

    @Test
    fun `connection status is still part of the key but telemetry is not`() {
        val discovering = connectionNavigationKey(connected.copy(connection = ConnectionState.DISCOVERING))
        val ready = connectionNavigationKey(connected)
        assertNotEquals(discovering, ready)
        assertFalse(shouldLeaveScanOnConnection(discovering, isScanDestination = true))
        assertEquals(ready, connectionNavigationKey(connected.copy(temperatureC = 27f, fanPercent = 70)))
    }

    private data class Observation(val state: CoolerLiveState, val isScan: Boolean)
}
