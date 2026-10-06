package io.github.wxmyyds.coldfront.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolerCapabilitiesTest {
    private val fan = CoolerBleConstants.FAN_SPEED_CHARACTERISTIC_UUID
    private val cooling = CoolerBleConstants.COOLING_SWITCH_UUID

    @Test
    fun `fan-only device exposes manual control without inventing a cooling report`() {
        val capabilities = CoolerCapabilities.fromCharacteristics(setOf(fan), setOf(fan))
        val state = CoolerLiveState(connection = ConnectionState.CONNECTED, capabilities = capabilities)
        assertTrue(capabilities.fanControl)
        assertFalse(capabilities.hasCoolingSwitch)
        assertFalse(capabilities.coolingControl)
        assertFalse(state.coolingOn)
        assertTrue(state.coolingAllowsControl)
        assertTrue(state.manualLevelEnabled)
    }

    @Test
    fun `a real cooling switch still gates fan control on its reported state`() {
        val capabilities = CoolerCapabilities.fromCharacteristics(setOf(fan, cooling), setOf(fan, cooling))
        val off = CoolerLiveState(connection = ConnectionState.CONNECTED, capabilities = capabilities)
        assertTrue(capabilities.coolingControl)
        assertFalse(off.manualLevelEnabled)
        assertTrue(off.copy(coolingOn = true).manualLevelEnabled)
        assertFalse(off.copy(coolingOn = true, smartOn = true).manualLevelEnabled)
        assertFalse(off.copy(coolingOn = true, connection = ConnectionState.DISCONNECTED).manualLevelEnabled)
    }

    @Test
    fun `read-only switch is displayed but cannot be written and still gates the fan`() {
        val capabilities = CoolerCapabilities.fromCharacteristics(setOf(fan, cooling), setOf(fan))
        assertTrue(capabilities.hasCoolingSwitch)
        assertFalse(capabilities.coolingControl)
        val off = CoolerLiveState(connection = ConnectionState.CONNECTED, capabilities = capabilities)
        assertFalse(off.manualLevelEnabled)
        assertTrue(off.copy(coolingOn = true).manualLevelEnabled)
    }

    @Test
    fun `unknown or read-only fan capabilities never enable a slider`() {
        assertFalse(CoolerLiveState(connection = ConnectionState.CONNECTED, coolingOn = true).manualLevelEnabled)
        val readOnlyFan = CoolerCapabilities.fromCharacteristics(setOf(fan), emptySet())
        assertFalse(CoolerLiveState(connection = ConnectionState.CONNECTED, capabilities = readOnlyFan).manualLevelEnabled)
    }

    @Test
    fun `optional controls follow writable characteristics independently`() {
        val controls = setOf(
            CoolerBleConstants.AUTO_MODE_CONTROL_UUID,
            CoolerBleConstants.BOOST_CONTROL_UUID,
            CoolerBleConstants.PROTECTION_UUID,
        )
        val writable = CoolerCapabilities.fromCharacteristics(controls, controls)
        assertTrue(writable.smartControl)
        assertTrue(writable.boostControl)
        assertTrue(writable.protectionControl)
        val readOnly = CoolerCapabilities.fromCharacteristics(controls, emptySet())
        assertFalse(readOnly.smartControl)
        assertFalse(readOnly.boostControl)
        assertFalse(readOnly.protectionControl)
    }
}
