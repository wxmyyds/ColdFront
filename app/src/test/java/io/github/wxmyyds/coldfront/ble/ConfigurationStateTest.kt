package io.github.wxmyyds.coldfront.ble

import io.github.wxmyyds.coldfront.domain.ConnectionState
import io.github.wxmyyds.coldfront.domain.CoolerBleConstants
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType
import io.github.wxmyyds.coldfront.domain.CoolerLiveState
import io.github.wxmyyds.coldfront.domain.CoolerTelemetryReducer
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurationStateTest {
    private val connected = CoolerLiveState(
        connection = ConnectionState.CONNECTED,
        deviceType = CoolerDeviceType.JACKET_8_PRO,
    )

    private fun report(state: CoolerLiveState, uuid: UUID, vararg bytes: Byte) =
        confirmedConfigurationState(state, uuid, CoolerTelemetryReducer.reduce(state, uuid, bytes))

    @Test
    fun `malformed configuration does not unlock default switch or fan values`() {
        for (uuid in configurationUuids - CoolerBleConstants.LIGHT_CONTROL_UUID) {
            assertFalse(report(connected, uuid).hasConfirmedConfiguration(uuid))
        }
        assertFalse(report(connected, CoolerBleConstants.AUTO_MODE_CONTROL_UUID, 2)
            .hasConfirmedConfiguration(CoolerBleConstants.AUTO_MODE_CONTROL_UUID))
        assertFalse(report(connected, CoolerBleConstants.BOOST_CONTROL_UUID, -1)
            .hasConfirmedConfiguration(CoolerBleConstants.BOOST_CONTROL_UUID))
        assertFalse(report(connected, CoolerBleConstants.COOLING_SWITCH_UUID, 0)
            .hasConfirmedConfiguration(CoolerBleConstants.COOLING_SWITCH_UUID))
    }

    @Test
    fun `valid OFF confirms configuration and malformed later replies preserve it`() {
        val uuid = CoolerBleConstants.AUTO_MODE_CONTROL_UUID
        val confirmed = report(connected, uuid, 0)
        assertFalse(confirmed.smartOn)
        assertTrue(confirmed.hasConfirmedConfiguration(uuid))
        assertEquals(confirmed, report(confirmed, uuid, 2))
        assertEquals(confirmed, report(confirmed, uuid, 0))
    }

    @Test
    fun `unknown light reply preserves recovery access without inventing a color`() {
        val uuid = CoolerBleConstants.LIGHT_CONTROL_UUID
        val state = report(connected, uuid, 0x11)
        assertTrue(state.hasConfirmedConfiguration(uuid))
        assertNull(state.rgb)
        assertEquals(setOf(uuid), state.confirmedConfiguration)
        val temperature = report(state, CoolerBleConstants.TEMPERATURE_NOTIFICATION_UUID, 20)
        assertEquals(state.confirmedConfiguration, temperature.confirmedConfiguration)
    }

    @Test
    fun `RGB revision advances only for parsed reports including identical repeats`() {
        val uuid = CoolerBleConstants.LIGHT_CONTROL_UUID
        val first = report(connected, uuid, 6)
        assertEquals(1L, first.rgbRevision)
        val repeated = report(first, uuid, 6)
        assertEquals(first.rgb, repeated.rgb)
        assertEquals(2L, repeated.rgbRevision)
        assertEquals(2L, report(repeated, uuid, 0x11).rgbRevision)
        assertEquals(2L, report(repeated, uuid, 4).rgbRevision)
        assertEquals(2L, report(repeated, CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID, 80).rgbRevision)
    }

    @Test
    fun `discovery limit is replayed once on readiness and not on repeated reports`() {
        val discovering = connected.copy(connection = ConnectionState.DISCOVERING)
        val limited = report(discovering, CoolerBleConstants.STATUS_UUID, 7, 6)
        assertFalse(powerLimitNeedsEnforcement(discovering, limited))
        val ready = limited.copy(connection = ConnectionState.CONNECTED)
        assertTrue(powerLimitNeedsEnforcement(limited, ready))
        val repeated = report(ready, CoolerBleConstants.STATUS_UUID, 7, 6)
        assertFalse(powerLimitNeedsEnforcement(ready, repeated))
        assertFalse(powerLimitNeedsEnforcement(ready, ready.copy(rssi = -40)))
        assertTrue(powerLimitNeedsEnforcement(ready, report(ready, CoolerBleConstants.STATUS_UUID, 7, 5)))
    }

    @Test
    fun `late control confirmation replays an existing limit but unrelated configuration does not`() {
        val limited = connected.copy(fanLimit = 6)
        val fan = report(limited, CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID, 80)
        assertTrue(powerLimitNeedsEnforcement(limited, fan))
        assertFalse(powerLimitNeedsEnforcement(fan, report(fan, CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID, 80)))
        val boost = report(fan, CoolerBleConstants.BOOST_CONTROL_UUID, 1)
        assertTrue(powerLimitNeedsEnforcement(fan, boost))
        assertFalse(powerLimitNeedsEnforcement(boost, report(boost, CoolerBleConstants.LIGHT_CONTROL_UUID, 6)))
        val malformed = report(limited, CoolerBleConstants.BOOST_CONTROL_UUID, 2)
        assertFalse(powerLimitNeedsEnforcement(limited, malformed))
    }

    @Test
    fun `late Boost ON report under an unchanged limit still triggers safety OFF`() {
        val limited = report(connected.copy(fanLimit = 6), CoolerBleConstants.BOOST_CONTROL_UUID, 0)
        val on = report(limited, CoolerBleConstants.BOOST_CONTROL_UUID, 1)
        assertEquals(limited.confirmedConfiguration, on.confirmedConfiguration)
        assertTrue(powerLimitNeedsEnforcement(limited, on))
        assertFalse(powerLimitNeedsEnforcement(on, report(on, CoolerBleConstants.BOOST_CONTROL_UUID, 1)))
    }

    @Test
    fun `unlimited and non 8 pro devices never request limit enforcement`() {
        assertFalse(powerLimitNeedsEnforcement(connected, connected.copy(fanLimit = 8)))
        assertFalse(powerLimitNeedsEnforcement(connected, connected.copy(fanLimit = null)))
        assertFalse(powerLimitNeedsEnforcement(connected, connected.copy(deviceType = CoolerDeviceType.JACKET_5, fanLimit = 6)))
    }
}
