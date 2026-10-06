package io.github.wxmyyds.coldfront.ui

import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryRecoveryTest {
    private val degraded = CoolerLiveState(
        connection = ConnectionState.CONNECTED,
        connectionSessionId = 1L,
        deviceAddress = "AA:BB:CC:DD:EE:FF",
        deviceType = CoolerDeviceType.JACKET_8_PRO,
        telemetryDegraded = true,
    )

    @Test
    fun `recovery selects the exact currently degraded device`() {
        assertEquals(
            TelemetryReconnectTarget("AA:BB:CC:DD:EE:FF", CoolerDeviceType.JACKET_8_PRO),
            telemetryReconnectTarget(degraded, degraded),
        )
        assertEquals(
            telemetryReconnectTarget(degraded, degraded),
            telemetryReconnectTarget(degraded, degraded.copy(temperatureC = 12f, fanPercent = 65)),
        )
    }

    @Test
    fun `old warning cannot disconnect a replacement session or device`() {
        listOf(
            degraded.copy(connectionSessionId = 2L),
            degraded.copy(deviceAddress = "11:22:33:44:55:66"),
            degraded.copy(deviceType = CoolerDeviceType.JACKET_5),
            degraded.copy(connection = ConnectionState.CONNECTING),
            degraded.copy(connection = ConnectionState.DISCONNECTED),
            degraded.copy(telemetryDegraded = false),
        ).forEach { current -> assertNull(telemetryReconnectTarget(degraded, current)) }
    }

    @Test
    fun `healthy missing or disconnected identity never produces a recovery target`() {
        assertNull(telemetryReconnectTarget(degraded.copy(telemetryDegraded = false), degraded))
        assertNull(telemetryReconnectTarget(degraded.copy(connection = ConnectionState.DISCONNECTED), degraded))
        for (incomplete in listOf(degraded.copy(deviceAddress = null), degraded.copy(deviceType = null))) {
            assertNull(telemetryReconnectTarget(incomplete, incomplete))
        }
        // Production creates a fresh state on each new connection, clearing the sticky warning.
        val fresh = CoolerLiveState(connection = ConnectionState.CONNECTED, connectionSessionId = 2L)
        assertNull(telemetryReconnectTarget(fresh, fresh))
    }
}
