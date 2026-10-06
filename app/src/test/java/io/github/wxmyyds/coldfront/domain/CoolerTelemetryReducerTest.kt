package io.github.wxmyyds.coldfront.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CoolerTelemetryReducerTest {
    private val state = CoolerLiveState(
        deviceType = CoolerDeviceType.JACKET_8_PRO,
        coolingOn = true,
        smartOn = true,
        boostOn = true,
        overcoldOn = true,
        fanMode = FanMode.AUTO,
        temperatureC = 14f,
        rgb = RGBConfig(LightEffect.ALWAYS_BRIGHT, 10, 20, 30),
    )

    @Test
    fun `empty reports on every characteristic are not authoritative`() {
        val uuids = listOf(
            CoolerBleConstants.COOLING_SWITCH_UUID,
            CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID,
            CoolerBleConstants.LIGHT_CONTROL_UUID,
            CoolerBleConstants.TEMPERATURE_NOTIFICATION_UUID,
            CoolerBleConstants.STATUS_UUID,
            CoolerBleConstants.RPM_UUID,
            CoolerBleConstants.POWER_UUID,
            CoolerBleConstants.PROTECTION_UUID,
            CoolerBleConstants.AUTO_MODE_CONTROL_UUID,
            CoolerBleConstants.BOOST_CONTROL_UUID,
        )
        for (uuid in uuids) {
            // handleData advances the write-fallback revision only for a non-null reduction.
            assertNull("Empty $uuid must not suppress an acknowledged write", reduce(uuid, byteArrayOf()))
        }
    }

    @Test
    fun `cooling accepts only 02 and 03 and ignores every unknown byte`() {
        for (raw in 0..255) {
            if (raw == 2 || raw == 3) continue
            assertNull("Unknown cooling byte $raw", reduce(CoolerBleConstants.COOLING_SWITCH_UUID, byteArrayOf(raw.toByte())))
        }
        val on = requireNotNull(reduce(CoolerBleConstants.COOLING_SWITCH_UUID, byteArrayOf(2)))
        assertTrue(on.state.coolingOn)
        assertEquals(FanMode.AUTO, on.state.fanMode)
        assertFalse(on.temperatureReported)
        val off = requireNotNull(reduce(CoolerBleConstants.COOLING_SWITCH_UUID, byteArrayOf(3)))
        assertFalse(off.state.coolingOn)
        assertEquals(FanMode.OFF, off.state.fanMode)
        val manual = CoolerTelemetryReducer.reduce(state.copy(smartOn = false), CoolerBleConstants.COOLING_SWITCH_UUID, byteArrayOf(2))
        assertEquals(FanMode.MANUAL, requireNotNull(manual).state.fanMode)
    }

    @Test
    fun `valid reports remain authoritative even when the state is unchanged`() {
        val repeatedCooling = reduce(CoolerBleConstants.COOLING_SWITCH_UUID, byteArrayOf(2))
        assertNotNull(repeatedCooling)
        assertEquals(state, repeatedCooling?.state)
        val repeatedTemperature = requireNotNull(reduce(CoolerBleConstants.STATUS_UUID, byteArrayOf(4, 20)))
        assertEquals(state, repeatedTemperature.state)
        assertTrue(repeatedTemperature.temperatureReported) // Refresh freshness even for identical samples.
    }

    @Test
    fun `unknown control values and undecodable packets are not authoritative`() {
        assertNull(reduce(CoolerBleConstants.LIGHT_CONTROL_UUID, byteArrayOf(0x11)))
        assertNull(reduce(CoolerBleConstants.AUTO_MODE_CONTROL_UUID, byteArrayOf(2)))
        assertNull(reduce(CoolerBleConstants.BOOST_CONTROL_UUID, byteArrayOf(-1)))
        assertNull(reduce(CoolerBleConstants.STATUS_UUID, byteArrayOf(0x7F, 20)))
        assertNull(reduce(CoolerBleConstants.RPM_UUID, byteArrayOf(1)))
        assertNull(reduce(UUID(0, 0), byteArrayOf(2)))
        assertNull(CoolerTelemetryReducer.reduce(state.copy(deviceType = null), CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID, byteArrayOf(80)))
    }

    @Test
    fun `temperature validation distinguishes standalone 1014 from tagged 1015`() {
        for (uuid in listOf(CoolerBleConstants.TEMPERATURE_NOTIFICATION_UUID, CoolerBleConstants.STATUS_UUID)) {
            assertNull(reduce(uuid, byteArrayOf()))
            assertNull(reduce(uuid, byteArrayOf(4, 81)))
            val valid = requireNotNull(reduce(uuid, byteArrayOf(4, 20)))
            assertEquals(14f, valid.state.temperatureC)
            assertTrue(valid.temperatureReported)
        }
        assertNull(reduce(CoolerBleConstants.STATUS_UUID, byteArrayOf(4)))
        val standalone = requireNotNull(reduce(CoolerBleConstants.TEMPERATURE_NOTIFICATION_UUID, byteArrayOf(4)))
        assertEquals(-2f, standalone.state.temperatureC) // Legitimate 4 C sample, existing -6 calibration.
        assertTrue(standalone.temperatureReported)
        assertEquals(14f, reduce(CoolerBleConstants.TEMPERATURE_NOTIFICATION_UUID, byteArrayOf(1, 0x25))?.state?.temperatureC)
        assertEquals(2f, reduce(CoolerBleConstants.TEMPERATURE_NOTIFICATION_UUID, byteArrayOf(8, 1, 0x25))?.state?.temperatureC)
    }

    @Test
    fun `status requires each tag payload and preserves unsigned rpm and power`() {
        for (packet in listOf(byteArrayOf(8), byteArrayOf(8, 0x18), byteArrayOf(9))) {
            assertNull(reduce(CoolerBleConstants.STATUS_UUID, packet))
        }
        val rpm = requireNotNull(reduce(CoolerBleConstants.STATUS_UUID, byteArrayOf(8, 0x18, 0x38)))
        assertEquals(6200, rpm.state.fanRpm)
        assertFalse(rpm.temperatureReported)
        val power = requireNotNull(reduce(CoolerBleConstants.STATUS_UUID, byteArrayOf(9, 200.toByte())))
        assertEquals(200, power.state.powerW)
        assertFalse(power.temperatureReported)
    }

    @Test
    fun `valid control packets preserve boolean bitfield fan and rgb semantics`() {
        assertEquals(FanMode.MANUAL, reduce(CoolerBleConstants.AUTO_MODE_CONTROL_UUID, byteArrayOf(0))?.state?.fanMode)
        assertEquals(true, reduce(CoolerBleConstants.AUTO_MODE_CONTROL_UUID, byteArrayOf(1))?.state?.smartOn)
        assertEquals(false, reduce(CoolerBleConstants.BOOST_CONTROL_UUID, byteArrayOf(0))?.state?.boostOn)
        assertEquals(true, reduce(CoolerBleConstants.BOOST_CONTROL_UUID, byteArrayOf(1))?.state?.boostOn)
        assertEquals(false, reduce(CoolerBleConstants.PROTECTION_UUID, byteArrayOf(3))?.state?.overcoldOn)
        assertEquals(true, reduce(CoolerBleConstants.PROTECTION_UUID, byteArrayOf(7))?.state?.overcoldOn)
        assertEquals(100, reduce(CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID, byteArrayOf(80))?.state?.fanPercent)
        assertEquals(state.rgb?.copy(effect = LightEffect.OFF), reduce(CoolerBleConstants.LIGHT_CONTROL_UUID, byteArrayOf(6))?.state?.rgb)
        // 0x05 (official scene effect, not offered by the app) is unknown: no state update.
        assertNull(reduce(CoolerBleConstants.LIGHT_CONTROL_UUID, byteArrayOf(5)))
        assertNull(CoolerTelemetryReducer.reduce(state.copy(rgb = null), CoolerBleConstants.LIGHT_CONTROL_UUID, byteArrayOf(4)))
        assertEquals(RGBConfig(LightEffect.ALWAYS_BRIGHT, 200, 30, 40), reduce(CoolerBleConstants.LIGHT_CONTROL_UUID, byteArrayOf(4, 200.toByte(), 30, 40))?.state?.rgb)
        assertEquals(6200, reduce(CoolerBleConstants.RPM_UUID, byteArrayOf(0x18, 0x38))?.state?.fanRpm)
        assertEquals(200, reduce(CoolerBleConstants.POWER_UUID, byteArrayOf(200.toByte()))?.state?.powerW)
    }

    private fun reduce(uuid: UUID, value: ByteArray) = CoolerTelemetryReducer.reduce(state, uuid, value)
}
