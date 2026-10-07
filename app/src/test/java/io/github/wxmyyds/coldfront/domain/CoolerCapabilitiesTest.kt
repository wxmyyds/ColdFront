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
        val state = CoolerLiveState(
            connection = ConnectionState.CONNECTED,
            capabilities = capabilities,
            confirmedConfiguration = setOf(fan),
        )
        assertTrue(capabilities.fanControl)
        assertFalse(capabilities.hasCoolingSwitch)
        assertFalse(capabilities.coolingControl)
        assertFalse(state.coolingOn)
        assertTrue(state.coolingAllowsControl)
        assertTrue(state.manualLevelEnabled)
    }

    @Test
    fun `a real cooling switch gates fan control on its device report`() {
        val capabilities = CoolerCapabilities.fromCharacteristics(setOf(fan, cooling), setOf(fan, cooling))
        val off = CoolerLiveState(
            connection = ConnectionState.CONNECTED,
            capabilities = capabilities,
            confirmedConfiguration = setOf(fan, cooling),
        )
        assertTrue(capabilities.coolingControl)
        assertFalse(off.manualLevelEnabled)
        assertTrue(off.copy(coolingOn = true).manualLevelEnabled)
        assertFalse(off.copy(coolingOn = true, smartOn = true).manualLevelEnabled)
        assertFalse(off.copy(coolingOn = true, connection = ConnectionState.DISCONNECTED).manualLevelEnabled)
    }

    @Test
    fun `boost mode hides manual fan control like smart mode does`() {
        val capabilities = CoolerCapabilities.fromCharacteristics(setOf(fan, cooling), setOf(fan, cooling))
        val on = CoolerLiveState(
            connection = ConnectionState.CONNECTED,
            capabilities = capabilities,
            confirmedConfiguration = setOf(fan, cooling),
            coolingOn = true,
        )
        assertTrue(on.manualLevelEnabled)
        assertFalse(on.copy(boostOn = true).manualLevelEnabled)
    }

    @Test
    fun `read-only switch is reported but cannot be written and still gates the fan`() {
        val capabilities = CoolerCapabilities.fromCharacteristics(setOf(fan, cooling), setOf(fan))
        assertTrue(capabilities.hasCoolingSwitch)
        assertFalse(capabilities.coolingControl)
        val off = CoolerLiveState(
            connection = ConnectionState.CONNECTED,
            capabilities = capabilities,
            confirmedConfiguration = setOf(fan, cooling),
        )
        assertFalse(off.manualLevelEnabled)
        assertTrue(off.copy(coolingOn = true).manualLevelEnabled)
    }

    @Test
    fun `unknown configuration and read-only fan never enable a slider`() {
        assertFalse(CoolerLiveState(
            connection = ConnectionState.CONNECTED,
            coolingOn = true,
            capabilities = CoolerCapabilities(fanControl = true),
        ).manualLevelEnabled)
        val readOnlyFan = CoolerCapabilities.fromCharacteristics(setOf(fan), emptySet())
        assertFalse(CoolerLiveState(
            connection = ConnectionState.CONNECTED,
            capabilities = readOnlyFan,
            confirmedConfiguration = setOf(fan),
        ).manualLevelEnabled)
        val writableButUnconfirmed = CoolerLiveState(
            connection = ConnectionState.CONNECTED,
            coolingOn = true,
            capabilities = CoolerCapabilities(fanControl = true),
            confirmedConfiguration = setOf(CoolerBleConstants.COOLING_SWITCH_UUID),
        )
        assertFalse(writableButUnconfirmed.manualLevelEnabled)
    }

    @Test
    fun `optional controls follow writable characteristics`() {
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

    @Test
    fun `new connection starts with no configuration carried from the previous one`() {
        val oldConnection = CoolerLiveState(
            connection = ConnectionState.CONNECTED,
            connectionSessionId = 1,
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            capabilities = CoolerCapabilities(fanControl = true),
            confirmedConfiguration = setOf(fan, cooling),
            fanRaw = 73,
            fanPercent = 82,
            coolingOn = true,
            smartOn = true,
        )
        val newConnection = CoolerLiveState(
            connection = ConnectionState.CONNECTING,
            connectionSessionId = 2,
            deviceAddress = oldConnection.deviceAddress,
            capabilities = oldConnection.capabilities,
        )
        assertTrue(oldConnection.confirmedConfiguration.isNotEmpty())
        assertTrue(newConnection.confirmedConfiguration.isEmpty())
        assertTrue(newConnection.fanRaw == null)
        assertFalse(newConnection.coolingOn)
        assertFalse(newConnection.smartOn)
    }
}
